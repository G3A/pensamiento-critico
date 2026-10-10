package pensamiento.expediente;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pensamiento.nucleo.CambioOpinion;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.puertos.RegistroDeRazonamiento;

/**
 * Lectura del diario de razonamiento y del registro de cambios de opinión en PostgreSQL: ejecuciones con el nombre de su
 * expediente y sus cambios, cambios con la técnica que los registró y afirmaciones con rol postura. Filtra por usuario;
 * RLS por debajo.
 */
@Repository
@Transactional(readOnly = true)
public class RegistroDeRazonamientoJdbc implements RegistroDeRazonamiento {

    private final JdbcClient jdbc;

    public RegistroDeRazonamientoJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<EjecucionEnDiario> ejecucionesDesde(UUID usuarioId, Instant desde) {
        return jdbc.sql("""
                SELECT e.id, e.tecnica_id, e.resumen, x.nombre AS expediente, e.creada_en,
                       (SELECT count(*) FROM cambio_opinion c WHERE c.ejecucion_id = e.id) AS cambios
                FROM ejecucion e LEFT JOIN expediente x ON x.id = e.expediente_id AND x.eliminado_en IS NULL
                WHERE e.usuario_id = :usuario AND e.eliminada_en IS NULL AND e.creada_en >= :desde
                ORDER BY e.creada_en, e.id
                """)
                .param("usuario", usuarioId).param("desde", Timestamp.from(desde))
                .query((rs, n) -> new EjecucionEnDiario(rs.getObject("id", UUID.class), IdTecnica.de(rs.getString("tecnica_id")),
                        rs.getString("resumen"), Optional.ofNullable(rs.getString("expediente")), rs.getInt("cambios"),
                        rs.getTimestamp("creada_en").toInstant()))
                .list();
    }

    @Override
    public List<CambioRegistrado> cambios(UUID usuarioId) {
        return jdbc.sql("""
                SELECT c.id, c.afirmacion_id, a.texto, c.confianza_antes, c.confianza_despues, c.causa, c.ejecucion_id, c.creado_en,
                       e.tecnica_id
                FROM cambio_opinion c JOIN afirmacion a ON a.id = c.afirmacion_id
                LEFT JOIN ejecucion e ON e.id = c.ejecucion_id
                WHERE c.usuario_id = :usuario
                ORDER BY c.creado_en, c.id
                """)
                .param("usuario", usuarioId)
                .query((rs, n) -> new CambioRegistrado(new CambioOpinion(rs.getObject("id", UUID.class), rs.getObject("afirmacion_id", UUID.class),
                        rs.getString("texto"), rs.getBigDecimal("confianza_antes").intValue(), rs.getBigDecimal("confianza_despues").intValue(),
                        CambioOpinion.Causa.de(rs.getString("causa")), Optional.ofNullable(rs.getObject("ejecucion_id", UUID.class)),
                        rs.getTimestamp("creado_en").toInstant()), Optional.ofNullable(rs.getString("tecnica_id")).map(IdTecnica::de)))
                .list();
    }

    @Override
    public List<PosturaRegistrada> posturas(UUID usuarioId) {
        return jdbc.sql("""
                SELECT a.id, a.texto, max(e.creada_en) AS ultima
                FROM afirmacion a
                JOIN ejecucion_afirmacion ea ON ea.afirmacion_id = a.id AND ea.rol = 'postura'
                JOIN ejecucion e ON e.id = ea.ejecucion_id AND e.eliminada_en IS NULL
                WHERE a.usuario_id = :usuario
                GROUP BY a.id, a.texto
                ORDER BY ultima, a.id
                """)
                .param("usuario", usuarioId)
                .query((rs, n) -> new PosturaRegistrada(rs.getObject("id", UUID.class), rs.getString("texto"), rs.getTimestamp("ultima").toInstant()))
                .list();
    }
}
