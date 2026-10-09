package pensamiento.expediente;

import java.math.BigDecimal;
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
import tools.jackson.core.type.TypeReference;

import pensamiento.catalogo.MapeadorJson;
import pensamiento.nucleo.Afirmacion;
import pensamiento.nucleo.EstadoAfirmacion;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.OrigenAfirmacion;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.Verificacion;
import pensamiento.nucleo.puertos.RepositorioVerificaciones;

/**
 * La ficha de verificación en PostgreSQL: el estado de la afirmación (tabla afirmacion de V1) y las preguntas marcadas
 * (tabla verificacion de V8). Filtra por usuario; RLS por debajo. El veredicto queda ligado a la versión 1 de R03.
 */
@Repository
@Transactional
public class RepositorioVerificacionesJdbc implements RepositorioVerificaciones {

    private final JdbcClient jdbc;

    public RepositorioVerificacionesJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Afirmacion> afirmacion(UUID usuarioId, UUID afirmacionId) {
        return jdbc.sql("""
                SELECT id, usuario_id, institucion_id, texto, tipo, origen, adoptada, confianza, estado, fuerza_neta
                FROM afirmacion WHERE id = :id AND usuario_id = :usuario
                """).param("id", afirmacionId).param("usuario", usuarioId).query(RepositorioVerificacionesJdbc::afirmacion).optional();
    }

    @Override
    public boolean cambiarTipo(UUID usuarioId, UUID afirmacionId, TipoAfirmacion tipo) {
        return jdbc.sql("UPDATE afirmacion SET tipo = :tipo WHERE id = :id AND usuario_id = :usuario")
                .param("tipo", tipo.enBaseDeDatos()).param("id", afirmacionId).param("usuario", usuarioId).update() == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public Verificacion verificacion(UUID usuarioId, UUID afirmacionId) {
        return jdbc.sql("SELECT afirmacion_id, preguntas, actualizada_en FROM verificacion WHERE afirmacion_id = :id AND usuario_id = :usuario")
                .param("id", afirmacionId).param("usuario", usuarioId).query(RepositorioVerificacionesJdbc::verificacion).optional()
                .orElse(Verificacion.nueva(afirmacionId));
    }

    @Override
    public void marcarPreguntas(UUID usuarioId, UUID institucionId, UUID afirmacionId, List<String> respondidas, Instant cuando) {
        int filas = jdbc.sql("""
                INSERT INTO verificacion (afirmacion_id, usuario_id, institucion_id, preguntas, actualizada_en)
                SELECT a.id, :usuario, :institucion, CAST(:preguntas AS jsonb), :cuando FROM afirmacion a WHERE a.id = :id AND a.usuario_id = :usuario
                ON CONFLICT (afirmacion_id) DO UPDATE SET preguntas = EXCLUDED.preguntas, actualizada_en = EXCLUDED.actualizada_en
                WHERE verificacion.usuario_id = :usuario
                """)
                .param("id", afirmacionId).param("usuario", usuarioId).param("institucion", institucionId)
                .param("preguntas", MapeadorJson.escribir(List.copyOf(respondidas)).texto()).param("cuando", Timestamp.from(cuando))
                .update();
        if (filas == 0) {
            throw new IllegalArgumentException("La afirmación no existe o es de otra persona");
        }
    }

    @Override
    public boolean guardarVeredicto(UUID usuarioId, UUID afirmacionId, Verificacion.Veredicto v) {
        return jdbc.sql("""
                UPDATE afirmacion SET tipo = :tipo, estado = :estado, fuerza_neta = :neta, confianza = :confianza,
                    regla_version_id = (SELECT id FROM regla_version WHERE regla = 'R03' AND version = 1)
                WHERE id = :id AND usuario_id = :usuario
                """)
                .param("tipo", v.tipo().enBaseDeDatos()).param("estado", v.estado().name().toLowerCase()).param("neta", v.fuerzaNeta())
                .param("confianza", v.confianza().map(BigDecimal::valueOf).orElse(null)).param("id", afirmacionId).param("usuario", usuarioId)
                .update() == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Verificacion.Origen> origen(UUID usuarioId, UUID afirmacionId) {
        return jdbc.sql("""
                SELECT e.id, e.tecnica_id, e.expediente_id
                FROM ejecucion_afirmacion ea JOIN ejecucion e ON e.id = ea.ejecucion_id
                WHERE ea.afirmacion_id = :id AND ea.sentido = 'producida' AND e.usuario_id = :usuario
                ORDER BY e.creada_en LIMIT 1
                """).param("id", afirmacionId).param("usuario", usuarioId)
                .query((rs, i) -> new Verificacion.Origen(rs.getObject("id", UUID.class), IdTecnica.de(rs.getString("tecnica_id")),
                        Optional.ofNullable(rs.getObject("expediente_id", UUID.class))))
                .optional();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Verificacion> deUsuario(UUID usuarioId) {
        return jdbc.sql("SELECT afirmacion_id, preguntas, actualizada_en FROM verificacion WHERE usuario_id = :usuario ORDER BY actualizada_en, afirmacion_id")
                .param("usuario", usuarioId).query(RepositorioVerificacionesJdbc::verificacion).list();
    }

    private static Verificacion verificacion(ResultSet rs, int i) throws SQLException {
        List<String> preguntas = MapeadorJson.mapper().readValue(rs.getString("preguntas"), new TypeReference<List<String>>() { });
        return new Verificacion(rs.getObject("afirmacion_id", UUID.class), preguntas, Optional.of(rs.getTimestamp("actualizada_en").toInstant()));
    }

    private static Afirmacion afirmacion(ResultSet rs, int i) throws SQLException {
        BigDecimal confianza = rs.getBigDecimal("confianza");
        return new Afirmacion(rs.getObject("id", UUID.class), rs.getObject("usuario_id", UUID.class), rs.getObject("institucion_id", UUID.class),
                rs.getString("texto"), TipoAfirmacion.valueOf(rs.getString("tipo").toUpperCase()),
                OrigenAfirmacion.valueOf(rs.getString("origen").toUpperCase()), rs.getBoolean("adoptada"),
                Optional.ofNullable(confianza).map(BigDecimal::intValue), EstadoAfirmacion.valueOf(rs.getString("estado").toUpperCase()),
                rs.getInt("fuerza_neta"));
    }
}
