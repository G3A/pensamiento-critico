package pensamiento.web.usuarios;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import pensamiento.nucleo.Usuario;
import pensamiento.nucleo.puertos.RegistroAuditoria;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.nucleo.puertos.RepositorioUsuarios;
import pensamiento.nucleo.puertos.RepositorioUsuarios.TipoCodigo;
import pensamiento.web.seguridad.ContextoRls;
import pensamiento.web.seguridad.LimiteDeIntentos;

/**
 * Cuentas (hito 9). El administrador crea cada cuenta con un nombre y la app genera un código de alta de un uso; la persona
 * lo canjea y elige su contraseña (nunca se imprime una contraseña). El administrador también genera códigos para
 * restablecer (que cierran las sesiones abiertas de esa persona), desactiva y reactiva; cada persona cambia su contraseña
 * dando la actual. Sin autoregistro. Todo queda en la auditoría, nunca la contraseña.
 *
 * <p>La transacción vive aquí (innegociable 4): cada operación abre la suya y un dato inválido o repetido sale como
 * {@link DatosInvalidos} después de deshacerla, sin marcar ninguna transacción de afuera para deshacer.
 */
public class ServicioUsuarios {

    public static final Duration VIGENCIA_ALTA = Duration.ofDays(14);
    public static final Duration VIGENCIA_RESTABLECER = Duration.ofHours(72);
    public static final int TAMANO_PAGINA = 10;

    public static class DatosInvalidos extends RuntimeException {
        public DatosInvalidos(String mensaje) {
            super(mensaje);
        }
    }

    /** El código no existe, ya se usó, caducó o la cuenta está desactivada: 410. */
    public static class CodigoNoValido extends RuntimeException {
        public CodigoNoValido() {
            super("Ese código no sirve: puede estar mal escrito, ya se usó o caducó. Pide uno nuevo a quien te dio la cuenta.");
        }
    }

    /** Lo que ve el administrador una sola vez: el código en claro no se guarda. */
    public record CodigoEmitido(Usuario cuenta, TipoCodigo tipo, String codigo, Instant caducaEn) {
        public String legible() {
            return CodigoDeUnUso.legible(codigo);
        }
    }

    private final RepositorioUsuarios usuarios;
    private final RegistroAuditoria auditoria;
    private final PasswordEncoder codificador;
    private final Reloj reloj;
    private final TransactionTemplate tx;
    private final PoliticaDeContrasenas politica;
    private final CodigoDeUnUso codigos;
    private final Consumer<UUID> cerrarSesiones;
    private final LimiteDeIntentos limite;

    public ServicioUsuarios(RepositorioUsuarios usuarios, RegistroAuditoria auditoria, PasswordEncoder codificador, Reloj reloj,
                            PlatformTransactionManager gestor, PoliticaDeContrasenas politica, CodigoDeUnUso codigos,
                            Consumer<UUID> cerrarSesiones, LimiteDeIntentos limite) {
        this.limite = limite;
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.codificador = codificador;
        this.reloj = reloj;
        this.tx = new TransactionTemplate(gestor);
        this.politica = politica;
        this.codigos = codigos;
        this.cerrarSesiones = cerrarSesiones;
    }

    /**
     * La primera cuenta de una institución, siempre administrador, con la contraseña que eligió quien canjeó la invitación
     * del operador. Si la institución ya tiene cuentas no hace nada.
     */
    public Optional<Usuario> primerAdministrador(UUID institucion, String nombre, String contrasena) {
        return enTransaccion(() -> {
            String limpio = validarNombre(nombre);
            exigir(politica.problema(contrasena, Usuario.RolGlobal.ADMINISTRADOR, limpio));
            if (usuarios.contar(institucion) > 0) {
                return Optional.<Usuario>empty();
            }
            Usuario creado = usuarios.crear(Usuario.nueva(UUID.randomUUID(), institucion, limpio, Usuario.RolGlobal.ADMINISTRADOR)
                    .conClave(codificador.encode(contrasena), Usuario.TipoClave.CONTRASENA)).orElseThrow(() -> repetido(limpio));
            registrar(Optional.empty(), institucion, RegistroAuditoria.Accion.CREAR, creado.id());
            return Optional.of(creado);
        });
    }

    /** Nombre y contraseña válidos para el primer administrador, sin escribir nada (la invitación se valida antes de gastarla). */
    public void validarPrimerAdministrador(String nombre, String contrasena) {
        exigir(politica.problema(contrasena, Usuario.RolGlobal.ADMINISTRADOR, validarNombre(nombre)));
    }

    /** El administrador crea una cuenta sin clave y recibe su código de alta, que se muestra una sola vez. */
    public CodigoEmitido crear(UUID administradorId, UUID institucion, String nombre, Usuario.RolGlobal rol) {
        return enTransaccion(() -> {
            String limpio = validarNombre(nombre);
            Usuario creada = usuarios.crear(Usuario.nueva(UUID.randomUUID(), institucion, limpio, rol)).orElseThrow(() -> repetido(limpio));
            registrar(Optional.of(administradorId), institucion, RegistroAuditoria.Accion.CREAR, creada.id());
            return emitir(administradorId, creada, TipoCodigo.ALTA, VIGENCIA_ALTA);
        });
    }

    /** Un código nuevo de alta para quien perdió el suyo antes de canjearlo. */
    public CodigoEmitido codigoDeAltaNuevo(UUID administradorId, UUID institucion, UUID usuarioId) {
        return enTransaccion(() -> {
            Usuario cuenta = existente(institucion, usuarioId);
            if (cuenta.tieneClave()) {
                throw new DatosInvalidos("Esa cuenta ya tiene contraseña: genera un código para restablecerla.");
            }
            return emitir(administradorId, cuenta, TipoCodigo.ALTA, VIGENCIA_ALTA);
        });
    }

    /** Código de restablecimiento de un uso; cierra en el acto las sesiones abiertas de esa persona. */
    public CodigoEmitido restablecer(UUID administradorId, UUID institucion, UUID usuarioId) {
        CodigoEmitido emitido = enTransaccion(() -> {
            Usuario cuenta = existente(institucion, usuarioId);
            if (cuenta.id().equals(administradorId)) {
                throw new DatosInvalidos("Para tu propia cuenta usa «Cambiar mi contraseña».");
            }
            if (!cuenta.activo()) {
                throw new DatosInvalidos("Esa cuenta está desactivada: reactívala primero.");
            }
            return emitir(administradorId, cuenta, TipoCodigo.RESTABLECER, VIGENCIA_RESTABLECER);
        });
        cerrarSesiones.accept(usuarioId);
        return emitido;
    }

    /**
     * La persona canjea su código (de alta o de restablecimiento) en la entrada de su institución y elige su contraseña. Se
     * corre antes de saber quién es: la institución la fija la ruta. Una contraseña que no sirve no gasta el código.
     */
    public Usuario canjear(UUID institucion, String codigoEscrito, String contrasena, String repetida) {
        String codigo = CodigoDeUnUso.normalizar(codigoEscrito);
        if (codigo.isEmpty()) {
            throw new CodigoNoValido();
        }
        Usuario cuenta = ContextoRls.conInstitucion(institucion, () -> enTransaccion(() -> {
            String hash = CodigoDeUnUso.hash(codigo);
            RepositorioUsuarios.CodigoCanjeado vigente = usuarios.codigoVigente(institucion, hash, reloj.ahora()).orElseThrow(CodigoNoValido::new);
            Usuario dueno = existente(institucion, vigente.usuarioId());
            exigirNuevaContrasena(dueno, contrasena, repetida);   // antes de gastar el código
            usuarios.canjearCodigo(institucion, hash, reloj.ahora()).orElseThrow(CodigoNoValido::new);
            usuarios.cambiarClave(institucion, dueno.id(), codificador.encode(contrasena), Usuario.TipoClave.CONTRASENA);
            registrar(Optional.of(dueno.id()), institucion, RegistroAuditoria.Accion.CANJEAR_CODIGO, dueno.id());
            return dueno;
        }));
        cerrarSesiones.accept(cuenta.id());
        return cuenta;
    }

    /** Cada persona cambia su contraseña dando la actual (o su PIN, si todavía entra con PIN). */
    public void cambiarContrasena(UUID institucion, UUID usuarioId, String actual, String nueva, String repetida) {
        enTransaccion(() -> {
            Usuario cuenta = existente(institucion, usuarioId);
            if (actual == null || !cuenta.tieneClave() || !codificador.matches(actual, cuenta.claveHash())) {
                throw new DatosInvalidos("La contraseña actual no coincide.");
            }
            guardarNueva(cuenta, nueva, repetida);
            return null;
        });
    }

    /** Quien entró con su PIN de las versiones anteriores elige su contraseña antes de seguir (ya probó su PIN al entrar). */
    public void reemplazarPin(UUID institucion, UUID usuarioId, String nueva, String repetida) {
        enTransaccion(() -> {
            Usuario cuenta = existente(institucion, usuarioId);
            if (cuenta.tipoClave() != Usuario.TipoClave.PIN) {
                throw new DatosInvalidos("Tu cuenta ya tiene contraseña.");
            }
            guardarNueva(cuenta, nueva, repetida);
            return null;
        });
    }

    public Usuario desactivar(UUID administradorId, UUID institucion, UUID usuarioId) {
        Usuario desactivada = enTransaccion(() -> {
            Usuario cuenta = existente(institucion, usuarioId);
            if (cuenta.id().equals(administradorId)) {
                throw new DatosInvalidos("No puedes desactivar tu propia cuenta.");
            }
            usuarios.cambiarActivo(institucion, usuarioId, false);
            registrar(Optional.of(administradorId), institucion, RegistroAuditoria.Accion.DESACTIVAR, usuarioId);
            return cuenta.conActivo(false);
        });
        cerrarSesiones.accept(usuarioId);
        return desactivada;
    }

    public Usuario reactivar(UUID administradorId, UUID institucion, UUID usuarioId) {
        return enTransaccion(() -> {
            Usuario cuenta = existente(institucion, usuarioId);
            usuarios.cambiarActivo(institucion, usuarioId, true);
            registrar(Optional.of(administradorId), institucion, RegistroAuditoria.Accion.REACTIVAR, usuarioId);
            return cuenta.conActivo(true);
        });
    }

    /** Las claves de nombre de quienes esperan para volver a intentar entrar (RF-15). */
    public java.util.List<String> enEspera(UUID institucion) {
        return enTransaccion(() -> limite.nombresEnEspera(institucion));
    }

    /** El administrador levanta la espera de una persona: puede volver a intentar ya. Queda en la auditoría. */
    public void levantarEspera(UUID administradorId, UUID institucion, String claveDeNombre) {
        enTransaccion(() -> {
            limite.levantar(institucion, claveDeNombre);
            auditoria.registrar(new RegistroAuditoria.Evento(Optional.of(administradorId), institucion, RegistroAuditoria.Accion.LEVANTAR_ESPERA,
                    "usuario", usuarios.porNombre(institucion, claveDeNombre).map(Usuario::id), reloj.ahora(), Optional.of(Usuario.claveDeNombre(claveDeNombre))));
            return null;
        });
    }

    public RepositorioUsuarios.PaginaDeCuentas buscar(UUID institucion, String texto, int pagina) {
        return enTransaccion(() -> usuarios.buscar(institucion, texto == null ? "" : texto.strip(), Math.max(pagina, 0), TAMANO_PAGINA));
    }

    public Optional<TipoCodigo> codigoPendiente(UUID institucion, UUID usuarioId) {
        return enTransaccion(() -> usuarios.codigoPendiente(institucion, usuarioId, reloj.ahora()));
    }

    public Optional<Usuario> porId(UUID institucion, UUID usuarioId) {
        return enTransaccion(() -> usuarios.porId(institucion, usuarioId));
    }

    /** Nombre de 1 a 60 caracteres; devuelve el nombre sin espacios al borde. */
    public static String validarNombre(String nombre) {
        String limpio = nombre == null ? "" : nombre.strip();
        if (limpio.isEmpty() || limpio.length() > 60) {
            throw new DatosInvalidos("El nombre es obligatorio y tiene como máximo 60 caracteres.");
        }
        return limpio;
    }

    private void guardarNueva(Usuario cuenta, String nueva, String repetida) {
        exigirNuevaContrasena(cuenta, nueva, repetida);
        usuarios.cambiarClave(cuenta.institucionId(), cuenta.id(), codificador.encode(nueva), Usuario.TipoClave.CONTRASENA);
        registrar(Optional.of(cuenta.id()), cuenta.institucionId(), RegistroAuditoria.Accion.CAMBIAR_CLAVE, cuenta.id());
    }

    private void exigirNuevaContrasena(Usuario cuenta, String nueva, String repetida) {
        exigir(politica.problema(nueva, cuenta.rol(), cuenta.nombre()));
        if (!nueva.equals(repetida)) {
            throw new DatosInvalidos("Las dos contraseñas no coinciden.");
        }
    }

    private CodigoEmitido emitir(UUID administradorId, Usuario cuenta, TipoCodigo tipo, Duration vigencia) {
        String codigo = codigos.nuevo();
        Instant caduca = reloj.ahora().plus(vigencia);
        usuarios.guardarCodigo(cuenta.institucionId(), cuenta.id(), tipo, CodigoDeUnUso.hash(codigo), caduca, Optional.of(administradorId));
        registrar(Optional.of(administradorId), cuenta.institucionId(),
                tipo == TipoCodigo.ALTA ? RegistroAuditoria.Accion.CODIGO_ALTA : RegistroAuditoria.Accion.CODIGO_RESTABLECER, cuenta.id());
        return new CodigoEmitido(cuenta, tipo, codigo, caduca);
    }

    private Usuario existente(UUID institucion, UUID usuarioId) {
        return usuarios.porId(institucion, usuarioId).orElseThrow(() -> new DatosInvalidos("No existe esa persona."));
    }

    private void registrar(Optional<UUID> quien, UUID institucion, RegistroAuditoria.Accion accion, UUID cuenta) {
        auditoria.registrar(new RegistroAuditoria.Evento(quien, institucion, accion, "usuario", Optional.of(cuenta), reloj.ahora()));
    }

    private static DatosInvalidos repetido(String nombre) {
        return new DatosInvalidos("Ya existe una persona llamada «" + nombre + "» en esta institución. Agrega un apellido o una inicial.");
    }

    private static void exigir(Optional<String> problema) {
        problema.ifPresent(p -> {
            throw new DatosInvalidos(p);
        });
    }

    private <T> T enTransaccion(Supplier<T> accion) {
        return tx.execute(estado -> accion.get());
    }
}
