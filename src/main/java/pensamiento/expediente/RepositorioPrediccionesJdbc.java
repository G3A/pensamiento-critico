package pensamiento.expediente;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pensamiento.nucleo.Prediccion;
import pensamiento.nucleo.PrediccionDeclarada;
import pensamiento.nucleo.puertos.RepositorioPredicciones;

/**
 * Predicciones en PostgreSQL. Filtra por usuario; RLS por debajo. Idempotente por identificador. Resolver solo toca una
 * fila pendiente; si ya estaba resuelta, no escribe nada (y el trigger de V6 lo impediría igual).
 */
@Repository
@Transactional
public class RepositorioPrediccionesJdbc implements RepositorioPredicciones {

    private static final String COLUMNAS = """
            p.id, p.ejecucion_id, p.afirmacion_id, a.texto, p.confianza, p.fecha_revision, p.resultado, p.resuelta_en
            FROM prediccion p JOIN afirmacion a ON a.id = p.afirmacion_id
            """;

    private final JdbcClient jdbc;

    public RepositorioPrediccionesJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void guardar(UUID usuarioId, UUID institucionId, UUID ejecucionId, List<PrediccionDeclarada> predicciones) {
        for (PrediccionDeclarada p : predicciones) {
            jdbc.sql("""
                    INSERT INTO prediccion (id, usuario_id, institucion_id, afirmacion_id, confianza, fecha_revision, ejecucion_id)
                    VALUES (:id, :usuario, :institucion, :afirmacion, :confianza, :fecha, :ejecucion)
                    ON CONFLICT (id) DO NOTHING
                    """)
                    .param("id", p.id()).param("usuario", usuarioId).param("institucion", institucionId).param("afirmacion", p.afirmacionId())
                    .param("confianza", p.confianza()).param("fecha", Date.valueOf(p.fechaRevision())).param("ejecucion", ejecucionId)
                    .update();
        }
    }

    @Override
    public void restaurar(UUID usuarioId, UUID institucionId, Prediccion p) {
        jdbc.sql("""
                INSERT INTO prediccion (id, usuario_id, institucion_id, afirmacion_id, confianza, fecha_revision, resultado, resuelta_en, ejecucion_id)
                VALUES (:id, :usuario, :institucion, :afirmacion, :confianza, :fecha, :resultado, :resuelta, :ejecucion)
                ON CONFLICT (id) DO NOTHING
                """)
                .param("id", p.id()).param("usuario", usuarioId).param("institucion", institucionId).param("afirmacion", p.afirmacionId())
                .param("confianza", p.confianza()).param("fecha", Date.valueOf(p.fechaRevision())).param("resultado", p.estado().toString())
                .param("resuelta", p.resueltaEn().map(Timestamp::from).orElse(null)).param("ejecucion", p.ejecucionId())
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Prediccion> porId(UUID usuarioId, UUID prediccionId) {
        return jdbc.sql("SELECT " + COLUMNAS + " WHERE p.usuario_id = :usuario AND p.id = :id")
                .param("usuario", usuarioId).param("id", prediccionId).query(RepositorioPrediccionesJdbc::fila).optional();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Prediccion> deUsuario(UUID usuarioId) {
        return jdbc.sql("SELECT " + COLUMNAS + """
                         JOIN ejecucion e ON e.id = p.ejecucion_id
                WHERE p.usuario_id = :usuario AND e.eliminada_en IS NULL
                ORDER BY p.fecha_revision, p.id
                """)
                .param("usuario", usuarioId).query(RepositorioPrediccionesJdbc::fila).list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Prediccion> deEjecucion(UUID usuarioId, UUID ejecucionId) {
        return jdbc.sql("SELECT " + COLUMNAS + " WHERE p.usuario_id = :usuario AND p.ejecucion_id = :ejecucion ORDER BY p.id")
                .param("usuario", usuarioId).param("ejecucion", ejecucionId).query(RepositorioPrediccionesJdbc::fila).list();
    }

    @Override
    public Optional<Prediccion> resolver(UUID usuarioId, UUID prediccionId, boolean seCumplio, Instant cuando) {
        Optional<Prediccion> actual = jdbc.sql("SELECT " + COLUMNAS + " WHERE p.usuario_id = :usuario AND p.id = :id FOR UPDATE OF p")
                .param("usuario", usuarioId).param("id", prediccionId).query(RepositorioPrediccionesJdbc::fila).optional();
        if (actual.isEmpty()) {
            return Optional.empty();
        }
        Prediccion resuelta = actual.get().resolver(seCumplio, cuando);
        jdbc.sql("UPDATE prediccion SET resultado = :resultado, resuelta_en = :cuando WHERE usuario_id = :usuario AND id = :id AND resultado = 'pendiente'")
                .param("resultado", resuelta.estado().toString()).param("cuando", Timestamp.from(cuando))
                .param("usuario", usuarioId).param("id", prediccionId).update();
        return Optional.of(resuelta);
    }

    private static Prediccion fila(ResultSet rs, int i) throws SQLException {
        Timestamp resuelta = rs.getTimestamp("resuelta_en");
        return new Prediccion(rs.getObject("id", UUID.class), rs.getObject("ejecucion_id", UUID.class), rs.getObject("afirmacion_id", UUID.class),
                rs.getString("texto"), rs.getBigDecimal("confianza").intValueExact(), rs.getDate("fecha_revision").toLocalDate(),
                Prediccion.Estado.valueOf(rs.getString("resultado").toUpperCase()), Optional.ofNullable(resuelta).map(Timestamp::toInstant));
    }
}
