package pensamiento.web.patrones;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pensamiento.tecnicas.f1.ResultadoMapa;
import pensamiento.tecnicas.f1.Textos;

/**
 * Patrón V01, grafo de nodos (T01 · Mapeo de argumentos y T06 · Reconstrucción de premisas ocultas). Record
 * tipado del fragmento tag/v/v01.jte: la raíz lleva id="res-{idEjecucion}" y data-patron="V01"; el SVG llega ya
 * saneado y sin colores, y debajo va la lista de nodos navegable por teclado, que también sirve si Graphviz no
 * respondió. Todo estado lleva texto además del color (corrección 13: nunca "válido").
 *
 * @param svg el mapa dibujado por Graphviz; vacío si no se pudo dibujar
 */
public record V01(Optional<UUID> idEjecucion, String sufijo, ResultadoMapa valor, Modo modo, Optional<String> svg) {

    public static final String PATRON = "V01";

    public String idRaiz() {
        return "res-" + idEjecucion.map(UUID::toString).orElse("V01-" + sufijo);
    }

    public String resumen() {
        return valor.resumen();
    }

    public boolean lectura() {
        return modo == Modo.LECTURA;
    }

    public String claseMapa() {
        return valor.coloresPorRol() ? "mapa" : "mapa sin-colores";
    }

    /** "N2 · premisa", "N3 · premisa oculta · supuesto". */
    public String etiquetaNodo(ResultadoMapa.Nodo n) {
        return n.codigo() + " · " + n.rol().nombre() + (n.asumible() && n.rol() != ResultadoMapa.Rol.OCULTA ? " · supuesto" : "");
    }

    /** "A1 · Más ventas · a favor de N1 · peso 3 · premisas N2 y N3". */
    public String etiquetaArgumento(ResultadoMapa.ArgumentoMapa a) {
        StringBuilder sb = new StringBuilder(a.codigo());
        if (a.titulo() != null) {
            sb.append(" · ").append(a.titulo());
        }
        sb.append(a.sentido() == ResultadoMapa.Sentido.PRO ? " · a favor de " : " · en contra de ").append(a.conclusion());
        if (valor.mostrarPesos()) {
            sb.append(" · peso ").append(a.peso());
        }
        sb.append(a.premisas().size() == 1 ? " · premisa " : " · premisas ").append(String.join(", ", a.premisas()));
        return sb.toString();
    }

    public String estadoArgumento(ResultadoMapa.ArgumentoMapa a) {
        return a.aplicable() ? "aplicable" : "no aplicable";
    }

    public String claseArgumento(ResultadoMapa.ArgumentoMapa a) {
        return a.aplicable() ? "chip chip-ok" : "chip chip-aviso";
    }

    public String textoConclusion(ResultadoMapa.Aceptabilidad c) {
        return valor.nodo(c.conclusion()).texto();
    }

    public String estadoConclusion(ResultadoMapa.Aceptabilidad c) {
        return c.aceptable() ? "aceptable bajo " + valor.estandar().nombre() : "no aceptable todavía";
    }

    public String claseConclusion(ResultadoMapa.Aceptabilidad c) {
        return c.aceptable() ? "chip chip-ok" : "chip chip-aviso";
    }

    /** Lo que la tarjeta dice además del resumen: objeciones por responder y supuestos ocultos por verificar. */
    public List<String> tarjeta() {
        List<String> frases = new ArrayList<>();
        List<String> sinResponder = valor.objecionesSinResponder().stream().map(c -> valor.nodo(c).texto()).toList();
        if (!sinResponder.isEmpty()) {
            frases.add((sinResponder.size() == 1 ? "Falta responder la objeción " : "Faltan responder las objeciones ")
                    + Textos.enumerarCitas(sinResponder) + ".");
        }
        List<String> ocultas = valor.nodos().stream().filter(n -> n.rol() == ResultadoMapa.Rol.OCULTA).map(ResultadoMapa.Nodo::texto).toList();
        if (!ocultas.isEmpty()) {
            frases.add("El argumento depende de " + (ocultas.size() == 1 ? "un supuesto que no estaba escrito: " : ocultas.size()
                    + " supuestos que no estaban escritos: ") + Textos.enumerarCitas(ocultas) + ". Siguiente paso: verificarlo"
                    + (ocultas.size() == 1 ? "." : "s."));
        }
        if (valor.argumentos().stream().noneMatch(ResultadoMapa.ArgumentoMapa::aplicable)) {
            frases.add("Ningún argumento es aplicable todavía: hace falta verificar sus premisas o marcarlas como supuestos.");
        }
        return frases;
    }
}
