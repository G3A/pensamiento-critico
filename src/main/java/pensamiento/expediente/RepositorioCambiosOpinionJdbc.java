package pensamiento.expediente;

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

import pensamiento.nucleo.CambioOpinion;
import pensamiento.nucleo.puertos.RepositorioCambiosOpinion;

/**
 * Cambios de opinión en PostgreSQL (tabla cambio_opinion de V1). Filtra por usuario; RLS por debajo. El rol de aplicación
 * solo puede insertar y leer: la tabla es inmutable. Idempotente por identificador.
 */
@Repository
@Transactional
public class RepositorioCambiosOpinionJdbc implements RepositorioCambiosOpinion {

    private static final String COLUMNAS = """
            c.id, c.afirmacion_id, a.texto, c.confianza_antes, c.confianza_despues, c.causa, c.ejecucion_id, c.creado_en
            FROM cambio_opinion c JOIN afirmacion a ON a.id = c.afirmacion_id
            """;

    private final JdbcClient jdbc;

    public RepositorioCambiosOpinionJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void guardar(UUID usuarioId, UUID institucionId, UUID ejecucionId, List<CambioOpinion.Declarado> cambios, Instant cuando) {
        for (CambioOpinion.Declarado c : cambios) {
            insertar(usuarioId, institucionId, c.id(), c.afirmacionId(), c.confianzaAntes(), c.confianzaDespues(), c.causa(), ejecucionId, cuando);
        }
    }

    @Override
    public void restaurar(UUID usuarioId, UUID institucionId, CambioOpinion c) {
        insertar(usuarioId, institucionId, c.id(), c.afirmacionId(), c.confianzaAntes(), c.confianzaDespues(), c.causa(), c.ejecucionId().orElse(null),
                c.creadoEn());
    }

    private void insertar(UUID usuarioId, UUID institucionId, UUID id, UUID afirmacionId, int antes, int despues, CambioOpinion.Causa causa,
                          UUID ejecucionId, Instant cuando) {
        jdbc.sql("""
                INSERT INTO cambio_opinion (id, usuario_id, institucion_id, afirmacion_id, confianza_antes, confianza_despues, causa, ejecucion_id, creado_en)
                VALUES (:id, :usuario, :institucion, :afirmacion, :antes, :despues, :causa, :ejecucion, :cuando)
                ON CONFLICT (id) DO NOTHING
                """)
                .param("id", id).param("usuario", usuarioId).param("institucion", institucionId).param("afirmacion", afirmacionId)
                .param("antes", antes).param("despues", despues).param("causa", causa.toString()).param("ejecucion", ejecucionId)
                .param("cuando", Timestamp.from(cuando))
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CambioOpinion> deUsuario(UUID usuarioId) {
        return jdbc.sql("SELECT " + COLUMNAS + " WHERE c.usuario_id = :usuario ORDER BY c.creado_en, c.id")
                .param("usuario", usuarioId).query(RepositorioCambiosOpinionJdbc::fila).list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CambioOpinion> deEjecucion(UUID usuarioId, UUID ejecucionId) {
        return jdbc.sql("SELECT " + COLUMNAS + " WHERE c.usuario_id = :usuario AND c.ejecucion_id = :ejecucion ORDER BY c.creado_en, c.id")
                .param("usuario", usuarioId).param("ejecucion", ejecucionId).query(RepositorioCambiosOpinionJdbc::fila).list();
    }

    private static CambioOpinion fila(ResultSet rs, int i) throws SQLException {
        return new CambioOpinion(rs.getObject("id", UUID.class), rs.getObject("afirmacion_id", UUID.class), rs.getString("texto"),
                rs.getBigDecimal("confianza_antes").intValue(), rs.getBigDecimal("confianza_despues").intValue(),
                CambioOpinion.Causa.de(rs.getString("causa")), Optional.ofNullable(rs.getObject("ejecucion_id", UUID.class)),
                rs.getTimestamp("creado_en").toInstant());
    }
}
