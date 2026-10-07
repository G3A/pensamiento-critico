package pensamiento.testutil.builders;

import java.util.UUID;

import pensamiento.nucleo.Evidencia;
import pensamiento.nucleo.Fuente;

/** Object Mother de evidencias: por defecto adoptadas y etiquetadas por el usuario (las que cuentan). */
public final class Evidencias {

    private Evidencias() {
    }

    public static Evidencia apoya(int fuerza) {
        return apoya(fuerza, "grupo-" + UUID.randomUUID());
    }

    public static Evidencia apoya(int fuerza, String grupoOrigen) {
        return nueva(Evidencia.Postura.APOYA, fuerza, grupoOrigen, true, Evidencia.EtiquetadaPor.USUARIO);
    }

    public static Evidencia contradice(int fuerza) {
        return contradice(fuerza, "grupo-" + UUID.randomUUID());
    }

    public static Evidencia contradice(int fuerza, String grupoOrigen) {
        return nueva(Evidencia.Postura.CONTRADICE, fuerza, grupoOrigen, true, Evidencia.EtiquetadaPor.USUARIO);
    }

    public static Evidencia matiza(int fuerza) {
        return nueva(Evidencia.Postura.MATIZA, fuerza, "grupo-" + UUID.randomUUID(), true, Evidencia.EtiquetadaPor.USUARIO);
    }

    /** Etiquetada por el modelo y todavía no adoptada: no cuenta. */
    public static Evidencia delModeloSinAdoptar(Evidencia.Postura postura, int fuerza) {
        return nueva(postura, fuerza, "grupo-" + UUID.randomUUID(), false, Evidencia.EtiquetadaPor.MODELO);
    }

    public static Evidencia nueva(Evidencia.Postura postura, int fuerza, String grupoOrigen, boolean adoptada, Evidencia.EtiquetadaPor por) {
        Fuente fuente = Fuentes.fuente().grupo(grupoOrigen).build();
        return new Evidencia(UUID.randomUUID(), UUID.randomUUID(), fuente, "pasaje literal", postura, fuerza, por, adoptada);
    }
}
