package pensamiento.trabajos;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pensamiento.nucleo.Json;
import pensamiento.nucleo.Trabajo;
import pensamiento.nucleo.Uuid7;
import pensamiento.nucleo.puertos.ColaTrabajos;

/**
 * La tabla trabajo de V1 (con disponible_en de V8). Tomar usa FOR UPDATE SKIP LOCKED: dos ejecutores nunca toman el mismo
 * trabajo. La tabla no tiene filas de usuario: el payload dice de quién es cada trabajo.
 */
@Repository
@Transactional
public class ColaTrabajosJdbc implements ColaTrabajos {

    private static final String COLUMNAS = "id, tipo, estado, intentos, payload::text AS payload, error, creado_en, disponible_en";

    private final JdbcClient jdbc;

    public ColaTrabajosJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UUID encolar(String tipo, Json payload, Instant disponibleEn) {
        UUID id = Uuid7.en(Instant.now());
        jdbc.sql("""
                INSERT INTO trabajo (id, tipo, estado, intentos, payload, disponible_en, creado_en)
                VALUES (:id, :tipo, 'pendiente', 0, CAST(:payload AS jsonb), :disponible, clock_timestamp())
                """).param("id", id).param("tipo", tipo).param("payload", payload.texto()).param("disponible", Timestamp.from(disponibleEn)).update();
        return id;
    }

    @Override
    public Optional<Trabajo> tomar(Instant ahora, Set<String> tipos) {
        if (tipos.isEmpty()) {
            return Optional.empty();
        }
        return jdbc.sql("""
                UPDATE trabajo SET estado = 'en_proceso', intentos = intentos + 1, actualizado_en = now()
                WHERE id = (SELECT id FROM trabajo WHERE estado = 'pendiente' AND disponible_en <= :ahora AND tipo IN (:tipos)
                            ORDER BY creado_en, id FOR UPDATE SKIP LOCKED LIMIT 1)
                RETURNING
                """ + COLUMNAS).param("ahora", Timestamp.from(ahora)).param("tipos", List.copyOf(tipos)).query(ColaTrabajosJdbc::fila).optional();
    }

    @Override
    public void terminar(UUID id) {
        jdbc.sql("UPDATE trabajo SET estado = 'hecho', error = NULL, actualizado_en = now() WHERE id = :id").param("id", id).update();
    }

    @Override
    public void reintentar(UUID id, Instant disponibleEn, String motivo) {
        jdbc.sql("UPDATE trabajo SET estado = 'pendiente', disponible_en = :disponible, error = :motivo, actualizado_en = now() WHERE id = :id")
                .param("disponible", Timestamp.from(disponibleEn)).param("motivo", motivo).param("id", id).update();
    }

    @Override
    public void fallar(UUID id, String motivo) {
        jdbc.sql("UPDATE trabajo SET estado = 'error', error = :motivo, actualizado_en = now() WHERE id = :id")
                .param("motivo", motivo).param("id", id).update();
    }

    @Override
    public int reencolarEnProceso(Set<String> tipos) {
        if (tipos.isEmpty()) {
            return 0;
        }
        return jdbc.sql("UPDATE trabajo SET estado = 'pendiente', actualizado_en = now() WHERE estado = 'en_proceso' AND tipo IN (:tipos)")
                .param("tipos", List.copyOf(tipos)).update();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Trabajo> porId(UUID id) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM trabajo WHERE id = :id").param("id", id).query(ColaTrabajosJdbc::fila).optional();
    }

    private static Trabajo fila(ResultSet rs, int i) throws SQLException {
        return new Trabajo(rs.getObject("id", UUID.class), rs.getString("tipo"), Trabajo.Estado.valueOf(rs.getString("estado").toUpperCase()),
                rs.getInt("intentos"), new Json(rs.getString("payload")), Optional.ofNullable(rs.getString("error")),
                rs.getTimestamp("creado_en").toInstant(), rs.getTimestamp("disponible_en").toInstant());
    }
}
