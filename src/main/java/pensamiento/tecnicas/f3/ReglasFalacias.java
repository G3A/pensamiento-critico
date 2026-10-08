package pensamiento.tecnicas.f3;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reglas léxicas en español de T13 · Falacias como esquemas fallidos (RF-10). Cada regla reconoce un esquema
 * de Walton por sus palabras y señala la pregunta crítica que quedó sin responder; nunca dice "falacia": eso
 * lo confirma la persona (R06). Se comparan sin mayúsculas ni tildes. Las reglas van en el orden del
 * catálogo (catalogo/esquemas.json): los esquemas más específicos primero, porque una oración puede encajar en
 * varios y la primera marca es la que cuenta en el banco de fragmentos.
 */
public final class ReglasFalacias {

    /**
     * Una regla. El grupo con nombre "p" del patrón, si existe, es la pista que se cita en el porqué; si no,
     * la coincidencia entera.
     *
     * @param pista texto fijo para citar en vez de la coincidencia (por ejemplo "o… o…"); nulo si no hace falta
     */
    public record Regla(String esquema, int pregunta, Pattern patron, String pista, String explicacion) {
    }

    /** Una oración del texto con su posición, en caracteres del texto original. */
    public record Oracion(int inicio, int fin, String texto) {
    }

    /** Lo que una regla encontró en una oración. */
    public record Hallazgo(Oracion oracion, Regla regla, String pista) {
        public String porque() {
            return "Encontré «" + pista + "»: " + regla.explicacion();
        }
    }

    private static Regla r(String esquema, int pregunta, String patron, String explicacion) {
        return new Regla(esquema, pregunta, Pattern.compile(patron), null, explicacion);
    }

    private static Regla r(String esquema, int pregunta, String patron, String pista, String explicacion) {
        return new Regla(esquema, pregunta, Pattern.compile(patron), pista, explicacion);
    }

    /** Patrones escritos en minúscula y sin tildes, igual que el texto plegado. */
    public static final List<Regla> REGLAS = List.of(
            r("ad_hominem", 1, "(?<p>tienen? algo que esconder)",
                    "descalifica a quien opina por lo que supuestamente oculta, no por sus razones."),
            r("ad_hominem", 1, "(?<p>que va a saber|si ni siquiera|no le hagan caso|no le hagas caso)",
                    "descalifica a quien habla por su situación, no por sus razones."),
            r("ad_hominem", 2, "\\b(?<p>(es|son) (un |una |unos |unas )?(perezos|vag|ignorant|mentiros|corrupt|incompetent|floj|ladron)\\w*)[^.]*\\b(asi que|por eso|entonces)\\b",
                    "pone un calificativo a la persona y con eso descarta lo que propone."),

            r("alternativas", 1, "^(?<p>o)\\b[^.]*\\bo\\b", "o… o…",
                    "presenta solo dos opciones como si no hubiera otras."),
            r("alternativas", 1, "(?<p>no hay otra (salida|opcion|alternativa|manera))",
                    "da por hecho que no hay más opciones."),

            r("pendiente_resbaladiza", 1, "^si\\b[^.]*\\b(?<p>luego|despues|al final|terminara|terminaremos|terminaran)\\b",
                    "encadena pasos desde un primer «si…» hasta un final malo sin mostrar que cada paso sea probable."),

            r("autoridad", 2, "\\b(?<p>(el|la|los|las|un|una|nuestro|nuestra) (proveedor|proveedora|vendedor|vendedora|fabricante|distribuidor|distribuidora)\\w* (dice|dijo|asegura|afirma|recomienda|promete))\\b",
                    "la razón es lo que dice alguien que gana si le crees."),
            r("autoridad", 1, "\\b(?<p>(influenciador|influenciadora|famoso|famosa|actor|actriz|cantante|futbolista|youtuber)\\w*)\\b[^.]*\\b(dice|dijo|asegura|afirma)\\b",
                    "quien lo dice es conocido, pero no se sabe si sabe del tema."),
            r("autoridad", 1, "(?<p>que es \\w+( \\w+)?,? (dice|dijo|asegura|afirma))\\b",
                    "cita la profesión de quien lo dice, que no es la del tema."),
            r("autoridad", 4, "\\b(?<p>(un|una) (experto|experta|especialista)\\b[^.]*?\\b(dijo|dice|asegura))\\b",
                    "se apoya en que alguien experto lo dijo, sin decir en qué se basa."),

            r("analogia", 1, "\\b(?<p>es como)\\b",
                    "compara dos casos sin decir en qué se parecen para esta conclusión."),
            r("analogia", 2, "^(?<p>si (a )?(mi|mis|el|la|los|las|al)\\b)[^,]{3,80},[^.]*\\btambien\\b",
                    "lo que pasó en un caso se pasa al otro sin revisar en qué se diferencian."),
            r("analogia", 2, "\\b(?<p>asi como)\\b[^.]*\\b(tambien|deberiamos|debemos)\\b",
                    "lo que pasó en un caso se pasa al otro sin revisar en qué se diferencian."),

            r("causa_efecto", 1, "\\b(?<p>desde que|despues de que|despues que)\\b[^.]*\\b(asi que|por eso|por lo tanto|es la causa|son la causa|tiene la culpa|tienen la culpa)\\b",
                    "que una cosa viniera después de la otra no muestra que la causara."),
            r("causa_efecto", 2, "\\b(asi que|por eso|entonces) [^.]*\\b(?<p>sube|suben|baja|bajan|aumenta|aumentan|mejora|mejoran|causa|causan|produce|producen)\\b",
                    "concluye que una cosa produce la otra solo porque van juntas."),

            r("signo", 2, "\\b(?<p>(es|son) (senal|sintoma|prueba) de que)\\b",
                    "toma una señal como prueba sin descartar otras explicaciones."),
            r("signo", 2, "\\b(?<p>seguro (que )?(no|si|le|les|lo|la))\\b",
                    "saca una conclusión segura de una sola señal."),

            r("ejemplo", 3, "\\b(?<p>no le hizo nada|no les hizo nada|y vivio \\d+)\\b",
                    "un caso que salió bien no muestra lo que suele pasar."),
            r("ejemplo", 2, "^(mi|un|una|conozco a un|conozco a una|a mi)\\b[^.]*[;,]\\s*((asi que|entonces|por eso) )?(?<p>(los|las|a nadie|nadie|todos|todas|ninguno|ninguna)\\b[^,.;]{0,60})",
                    "de un solo caso saca una regla para todos."),

            r("opinion_popular", 2, "\\b(?<p>dicen que (casi )?(todos|todo el mundo|la mayoria))\\b",
                    "da por hecho lo que se dice que piensa la mayoría, sin contarlo."),
            r("opinion_popular", 1, "\\b(?<p>todo el mundo|todos|todas|la mayoria)\\b[^.]*\\b(asi que|por eso|entonces)\\b",
                    "la razón es cuánta gente lo cree o lo hace."),

            r("clasificacion_verbal", 2, "\\b(?<p>ya es)\\b[^.]*\\b(asi que|por eso|entonces)\\b",
                    "acomoda la definición para que el caso entre en la categoría."),
            r("clasificacion_verbal", 1, "\\b(?<p>(es|son) (una |un )?(emergencia|delito|robo|acoso|estudiar|estudio|trabajar|trabajo|violencia|abuso|censura|discriminacion|ilegal))\\b[^.]*\\b(asi que|por eso|entonces)\\b",
                    "pone el caso en una categoría y le aplica lo que se dice de toda esa categoría."),

            r("consecuencias", 3, "\\b(?<p>no puede ser que)\\b[^.]*\\b(porque eso|eso) (significaria|implicaria|querria decir)\\b",
                    "rechaza algo por lo malo que sería que fuera cierto."),
            r("consecuencias", 2, "\\b(?<p>(hara|haria|traera|traeria|dara|daria) \\w+)\\b[^.]*\\b(asi que|por eso|entonces) hay que\\b",
                    "solo mira una consecuencia buena; faltan las del otro lado."),
            r("consecuencias", 1, "^si (no )?[^,]{3,80}, [^.]*\\b(?<p>se va a|se van a|va a|van a)\\b",
                    "anuncia una consecuencia sin decir qué tan probable es."));

    /** Identificadores de los esquemas que tienen reglas, en el orden del catálogo. */
    public static final List<String> ESQUEMAS = REGLAS.stream().map(Regla::esquema).distinct().toList();

    private ReglasFalacias() {
    }

    /**
     * Todas las marcas del texto: por cada oración, a lo sumo una por esquema activo (la primera regla del
     * esquema que coincide), en el orden del texto y, dentro de la oración, en el orden del catálogo.
     */
    public static List<Hallazgo> buscar(String texto, Set<String> esquemasActivos) {
        List<Hallazgo> hallazgos = new ArrayList<>();
        for (Oracion oracion : oraciones(texto)) {
            String plegada = plegar(oracion.texto());
            for (String esquema : ESQUEMAS) {
                if (!esquemasActivos.contains(esquema)) {
                    continue;
                }
                REGLAS.stream().filter(r -> r.esquema().equals(esquema))
                        .map(r -> encontrar(r, oracion, plegada))
                        .flatMap(Optional::stream)
                        .findFirst()
                        .ifPresent(hallazgos::add);
            }
        }
        return hallazgos;
    }

    private static Optional<Hallazgo> encontrar(Regla regla, Oracion oracion, String plegada) {
        Matcher m = regla.patron().matcher(plegada);
        if (!m.find()) {
            return Optional.empty();
        }
        if (regla.pista() != null) {
            return Optional.of(new Hallazgo(oracion, regla, regla.pista()));
        }
        int inicio = m.start();
        int fin = m.end();
        if (regla.patron().pattern().contains("(?<p>") && m.group("p") != null) {
            inicio = m.start("p");
            fin = m.end("p");
        }
        // El plegado conserva el largo: la pista sale del texto original, con sus tildes y mayúsculas.
        return Optional.of(new Hallazgo(oracion, regla, oracion.texto().substring(inicio, fin)));
    }

    /** Parte en oraciones por punto, signo de cierre o salto de línea; sin espacios en los bordes. */
    public static List<Oracion> oraciones(String texto) {
        List<Oracion> oraciones = new ArrayList<>();
        int inicio = 0;
        for (int i = 0; i <= texto.length(); i++) {
            boolean fin = i == texto.length();
            boolean corte = fin || texto.charAt(i) == '\n'
                    || (".!?".indexOf(texto.charAt(i)) >= 0 && (i + 1 == texto.length() || Character.isWhitespace(texto.charAt(i + 1))));
            if (!corte) {
                continue;
            }
            int finOracion = fin || texto.charAt(i) == '\n' ? i : i + 1;
            agregar(oraciones, texto, inicio, finOracion);
            inicio = finOracion;
        }
        return oraciones;
    }

    private static void agregar(List<Oracion> oraciones, String texto, int inicio, int fin) {
        while (inicio < fin && Character.isWhitespace(texto.charAt(inicio))) {
            inicio++;
        }
        while (fin > inicio && Character.isWhitespace(texto.charAt(fin - 1))) {
            fin--;
        }
        if (fin > inicio) {
            oraciones.add(new Oracion(inicio, fin, texto.substring(inicio, fin)));
        }
    }

    /** Minúsculas y sin tildes, carácter por carácter, para que las posiciones coincidan con el original. */
    static String plegar(String texto) {
        StringBuilder sb = new StringBuilder(texto.length());
        for (int i = 0; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (c < 128) {
                sb.append(Character.toLowerCase(c));
            } else {
                String base = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
                sb.append(base.toLowerCase(Locale.ROOT).charAt(0));
            }
        }
        return sb.toString();
    }
}
