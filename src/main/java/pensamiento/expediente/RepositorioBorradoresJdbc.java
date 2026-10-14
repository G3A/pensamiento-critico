package pensamiento.expediente;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;

import pensamiento.catalogo.MapeadorJson;
import pensamiento.nucleo.puertos.RepositorioBorradores;

/** Tabla borrador (V18) bajo RLS por usuario: guardar reemplaza con INSERT … ON CONFLICT DO UPDATE, sin lanzar. */
@Repository
@Transactional
public class RepositorioBorradoresJdbc implements RepositorioBorradores {

    private final JdbcClient jdbc;

    public RepositorioBorradoresJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void guardar(UUID usuarioId, UUID institucionId, String clave, Map<String, String> campos, Instant ahora) {
        jdbc.sql("""
                INSERT INTO borrador (usuario_id, institucion_id, clave, campos, actualizado_en)
                VALUES (:usuario, :institucion, :clave, CAST(:campos AS jsonb), :ahora)
                ON CONFLICT (usuario_id, clave) DO UPDATE SET campos = EXCLUDED.campos, actualizado_en = EXCLUDED.actualizado_en""")
                .param("usuario", usuarioId).param("institucion", institucionId).param("clave", clave)
                .param("campos", MapeadorJson.escribir(campos).texto()).param("ahora", Timestamp.from(ahora)).update();
    }

    @Override
    public Optional<Borrador> leer(UUID usuarioId, String clave) {
        return jdbc.sql("SELECT clave, campos, actualizado_en FROM borrador WHERE usuario_id = :usuario AND clave = :clave")
                .param("usuario", usuarioId).param("clave", clave)
                .query((rs, i) -> new Borrador(rs.getString("clave"),
                        new LinkedHashMap<>(MapeadorJson.mapper().readValue(rs.getString("campos"), new TypeReference<Map<String, String>>() { })),
                        rs.getTimestamp("actualizado_en").toInstant()))
                .optional();
    }

    @Override
    public boolean borrar(UUID usuarioId, String clave) {
        return jdbc.sql("DELETE FROM borrador WHERE usuario_id = :usuario AND clave = :clave")
                .param("usuario", usuarioId).param("clave", clave).update() > 0;
    }
}
