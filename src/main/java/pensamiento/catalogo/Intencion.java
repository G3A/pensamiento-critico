package pensamiento.catalogo;

import java.util.Set;

import pensamiento.nucleo.Tecnica;

/** Las seis intenciones del Inicio, mapeadas a técnicas por las facetas de la sección 5b. */
public enum Intencion {
    TOMAR_UNA_DECISION("decision", "Tomar una decisión"),
    SABER_SI_CREER_ALGO("creer", "Saber si creer algo"),
    REVISAR_MI_ARGUMENTO("argumento", "Revisar mi argumento"),
    ENTENDER_UN_DESACUERDO("desacuerdo", "Entender un desacuerdo"),
    ENTENDER_POR_QUE_PASO_ALGO("por-que", "Entender por qué pasó algo"),
    PRACTICAR("practicar", "Practicar");

    private final String clave;
    private final String titulo;

    Intencion(String clave, String titulo) {
        this.clave = clave;
        this.titulo = titulo;
    }

    public String clave() {
        return clave;
    }

    public String titulo() {
        return titulo;
    }

    public static Intencion porClave(String clave) {
        for (Intencion i : values()) {
            if (i.clave.equals(clave)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Intención desconocida: " + clave);
    }

    public boolean incluye(Tecnica t) {
        String id = t.id().valor();
        return switch (this) {
            case TOMAR_UNA_DECISION -> t.objeto() == Tecnica.Objeto.DECISION;
            case SABER_SI_CREER_ALGO -> t.objeto() == Tecnica.Objeto.AFIRMACION || t.objeto() == Tecnica.Objeto.FUENTE;
            case REVISAR_MI_ARGUMENTO -> t.objeto() == Tecnica.Objeto.ARGUMENTO;
            case ENTENDER_UN_DESACUERDO -> Set.of("T12", "T15", "T34", "T35", "T36", "T37", "T38", "T39").contains(id);
            case ENTENDER_POR_QUE_PASO_ALGO -> t.objeto() == Tecnica.Objeto.PROBLEMA || Set.of("T28", "T33").contains(id);
            case PRACTICAR -> t.objeto() == Tecnica.Objeto.UNO_MISMO || Set.of("T13", "T19").contains(id);
        };
    }
}
