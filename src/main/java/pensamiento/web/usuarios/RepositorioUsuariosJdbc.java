package pensamiento.web.usuarios;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pensamiento.nucleo.Usuario;
import pensamiento.nucleo.puertos.RepositorioUsuarios;

@Repository
@Transactional
public class RepositorioUsuariosJdbc implements RepositorioUsuarios {

    private static final String COLUMNAS = "id, institucion_id, nombre, pin_hash, rol_global, activo";

    private final JdbcClient jdbc;

    public RepositorioUsuariosJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UUID> institucionUnica() {
        return jdbc.sql("SELECT id FROM institucion ORDER BY creada_en LIMIT 1").query(UUID.class).optional();
    }

    @Override
    public UUID crearInstitucion(String nombre) {
        return institucionUnica().orElseGet(() ->
                jdbc.sql("INSERT INTO institucion (nombre) VALUES (:nombre) RETURNING id")
                        .param("nombre", nombre)
                        .query(UUID.class)
                        .single());
    }

    @Override
    public Usuario guardar(Usuario u) {
        try {
            jdbc.sql("""
                    INSERT INTO usuario (id, institucion_id, nombre, pin_hash, rol_global, activo)
                    VALUES (:id, :institucion, :nombre, :pin, :rol, :activo)
                    ON CONFLICT (id) DO UPDATE SET nombre = EXCLUDED.nombre, pin_hash = EXCLUDED.pin_hash,
                      rol_global = EXCLUDED.rol_global, activo = EXCLUDED.activo
                    """)
                    .param("id", u.id())
                    .param("institucion", u.institucionId())
                    .param("nombre", u.nombre())
                    .param("pin", u.pinHash())
                    .param("rol", u.rol().enBaseDeDatos())
                    .param("activo", u.activo())
                    .update();
        } catch (DuplicateKeyException e) {
            throw new NombreRepetido(u.nombre());
        }
        return u;
    }

    @Override
    public Optional<Usuario> porId(UUID institucionId, UUID id) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM usuario WHERE institucion_id = :institucion AND id = :id")
                .param("institucion", institucionId).param("id", id)
                .query(RepositorioUsuariosJdbc::fila).optional();
    }

    @Override
    public Optional<Usuario> porNombre(UUID institucionId, String nombre) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM usuario WHERE institucion_id = :institucion AND nombre = :nombre")
                .param("institucion", institucionId).param("nombre", nombre)
                .query(RepositorioUsuariosJdbc::fila).optional();
    }

    @Override
    public List<Usuario> todos(UUID institucionId) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM usuario WHERE institucion_id = :institucion ORDER BY nombre")
                .param("institucion", institucionId)
                .query(RepositorioUsuariosJdbc::fila).list();
    }

    @Override
    public long contar(UUID institucionId) {
        return jdbc.sql("SELECT count(*) FROM usuario WHERE institucion_id = :institucion")
                .param("institucion", institucionId).query(Long.class).single();
    }

    static Usuario fila(ResultSet rs, int i) throws SQLException {
        return new Usuario(
                rs.getObject("id", UUID.class),
                rs.getObject("institucion_id", UUID.class),
                rs.getString("nombre"),
                rs.getString("pin_hash"),
                Usuario.RolGlobal.valueOf(rs.getString("rol_global").toUpperCase()),
                rs.getBoolean("activo"));
    }
}
