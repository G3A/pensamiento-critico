package pensamiento.nucleo;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Un turno de una sesión del Consejero (tabla turno_consejero). El de la persona es su respuesta; el del Consejero es una
 * pregunta o un ataque, del banco o redactado por el modelo y validado. Mientras el modelo redacta, el turno del Consejero
 * ya existe con la pregunta del banco y el estado "redactando"; al terminar se reemplaza por la versión validada.
 *
 * @param paso                qué toca en ese turno: "evidencia/informacion", "datos", "blanco", "A1", "sintesis" o "cierre"
 * @param elementoPropuesto   en un turno de la persona con el modelo: el elemento de Paul-Elder que propuso el segundo paso;
 *                            vacío si no propuso otro
 * @param propuestaAdoptada   si la persona adoptó esa propuesta
 */
public record TurnoConsejero(UUID id, UUID sesionId, int numero, Rol rol, String paso, String texto, Origen origen, Estado estado, int intentos,
                             Optional<Ejecucion.RegistroModelo> modelo, Optional<String> elementoPropuesto, Optional<String> porquePropuesto,
                             boolean propuestaAdoptada, Instant creadoEn) {

    public enum Rol {
        PERSONA, CONSEJERO;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    public enum Origen {
        PERSONA, BANCO, MODELO;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    public enum Estado {
        LISTO, REDACTANDO;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    public static final String CIERRE = "cierre";
    public static final String SINTESIS = "sintesis";

    public TurnoConsejero {
        if (numero < 1) {
            throw new IllegalArgumentException("Los turnos se numeran desde 1");
        }
        if (rol == Rol.PERSONA && origen != Origen.PERSONA) {
            throw new IllegalArgumentException("Un turno de la persona tiene origen persona");
        }
    }

    public boolean delConsejero() {
        return rol == Rol.CONSEJERO;
    }

    public boolean cierre() {
        return CIERRE.equals(paso);
    }
}
