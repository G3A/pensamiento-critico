package pensamiento.tecnicas.f1;

import java.util.List;

/** Frases cortas en español para resúmenes y motivos: enumeraciones con «comillas» y conteos con plural. */
public final class Textos {

    private Textos() {
    }

    /** «A», «A» y «B», «A», «B» y «C». */
    public static String enumerarCitas(List<String> textos) {
        List<String> citas = textos.stream().map(t -> "«" + t + "»").toList();
        if (citas.size() == 1) {
            return citas.getFirst();
        }
        return String.join(", ", citas.subList(0, citas.size() - 1)) + " y " + citas.getLast();
    }

    /** "1 apoyo", "2 apoyos". */
    public static String contar(int n, String singular, String plural) {
        return n + " " + (n == 1 ? singular : plural);
    }
}
