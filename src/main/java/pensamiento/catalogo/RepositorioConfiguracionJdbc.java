package pensamiento.catalogo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.puertos.RepositorioConfiguracion;

/** Tabla configuracion_usuario. Filtra por usuario en SQL; RLS vuelve a filtrar por debajo. */
@Repository
@Transactional
public class RepositorioConfiguracionJdbc implements RepositorioConfiguracion {

    private final JdbcClient jdbc;

    public RepositorioConfiguracionJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Guardada> de(UUID usuarioId, IdTecnica tecnica) {
        return jdbc.sql("SELECT version_esquema, valores::text AS valores FROM configuracion_usuario WHERE usuario_id = :u AND tecnica_id = :t")
                .param("u", usuarioId).param("t", tecnica.valor())
                .query((rs, i) -> new Guardada(rs.getInt("version_esquema"), new Json(rs.getString("valores"))))
                .optional();
    }

    @Override
    public void guardar(UUID usuarioId, UUID institucionId, IdTecnica tecnica, int versionEsquema, Json valores) {
        jdbc.sql("""
                INSERT INTO configuracion_usuario (usuario_id, institucion_id, tecnica_id, version_esquema, valores)
                VALUES (:u, :i, :t, :v, :valores::jsonb)
                ON CONFLICT (usuario_id, tecnica_id) DO UPDATE SET version_esquema = EXCLUDED.version_esquema, valores = EXCLUDED.valores
                """)
                .param("u", usuarioId).param("i", institucionId).param("t", tecnica.valor()).param("v", versionEsquema)
                .param("valores", valores.texto())
                .update();
    }

    @Override
    public void restablecer(UUID usuarioId, IdTecnica tecnica) {
        jdbc.sql("DELETE FROM configuracion_usuario WHERE usuario_id = :u AND tecnica_id = :t")
                .param("u", usuarioId).param("t", tecnica.valor()).update();
    }

    @Override
    public Map<IdTecnica, Guardada> todas(UUID usuarioId) {
        Map<IdTecnica, Guardada> todas = new LinkedHashMap<>();
        jdbc.sql("SELECT tecnica_id, version_esquema, valores::text AS valores FROM configuracion_usuario WHERE usuario_id = :u ORDER BY tecnica_id")
                .param("u", usuarioId)
                .query(rs -> {
                    todas.put(IdTecnica.de(rs.getString("tecnica_id")), new Guardada(rs.getInt("version_esquema"), new Json(rs.getString("valores"))));
                });
        return todas;
    }
}
