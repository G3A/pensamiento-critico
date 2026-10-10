package pensamiento.nucleo.puertos;

import java.util.List;
import java.util.UUID;

import pensamiento.nucleo.Competencia;
import pensamiento.nucleo.IntentoDojo;

/**
 * Los intentos del Dojo de razonamiento y la competencia de cada tema (tablas intento_dojo y competencia). Todo método
 * recibe el usuario de la sesión y solo devuelve lo suyo; la implementación real además corre bajo RLS. Los intentos son
 * de solo inserción; la competencia es una proyección que se reescribe con cada intento, en la misma transacción.
 */
public interface RepositorioDojo {

    /**
     * Guarda el intento y reescribe la competencia de su tema. Si la persona ya guardó un intento con la misma clave (doble
     * clic), no escribe nada y devuelve falso.
     */
    boolean guardar(UUID usuarioId, UUID institucionId, IntentoDojo intento, Competencia competencia);

    /** Todos los intentos de la persona, del más viejo al más nuevo (y por identificador si coinciden). */
    List<IntentoDojo> intentos(UUID usuarioId);

    /** La competencia de cada tema que la persona practicó, por código de técnica. */
    List<Competencia> competencias(UUID usuarioId);

    /** Importa un intento tal como estaba en el respaldo. Si el identificador o la clave ya existen, no cambia nada. */
    void restaurar(UUID usuarioId, UUID institucionId, IntentoDojo intento);

    /** Importa la competencia de un tema; reemplaza la que hubiera de ese tema. */
    void restaurar(UUID usuarioId, UUID institucionId, Competencia competencia);
}
