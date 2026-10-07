package pensamiento.web.usuarios;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.security.crypto.password.PasswordEncoder;

import pensamiento.nucleo.Usuario;
import pensamiento.nucleo.puertos.RegistroAuditoria;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.nucleo.puertos.RepositorioUsuarios;

/**
 * Cuentas: el administrador crea y desactiva; toda cuenta tiene PIN (Argon2); sin autoregistro.
 * Cada creación y desactivación queda en auditoría.
 */
public class ServicioUsuarios {

    public static final Pattern PIN_VALIDO = Pattern.compile("^[0-9]{4,8}$");
    public static final String NOMBRE_ADMINISTRADOR = "administrador";
    public static final String NOMBRE_INSTITUCION = "Instalación local";

    public static class DatosInvalidos extends RuntimeException {
        public DatosInvalidos(String mensaje) {
            super(mensaje);
        }
    }

    private final RepositorioUsuarios usuarios;
    private final RegistroAuditoria auditoria;
    private final PasswordEncoder codificador;
    private final Reloj reloj;

    public ServicioUsuarios(RepositorioUsuarios usuarios, RegistroAuditoria auditoria, PasswordEncoder codificador, Reloj reloj) {
        this.usuarios = usuarios;
        this.auditoria = auditoria;
        this.codificador = codificador;
        this.reloj = reloj;
    }

    /** Primer arranque: crea la institución y la cuenta administrador con el PIN del secreto. Idempotente. */
    public Optional<Usuario> inicializar(String pinAdministrador) {
        UUID institucion = usuarios.institucionUnica().orElseGet(() -> usuarios.crearInstitucion(NOMBRE_INSTITUCION));
        if (usuarios.contar(institucion) > 0) {
            return Optional.empty();
        }
        return Optional.of(crear(Optional.empty(), institucion, NOMBRE_ADMINISTRADOR, pinAdministrador, Usuario.RolGlobal.ADMINISTRADOR));
    }

    public Usuario crearPersona(UUID administradorId, UUID institucion, String nombre, String pin) {
        return crear(Optional.of(administradorId), institucion, nombre, pin, Usuario.RolGlobal.PERSONA);
    }

    private Usuario crear(Optional<UUID> quien, UUID institucion, String nombre, String pin, Usuario.RolGlobal rol) {
        String nombreLimpio = nombre == null ? "" : nombre.trim();
        if (nombreLimpio.isEmpty() || nombreLimpio.length() > 60) {
            throw new DatosInvalidos("El nombre es obligatorio y tiene como máximo 60 caracteres");
        }
        if (pin == null || !PIN_VALIDO.matcher(pin).matches()) {
            throw new DatosInvalidos("El PIN es obligatorio: de 4 a 8 dígitos");
        }
        Usuario creado = usuarios.guardar(new Usuario(UUID.randomUUID(), institucion, nombreLimpio, codificador.encode(pin), rol, true));
        auditoria.registrar(new RegistroAuditoria.Evento(quien, institucion, RegistroAuditoria.Accion.CREAR, "usuario",
                Optional.of(creado.id()), reloj.ahora()));
        return creado;
    }

    public Usuario desactivar(UUID administradorId, UUID institucion, UUID usuarioId) {
        Usuario usuario = usuarios.porId(institucion, usuarioId)
                .orElseThrow(() -> new DatosInvalidos("No existe esa persona"));
        if (usuario.id().equals(administradorId)) {
            throw new DatosInvalidos("No puedes desactivar tu propia cuenta");
        }
        Usuario desactivado = usuarios.guardar(new Usuario(usuario.id(), usuario.institucionId(), usuario.nombre(),
                usuario.pinHash(), usuario.rol(), false));
        auditoria.registrar(new RegistroAuditoria.Evento(Optional.of(administradorId), institucion,
                RegistroAuditoria.Accion.DESACTIVAR, "usuario", Optional.of(usuarioId), reloj.ahora()));
        return desactivado;
    }

    public List<Usuario> todos(UUID institucion) {
        return usuarios.todos(institucion);
    }
}
