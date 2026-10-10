package pensamiento.tecnicas.f8;

import pensamiento.nucleo.IdTecnica;

/**
 * Los cuatro temas que practica el Dojo, cada uno con su técnica y su competencia (docs/dojo.md). Viaja en JSON por su código
 * ("T13"), como la opción del catálogo.
 */
public enum TemaDojo {
    FALACIAS("T13", "T13 · Falacias como esquemas fallidos", "falacias", "Falacias", "falacia", "falacias"),
    SESGOS("T14", "T14 · Sesgos cognitivos", "sesgos", "Sesgos", "sesgo", "sesgos"),
    DATOS("T18", "T18 · Correlación, causalidad y tasas base", "datos", "Datos", "concepto de datos", "conceptos de datos"),
    FUENTES("T19", "T19 · SIFT", "fuentes", "Fuentes", "movimiento SIFT", "movimientos SIFT");

    private final String codigo;
    private final String cita;
    private final String clave;
    private final String nombre;
    private final String singular;
    private final String plural;

    TemaDojo(String codigo, String cita, String clave, String nombre, String singular, String plural) {
        this.codigo = codigo;
        this.cita = cita;
        this.clave = clave;
        this.nombre = nombre;
        this.singular = singular;
        this.plural = plural;
    }

    public IdTecnica tecnica() {
        return IdTecnica.de(codigo);
    }

    /** "T13 · Falacias como esquemas fallidos". */
    public String cita() {
        return cita;
    }

    /** "falacias", la del filtro del Dojo. */
    public String clave() {
        return clave;
    }

    /** "Falacias". */
    public String nombre() {
        return nombre;
    }

    /** "2 falacias", "1 movimiento SIFT". */
    public String contar(int n) {
        return n + " " + (n == 1 ? singular : plural);
    }

    @Override
    public String toString() {
        return codigo;
    }

    public static TemaDojo de(IdTecnica tecnica) {
        for (TemaDojo t : values()) {
            if (t.codigo.equals(tecnica.valor())) {
                return t;
            }
        }
        throw new IllegalArgumentException("El Dojo no practica " + tecnica);
    }

    public static TemaDojo porClave(String clave) {
        for (TemaDojo t : values()) {
            if (t.clave.equals(clave)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Tema del Dojo desconocido: " + clave);
    }
}
