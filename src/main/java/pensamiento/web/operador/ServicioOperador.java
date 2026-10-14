package pensamiento.web.operador;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;

import pensamiento.nucleo.Institucion;
import pensamiento.nucleo.Usuario;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.nucleo.puertos.RepositorioInstituciones;
import pensamiento.nucleo.puertos.RepositorioOperador;
import pensamiento.web.seguridad.RutasReservadas;
import pensamiento.web.usuarios.CodigoDeUnUso;
import pensamiento.web.usuarios.ServicioUsuarios;

/**
 * Operador del servicio (hito 8): crea instituciones, las suspende o reactiva e invita a su primer administrador con un
 * código de un solo uso que caduca a los 7 días. El canje crea la cuenta administrador con una contraseña de 12 caracteres
 * o más (hito 9), solo si la institución
 * todavía no tiene cuentas: así una invitación no sirve para meterse en una institución que ya funciona.
 */
public class ServicioOperador {

    public static final String NOMBRE_OPERADOR = "operador";
    public static final int LARGO_MINIMO_CLAVE = 12;
    public static final Duration VIGENCIA_INVITACION = Duration.ofDays(7);

    public static class DatosInvalidos extends RuntimeException {
        public DatosInvalidos(String mensaje) {
            super(mensaje);
        }
    }

    /** El código no existe, ya se usó, caducó, o la institución ya tiene cuentas o está suspendida. */
    public static class InvitacionNoValida extends RuntimeException {
        public InvitacionNoValida() {
            super("Ese código no sirve: puede estar mal escrito, ya se usó o caducó. Pide uno nuevo al operador del servicio.");
        }
    }

    /** Lo que ve el operador una sola vez al invitar: el código en claro no se guarda. */
    public record Invitacion(String codigo, Instant caducaEn) {
        public String legible() {
            return CodigoDeUnUso.legible(codigo);
        }
    }

    private final RepositorioOperador operador;
    private final RepositorioInstituciones instituciones;
    private final ServicioUsuarios usuarios;
    private final PasswordEncoder codificador;
    private final Reloj reloj;
    private final CodigoDeUnUso codigos;

    public ServicioOperador(RepositorioOperador operador, RepositorioInstituciones instituciones, ServicioUsuarios usuarios,
                            PasswordEncoder codificador, Reloj reloj, CodigoDeUnUso codigos) {
        this.operador = operador;
        this.instituciones = instituciones;
        this.usuarios = usuarios;
        this.codificador = codificador;
        this.reloj = reloj;
        this.codigos = codigos;
    }

    /** Primer arranque: crea el operador con la clave del secreto del despliegue si todavía no hay ninguno. */
    public boolean crearOperadorSiNoHay(String clave) {
        if (clave == null || clave.strip().length() < LARGO_MINIMO_CLAVE) {
            throw new DatosInvalidos("La clave del operador tiene " + LARGO_MINIMO_CLAVE + " caracteres o más");
        }
        return operador.crearSiNoHay(NOMBRE_OPERADOR, codificador.encode(clave.strip()));
    }

    public List<RepositorioOperador.InstitucionDelOperador> instituciones() {
        return operador.instituciones();
    }

    public List<RepositorioOperador.Evento> auditoria(int limite) {
        return operador.auditoria(limite);
    }

    public UUID crearInstitucion(UUID quien, String nombre, String ruta) {
        String nombreLimpio = nombre == null ? "" : nombre.strip();
        if (nombreLimpio.isEmpty() || nombreLimpio.length() > 120) {
            throw new DatosInvalidos("El nombre es obligatorio y tiene como máximo 120 caracteres");
        }
        String rutaLimpia = ruta == null ? "" : ruta.strip();
        String valida = RutasReservadas.deInstitucion(rutaLimpia).orElseThrow(() -> new DatosInvalidos(
                "La ruta lleva de 3 a 40 minúsculas sin tildes, números o guiones (sin guion al principio ni al final) y no puede "
                        + "ser una ruta de la app"));
        // Se revisa antes de escribir: una excepción dentro de la transacción la dejaría marcada para deshacer.
        if (operador.instituciones().stream().anyMatch(i -> i.institucion().ruta().equals(valida))) {
            throw new RepositorioOperador.RutaRepetida(valida);
        }
        return operador.crearInstitucion(quien, nombreLimpio, valida);
    }

    public boolean suspender(UUID quien, UUID institucion) {
        return operador.cambiarEstado(quien, institucion, Institucion.Estado.SUSPENDIDA);
    }

    public boolean reactivar(UUID quien, UUID institucion) {
        return operador.cambiarEstado(quien, institucion, Institucion.Estado.ACTIVA);
    }

    public Invitacion invitar(UUID quien, UUID institucion) {
        String codigo = codigos.nuevo();
        Instant caduca = reloj.ahora().plus(VIGENCIA_INVITACION);
        operador.invitar(quien, institucion, codificador.encode(codigo), caduca);
        return new Invitacion(codigo, caduca);
    }

    /**
     * Canjea la invitación de la institución de esa ruta y crea su primer administrador. Corre dentro de una transacción con
     * la institución fijada: si algo falla después de marcar el código como usado, todo se deshace.
     */
    public Usuario canjear(UUID institucion, String codigoEscrito, String nombre, String contrasena) {
        usuarios.validarPrimerAdministrador(nombre, contrasena);
        String codigo = CodigoDeUnUso.normalizar(codigoEscrito);
        if (codigo.isEmpty()) {
            throw new InvitacionNoValida();
        }
        RepositorioOperador.InvitacionVigente vigente = operador.vigentes(institucion).stream()
                .filter(v -> codificador.matches(codigo, v.codigoHash())).findFirst()
                .orElseThrow(InvitacionNoValida::new);
        if (!operador.canjear(vigente.id())) {
            throw new InvitacionNoValida();
        }
        return usuarios.primerAdministrador(institucion, nombre, contrasena).orElseThrow(InvitacionNoValida::new);
    }

    /** El id de la institución activa con esa ruta (para la página de canje). */
    public java.util.Optional<UUID> institucionActiva(String ruta) {
        return instituciones.activaPorRuta(ruta);
    }
}
