package pensamiento.web.patrones;

import java.util.ArrayList;
import java.util.List;

import pensamiento.nucleo.puertos.Grafico;
import pensamiento.tecnicas.f1.ResultadoMapa;

/**
 * El DOT del mapa (V01), generado solo en el servidor: cada nodo con id igual al identificador de su afirmación y
 * class por rol; cada arista con class apoyo o ataque. Sin colores: los pone app.css. Todo texto del usuario pasa
 * por Grafico.cadena, así que nunca cierra una cadena ni cambia el grafo.
 */
public final class GeneradorDot {

    static final int ANCHO_LINEA = 28;

    private GeneradorDot() {
    }

    public static String dot(ResultadoMapa mapa) {
        StringBuilder sb = new StringBuilder("digraph mapa {\n");
        // Las aristas van de la premisa a lo que apoya o ataca: con BT la conclusión queda arriba.
        sb.append("  graph [rankdir=").append(mapa.direccion() == ResultadoMapa.Direccion.IZQUIERDA_DERECHA ? "LR" : "BT")
                .append(", nodesep=0.35, ranksep=0.45];\n");
        sb.append("  node [shape=box, style=rounded, fontname=\"Helvetica\", fontsize=11, margin=\"0.15,0.08\"];\n");
        sb.append("  edge [fontname=\"Helvetica\", fontsize=9];\n");
        for (ResultadoMapa.Nodo n : mapa.nodos()) {
            String id = n.afirmacionId().toString();
            sb.append("  ").append(Grafico.cadena(id)).append(" [id=").append(Grafico.cadena(id))
                    .append(", class=").append(Grafico.cadena(n.rol().toString()))
                    .append(", label=").append(Grafico.cadena(n.codigo() + " · " + n.rol().nombre() + "\n" + envolver(n.texto())))
                    .append("];\n");
        }
        for (ResultadoMapa.ArgumentoMapa a : mapa.argumentos()) {
            String conclusion = mapa.nodo(a.conclusion()).afirmacionId().toString();
            for (String premisa : a.premisas()) {
                sb.append("  ").append(Grafico.cadena(mapa.nodo(premisa).afirmacionId().toString())).append(" -> ").append(Grafico.cadena(conclusion))
                        .append(" [class=").append(Grafico.cadena(a.sentido() == ResultadoMapa.Sentido.PRO ? "apoyo" : "ataque"))
                        .append(", label=").append(Grafico.cadena(etiquetaArista(a, mapa.mostrarPesos()))).append("];\n");
            }
        }
        return sb.append("}\n").toString();
    }

    /** "A1 · Más ventas · apoya · peso 3": el sentido va en texto, no solo en el trazo. */
    static String etiquetaArista(ResultadoMapa.ArgumentoMapa a, boolean conPeso) {
        StringBuilder sb = new StringBuilder(a.codigo());
        if (a.titulo() != null) {
            sb.append(" · ").append(a.titulo());
        }
        sb.append(" · ").append(a.sentido() == ResultadoMapa.Sentido.PRO ? "apoya" : "ataca");
        if (conPeso) {
            sb.append(" · peso ").append(a.peso());
        }
        return sb.toString();
    }

    /** Parte el texto en líneas de unas 28 letras por palabras, para que el nodo no sea una tira larga. */
    static String envolver(String texto) {
        List<String> lineas = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        for (String palabra : texto.split("\\s+")) {
            if (!actual.isEmpty() && actual.length() + 1 + palabra.length() > ANCHO_LINEA) {
                lineas.add(actual.toString());
                actual.setLength(0);
            }
            if (!actual.isEmpty()) {
                actual.append(' ');
            }
            actual.append(palabra);
        }
        if (!actual.isEmpty()) {
            lineas.add(actual.toString());
        }
        return String.join("\n", lineas);
    }
}
