package pensamiento.biblioteca;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pensamiento.nucleo.Documento;
import pensamiento.nucleo.Fragmento;
import pensamiento.nucleo.Pasaje;
import pensamiento.nucleo.puertos.Biblioteca;

/**
 * La biblioteca en PostgreSQL con pgvector (tablas documento y fragmento de V1, completadas en V8). El texto completo usa la
 * columna tsv en español con un índice GIN; la búsqueda semántica, el índice HNSW sobre embedding con similitud coseno y el
 * escaneo iterativo de pgvector para que el filtro por persona no deje la lista corta. Filtra por persona (propio o
 * compartido); RLS por debajo, que además limita lo compartido a la institución.
 */
@Repository
@Transactional
public class BibliotecaPgvector implements Biblioteca {

    private static final String DOCUMENTO = """
            d.id, d.usuario_id, d.nombre, d.tipo, d.estado, d.compartido, d.hash, d.tamano, d.paginas, d.error, d.creado_en,
            d.contenido IS NOT NULL AS con_original,
            (SELECT count(*) FROM fragmento f WHERE f.documento_id = d.id) AS fragmentos,
            (SELECT count(*) FROM fragmento f WHERE f.documento_id = d.id AND f.embedding IS NOT NULL) AS con_vector
            FROM documento d
            """;
    private static final String VISIBLE = "(d.usuario_id = :usuario OR d.compartido)";

    private final JdbcClient jdbc;

    public BibliotecaPgvector(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Documento crear(UUID usuarioId, UUID institucionId, NuevoDocumento nuevo) {
        Optional<String> repetido = jdbc.sql("SELECT nombre FROM documento WHERE usuario_id = :usuario AND hash = :hash")
                .param("usuario", usuarioId).param("hash", nuevo.hash()).query(String.class).optional();
        if (repetido.isPresent()) {
            throw new DocumentoRepetido(repetido.get());
        }
        jdbc.sql("""
                INSERT INTO documento (id, usuario_id, institucion_id, nombre, tipo, estado, compartido, hash, contenido, tamano)
                VALUES (:id, :usuario, :institucion, :nombre, :tipo, 'en_proceso', false, :hash, :contenido, :tamano)
                """)
                .param("id", nuevo.id()).param("usuario", usuarioId).param("institucion", institucionId).param("nombre", nuevo.nombre())
                .param("tipo", nuevo.tipo().enBaseDeDatos()).param("hash", nuevo.hash()).param("contenido", nuevo.contenido())
                .param("tamano", (long) nuevo.contenido().length)
                .update();
        return porId(usuarioId, nuevo.id()).orElseThrow();
    }

    @Override
    public boolean restaurar(UUID usuarioId, UUID institucionId, Documento d, List<Fragmento> fragmentos) {
        boolean existe = jdbc.sql("SELECT EXISTS (SELECT 1 FROM documento WHERE id = :id OR (usuario_id = :usuario AND hash = :hash))")
                .param("id", d.id()).param("usuario", usuarioId).param("hash", d.hash()).query(Boolean.class).single();
        if (existe) {
            return false;
        }
        jdbc.sql("""
                INSERT INTO documento (id, usuario_id, institucion_id, nombre, tipo, estado, compartido, hash, contenido, tamano, paginas, creado_en)
                VALUES (:id, :usuario, :institucion, :nombre, :tipo, 'indexado', false, :hash, NULL, :tamano, :paginas, :creado)
                """)
                .param("id", d.id()).param("usuario", usuarioId).param("institucion", institucionId).param("nombre", d.nombre())
                .param("tipo", d.tipo().enBaseDeDatos()).param("hash", d.hash()).param("tamano", d.tamano()).param("paginas", d.paginas().orElse(null))
                .param("creado", java.sql.Timestamp.from(d.creadoEn()))
                .update();
        for (Fragmento f : fragmentos) {
            jdbc.sql("INSERT INTO fragmento (id, documento_id, orden, texto, pagina) VALUES (:id, :documento, :orden, :texto, :pagina)")
                    .param("id", f.id()).param("documento", d.id()).param("orden", f.orden()).param("texto", f.texto())
                    .param("pagina", f.pagina().orElse(null)).update();
        }
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Documento> propioPorHash(UUID usuarioId, String hash) {
        return jdbc.sql("SELECT " + DOCUMENTO + " WHERE d.usuario_id = :usuario AND d.hash = :hash")
                .param("usuario", usuarioId).param("hash", hash).query(BibliotecaPgvector::documento).optional();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Documento> porId(UUID usuarioId, UUID id) {
        return jdbc.sql("SELECT " + DOCUMENTO + " WHERE d.id = :id AND " + VISIBLE)
                .param("id", id).param("usuario", usuarioId).query(BibliotecaPgvector::documento).optional();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Documento> visibles(UUID usuarioId) {
        return jdbc.sql("SELECT " + DOCUMENTO + " WHERE " + VISIBLE + " ORDER BY d.creado_en DESC, d.id DESC")
                .param("usuario", usuarioId).query(BibliotecaPgvector::documento).list();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<byte[]> contenido(UUID usuarioId, UUID id) {
        return jdbc.sql("SELECT d.contenido FROM documento d WHERE d.id = :id AND d.contenido IS NOT NULL AND " + VISIBLE)
                .param("id", id).param("usuario", usuarioId).query((rs, i) -> rs.getBytes("contenido")).optional();
    }

    @Override
    public void indexar(UUID usuarioId, UUID documentoId, List<Fragmento.Nuevo> fragmentos, Optional<Integer> paginas) {
        if (!esDuenio(usuarioId, documentoId)) {
            throw new IllegalArgumentException("El documento no existe o es de otra persona");
        }
        jdbc.sql("DELETE FROM fragmento WHERE documento_id = :documento").param("documento", documentoId).update();
        for (Fragmento.Nuevo f : fragmentos) {
            jdbc.sql("INSERT INTO fragmento (documento_id, orden, texto, pagina) VALUES (:documento, :orden, :texto, :pagina)")
                    .param("documento", documentoId).param("orden", f.orden()).param("texto", f.texto()).param("pagina", f.pagina().orElse(null))
                    .update();
        }
        jdbc.sql("UPDATE documento SET estado = 'indexado', error = NULL, paginas = :paginas WHERE id = :id AND usuario_id = :usuario")
                .param("paginas", paginas.orElse(null)).param("id", documentoId).param("usuario", usuarioId).update();
    }

    @Override
    public void marcarError(UUID usuarioId, UUID documentoId, String motivo) {
        jdbc.sql("DELETE FROM fragmento f USING documento d WHERE f.documento_id = :id AND d.id = f.documento_id AND d.usuario_id = :usuario")
                .param("id", documentoId).param("usuario", usuarioId).update();
        jdbc.sql("UPDATE documento SET estado = 'error', error = :motivo WHERE id = :id AND usuario_id = :usuario")
                .param("motivo", motivo).param("id", documentoId).param("usuario", usuarioId).update();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Fragmento> sinVector(UUID usuarioId, UUID documentoId, int limite) {
        return jdbc.sql("""
                SELECT f.id, f.documento_id, f.orden, f.texto, f.pagina FROM fragmento f JOIN documento d ON d.id = f.documento_id
                WHERE f.documento_id = :documento AND d.usuario_id = :usuario AND f.embedding IS NULL ORDER BY f.orden LIMIT :limite
                """).param("documento", documentoId).param("usuario", usuarioId).param("limite", limite)
                .query(BibliotecaPgvector::fragmento).list();
    }

    @Override
    public void guardarVectores(UUID usuarioId, Map<UUID, float[]> vectores) {
        for (Map.Entry<UUID, float[]> v : vectores.entrySet()) {
            jdbc.sql("""
                    UPDATE fragmento f SET embedding = CAST(:vector AS vector) FROM documento d
                    WHERE f.id = :id AND d.id = f.documento_id AND d.usuario_id = :usuario
                    """).param("vector", literal(v.getValue())).param("id", v.getKey()).param("usuario", usuarioId).update();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Fragmento> fragmentos(UUID usuarioId, UUID documentoId) {
        return jdbc.sql("""
                SELECT f.id, f.documento_id, f.orden, f.texto, f.pagina FROM fragmento f JOIN documento d ON d.id = f.documento_id
                WHERE f.documento_id = :documento AND
                """ + VISIBLE + " ORDER BY f.orden")
                .param("documento", documentoId).param("usuario", usuarioId).query(BibliotecaPgvector::fragmento).list();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Cita> cita(UUID usuarioId, UUID fragmentoId) {
        return jdbc.sql("""
                SELECT f.id, f.documento_id, d.nombre, f.pagina, f.texto FROM fragmento f JOIN documento d ON d.id = f.documento_id
                WHERE f.id = :id AND
                """ + VISIBLE).param("id", fragmentoId).param("usuario", usuarioId)
                .query((rs, i) -> new Cita(rs.getObject("id", UUID.class), rs.getObject("documento_id", UUID.class), rs.getString("nombre"),
                        Optional.ofNullable((Integer) rs.getObject("pagina")), rs.getString("texto")))
                .optional();
    }

    /** Cualquier palabra de la consulta: el tsquery en español con sus términos unidos por OR en vez de AND. */
    @Override
    @Transactional(readOnly = true)
    public List<Pasaje> buscarPorTexto(UUID usuarioId, String consulta, int limite) {
        return jdbc.sql("""
                WITH q AS (SELECT replace(plainto_tsquery('spanish', :consulta)::text, ' & ', ' | ')::tsquery AS q)
                SELECT f.id, f.documento_id, d.nombre, f.pagina, f.texto, ts_rank_cd(f.tsv, q.q) AS puntaje
                FROM fragmento f JOIN documento d ON d.id = f.documento_id, q
                WHERE f.tsv @@ q.q AND
                """ + VISIBLE + """
                 ORDER BY puntaje DESC, d.creado_en DESC, f.orden LIMIT :limite
                """)
                .param("consulta", consulta).param("usuario", usuarioId).param("limite", limite)
                .query((rs, i) -> pasaje(rs, Pasaje.Modo.TEXTO_COMPLETO)).list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Pasaje> buscarPorVector(UUID usuarioId, float[] consulta, int limite) {
        jdbc.sql("SET LOCAL hnsw.iterative_scan = strict_order").update();
        return jdbc.sql("""
                SELECT f.id, f.documento_id, d.nombre, f.pagina, f.texto, 1 - (f.embedding <=> CAST(:vector AS vector)) AS puntaje
                FROM fragmento f JOIN documento d ON d.id = f.documento_id
                WHERE f.embedding IS NOT NULL AND
                """ + VISIBLE + """
                 ORDER BY f.embedding <=> CAST(:vector AS vector) LIMIT :limite
                """)
                .param("vector", literal(consulta)).param("usuario", usuarioId).param("limite", limite)
                .query((rs, i) -> pasaje(rs, Pasaje.Modo.SEMANTICA)).list();
    }

    @Override
    public boolean compartir(UUID usuarioId, UUID documentoId, boolean compartido) {
        return jdbc.sql("UPDATE documento SET compartido = :compartido WHERE id = :id AND usuario_id = :usuario")
                .param("compartido", compartido).param("id", documentoId).param("usuario", usuarioId).update() == 1;
    }

    @Override
    public boolean borrar(UUID usuarioId, UUID documentoId) {
        return jdbc.sql("DELETE FROM documento WHERE id = :id AND usuario_id = :usuario")
                .param("id", documentoId).param("usuario", usuarioId).update() == 1;
    }

    private boolean esDuenio(UUID usuarioId, UUID documentoId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM documento WHERE id = :id AND usuario_id = :usuario)")
                .param("id", documentoId).param("usuario", usuarioId).query(Boolean.class).single();
    }

    /** El literal de pgvector: [0.1,0.2,…]. */
    static String literal(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 10).append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }

    private static Documento documento(ResultSet rs, int i) throws SQLException {
        return new Documento(rs.getObject("id", UUID.class), rs.getObject("usuario_id", UUID.class), rs.getString("nombre"),
                Documento.Tipo.valueOf(rs.getString("tipo").toUpperCase()), Documento.Estado.valueOf(rs.getString("estado").toUpperCase()),
                rs.getBoolean("compartido"), rs.getString("hash"), rs.getLong("tamano"), Optional.ofNullable((Integer) rs.getObject("paginas")),
                Optional.ofNullable(rs.getString("error")), rs.getBoolean("con_original"), rs.getInt("fragmentos"), rs.getInt("con_vector"),
                rs.getTimestamp("creado_en").toInstant());
    }

    private static Fragmento fragmento(ResultSet rs, int i) throws SQLException {
        return new Fragmento(rs.getObject("id", UUID.class), rs.getObject("documento_id", UUID.class), rs.getInt("orden"), rs.getString("texto"),
                Optional.ofNullable((Integer) rs.getObject("pagina")));
    }

    private static Pasaje pasaje(ResultSet rs, Pasaje.Modo modo) throws SQLException {
        return new Pasaje(rs.getObject("id", UUID.class), rs.getObject("documento_id", UUID.class), rs.getString("nombre"),
                Optional.ofNullable((Integer) rs.getObject("pagina")), rs.getString("texto"), rs.getDouble("puntaje"), modo);
    }
}
