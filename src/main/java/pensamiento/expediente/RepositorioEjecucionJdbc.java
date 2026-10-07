package pensamiento.expediente;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.puertos.RepositorioEjecucion;

/** Historial de ejecuciones. Idempotente por clave_idempotencia; filtra por usuario; RLS por debajo. */
@Repository
@Transactional
public class RepositorioEjecucionJdbc implements RepositorioEjecucion {

    private static final String COLUMNAS = """
            id, usuario_id, institucion_id, tecnica_id, version_esquema, expediente_id, config::text AS config,
            datos::text AS datos, resultado::text AS resultado, resumen, modelo, modelo_digest, prompt_version,
            temperatura, semilla, clave_idempotencia, creada_en
            """;

    private final JdbcClient jdbc;

    public RepositorioEjecucionJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Ejecucion guardar(Ejecucion e) {
        Optional<Ejecucion> previa = jdbc.sql("SELECT " + COLUMNAS + " FROM ejecucion WHERE clave_idempotencia = :clave")
                .param("clave", e.claveIdempotencia())
                .query(RepositorioEjecucionJdbc::fila).optional();
        if (previa.isPresent()) {
            return previa.get();
        }
        jdbc.sql("""
                INSERT INTO ejecucion (id, usuario_id, institucion_id, tecnica_id, version_esquema, expediente_id, config, datos,
                                       resultado, resumen, modelo, modelo_digest, prompt_version, temperatura, semilla,
                                       clave_idempotencia, creada_en)
                VALUES (:id, :usuario, :institucion, :tecnica, :version, :expediente, :config::jsonb, :datos::jsonb,
                        :resultado::jsonb, :resumen, :modelo, :digest, :prompt, :temperatura, :semilla, :clave, :creada)
                """)
                .param("id", e.id())
                .param("usuario", e.usuarioId())
                .param("institucion", e.institucionId())
                .param("tecnica", e.tecnica().valor())
                .param("version", e.versionEsquema())
                .param("expediente", e.expedienteId().orElse(null))
                .param("config", e.config().texto())
                .param("datos", e.datos().texto())
                .param("resultado", e.resultado().texto())
                .param("resumen", e.resumen())
                .param("modelo", e.modelo().map(Ejecucion.RegistroModelo::modelo).orElse(null))
                .param("digest", e.modelo().map(Ejecucion.RegistroModelo::digest).orElse(null))
                .param("prompt", e.modelo().map(Ejecucion.RegistroModelo::promptVersion).orElse(null))
                .param("temperatura", e.modelo().map(Ejecucion.RegistroModelo::temperatura).orElse(null))
                .param("semilla", e.modelo().map(Ejecucion.RegistroModelo::semilla).orElse(null))
                .param("clave", e.claveIdempotencia())
                .param("creada", Timestamp.from(e.creadaEn()))
                .update();
        return e;
    }

    @Override
    public Optional<Ejecucion> porId(UUID usuarioId, UUID id) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM ejecucion WHERE usuario_id = :usuario AND id = :id AND eliminada_en IS NULL")
                .param("usuario", usuarioId).param("id", id)
                .query(RepositorioEjecucionJdbc::fila).optional();
    }

    @Override
    public List<Ejecucion> porTecnica(UUID usuarioId, IdTecnica tecnica) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM ejecucion WHERE usuario_id = :usuario AND tecnica_id = :tecnica AND eliminada_en IS NULL ORDER BY creada_en DESC, id DESC")
                .param("usuario", usuarioId).param("tecnica", tecnica.valor())
                .query(RepositorioEjecucionJdbc::fila).list();
    }

    static Ejecucion fila(ResultSet rs, int i) throws SQLException {
        String modelo = rs.getString("modelo");
        Optional<Ejecucion.RegistroModelo> registro = modelo == null ? Optional.empty()
                : Optional.of(new Ejecucion.RegistroModelo(modelo, rs.getString("modelo_digest"), rs.getString("prompt_version"),
                        rs.getDouble("temperatura"), rs.getLong("semilla")));
        return new Ejecucion(
                rs.getObject("id", UUID.class),
                rs.getObject("usuario_id", UUID.class),
                rs.getObject("institucion_id", UUID.class),
                IdTecnica.de(rs.getString("tecnica_id")),
                rs.getInt("version_esquema"),
                Optional.ofNullable(rs.getObject("expediente_id", UUID.class)),
                new Json(rs.getString("config")),
                new Json(rs.getString("datos")),
                new Json(rs.getString("resultado")),
                rs.getString("resumen"),
                registro,
                rs.getString("clave_idempotencia"),
                rs.getTimestamp("creada_en").toInstant());
    }
}
