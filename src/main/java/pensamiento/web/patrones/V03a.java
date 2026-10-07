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

    /** Palabra que acompaña a la marca para que el estado no dependa del color. */
    public String palabra(String celda) {
        return switch (celda) {
            case "C", "1" -> "consistente";
            case "2" -> "muy consistente";
            case "I", "-1" -> "inconsistente";
            case "-2" -> "muy inconsistente";
            default -> "neutral";
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
        return e.peso() == null ? "sin peso" : "peso " + e.peso() + " (" + e.pesoValor() + ")";
    }

    public boolean esMenosRefutada(String codigo) {
        return valor.menosRefutadas().contains(codigo);
    }

    /** "Menos refutada: H1, la feria." o "Empate: H1 y H2 quedan igual de refutadas." */
    public String titular() {
        if (valor.empate()) {
            int n = valor.hipotesis(valor.menosRefutadas().getFirst()).inconsistencias();
            return "Empate: " + EjecutorAch.enumerar(valor.menosRefutadas()) + " quedan igual de refutadas ("
                    + n + (n == 1 ? " inconsistencia ponderada" : " inconsistencias ponderadas") + " cada una).";
        }
        ResultadoAch.HipotesisEvaluada h = valor.hipotesis(valor.menosRefutadas().getFirst());
        return "Menos refutada: " + h.codigo() + ", " + h.texto() + ".";
    }

    /** Lo que la tarjeta explica además del titular, en frases cortas. */
    public List<String> explicacion() {
        List<String> frases = new ArrayList<>();
        if (!valor.empate()) {
            ResultadoAch.HipotesisEvaluada h = valor.hipotesis(valor.menosRefutadas().getFirst());
            if (h.inconsistencias() == 0) {
                frases.add("Ninguna evidencia la contradice, pero eso no la confirma.");
            }
        } else {
            frases.add("ACH no puede elegir entre ellas con esta evidencia: busca una evidencia que sea consistente con una e inconsistente con la otra.");
        }
        if (!valor.masRefutadas().isEmpty()) {
            List<String> mas = valor.masRefutadas();
            ResultadoAch.HipotesisEvaluada primera = valor.hipotesis(mas.getFirst());
            frases.add(mas.size() == 1
                    ? primera.codigo() + " es la más refutada, por " + EjecutorAch.enumerar(primera.evidenciasEnContra()) + "."
                    : EjecutorAch.enumerar(mas) + " son las más refutadas.");
        }
        frases.add("Recuerda: ACH elimina, no confirma.");
        return frases;
    }

    /** Siguientes pasos, uno por cada hipótesis menos refutada. */
    public List<String> siguientesPasos() {
        List<String> pasos = new ArrayList<>();
        for (ResultadoAch.Verificacion v : valor.verificar()) {
            if (v.evidencia() == null) {
                pasos.add(v.hipotesis() + " no tiene ninguna evidencia a favor: busca una evidencia que la distinga de las demás.");
            } else {
                pasos.add("Verificar " + v.evidencia() + ": " + valor.evidencia(v.evidencia()).texto() + ".");
            }
        }
        return pasos;
    }

    public String leyenda() {
        return escalaNumerica()
                ? "De +2 (muy consistente) a -2 (muy inconsistente); 0 es neutral. Solo los negativos suman inconsistencias."
                : "C consistente · I inconsistente · N neutral. Solo las I suman inconsistencias.";
    }
}
