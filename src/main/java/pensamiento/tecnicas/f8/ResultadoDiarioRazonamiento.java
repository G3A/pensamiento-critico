package pensamiento.tecnicas.f8;

import java.util.List;

/**
 * Valor que pinta el patrón V11 (línea de tiempo) para T45 · Diario de razonamiento: las semanas con registros, de la más
 * reciente a la más vieja, cada una con su resumen; cuántos registros hay por familia y las familias activas sin usar.
 *
 * @param porFamilia de más a menos registros y, si empatan, por código
 * @param sinUsar    las familias activas sin ningún registro, por código
 */
public record ResultadoDiarioRazonamiento(List<Semana> semanas, List<PorFamilia> porFamilia, List<String> sinUsar, List<String> avisos,
                                          String resumen) {

    /**
     * @param desde   el lunes, AAAA-MM-DD
     * @param resumen "3 ejecuciones, 1 expediente, 1 cambio de opinión."; nulo sin resumen semanal
     */
    public record Semana(String desde, String titulo, String resumen, List<Registro> registros) {
        public Semana {
            registros = List.copyOf(registros);
        }
    }

    /** @param expediente nulo si no tiene */
    public record Registro(String fecha, String fechaTexto, String tecnica, String resumen, String expediente, int cambios) {
    }

    public record PorFamilia(String familia, int registros) {
    }

    public ResultadoDiarioRazonamiento {
        semanas = List.copyOf(semanas);
        porFamilia = List.copyOf(porFamilia);
        sinUsar = List.copyOf(sinUsar);
        avisos = List.copyOf(avisos);
    }
}
