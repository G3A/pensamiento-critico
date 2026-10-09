package pensamiento.nucleo;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Un cambio de opinión (tabla cambio_opinion, R05): la confianza que la persona declaró sobre una afirmación antes y
 * después, con la causa. Es inmutable: solo se inserta. El registro global (T46 · Registro de cambios de opinión, P20)
 * llega en el hito 7; desde el hito 5 lo escriben las técnicas y el Consejero al cerrar.
 *
 * @param texto       el texto de la afirmación, leído de la tabla afirmacion
 * @param ejecucionId la ejecución que lo produjo; vacío si se borró
 */
public record CambioOpinion(UUID id, UUID afirmacionId, String texto, int confianzaAntes, int confianzaDespues, Causa causa,
                            Optional<UUID> ejecucionId, Instant creadoEn) {

    /** Las causas permitidas por la tabla. El Consejero ofrece evidencia, steelman y manual. */
    public enum Causa {
        EVIDENCIA, STEELMAN, REVISION, REGLA, MANUAL;

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
