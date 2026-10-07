package pensamiento.web.usuarios;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pensamiento.nucleo.puertos.RegistroAuditoria;

/** Tabla auditoria: el rol de aplicación solo puede insertar y leer lo propio. */
@Repository
@Transactional
public class RegistroAuditoriaJdbc implements RegistroAuditoria {

    private final JdbcClient jdbc;

    public RegistroAuditoriaJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void registrar(Evento e) {
        jdbc.sql("""
                INSERT INTO auditoria (usuario_id, institucion_id, accion, objeto_tipo, objeto_id, fecha)
                VALUES (:usuario, :institucion, :accion, :tipo, :objeto, :fecha)
                """)
                .param("usuario", e.usuarioId().orElse(null))
                .param("institucion", e.institucionId())
                .param("accion", e.accion().name().toLowerCase())
                .param("tipo", e.objetoTipo())
                .param("objeto", e.objetoId().orElse(null))
                .param("fecha", Timestamp.from(e.fecha()))
                .update();
    }

    @Override
    public List<Evento> deUsuario(UUID usuarioId) {
        return jdbc.sql("""
                SELECT usuario_id, institucion_id, accion, objeto_tipo, objeto_id, fecha
                FROM auditoria WHERE usuario_id = :usuario ORDER BY fecha DESC, id DESC
                """)
                .param("usuario", usuarioId)
                .query((rs, i) -> new Evento(
                        Optional.ofNullable(rs.getObject("usuario_id", UUID.class)),
                        rs.getObject("institucion_id", UUID.class),
                        Accion.valueOf(rs.getString("accion").toUpperCase()),
                        rs.getString("objeto_tipo"),
                        Optional.ofNullable(rs.getObject("objeto_id", UUID.class)),
                        rs.getTimestamp("fecha").toInstant()))
                .list();
    }
}
