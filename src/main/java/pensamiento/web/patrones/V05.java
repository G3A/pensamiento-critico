package pensamiento.web.patrones;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pensamiento.tecnicas.f3.ResultadoFalacias;

/**
 * Patrón V05, texto propio marcado (T13 · Falacias como esquemas fallidos). Record tipado de tag/v/v05.jte: raíz
 * id="res-{idEjecucion}" y data-patron="V05". El texto se pinta entero, con cada fragmento marcado en su lugar;
 * cada marca dice esquema, pregunta crítica sin responder y porqué. La etiqueta de falacia solo se afirma si la
 * persona la confirmó (R06); si no, se ofrece como propuesta.
 */
public record V05(Optional<UUID> idEjecucion, String sufijo, ResultadoFalacias valor, Modo modo) {

    public static final String PATRON = "V05";

    /** Un trozo del texto: sin marca, o el fragmento de una o más marcas (varios esquemas en la misma oración). */
    public record Segmento(String texto, List<ResultadoFalacias.Marca> marcas) {
        public boolean marcado() {
            return !marcas.isEmpty();
        }

        public String codigos() {
            return String.join(", ", marcas.stream().map(ResultadoFalacias.Marca::codigo).toList());
        }

        public String clase() {
            return marcas.stream().anyMatch(ResultadoFalacias.Marca::confirmada) ? "marca-texto confirmada" : "marca-texto propuesta";
        }
    }

    public String idRaiz() {
        return "res-" + idEjecucion.map(UUID::toString).orElse("V05-" + sufijo);
    }

    public String idMarca(ResultadoFalacias.Marca m) {
        return idRaiz() + "-" + m.codigo();
    }

    public String resumen() {
        return valor.resumen();
    }

    public boolean lectura() {
        return modo == Modo.LECTURA;
    }

    /** El texto completo partido en trozos marcados y sin marcar, en orden. */
    public List<Segmento> segmentos() {
        List<Segmento> segmentos = new ArrayList<>();
        String texto = valor.texto();
        int posicion = 0;
        List<ResultadoFalacias.Marca> marcas = valor.marcas();
        int i = 0;
        while (i < marcas.size()) {
            ResultadoFalacias.Marca m = marcas.get(i);
            List<ResultadoFalacias.Marca> mismas = new ArrayList<>();
            while (i < marcas.size() && marcas.get(i).inicio() == m.inicio() && marcas.get(i).fin() == m.fin()) {
                mismas.add(marcas.get(i));
                i++;
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

    public String estado(ResultadoFalacias.Marca m) {
        return m.confirmada() ? "falacia confirmada por ti" : "esquema derrotable · sin confirmar";
    }

    public String claseEstado(ResultadoFalacias.Marca m) {
        return m.confirmada() ? "chip chip-aviso" : "chip chip-pendiente";
    }

    /** R06: sin confirmar, la etiqueta es solo una propuesta. */
    public String etiqueta(ResultadoFalacias.Marca m) {
        return m.confirmada() ? "Falacia: " + m.falacia() + "." : "Si confirmas que la pregunta falla, la etiqueta sería: " + m.falacia() + ".";
    }

    public String tarjeta() {
        if (valor.marcas().isEmpty()) {
            return "Las reglas no reconocieron ningún esquema con preguntas sin responder. Eso no prueba que el razonamiento sea bueno: "
                    + "las reglas solo ven patrones de palabras.";
        }
        return "Las marcas son propuestas de las reglas: cada una dice qué pregunta crítica falta responder. La etiqueta de falacia "
                + "solo cuenta si tú la confirmas.";
    }
}
