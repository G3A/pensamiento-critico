package pensamiento.nucleo;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Un cambio de opinión (tabla cambio_opinion, R05): la confianza que la persona declaró sobre una afirmación antes y
 * después, con la causa. Es inmutable: solo se inserta. Lo escriben T08, el cierre del Consejero, la ficha de verificación y
 * el registro a mano de T46 · Registro de cambios de opinión, que los muestra todos en una línea de tiempo (P20).
 *
 * @param texto       el texto de la afirmación, leído de la tabla afirmacion
 * @param ejecucionId la ejecución que lo produjo; vacío si se borró
 */
public record CambioOpinion(UUID id, UUID afirmacionId, String texto, int confianzaAntes, int confianzaDespues, Causa causa,
                            Optional<UUID> ejecucionId, Instant creadoEn) {

    /**
     * Las causas permitidas por la tabla, en el orden del resumen de T46 · Registro de cambios de opinión. El Consejero ofrece
     * evidencia, steelman y manual; presión social (V10) se registra a mano en P20.
     */
    public enum Causa {
        EVIDENCIA("evidencia nueva"), STEELMAN("steelman o debate"), REVISION("revisión de una decisión"), REGLA("cambio de regla"),
        MANUAL("a mano"), PRESION("presión social");

        private final String nombre;

        Causa(String nombre) {
            this.nombre = nombre;
        }

        /** Cómo la dice el registro: "evidencia nueva", "presión social". */
        public String nombre() {
            return nombre;
        }

        /** Evidencia, steelman, revisión y regla son razones; a mano y presión social, no. */
        public boolean esRazon() {
            return this == EVIDENCIA || this == STEELMAN || this == REVISION || this == REGLA;
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }

        public static Causa de(String valor) {
            return valueOf(valor.toUpperCase());
        }
    }

    public CambioOpinion {
        Declarado.validar(confianzaAntes, confianzaDespues);
    }

    /**
     * Lo que un ejecutor (o el Consejero al cerrar) declara para guardar en la misma transacción que la ejecución: la
     * afirmación ya producida por esa ejecución, la confianza antes y después y la causa.
     */
    public record Declarado(UUID id, UUID afirmacionId, int confianzaAntes, int confianzaDespues, Causa causa) {
        public Declarado {
            validar(confianzaAntes, confianzaDespues);
            if (causa == null) {
                throw new IllegalArgumentException("Un cambio de opinión necesita su causa");
            }
        }

        static void validar(int antes, int despues) {
            if (antes < 0 || antes > 100 || despues < 0 || despues > 100) {
                throw new IllegalArgumentException("La confianza va de 0 a 100: " + antes + " → " + despues);
            }
            if (antes == despues) {
                throw new IllegalArgumentException("No hay cambio de opinión si la confianza no cambió");
            }
        }
    }
}
