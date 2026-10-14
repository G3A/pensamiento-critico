package pensamiento.nucleo.puertos;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.Usuario;

/**
 * Puerto de cuentas de cada institución (hito 9): alta, códigos de un uso para dar de alta y restablecer, cambio de clave,
 * desactivar y reactivar, y búsqueda paginada. El nombre es único dentro de la institución sin distinguir mayúsculas,
 * tildes ni espacios repetidos ({@link Usuario#claveDeNombre(String)}). Ninguna operación lanza por un dato repetido o que
 * no existe: lo dice el valor de retorno, así la transacción del servicio sigue sana (innegociable 4).
 */
public interface RepositorioUsuarios {

    enum TipoCodigo {
        ALTA, RESTABLECER;

        public String enBaseDeDatos() {
            return name().toLowerCase();
        }
    }

    /** Una página de la búsqueda: las cuentas de esa página, ordenadas por nombre, y cuántas hay en total. */
    record PaginaDeCuentas(List<Usuario> cuentas, long total) {
    }

    /** Lo que deja un código canjeado: de quién era y para qué. */
    record CodigoCanjeado(UUID usuarioId, TipoCodigo tipo) {
    }

    /** Crea la cuenta; vacío si ya hay alguien con la misma clave de nombre en la institución (no lanza). */
    Optional<Usuario> crear(Usuario usuario);

    Optional<Usuario> porId(UUID institucionId, UUID id);

    /** Busca por la clave del nombre: "ANA MARÍA" encuentra a "Ana María". También antes de entrar. */
    Optional<Usuario> porNombre(UUID institucionId, String nombre);

    /**
     * Cuentas cuyo nombre contiene el texto (sin distinguir mayúsculas ni tildes; vacío trae todas), ordenadas por nombre.
     * {@code pagina} empieza en 0.
     */
    PaginaDeCuentas buscar(UUID institucionId, String texto, int pagina, int tamano);

    long contar(UUID institucionId);

    /** Las cuentas activas de ese rol en la institución, por nombre (hito 10: elegir el docente de un grupo). */
    List<Usuario> porRol(UUID institucionId, Usuario.RolGlobal rol);

    /** Fija la clave (hash) y su tipo; false si la cuenta no existe. */
    boolean cambiarClave(UUID institucionId, UUID id, String claveHash, Usuario.TipoClave tipo);

    /** Activa o desactiva; false si la cuenta no existe. */
    boolean cambiarActivo(UUID institucionId, UUID id, boolean activo);

    /**
     * Guarda un código de un uso (solo su hash) para esa cuenta y deja sin efecto los anteriores sin usar de la misma
     * cuenta. {@code creadoPor} es quien lo generó.
     */
    void guardarCodigo(UUID institucionId, UUID usuarioId, TipoCodigo tipo, String codigoHash, Instant caducaEn, Optional<UUID> creadoPor);

    /** Mira un código sin gastarlo: de quién es, si existe, no se usó, no caducó y la cuenta está activa. */
    Optional<CodigoCanjeado> codigoVigente(UUID institucionId, String codigoHash, Instant ahora);

    /**
     * Canjea el código una sola vez: la primera vez devuelve de quién era; vacío si no existe, ya se usó, caducó, la cuenta
     * está desactivada o es de otra institución.
     */
    Optional<CodigoCanjeado> canjearCodigo(UUID institucionId, String codigoHash, Instant ahora);

    /** Si la cuenta tiene un código sin usar y sin caducar (para mostrarlo en /usuarios). */
    Optional<TipoCodigo> codigoPendiente(UUID institucionId, UUID usuarioId, Instant ahora);
}
