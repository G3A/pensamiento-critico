package pensamiento.web.patrones;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.Propuesta;

/**
 * Patrón V05, texto propio marcado (T13 · Falacias como esquemas fallidos, T17 · Hecho, inferencia, juicio). Record
 * tipado de tag/v/v05.jte: raíz id="res-{idEjecucion}" y data-patron="V05". El texto se pinta entero, con cada
 * fragmento marcado en su lugar y su código; debajo, una nota por marca con su estado en texto y sus líneas.
 *
 * @param marcas las marcas en el orden del texto; varias con el mismo tramo se pintan juntas
 * @param notas  una por marca, con su id, su estado y sus líneas
 */
public record V05(Optional<UUID> idEjecucion, String sufijo, Modo modo, String titulo, String texto, List<Marca> marcas,
                  String claseNotas, List<Nota> notas, List<Propuesta> propuestas, String resumen, String tarjeta) {

    public static final String PATRON = "V05";

    /** @param clase clases del mark, por ejemplo "marca-texto propuesta" */
    public record Marca(String codigo, int inicio, int fin, String clase) {
    }

    /**
     * @param claseItem clases del li, por ejemplo "marca-falacia marca-propuesta"
     * @param chip      el estado con texto
     */
    public record Nota(String codigo, String claseItem, String chip, String claseChip, String titulo, List<Linea> lineas) {
        public Nota {
            lineas = List.copyOf(lineas);
        }
    }

    /** @param etiqueta un prefijo en gris ("Pregunta crítica sin responder:"); nulo si no hay */
    public record Linea(String clase, String etiqueta, String texto) {
    }

    /** Un trozo del texto: sin marca, o el tramo de una o más marcas. */
    public record Segmento(String texto, List<Marca> marcas) {
        public boolean marcado() {
            return !marcas.isEmpty();
        }

        public String codigos() {
            return String.join(", ", marcas.stream().map(Marca::codigo).toList());
        }

        public String clase() {
            return marcas.getFirst().clase();
        }
    }

    public V05 {
        marcas = List.copyOf(marcas);
        notas = List.copyOf(notas);
        propuestas = propuestas == null ? List.of() : List.copyOf(propuestas);
    }

    public String idRaiz() {
        return "res-" + idEjecucion.map(UUID::toString).orElse(PATRON + "-" + sufijo);
    }

    public String idNota(Nota n) {
        return idRaiz() + "-" + n.codigo();
    }

    public boolean lectura() {
        return modo == Modo.LECTURA;
    }

    /** El texto completo partido en trozos marcados y sin marcar, en orden. */
    public List<Segmento> segmentos() {
        List<Segmento> segmentos = new ArrayList<>();
        int posicion = 0;
        int i = 0;
        while (i < marcas.size()) {
            Marca m = marcas.get(i);
            List<Marca> mismas = new ArrayList<>();
            while (i < marcas.size() && marcas.get(i).inicio() == m.inicio() && marcas.get(i).fin() == m.fin()) {
                mismas.add(marcas.get(i));
                i++;
            }
            if (m.inicio() < posicion) {
                continue;
            }
            if (m.inicio() > posicion) {
                segmentos.add(new Segmento(texto.substring(posicion, m.inicio()), List.of()));
            }
            segmentos.add(new Segmento(texto.substring(m.inicio(), m.fin()), mismas));
            posicion = m.fin();
        }
        if (posicion < texto.length()) {
            segmentos.add(new Segmento(texto.substring(posicion), List.of()));
        }
        return segmentos;
    }
}
