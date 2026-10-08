package pensamiento.web.patrones;

import java.util.Optional;
import java.util.UUID;

import pensamiento.tecnicas.f1.ResultadoToulmin;

/**
 * Patrón V02, lista de verificación con estado (T02 · Modelo de Toulmin). Record tipado de tag/v/v02.jte: raíz
 * id="res-{idEjecucion}" y data-patron="V02"; cada parte dice su estado con texto y, si no está completa, la
 * pregunta que la completaría. Seis de seis no dice "válido": dice que están las partes (corrección 13).
 */
public record V02(Optional<UUID> idEjecucion, String sufijo, ResultadoToulmin valor, Modo modo) {

    public static final String PATRON = "V02";

    public String idRaiz() {
        return "res-" + idEjecucion.map(UUID::toString).orElse("V02-" + sufijo);
    }

    public String resumen() {
        return valor.resumen();
    }

    public boolean lectura() {
        return modo == Modo.LECTURA;
    }

    public String claseEstado(ResultadoToulmin.ParteEvaluada p) {
        return switch (p.estado()) {
            case COMPLETA -> "chip chip-ok";
            case FALTA -> "chip chip-aviso";
            case SIN_FUENTE, SIN_RESPONDER -> "chip chip-pendiente";
        };
    }

    /** "Fuente: …" o "Respuesta: …", según la parte. */
    public String complemento(ResultadoToulmin.ParteEvaluada p) {
        if (p.complemento() == null) {
            return "";
        }
        return (p.parte() == ResultadoToulmin.Parte.RESPALDO ? "Fuente: " : "Respuesta: ") + p.complemento();
    }

    public String tarjeta() {
        return valor.completas() == valor.total()
                ? "Están todas las partes. Que estén no las hace ciertas: los datos y el respaldo todavía se pueden verificar."
                : "Cada parte que falta trae la pregunta que la completaría.";
    }
}
