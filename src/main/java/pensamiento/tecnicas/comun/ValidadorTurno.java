package pensamiento.tecnicas.comun;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * El validador del turno (sección 4): decide si una pregunta redactada por el modelo puede llegar a la persona. La
 * rechaza si no termina en "?", si pasa del largo, si trae una frase de veredicto (el modelo no opina ni califica), voseo
 * o español peninsular (las formas de sensores/voseo-prohibido.txt, copiadas en validador/formas-prohibidas.txt) o
 * "usted" (el trato es de tú). Quien llama reintenta hasta dos veces y, si sigue fallando, usa la pregunta del banco.
 */
public final class ValidadorTurno {

    /** Por qué se rechazó un turno, para contarlo en la evaluación del modelo. */
    public enum Motivo {
        VACIO("vacío"), SIN_PREGUNTA("no termina en pregunta"), LARGO("demasiado largo"), VEREDICTO("trae un veredicto"),
        VOSEO("trae voseo o español peninsular"), USTED("trata de usted");

        private final String texto;

        Motivo(String texto) {
            this.texto = texto;
        }

        public String texto() {
            return texto;
        }
    }

    /** Frases de veredicto, en minúscula y sin tildes (las mismas del arnés de evaluación del hito 3). */
    public static final List<String> VEREDICTOS = List.of("tienes razon", "estas equivocad", "lo mejor es", "deberias", "te recomiendo",
            "es correcto", "es incorrecto", "es una buena idea", "es una mala idea", "la respuesta es", "estas en lo cierto", "no tienes razon");

    private static final String RUTA = "/validador/formas-prohibidas.txt";
    private static final Pattern PROHIBIDAS = compilar(leerFormas());
    private static final Pattern USTED = Pattern.compile("(?iu)(?<![\\p{L}\\p{N}_-])usted(?![\\p{L}\\p{N}_-])");

    private ValidadorTurno() {
    }

    /** El primer motivo de rechazo, o vacío si la pregunta puede llegar a la persona. */
    public static Optional<Motivo> rechazo(String texto, int palabrasMaximas) {
        if (Textos.vacio(texto)) {
            return Optional.of(Motivo.VACIO);
        }
        String limpio = ModeloLocal.limpiar(texto);
        if (!limpio.endsWith("?")) {
            return Optional.of(Motivo.SIN_PREGUNTA);
        }
        if (Textos.palabras(limpio) > palabrasMaximas) {
            return Optional.of(Motivo.LARGO);
        }
        String plegado = Textos.plegar(limpio);
        if (VEREDICTOS.stream().anyMatch(plegado::contains)) {
            return Optional.of(Motivo.VEREDICTO);
        }
        if (PROHIBIDAS.matcher(limpio).find()) {
            return Optional.of(Motivo.VOSEO);
        }
        if (USTED.matcher(limpio).find()) {
            return Optional.of(Motivo.USTED);
        }
        return Optional.empty();
    }

    /** El validador como predicado, para la petición de chat del puerto Ia. */
    public static Predicate<String> pregunta(int palabrasMaximas) {
        return texto -> rechazo(texto, palabrasMaximas).isEmpty();
    }

    /** Las formas prohibidas, una por línea, sin comentarios ni líneas vacías. */
    public static List<String> formasProhibidas() {
        return leerFormas();
    }

    private static List<String> leerFormas() {
        try (InputStream in = ValidadorTurno.class.getResourceAsStream(RUTA)) {
            if (in == null) {
                throw new IllegalStateException("No existe " + RUTA);
            }
            return Arrays.stream(new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")).map(String::strip)
                    .filter(l -> !l.isEmpty() && !l.startsWith("#")).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + RUTA, e);
        }
    }

    private static Pattern compilar(List<String> formas) {
        String alternativas = String.join("|", formas.stream().map(Pattern::quote).toList());
        return Pattern.compile("(?iu)(?<![\\p{L}\\p{N}_-])(" + alternativas + ")(?![\\p{L}\\p{N}_-])");
    }
}
