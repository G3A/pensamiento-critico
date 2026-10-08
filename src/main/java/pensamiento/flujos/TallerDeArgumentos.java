package pensamiento.flujos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import pensamiento.tecnicas.f1.ResultadoMapa;

/**
 * Flujo A reducido, el Taller de argumentos (P14): un solo texto Argdown alimenta tres técnicas. T01 · Mapeo de
 * argumentos lo dibuja; de ese mapa salen las partes de T02 · Modelo de Toulmin que el texto sí dice, y el texto de
 * los enunciados va a T13 · Falacias como esquemas fallidos. Dominio puro: sin web ni base.
 */
public final class TallerDeArgumentos {

    /** Lo que el texto Argdown no dice y la persona completa en el panel Toulmin. */
    public record ExtrasToulmin(String respaldo, String fuenteRespaldo, String calificador) {
        public static final ExtrasToulmin VACIOS = new ExtrasToulmin(null, null, null);
    }

    private TallerDeArgumentos() {
    }

    /**
     * La entrada de T02 a partir del mapa (decisión del hito 2): la afirmación es la primera conclusión; los datos,
     * sus premisas a favor que no son supuestos; la garantía, sus supuestos a favor (#asumible u #oculta); la
     * refutación, la primera objeción contra ella; la respuesta, lo que ataca a esa objeción. Respaldo, fuente y
     * calificador los escribe la persona. Partes con varios textos se unen como oraciones seguidas.
     */
    public static Map<String, Object> entradaToulmin(ResultadoMapa mapa, ExtrasToulmin extras) {
        Map<String, Object> entrada = new LinkedHashMap<>();
        Optional<ResultadoMapa.Nodo> conclusion = mapa.nodos().stream().filter(n -> n.rol() == ResultadoMapa.Rol.CONCLUSION).findFirst();
        if (conclusion.isPresent()) {
            String c = conclusion.get().codigo();
            List<String> datos = new ArrayList<>();
            List<String> garantias = new ArrayList<>();
            List<String> refutaciones = new ArrayList<>();
            for (ResultadoMapa.ArgumentoMapa a : mapa.argumentos()) {
                if (!a.conclusion().equals(c)) {
                    continue;
                }
                for (String p : a.premisas()) {
                    ResultadoMapa.Nodo premisa = mapa.nodo(p);
                    if (a.sentido() == ResultadoMapa.Sentido.CONTRA) {
                        agregar(refutaciones, premisa.texto());
                    } else if (premisa.asumible()) {
                        agregar(garantias, premisa.texto());
                    } else {
                        agregar(datos, premisa.texto());
                    }
                }
            }
            poner(entrada, "afirmacion", conclusion.get().texto());
            poner(entrada, "datos", oraciones(datos));
            poner(entrada, "garantia", oraciones(garantias));
            if (!refutaciones.isEmpty()) {
                String objecion = mapa.nodos().stream().filter(n -> n.texto().equals(refutaciones.getFirst())).findFirst().orElseThrow().codigo();
                List<String> respuestas = new ArrayList<>();
                mapa.argumentos().stream().filter(a -> a.conclusion().equals(objecion) && a.sentido() == ResultadoMapa.Sentido.CONTRA)
                        .forEach(a -> a.premisas().forEach(p -> agregar(respuestas, mapa.nodo(p).texto())));
                poner(entrada, "refutacion", refutaciones.getFirst());
                poner(entrada, "respuestaRefutacion", oraciones(respuestas));
            }
        }
        poner(entrada, "respaldo", extras.respaldo());
        poner(entrada, "fuenteRespaldo", extras.fuenteRespaldo());
        poner(entrada, "calificador", extras.calificador());
        return entrada;
    }

    /** El texto que revisa T13: los enunciados del mapa en orden, cada uno como una oración. */
    public static String textoParaFalacias(ResultadoMapa mapa) {
        return oraciones(mapa.nodos().stream().map(ResultadoMapa.Nodo::texto).toList());
    }

    /** Cada texto termina en punto (o signo de cierre) y se une al siguiente con un espacio. */
    private static String oraciones(List<String> textos) {
        List<String> oraciones = new ArrayList<>();
        for (String texto : textos) {
            String t = texto.strip();
            oraciones.add(t.endsWith(".") || t.endsWith("!") || t.endsWith("?") ? t : t + ".");
        }
        return String.join(" ", oraciones);
    }

    private static void agregar(List<String> lista, String texto) {
        if (!lista.contains(texto)) {
            lista.add(texto);
        }
    }

    private static void poner(Map<String, Object> entrada, String campo, String valor) {
        if (valor != null && !valor.isBlank()) {
            entrada.put(campo, valor.strip());
        }
    }
}
