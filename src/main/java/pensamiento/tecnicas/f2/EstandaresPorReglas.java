package pensamiento.tecnicas.f2;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import pensamiento.catalogo.BancoSocratico;
import pensamiento.tecnicas.comun.Textos;
import pensamiento.tecnicas.f1.EjecutorPaulElder.Elemento;
import pensamiento.tecnicas.f1.EjecutorPaulElder.Estandar;

/**
 * Los estándares de Paul-Elder que se pueden puntuar por reglas al cierre de una sesión socrática (claridad, exactitud,
 * profundidad, amplitud y lógica), con 10, 5 o 0 y su motivo. Nunca un puntaje global; los otros cuatro estándares se
 * puntúan a mano en T04 · Elementos y estándares de Paul-Elder. Reglas en docs/ejemplos/T08.md.
 */
public final class EstandaresPorReglas {

    private static final Pattern NUMERO = Pattern.compile("\\d");

    private EstandaresPorReglas() {
    }

    /** @param elementos el texto de cada elemento lleno */
    public static List<ResultadoPreguntasSocraticas.EstandarPanel> puntuar(BancoSocratico banco, String postura, Map<Elemento, String> elementos) {
        return List.of(claridad(banco, postura, elementos), exactitud(banco, elementos), profundidad(elementos), amplitud(elementos), logica(elementos));
    }

    private static boolean lleno(Map<Elemento, String> elementos, Elemento e) {
        return !Textos.vacio(elementos.get(e));
    }

    private static ResultadoPreguntasSocraticas.EstandarPanel claridad(BancoSocratico banco, String postura, Map<Elemento, String> elementos) {
        if (lleno(elementos, Elemento.CONCEPTOS)) {
            return panel(Estandar.CLARIDAD, 10, "Los conceptos están definidos.");
        }
        Optional<String> difuso = Marcas.primera(postura, banco.marcas("difuso"));
        return difuso.map(d -> panel(Estandar.CLARIDAD, 0, "Faltan los conceptos y la postura tiene un término difuso: «" + d + "»."))
                .orElseGet(() -> panel(Estandar.CLARIDAD, 5, "Faltan los conceptos, aunque la postura no tiene términos difusos."));
    }

    private static ResultadoPreguntasSocraticas.EstandarPanel exactitud(BancoSocratico banco, Map<Elemento, String> elementos) {
        if (!lleno(elementos, Elemento.INFORMACION)) {
            return panel(Estandar.EXACTITUD, 0, "Falta la información.");
        }
        String info = elementos.get(Elemento.INFORMACION);
        if (NUMERO.matcher(info).find() || Marcas.primera(info, banco.marcas("fuente")).isPresent()) {
            return panel(Estandar.EXACTITUD, 10, "La información trae un número o una fuente.");
        }
        return panel(Estandar.EXACTITUD, 5, "La información no trae número ni fuente.");
    }

    private static ResultadoPreguntasSocraticas.EstandarPanel profundidad(Map<Elemento, String> elementos) {
        boolean supuestos = lleno(elementos, Elemento.SUPUESTOS);
        boolean implicaciones = lleno(elementos, Elemento.IMPLICACIONES);
        if (supuestos && implicaciones) {
            return panel(Estandar.PROFUNDIDAD, 10, "Hay supuestos e implicaciones.");
        }
        if (supuestos || implicaciones) {
            return panel(Estandar.PROFUNDIDAD, 5, supuestos ? "Faltan las implicaciones." : "Faltan los supuestos.");
        }
        return panel(Estandar.PROFUNDIDAD, 0, "Faltan los supuestos y las implicaciones.");
    }

    private static ResultadoPreguntasSocraticas.EstandarPanel amplitud(Map<Elemento, String> elementos) {
        return lleno(elementos, Elemento.PUNTOS_DE_VISTA) ? panel(Estandar.AMPLITUD, 10, "Hay otro punto de vista.")
                : panel(Estandar.AMPLITUD, 0, "Falta otro punto de vista.");
    }

    private static ResultadoPreguntasSocraticas.EstandarPanel logica(Map<Elemento, String> elementos) {
        return lleno(elementos, Elemento.INFERENCIAS) ? panel(Estandar.LOGICA, 10, "Hay una inferencia explícita.")
                : panel(Estandar.LOGICA, 0, "Falta la inferencia: cómo pasas de lo que sabes a la conclusión.");
    }

    private static ResultadoPreguntasSocraticas.EstandarPanel panel(Estandar e, int puntaje, String motivo) {
        String estado = puntaje == 10 ? "cumple" : puntaje == 5 ? "a medias" : "falta";
        return new ResultadoPreguntasSocraticas.EstandarPanel(e.toString(), e.nombre(), puntaje, estado, motivo, puntaje == 10 ? null : e.pregunta());
    }
}
