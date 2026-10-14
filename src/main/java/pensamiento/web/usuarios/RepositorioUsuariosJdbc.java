package pensamiento.web.usuarios;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pensamiento.nucleo.Usuario;
import pensamiento.nucleo.puertos.RepositorioUsuarios;

/**
 * Cuentas bajo RLS por institución (V13). Ningún método lanza por un nombre repetido: el alta usa
 * INSERT … ON CONFLICT DO NOTHING RETURNING, así la transacción no queda marcada para deshacer (innegociable 4). Buscar por
 * nombre y canjear un código, que ocurren antes de saber quién es la persona, van por funciones SECURITY DEFINER.
 */
@Repository
@Transactional
public class RepositorioUsuariosJdbc implements RepositorioUsuarios {

    private static final String COLUMNAS = "id, institucion_id, nombre, clave_hash, tipo_clave, rol_global, activo";

    private final JdbcClient jdbc;

    public RepositorioUsuariosJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Usuario> crear(Usuario u) {
        return jdbc.sql("""
                INSERT INTO usuario (id, institucion_id, nombre, clave_hash, tipo_clave, rol_global, activo)
                VALUES (:id, :institucion, :nombre, :clave, :tipo, :rol, :activo)
                ON CONFLICT DO NOTHING RETURNING\s""" + COLUMNAS)
                .param("id", u.id())
                .param("institucion", u.institucionId())
                .param("nombre", u.nombre())
                .param("clave", u.claveHash().isEmpty() ? null : u.claveHash())
                .param("tipo", u.tipoClave().enBaseDeDatos())
                .param("rol", u.rol().enBaseDeDatos())
                .param("activo", u.activo())
                .query(RepositorioUsuariosJdbc::fila).optional();
    }

    @Override
    public Optional<Usuario> porId(UUID institucionId, UUID id) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM usuario WHERE institucion_id = :institucion AND id = :id")
                .param("institucion", institucionId).param("id", id)
                .query(RepositorioUsuariosJdbc::fila).optional();
    }

    @Override
    public Optional<Usuario> porNombre(UUID institucionId, String nombre) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM cuenta_para_entrar(:institucion, :nombre)")
                .param("institucion", institucionId).param("nombre", nombre)
                .query(RepositorioUsuariosJdbc::fila).optional();
    }

    @Override
    public PaginaDeCuentas buscar(UUID institucionId, String texto, int pagina, int tamano) {
        String patron = Usuario.claveDeNombre(texto == null ? "" : texto).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        long total = jdbc.sql("SELECT count(*) FROM usuario WHERE institucion_id = :institucion AND nombre_clave LIKE :patron")
                .param("institucion", institucionId).param("patron", "%" + patron + "%").query(Long.class).single();
        var cuentas = jdbc.sql("SELECT " + COLUMNAS + " FROM usuario WHERE institucion_id = :institucion AND nombre_clave LIKE :patron "
                        + "ORDER BY nombre_clave, id LIMIT :tamano OFFSET :desde")
                .param("institucion", institucionId).param("patron", "%" + patron + "%")
                .param("tamano", tamano).param("desde", (long) Math.max(pagina, 0) * tamano)
                .query(RepositorioUsuariosJdbc::fila).list();
        return new PaginaDeCuentas(cuentas, total);
    }

    @Override
    public long contar(UUID institucionId) {
        return jdbc.sql("SELECT count(*) FROM usuario WHERE institucion_id = :institucion")
                .param("institucion", institucionId).query(Long.class).single();
    }

    @Override
    public boolean cambiarClave(UUID institucionId, UUID id, String claveHash, Usuario.TipoClave tipo) {
        return jdbc.sql("UPDATE usuario SET clave_hash = :clave, tipo_clave = :tipo WHERE institucion_id = :institucion AND id = :id")
                .param("clave", claveHash.isEmpty() ? null : claveHash).param("tipo", tipo.enBaseDeDatos())
                .param("institucion", institucionId).param("id", id).update() == 1;
    }

    @Override
    public boolean cambiarActivo(UUID institucionId, UUID id, boolean activo) {
        return jdbc.sql("UPDATE usuario SET activo = :activo WHERE institucion_id = :institucion AND id = :id")
                .param("activo", activo).param("institucion", institucionId).param("id", id).update() == 1;
    }

    @Override
    public void guardarCodigo(UUID institucionId, UUID usuarioId, TipoCodigo tipo, String codigoHash, Instant caducaEn, Optional<UUID> creadoPor) {
        jdbc.sql("UPDATE codigo_cuenta SET caduca_en = '-infinity' WHERE usuario_id = :usuario AND usado_en IS NULL")
                .param("usuario", usuarioId).update();
        jdbc.sql("""
                INSERT INTO codigo_cuenta (institucion_id, usuario_id, tipo, codigo_hash, creado_por, caduca_en)
                VALUES (:institucion, :usuario, :tipo, :hash, :por, :caduca)""")
                .param("institucion", institucionId).param("usuario", usuarioId).param("tipo", tipo.enBaseDeDatos())
                .param("hash", codigoHash).param("por", creadoPor.orElse(null)).param("caduca", Timestamp.from(caducaEn))
                .update();
    }

    @Override
    public Optional<CodigoCanjeado> codigoVigente(UUID institucionId, String codigoHash, Instant ahora) {
        return jdbc.sql("SELECT usuario_id, tipo FROM cuenta_codigo_vigente(:institucion, :hash, :ahora)")
                .param("institucion", institucionId).param("hash", codigoHash).param("ahora", Timestamp.from(ahora))
                .query((rs, i) -> new CodigoCanjeado(rs.getObject("usuario_id", UUID.class), TipoCodigo.valueOf(rs.getString("tipo").toUpperCase())))
                .optional();
    }

    @Override
    public Optional<CodigoCanjeado> canjearCodigo(UUID institucionId, String codigoHash, Instant ahora) {
        return jdbc.sql("SELECT usuario_id, tipo FROM cuenta_canjear_codigo(:institucion, :hash, :ahora)")
                .param("institucion", institucionId).param("hash", codigoHash).param("ahora", Timestamp.from(ahora))
                .query((rs, i) -> new CodigoCanjeado(rs.getObject("usuario_id", UUID.class), TipoCodigo.valueOf(rs.getString("tipo").toUpperCase())))
                .optional();
    }

    @Override
    public Optional<TipoCodigo> codigoPendiente(UUID institucionId, UUID usuarioId, Instant ahora) {
        return jdbc.sql("""
                SELECT tipo FROM codigo_cuenta WHERE institucion_id = :institucion AND usuario_id = :usuario
                  AND usado_en IS NULL AND caduca_en > :ahora ORDER BY creado_en DESC LIMIT 1""")
                .param("institucion", institucionId).param("usuario", usuarioId).param("ahora", Timestamp.from(ahora))
                .query((rs, i) -> TipoCodigo.valueOf(rs.getString("tipo").toUpperCase())).optional();
    }

    static Usuario fila(ResultSet rs, int i) throws SQLException {
        String clave = rs.getString("clave_hash");
        return new Usuario(
                rs.getObject("id", UUID.class),
                rs.getObject("institucion_id", UUID.class),
                rs.getString("nombre"),
                clave == null ? "" : clave,
                Usuario.TipoClave.valueOf(rs.getString("tipo_clave").toUpperCase()),
                Usuario.RolGlobal.valueOf(rs.getString("rol_global").toUpperCase()),
                rs.getBoolean("activo"));
    }
}
