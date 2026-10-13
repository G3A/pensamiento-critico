package pensamiento.nucleo.puertos;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.Institucion;

/**
 * Lo que puede hacer el operador del servicio (hito 8): fuera de toda institución, crea instituciones, las suspende o
 * reactiva e invita a su primer administrador. No lee datos de ninguna institución: solo su nombre, ruta, estado y si
 * ya tiene cuentas. Cada acción queda en su auditoría propia. El canje de la invitación también pasa por aquí.
 */
public interface RepositorioOperador {

    record Operador(UUID id, String claveHash) {
    }

    /** Una institución vista por el operador: sin nada de sus personas, salvo si ya tiene alguna cuenta. */
    record InstitucionDelOperador(Institucion institucion, Instant creadaEn, boolean tieneCuentas) {
    }

    record InvitacionVigente(UUID id, String codigoHash) {
    }

    enum Accion {
        CREAR_INSTITUCION, SUSPENDER, REACTIVAR, INVITAR, CANJEAR_INVITACION,
        /** Lo escribe docker/respaldo/institucion.sh al borrar una institución completa, no la app. */
        BORRAR_INSTITUCION;

        public String enBaseDeDatos() {
            return name().toLowerCase();
        }
    }

    /** Una línea de la auditoría del operador; el canje no tiene operador (lo hace quien recibió la invitación). */
    record Evento(Instant fecha, Accion accion, String ruta, Optional<UUID> operador, UUID institucion) {
    }

    Optional<Operador> porNombre(String nombre);

    /** Crea el operador solo si todavía no hay ninguno; devuelve si lo creó. */
    boolean crearSiNoHay(String nombre, String claveHash);

    /** Todas las instituciones, de la más antigua a la más nueva. */
    List<InstitucionDelOperador> instituciones();

    /** Crea una institución activa. Una ruta que ya existe lanza RutaRepetida; un operador que no existe, OperadorDesconocido. */
    UUID crearInstitucion(UUID operador, String nombre, String ruta);

    /** Suspende o reactiva; devuelve false si la institución no existe. */
    boolean cambiarEstado(UUID operador, UUID institucion, Institucion.Estado estado);

    /** Guarda una invitación (solo el hash del código) y deja sin efecto las anteriores sin usar de esa institución. */
    UUID invitar(UUID operador, UUID institucion, String codigoHash, Instant caducaEn);

    /** Invitaciones sin usar y sin caducar de una institución activa. */
    List<InvitacionVigente> vigentes(UUID institucion);

    /** Marca la invitación como usada una sola vez: true la primera vez, false si ya se usó, caducó o no existe. */
    boolean canjear(UUID invitacion);

    /** Las últimas acciones del operador y los canjes, de la más reciente a la más antigua. */
    List<Evento> auditoria(int limite);

    class RutaRepetida extends RuntimeException {
        public RutaRepetida(String ruta) {
            super("Ya existe una institución con la ruta \"" + ruta + "\"");
        }
    }

    class OperadorDesconocido extends RuntimeException {
        public OperadorDesconocido() {
            super("El operador no existe");
        }
    }
}
