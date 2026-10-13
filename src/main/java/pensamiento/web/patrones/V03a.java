package pensamiento.web.patrones;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pensamiento.tecnicas.f5.ConfigAch;
import pensamiento.tecnicas.f5.EjecutorAch;
import pensamiento.tecnicas.f5.ResultadoAch;

/**
 * Patrón V03a, matriz de consistencia (T28 · Análisis de hipótesis en competencia (ACH)). Record tipado del
 * fragmento tag/v/v03a.jte: la raíz lleva id="res-{idEjecucion}" y data-patron="V03a"; toda celda y todo
 * estado llevan texto además del color. La tarjeta nunca dice "confirmada" ni "verdadera" (corrección 13).
 *
 * @param idEjecucion vacío si es una evaluación sin guardar o un ejemplo del catálogo
 * @param sufijo      distingue dos fragmentos sin guardar en la misma página (por ejemplo, un ejemplo)
 */
public record V03a(Optional<UUID> idEjecucion, String sufijo, ResultadoAch valor, Modo modo) {

    public static final String PATRON = "V03a";

    public String idRaiz() {
        return "res-" + idEjecucion.map(UUID::toString).orElse("T28-" + sufijo);
    }

    public String resumen() {
        return valor.resumen();
    }

    public boolean lectura() {
        return modo == Modo.LECTURA;
    }

    public boolean escalaNumerica() {
        return valor.escala() == ConfigAch.Escala.NUMERICA;
    }

    /** Lo que se ve en la celda: "C", "I", "N" o "+2", "0", "-1". */
    public String marca(String celda) {
        return escalaNumerica() && !celda.startsWith("-") && !"0".equals(celda) ? "+" + celda : celda;
    }

    /** Palabra que acompaña a la marca para que el estado no dependa del color: lenguaje llano, sin "consistente". */
    public String palabra(String celda) {
        return switch (celda) {
            case "C", "1" -> "encaja";
            case "2" -> "encaja mucho";
            case "I", "-1" -> "va en contra";
            case "-2" -> "va muy en contra";
            default -> "no dice nada";
        };
    }

    /** Clase CSS del estado: el color sale de app.css, nunca de un atributo de estilo. */
    public String clase(String celda) {
        return switch (celda) {
            case "C", "1", "2" -> "celda celda-consistente";
            case "I", "-1", "-2" -> "celda celda-inconsistente";
            default -> "celda celda-neutral";
        };
    }

    public String peso(ResultadoAch.EvidenciaEvaluada e) {
        return e.peso() == null ? "sin peso" : "pesa " + e.peso() + " (" + e.pesoValor() + ")";
    }

    public boolean esMenosRefutada(String codigo) {
        return valor.menosRefutadas().contains(codigo);
    }

    /**
     * Una idea con lo que tiene en contra: la tarjeta cuenta pistas en vez de decir "menos refutada", que se leía como
     * "ganó" (ensayo de RF-08). El medidor va de 0 al máximo de las ideas.
     */
    public record Idea(String codigo, String texto, String lectura, boolean enPie, int pistasEnContra, int peso, int maximo, String cuenta) {
    }

    public List<Idea> ideas() {
        int maximo = Math.max(1, valor.hipotesis().stream().mapToInt(ResultadoAch.HipotesisEvaluada::inconsistencias).max().orElse(0));
        List<Idea> ideas = new ArrayList<>();
        for (ResultadoAch.HipotesisEvaluada h : valor.hipotesis()) {
            boolean enPie = esMenosRefutada(h.codigo());
            String lectura = enPie ? "sigue en pie"
                    : valor.masRefutadas().contains(h.codigo()) ? "la que más tiene en contra" : "tiene pistas en contra";
            ideas.add(new Idea(h.codigo(), h.texto(), lectura, enPie, h.evidenciasEnContra().size(), h.inconsistencias(), maximo, cuenta(h)));
        }
        return ideas;
    }

    /** "ninguna pista en contra", "1 pista en contra (pesa 3)", "2 pistas en contra (pesan 5)". */
    private String cuenta(ResultadoAch.HipotesisEvaluada h) {
        int n = h.evidenciasEnContra().size();
        if (n == 0) {
            return "ninguna pista en contra";
        }
        String texto = n + (n == 1 ? " pista en contra" : " pistas en contra");
        return h.inconsistencias() == n ? texto : texto + " (" + (n == 1 ? "pesa " : "pesan ") + h.inconsistencias() + ")";
    }

    /** El titular nunca dice "confirmada" ni "verdadera", y tampoco "menos refutada": dice qué no la contradice (corrección 13). */
    public String titular() {
        if (valor.empate()) {
            return "Quedan empatadas " + EjecutorAch.enumerar(valor.menosRefutadas()) + ": tienen lo mismo en contra. Ninguna está probada.";
        }
        ResultadoAch.HipotesisEvaluada h = valor.hipotesis(valor.menosRefutadas().getFirst());
        return h.inconsistencias() == 0
                ? "Ninguna de tus pistas va en contra de " + h.codigo() + ": «" + h.texto() + "». Todavía no está probada."
                : "La idea con menos en contra es " + h.codigo() + ": «" + h.texto() + "». Todavía no está probada.";
    }

    /** Lo que la tarjeta explica además del titular, en frases cortas y sin la sigla de la técnica. */
    public List<String> explicacion() {
        List<String> frases = new ArrayList<>();
        frases.add("Esta técnica sirve para descartar ideas, no para confirmarlas.");
        if (valor.empate()) {
            frases.add("Para separarlas, busca una pista que encaje con una y vaya en contra de la otra.");
        } else if (valor.hipotesis(valor.menosRefutadas().getFirst()).inconsistencias() == 0) {
            frases.add("Que nada la contradiga todavía no la demuestra.");
        }
        if (!valor.masRefutadas().isEmpty()) {
            List<String> mas = valor.masRefutadas();
            ResultadoAch.HipotesisEvaluada primera = valor.hipotesis(mas.getFirst());
            frases.add(mas.size() == 1
                    ? primera.codigo() + " es la que más tiene en contra (" + EjecutorAch.enumerar(primera.evidenciasEnContra())
                            + "). Pierde fuerza, pero no queda descartada para siempre: una pista nueva puede cambiarlo."
                    : EjecutorAch.enumerar(mas) + " son las que más tienen en contra. Pierden fuerza, pero no quedan descartadas para siempre.");
        }
        return frases;
    }

    /** Qué hacer ahora, uno por cada idea que sigue en pie, con la razón. */
    public List<String> siguientesPasos() {
        List<String> pasos = new ArrayList<>();
        for (ResultadoAch.Verificacion v : valor.verificar()) {
            if (v.evidencia() == null) {
                pasos.add(v.hipotesis() + " no tiene ninguna pista a favor: busca una que la distinga de las demás.");
            } else {
                pasos.add("Comprueba que " + v.evidencia() + " sea cierta: «" + valor.evidencia(v.evidencia()).texto() + "». Es la pista que más apoya a "
                        + v.hipotesis() + "; si resulta falsa, " + v.hipotesis() + " se queda sin ese apoyo.");
            }
        }
        return pasos;
    }

    public String leyenda() {
        return escalaNumerica()
                ? "De +2 (encaja mucho) a -2 (va muy en contra); 0 no dice nada. Solo cuentan los negativos."
                : "Cada pista frente a cada idea: encaja, va en contra o no dice nada. Solo cuentan las que van en contra.";
    }
}
