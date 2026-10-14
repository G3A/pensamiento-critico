package pensamiento.web.seguridad;

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

import pensamiento.nucleo.LlaveDeAcceso;
import pensamiento.nucleo.Titular;
import pensamiento.nucleo.puertos.RepositorioLlaves;

/** Todo por las funciones SECURITY DEFINER de V15: el rol de la app no tiene permisos sobre llave_acceso ni codigo_recuperacion. */
@Repository
@Transactional
public class RepositorioLlavesJdbc implements RepositorioLlaves {

    private final JdbcClient jdbc;

    public RepositorioLlavesJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean guardar(Titular t, LlaveDeAcceso l) {
        return Boolean.TRUE.equals(titular(jdbc.sql("""
                SELECT llave_guardar(:u, :i, :o, :credencial, :entidad, :clave, :firmas, :uv, :transportes, :respaldable, :respaldada,
                                     :atestacion, :cliente, :etiqueta, :creada, :usada)"""), t)
                .param("credencial", l.credencialId()).param("entidad", l.entidadUsuario()).param("clave", l.clavePublicaCose())
                .param("firmas", l.firmas()).param("uv", l.uvInicializada()).param("transportes", l.transportes().stream().sorted().toArray(String[]::new))
                .param("respaldable", l.respaldable()).param("respaldada", l.respaldada()).param("atestacion", l.atestacion())
                .param("cliente", l.datosCliente()).param("etiqueta", l.etiqueta()).param("creada", Timestamp.from(l.creada()))
                .param("usada", Timestamp.from(l.usada()))
                .query(Boolean.class).single());
    }

    @Override
    public Optional<LlaveDeAcceso> porCredencial(Titular t, byte[] credencialId) {
        return titular(jdbc.sql("SELECT * FROM llaves_de(:u, :i, :o) WHERE credencial_id = :credencial"), t).param("credencial", credencialId)
                .query(RepositorioLlavesJdbc::fila).optional();
    }

    @Override
    public List<LlaveDeAcceso> de(Titular t) {
        return titular(jdbc.sql("SELECT * FROM llaves_de(:u, :i, :o)"), t).query(RepositorioLlavesJdbc::fila).list();
    }

    @Override
    public boolean borrar(Titular t, byte[] credencialId) {
        return Boolean.TRUE.equals(titular(jdbc.sql("SELECT llave_borrar(:u, :i, :o, :credencial)"), t).param("credencial", credencialId)
                .query(Boolean.class).single());
    }

    @Override
    public boolean reemplazarRecuperacion(Titular t, List<String> hashes) {
        return Boolean.TRUE.equals(titular(jdbc.sql("SELECT recuperacion_reemplazar(:u, :i, :o, :hashes)"), t)
                .param("hashes", hashes.toArray(String[]::new)).query(Boolean.class).single());
    }

    @Override
    public List<CodigoDeRecuperacion> recuperacionVigente(Titular t) {
        return titular(jdbc.sql("SELECT id, codigo_hash FROM recuperacion_vigentes(:u, :i, :o)"), t)
                .query((rs, n) -> new CodigoDeRecuperacion(rs.getObject("id", UUID.class), rs.getString("codigo_hash"))).list();
    }

    @Override
    public boolean usarRecuperacion(Titular t, UUID id, Instant ahora) {
        return Boolean.TRUE.equals(titular(jdbc.sql("SELECT recuperacion_usar(:u, :i, :o, :id, :ahora)"), t)
                .param("id", id).param("ahora", Timestamp.from(ahora)).query(Boolean.class).single());
    }

    private static JdbcClient.StatementSpec titular(JdbcClient.StatementSpec sql, Titular t) {
        return sql.param("u", t.usuario().orElse(null)).param("i", t.institucion().orElse(null)).param("o", t.operador().orElse(null));
    }

    static LlaveDeAcceso fila(ResultSet rs, int n) throws SQLException {
        String[] transportes = (String[]) rs.getArray("transportes").getArray();
        return new LlaveDeAcceso(rs.getBytes("credencial_id"), rs.getBytes("entidad_usuario"), rs.getBytes("clave_publica"), rs.getLong("firmas"),
                rs.getBoolean("uv_inicializada"), Set.of(transportes), rs.getBoolean("respaldable"), rs.getBoolean("respaldada"),
                rs.getBytes("atestacion"), rs.getBytes("datos_cliente"), rs.getString("etiqueta"), rs.getTimestamp("creada_en").toInstant(),
                rs.getTimestamp("usada_en").toInstant());
    }
}
