package pensamiento.nucleo;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * El banco del Dojo de razonamiento (catalogo/dojo.json): los conceptos en el orden en que salen por primera vez y sus retos,
 * cada uno con su respuesta correcta escrita a mano. Un reto de identificar, analizar o evaluar trae opciones; uno de crear trae
 * una rúbrica de chequeos por palabras y una respuesta modelo. Ninguna calificación sale de un modelo. Reglas en docs/dojo.md.
 */
public record BancoDojo(List<Concepto> conceptos, List<Reto> retos) {

    /** @param id "T13:generalizacion"; @param tecnica T13, T14, T18 o T19 */
    public record Concepto(String id, String tecnica, String nombre, String definicion) {
        public IdTecnica idTecnica() {
            return IdTecnica.de(tecnica);
        }
    }

    public record Opcion(String id, String texto) {
    }

    /** Un chequeo de la rúbrica de "crear": sinMarcas, conAlguna o largo (con su mínimo de palabras). */
    public record Chequeo(String texto, String tipo, List<String> marcas, Integer minimo) {
        public Chequeo {
            marcas = marcas == null ? List.of() : List.copyOf(marcas);
        }

        /** Compara en minúsculas, sin tildes y por palabra o frase completa. */
        public boolean cumple(String respuesta) {
            String r = " " + plegar(respuesta) + " ";
            return switch (tipo) {
                case "sinMarcas" -> marcas.stream().noneMatch(m -> r.contains(" " + plegar(m).strip() + " "));
                case "conAlguna" -> marcas.stream().anyMatch(m -> r.contains(" " + plegar(m).strip() + " "));
                case "largo" -> respuesta.strip().split("\\s+").length >= (minimo == null ? 0 : minimo) && !respuesta.isBlank();
                default -> throw new IllegalStateException("Chequeo desconocido: " + tipo);
            };
        }
    }

    /**
     * @param correcta  el id de la opción correcta; nulo en crear
     * @param rubrica   vacía salvo en crear
     */
    public record Reto(String id, String concepto, NivelBloom nivel, String ambito, String texto, String pregunta, List<Opcion> opciones,
                       String correcta, List<Chequeo> rubrica, String respuestaModelo, String explicacion) {
        public Reto {
            opciones = opciones == null ? List.of() : List.copyOf(opciones);
            rubrica = rubrica == null ? List.of() : List.copyOf(rubrica);
        }

        public boolean esDeEscribir() {
            return nivel == NivelBloom.CREAR;
        }

        public Optional<Opcion> opcion(String id) {
            return opciones.stream().filter(o -> o.id().equals(id)).findFirst();
        }

        /** Acierto si la opción es la correcta o, en crear, si la respuesta cumple todos los chequeos. */
        public boolean califica(String respuesta) {
            if (respuesta == null || respuesta.isBlank()) {
                return false;
            }
            return esDeEscribir() ? rubrica.stream().allMatch(c -> c.cumple(respuesta)) : respuesta.strip().equals(correcta);
        }
    }

    public BancoDojo {
        conceptos = List.copyOf(conceptos);
        retos = List.copyOf(retos);
    }

    public Optional<Concepto> concepto(String id) {
        return conceptos.stream().filter(c -> c.id().equals(id)).findFirst();
    }

    public Optional<Reto> reto(String id) {
        return retos.stream().filter(r -> r.id().equals(id)).findFirst();
    }

    /** Su posición en el banco: el orden en que los conceptos nuevos salen. */
    public int orden(String conceptoId) {
        for (int i = 0; i < conceptos.size(); i++) {
            if (conceptos.get(i).id().equals(conceptoId)) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    /** Minúsculas, sin tildes, y todo lo que no es letra, cifra o % pasa a espacio. */
    static String plegar(String texto) {
        String sinTildes = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return sinTildes.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9%]+", " ").strip();
    }
}
