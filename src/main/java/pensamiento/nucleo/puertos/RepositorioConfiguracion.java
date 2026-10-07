package pensamiento.nucleo.puertos;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;

/**
 * Configuración de cada técnica guardada por usuario (tabla configuracion_usuario). Sin fila, se usa la del
 * catálogo. Como todo repositorio de usuario, filtra por usuario y la implementación real corre bajo RLS.
 */
public interface RepositorioConfiguracion {

    /** La configuración guardada, con la versión de esquema con que se guardó. */
    record Guardada(int versionEsquema, Json valores) {
    }

    Optional<Guardada> de(UUID usuarioId, IdTecnica tecnica);

    /** Crea o reemplaza la configuración del usuario para esa técnica. */
    void guardar(UUID usuarioId, UUID institucionId, IdTecnica tecnica, int versionEsquema, Json valores);

    /** Vuelve a la configuración del catálogo: borra la del usuario. */
    void restablecer(UUID usuarioId, IdTecnica tecnica);

    /** Todas las configuraciones del usuario, para exportar sus datos. */
    Map<IdTecnica, Guardada> todas(UUID usuarioId);
}
