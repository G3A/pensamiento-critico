package pensamiento.tecnicas.f5;

import java.util.List;

import pensamiento.nucleo.reglas.R05Calibracion;

/**
 * Valor que pinta el patrón V08 (curva de calibración) para T25 · Calibración y puntaje Brier y para el tablero del
 * Diario: el puntaje, la curva por tramos y los avisos de R05, en una forma que se guarda en JSON.
 *
 * @param puntaje           "0,17"; nulo si no hay ninguna resuelta
 * @param fueraDeHorizonte  resueltas que el horizonte dejó afuera
 */
public record ResultadoCalibracion(String tipoPuntaje, String puntaje, int resueltas, int sinResolver, int fueraDeHorizonte, int umbral,
                                   List<Tramo> tramos, List<String> avisos, String resumen) {

    public record Tramo(int desde, int hasta, int n, int confianzaMedia, int porcentajeCumplido, boolean provisional) {
    }

    public ResultadoCalibracion {
        tramos = List.copyOf(tramos);
        avisos = List.copyOf(avisos);
    }

    /** Traduce lo que calculó R05 y arma el resumen (docs/ejemplos/T25.md, regla 8). */
    public static ResultadoCalibracion de(R05Calibracion.Calibracion c, int umbral) {
        String nombre = c.tipo() == R05Calibracion.Puntaje.BRIER ? "Brier" : "Puntaje logarítmico";
        String puntaje = c.puntajeTexto().orElse(null);
        StringBuilder resumen = new StringBuilder();
        if (puntaje == null) {
            resumen.append("Todavía sin resueltas");
        } else {
            resumen.append(nombre).append(' ').append(puntaje).append(" con ").append(c.resueltas()).append(c.resueltas() == 1 ? " resuelta" : " resueltas");
        }
        if (c.sinResolver() > 0) {
            resumen.append(puntaje == null ? ": " : " y ").append(c.sinResolver()).append(" sin resolver");
        }
        if (!c.avisos().isEmpty()) {
            resumen.append(" · ").append(c.avisos().size()).append(c.avisos().size() == 1 ? " aviso" : " avisos");
        }
        resumen.append('.');
        List<Tramo> tramos = c.tramos().stream().map(t -> new Tramo(t.desde(), t.hasta(), t.n(), t.confianzaMedia(), t.porcentajeCumplido(),
                t.provisional())).toList();
        return new ResultadoCalibracion(c.tipo().toString(), puntaje, c.resueltas(), c.sinResolver(), c.fueraDeHorizonte(), umbral, tramos,
                c.avisos(), resumen.toString());
    }
}
