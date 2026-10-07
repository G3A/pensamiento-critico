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

import pensamiento.nucleo.Expediente;
import pensamiento.nucleo.puertos.RepositorioExpediente;

/** Filtra por usuario en SQL; RLS vuelve a filtrar por debajo. Borrado lógico: eliminado_en. */
@Repository
@Transactional
public class RepositorioExpedienteJdbc implements RepositorioExpediente {

    private static final String COLUMNAS = "id, usuario_id, institucion_id, nombre, postura_id, estado, creado_en";

    private final JdbcClient jdbc;

    public RepositorioExpedienteJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Expediente guardar(Expediente e) {
        jdbc.sql("""
                INSERT INTO expediente (id, usuario_id, institucion_id, nombre, postura_id, estado, creado_en)
                VALUES (:id, :usuario, :institucion, :nombre, :postura, :estado, :creado)
                ON CONFLICT (id) DO UPDATE SET nombre = EXCLUDED.nombre, postura_id = EXCLUDED.postura_id, estado = EXCLUDED.estado
                """)
                .param("id", e.id())
                .param("usuario", e.usuarioId())
                .param("institucion", e.institucionId())
                .param("nombre", e.nombre())
                .param("postura", e.posturaId().orElse(null))
                .param("estado", e.estado().name().toLowerCase())
                .param("creado", Timestamp.from(e.creadoEn()))
                .update();
        return e;
    }

    @Override
    public Optional<Expediente> porId(UUID usuarioId, UUID id) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM expediente WHERE usuario_id = :usuario AND id = :id AND eliminado_en IS NULL")
                .param("usuario", usuarioId).param("id", id)
                .query(RepositorioExpedienteJdbc::fila).optional();
    }

    @Override
    public List<Expediente> deUsuario(UUID usuarioId) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM expediente WHERE usuario_id = :usuario AND eliminado_en IS NULL ORDER BY creado_en DESC, id DESC")
                .param("usuario", usuarioId)
                .query(RepositorioExpedienteJdbc::fila).list();
    }

    @Override
    public boolean borrar(UUID usuarioId, UUID id, java.time.Instant cuando) {
        return jdbc.sql("UPDATE expediente SET eliminado_en = :cuando WHERE usuario_id = :usuario AND id = :id AND eliminado_en IS NULL")
                .param("cuando", Timestamp.from(cuando)).param("usuario", usuarioId).param("id", id)
                .update() == 1;
    }

    static Expediente fila(ResultSet rs, int i) throws SQLException {
        return new Expediente(
                rs.getObject("id", UUID.class),
                rs.getObject("usuario_id", UUID.class),
                rs.getObject("institucion_id", UUID.class),
                rs.getString("nombre"),
                Optional.ofNullable(rs.getObject("postura_id", UUID.class)),
                Expediente.Estado.valueOf(rs.getString("estado").toUpperCase()),
                rs.getTimestamp("creado_en").toInstant());
    }
}
