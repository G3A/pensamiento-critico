package pensamiento.web.patrones;

import java.util.List;

import pensamiento.nucleo.puertos.Grafico;
import pensamiento.tecnicas.f7.ResultadoArbolMece;
import pensamiento.tecnicas.f7.ResultadoIshikawa;

/**
 * El DOT de los diagramas del hito 4, generado solo en el servidor con el convenio de V01 (sección 7): cada nodo con id
 * igual al identificador de su afirmación y class por papel; los nodos de estructura (puntos de la espina, categorías)
 * con el id del fragmento más un sufijo. Sin colores: los pone app.css. Todo texto del usuario pasa por Grafico.cadena.
 */
public final class GeneradorDotDiagramas {

    private GeneradorDotDiagramas() {
    }

    /** V06, árbol de T42 · Árbol de hipótesis MECE: de izquierda a derecha, de la raíz a las hojas. */
    public static String arbol(ResultadoArbolMece arbol) {
        StringBuilder sb = cabecera("mece", "nodesep=0.25, ranksep=0.45");
        String raiz = arbol.raizId().toString();
        nodo(sb, raiz, "raiz", arbol.raiz());
        for (ResultadoArbolMece.NodoArbol n : arbol.nodos()) {
            String clase = n.clase().css() + (n.solape() ? " solape" : "");
            String etiqueta = n.codigo() + " · " + n.texto() + (n.clase() == ResultadoArbolMece.Clase.RAMA_VACIA ? "\nrama vacía" : "")
                    + (n.solape() ? "\nse solapa" : "");
            nodo(sb, n.afirmacionId().toString(), clase, etiqueta);
        }
        for (ResultadoArbolMece.NodoArbol n : arbol.nodos()) {
            String padre = n.padre() == null ? raiz : arbol.nodos().stream().filter(x -> x.codigo().equals(n.padre())).findFirst()
                    .map(x -> x.afirmacionId().toString()).orElse(raiz);
            arista(sb, padre, n.afirmacionId().toString(), "descompone", null);
        }
        return sb.append("}\n").toString();
    }

    /**
     * V07, espina de pescado de T43 · Diagrama de Ishikawa: una espina de puntos que termina en el efecto; las categorías se
     * cuelgan de a dos por punto, en su orden, y cada causa de su categoría.
     *
     * @param idFragmento el id de la raíz del fragmento (res-…): prefijo de los nodos de estructura
     */
    public static String espina(ResultadoIshikawa ishikawa, String idFragmento) {
        StringBuilder sb = cabecera("ishikawa", "nodesep=0.25, ranksep=0.35");
        List<ResultadoIshikawa.Categoria> categorias = ishikawa.categorias();
        int puntos = Math.max(1, (categorias.size() + 1) / 2);
        for (int p = 1; p <= puntos; p++) {
            String id = idFragmento + "-espina-" + p;
            sb.append("  ").append(Grafico.cadena(id)).append(" [id=").append(Grafico.cadena(id)).append(", class=\"espina\", shape=point, label=\"\"];\n");
        }
        String efecto = ishikawa.efectoId().toString();
        nodo(sb, efecto, "efecto", ishikawa.efecto());
        for (int p = 1; p < puntos; p++) {
            arista(sb, idFragmento + "-espina-" + p, idFragmento + "-espina-" + (p + 1), "espina", null);
        }
        arista(sb, idFragmento + "-espina-" + puntos, efecto, "espina", null);
        for (int c = 0; c < categorias.size(); c++) {
            ResultadoIshikawa.Categoria cat = categorias.get(c);
            String id = idFragmento + "-categoria-" + (c + 1);
            nodo(sb, id, cat.vacia() ? "categoria vacia" : "categoria", cat.nombre() + (cat.vacia() ? "\nvacía" : ""));
            arista(sb, id, idFragmento + "-espina-" + (c / 2 + 1), "categoria", null);
        }
        for (int c = 0; c < categorias.size(); c++) {
            for (ResultadoIshikawa.CausaEn causa : categorias.get(c).causas()) {
                nodo(sb, causa.afirmacionId().toString(), "causa", causa.texto());
                arista(sb, causa.afirmacionId().toString(), idFragmento + "-categoria-" + (c + 1), "causa", null);
            }
        }
        return sb.append("}\n").toString();
    }

    /** V06, cadena de T26 · Estimación de Fermi: los factores en fila, multiplicados, hasta el resultado. */
    public static String cadena(pensamiento.tecnicas.f5.ResultadoFermi fermi) {
        StringBuilder sb = cabecera("fermi", "nodesep=0.3, ranksep=0.4");
        for (var f : fermi.factores()) {
            nodo(sb, f.afirmacionId().toString(), f.masAncho() ? "factor ancho" : "factor", f.codigo() + " · " + f.texto() + "\n" + f.rango()
                    + (f.masAncho() ? "\nel más incierto" : ""));
        }
        String resultado = fermi.resultadoId().toString();
        nodo(sb, resultado, "resultado", "Entre " + fermi.minimo() + " y " + fermi.maximo() + " " + fermi.unidad() + "\ncentral " + fermi.central());
        for (int i = 0; i + 1 < fermi.factores().size(); i++) {
            arista(sb, fermi.factores().get(i).afirmacionId().toString(), fermi.factores().get(i + 1).afirmacionId().toString(), "multiplica", "×");
        }
        arista(sb, fermi.factores().getLast().afirmacionId().toString(), resultado, "da", "=");
        return sb.append("}\n").toString();
    }

    /**
     * La cadena de T09 · 5 porqués (docs/ejemplos/T09.md, regla 9): el problema y cada porqué con el id de su afirmación y
     * su clase (problema, porque, porque raiz, porque sin-terminar); las aristas van de lo que se pregunta a su respuesta.
     */
    public static String porques(pensamiento.tecnicas.f2.ResultadoCincoPorques r) {
        StringBuilder sb = cabecera("porques", "nodesep=0.25, ranksep=0.45");
        String problema = r.problemaId().toString();
        nodo(sb, problema, "problema", r.problema());
        java.util.Map<String, String> ids = new java.util.HashMap<>();
        for (pensamiento.tecnicas.f2.ResultadoCincoPorques.Porque p : r.porques()) {
            ids.put(p.codigo(), p.afirmacionId().toString());
            String clase = switch (p.estado()) {
                case CAUSA_RAIZ -> "porque raiz";
                case SIN_TERMINAR -> "porque sin-terminar";
                case INTERMEDIO -> "porque";
            };
            String etiqueta = p.codigo() + " · " + p.texto() + (p.estado() == pensamiento.tecnicas.f2.ResultadoCincoPorques.Estado.INTERMEDIO ? ""
                    : "\n" + p.estado().texto());
            nodo(sb, p.afirmacionId().toString(), clase, etiqueta);
        }
        for (pensamiento.tecnicas.f2.ResultadoCincoPorques.Porque p : r.porques()) {
            arista(sb, p.padre() == null ? problema : ids.get(p.padre()), p.afirmacionId().toString(), "por-que", "¿por qué?");
        }
        return sb.append("}\n").toString();
    }

    static StringBuilder cabecera(String nombre, String separacion) {
        StringBuilder sb = new StringBuilder("digraph ").append(nombre).append(" {\n");
        sb.append("  graph [rankdir=LR, ").append(separacion).append("];\n");
        sb.append("  node [shape=box, style=rounded, fontname=\"Helvetica\", fontsize=11, margin=\"0.15,0.08\"];\n");
        sb.append("  edge [fontname=\"Helvetica\", fontsize=9];\n");
        return sb;
    }

    static void nodo(StringBuilder sb, String id, String clase, String etiqueta) {
        sb.append("  ").append(Grafico.cadena(id)).append(" [id=").append(Grafico.cadena(id)).append(", class=").append(Grafico.cadena(clase))
                .append(", label=").append(Grafico.cadena(envolver(etiqueta))).append("];\n");
    }

    static void arista(StringBuilder sb, String desde, String hasta, String clase, String etiqueta) {
        sb.append("  ").append(Grafico.cadena(desde)).append(" -> ").append(Grafico.cadena(hasta)).append(" [class=").append(Grafico.cadena(clase));
        if (etiqueta != null) {
            sb.append(", label=").append(Grafico.cadena(etiqueta));
        }
        sb.append("];\n");
    }

    /** Cada línea de la etiqueta partida en trozos de unas 28 letras, como en el mapa. */
    static String envolver(String etiqueta) {
        return String.join("\n", java.util.Arrays.stream(etiqueta.split("\n", -1)).map(GeneradorDot::envolver).toList());
    }
}
