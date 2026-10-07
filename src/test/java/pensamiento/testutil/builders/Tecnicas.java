package pensamiento.testutil.builders;

import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.Tecnica;

/** Object Mother de técnicas del catálogo para pruebas sin base de datos. */
public final class Tecnicas {

    private Tecnicas() {
    }

    public static Tecnica pendiente(String id) {
        return tecnica(id, Tecnica.Estado.PENDIENTE);
    }

    public static Tecnica activa(String id) {
        return tecnica(id, Tecnica.Estado.ACTIVA);
    }

    public static Tecnica tecnica(String id, Tecnica.Estado estado) {
        IdTecnica idTecnica = IdTecnica.de(id);
        int familia = Math.min(8, (idTecnica.numero() - 1) / 7 + 1);
        return new Tecnica(idTecnica, "F" + familia, "Técnica " + id, "Nombre llano de " + id,
                "Úsala cuando necesites probar " + id, "Definición canónica de " + id,
                Tecnica.Tipo.PROCEDIMIENTO, Tecnica.Operacion.EVALUAR, Tecnica.Objeto.AFIRMACION, Tecnica.Modalidad.FORMULARIO,
                "V03a", "Origen de prueba", Tecnica.RequiereIa.NO, 1, Json.VACIO, Json.VACIO, Json.VACIO, estado);
    }

    /** T28 · Análisis de hipótesis en competencia (ACH), con tildes y ñ para probar la ida y vuelta. */
    public static Tecnica t28() {
        return new Tecnica(IdTecnica.de("T28"), "F5", "Análisis de hipótesis en competencia (ACH)",
                "Descartar explicaciones con la evidencia", "Hay varias explicaciones y quieres cruzarlas con la evidencia; añade ñandú y niño.",
                "Matriz hipótesis × evidencia; gana la menos refutada.", Tecnica.Tipo.PROCEDIMIENTO, Tecnica.Operacion.EVALUAR,
                Tecnica.Objeto.AFIRMACION, Tecnica.Modalidad.FORMULARIO, "V03a", "Heuer 1999", Tecnica.RequiereIa.NO, 1,
                new Json("{\"escala\":\"CIN\"}"), new Json("{\"hipotesis\":\"filas\"}"), new Json("{\"maxHipotesis\":4}"), Tecnica.Estado.PENDIENTE);
    }
}
