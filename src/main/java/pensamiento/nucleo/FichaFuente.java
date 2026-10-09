package pensamiento.nucleo;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * La ficha de una fuente (tabla fuente, P12): los campos de SIFT, lectura lateral y CRAAP que la persona llena. Es lo que R01
 * necesita de la fuente más lo que la ficha muestra: el puntaje CRAAP con sus cinco criterios, las notas de SIFT y, si viene
 * de la biblioteca, el documento y la página. Si el documento se borró, la ficha queda con su nombre: "documento retirado".
 *
 * @param grupoOrigen     organización, autor o cadena de citas; define la independencia mutua de R03
 * @param independiente   independiente de quien afirma (lectura lateral); suma en R01
 * @param documentoNombre el nombre del documento de la biblioteca de donde salió, aunque ya no exista
 */
public record FichaFuente(UUID id, String titulo, Optional<String> autor, Optional<LocalDate> fecha, Fuente.TipoFuente tipo,
                          Optional<Fuente.DisenoEstudio> disenoEstudio, Optional<String> grupoOrigen, boolean independiente, boolean accesoOriginal,
                          Optional<Craap> craap, Sift sift, Optional<UUID> documentoId, Optional<String> documentoNombre, Optional<Integer> pagina) {

    /** Los cinco criterios de 0 a 5 y el puntaje de 0 a 25 calculado con los pesos de T21 · CRAAP de la persona al guardar. */
    public record Craap(int actualidad, int relevancia, int autoridad, int exactitud, int proposito, int puntaje) {
        public Craap {
            for (int v : new int[] {actualidad, relevancia, autoridad, exactitud, proposito}) {
                if (v < 0 || v > 5) {
                    throw new IllegalArgumentException("Cada criterio CRAAP va de 0 a 5");
                }
            }
            if (puntaje < 0 || puntaje > 25) {
                throw new IllegalArgumentException("El puntaje CRAAP va de 0 a 25");
            }
        }
    }

    /** Lo que la persona anotó en los movimientos de SIFT que dejan nota; vacío si no anotó. */
    public record Sift(String investigue, String cobertura, String contexto) {
        public static final Sift VACIA = new Sift("", "", "");

        public Sift {
            investigue = investigue == null ? "" : investigue;
            cobertura = cobertura == null ? "" : cobertura;
            contexto = contexto == null ? "" : contexto;
        }
    }

    public FichaFuente {
        if (titulo == null || titulo.isBlank()) {
            throw new IllegalArgumentException("Una fuente necesita título");
        }
        sift = sift == null ? Sift.VACIA : sift;
    }

    /** El documento de la biblioteca se borró pero la ficha lo citaba. */
    public boolean documentoRetirado() {
        return documentoId.isEmpty() && documentoNombre.isPresent();
    }

    /** Lo que R01 necesita de esta fuente. */
    public Fuente comoFuente() {
        return new Fuente(id, titulo, tipo, disenoEstudio, fecha, grupoOrigen, independiente, accesoOriginal, craap.map(Craap::puntaje));
    }
}
