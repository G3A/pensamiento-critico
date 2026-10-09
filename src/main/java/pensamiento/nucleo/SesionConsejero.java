package pensamiento.nucleo;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Una sesión del Consejero socrático (flujo C, tabla sesion_consejero). Guarda la postura, el modo, la configuración de
 * la técnica del modo tal como estaba al empezar y, al cerrar, la reflexión y la ejecución que produjo. Los turnos van
 * aparte ({@link TurnoConsejero}); la estrategia no se guarda: el motor la rehace desde la configuración y las respuestas.
 *
 * @param razones   en el modo debate, las razones de la postura con "en qué se apoya"; vacía en los otros modos
 * @param config    la configuración de la técnica del modo (T08, T10, T35 o T36), en JSON
 * @param usaModelo si la persona pidió que el modelo redacte las preguntas cuando esté disponible
 */
public record SesionConsejero(UUID id, UUID usuarioId, UUID institucionId, Optional<UUID> expedienteId, Modo modo, String postura,
                              List<Razon> razones, Json config, boolean usaModelo, Optional<Integer> confianzaAntes, boolean cierrePedido,
                              Estado estado, Optional<String> reflexion, Optional<Integer> confianzaDespues, Optional<UUID> ejecucionId,
                              Instant creadaEn, Optional<Instant> cerradaEn) {

    public enum Modo {
        ENSAYO("ensayo", IdTecnica.de("T08")), DECISION("decisión", IdTecnica.de("T08")), ESCALERA("escalera", IdTecnica.de("T10")),
        SOMBREROS("sombreros", IdTecnica.de("T35")), DEBATE("debate", IdTecnica.de("T36"));

        private final String nombre;
        private final IdTecnica tecnica;

        Modo(String nombre, IdTecnica tecnica) {
            this.nombre = nombre;
            this.tecnica = tecnica;
        }

        public String nombre() {
            return nombre;
        }

        /** La técnica cuya estrategia mueve el modo y cuya ejecución se guarda al cerrar. */
        public IdTecnica tecnica() {
            return tecnica;
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }

        public static Modo de(String valor) {
            return valueOf(valor.toUpperCase());
        }
    }

    public enum Estado {
        ABIERTA, CERRADA;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /** @param apoyo en qué se apoya, con los valores de T36 ("no_se", "causa"…) */
    public record Razon(String texto, String apoyo) {
    }

    public SesionConsejero {
        razones = razones == null ? List.of() : List.copyOf(razones);
        if (confianzaAntes.isPresent() && (confianzaAntes.get() < 0 || confianzaAntes.get() > 100)) {
            throw new IllegalArgumentException("La confianza va de 0 a 100");
        }
    }

    public boolean cerrada() {
        return estado == Estado.CERRADA;
    }
}
