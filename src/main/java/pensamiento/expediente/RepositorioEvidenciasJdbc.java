package pensamiento.expediente;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pensamiento.catalogo.MapeadorJson;
import pensamiento.nucleo.Evidencia;
import pensamiento.nucleo.EvidenciaGuardada;
import pensamiento.nucleo.FichaFuente;
import pensamiento.nucleo.Fuente;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.puertos.RepositorioEvidencias;

/**
 * Evidencias con la ficha de su fuente en PostgreSQL (tablas evidencia y fuente de V1, completadas en V8). Filtra por
 * usuario; RLS por debajo. La fuerza de cada evidencia queda ligada a la versión 1 de R01.
 */
@Repository
@Transactional
public class RepositorioEvidenciasJdbc implements RepositorioEvidencias {

    private static final String COLUMNAS = """
            e.id, e.afirmacion_id, e.fragmento_id, e.pasaje, e.postura, e.fuerza, e.etiquetada_por, e.adoptada,
            f.id AS fuente_id, f.titulo, f.autor, f.fecha, f.tipo, f.diseno_estudio, f.grupo_origen, f.independiente_del_autor,
            f.acceso_original, f.craap, f.puntaje_craap, f.sift, f.documento_id, f.documento_nombre, f.pagina
            FROM evidencia e JOIN fuente f ON f.id = e.fuente_id JOIN afirmacion a ON a.id = e.afirmacion_id
            """;

    /** Lo que guarda la columna craap: los cinco criterios; el puntaje va en puntaje_craap. */
    record CriteriosCraap(int actualidad, int relevancia, int autoridad, int exactitud, int proposito) {
    }

    private final JdbcClient jdbc;

    public RepositorioEvidenciasJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void guardar(UUID usuarioId, UUID institucionId, EvidenciaGuardada e) {
        boolean suya = jdbc.sql("SELECT EXISTS (SELECT 1 FROM afirmacion WHERE id = :afirmacion AND usuario_id = :usuario)")
                .param("afirmacion", e.afirmacionId()).param("usuario", usuarioId).query(Boolean.class).single();
        if (!suya) {
            throw new IllegalArgumentException("La afirmación de la evidencia no existe o es de otra persona");
        }
        FichaFuente f = e.fuente();
        jdbc.sql("""
                INSERT INTO fuente (id, usuario_id, institucion_id, titulo, autor, fecha, tipo, diseno_estudio, grupo_origen, independiente_del_autor,
                                    acceso_original, craap, puntaje_craap, sift, documento_id, documento_nombre, pagina)
                VALUES (:id, :usuario, :institucion, :titulo, :autor, :fecha, :tipo, :diseno, :grupo, :independiente, :original,
                        CAST(:craap AS jsonb), :puntaje, CAST(:sift AS jsonb), :documento, :documentoNombre, :pagina)
                ON CONFLICT (id) DO UPDATE SET titulo = EXCLUDED.titulo, autor = EXCLUDED.autor, fecha = EXCLUDED.fecha, tipo = EXCLUDED.tipo,
                    diseno_estudio = EXCLUDED.diseno_estudio, grupo_origen = EXCLUDED.grupo_origen,
                    independiente_del_autor = EXCLUDED.independiente_del_autor, acceso_original = EXCLUDED.acceso_original, craap = EXCLUDED.craap,
                    puntaje_craap = EXCLUDED.puntaje_craap, sift = EXCLUDED.sift, documento_id = EXCLUDED.documento_id,
                    documento_nombre = EXCLUDED.documento_nombre, pagina = EXCLUDED.pagina
                WHERE fuente.usuario_id = :usuario
                """)
                .param("id", f.id()).param("usuario", usuarioId).param("institucion", institucionId).param("titulo", f.titulo())
                .param("autor", f.autor().orElse(null)).param("fecha", f.fecha().map(Date::valueOf).orElse(null))
                .param("tipo", f.tipo().name().toLowerCase()).param("diseno", f.disenoEstudio().map(d -> d.name().toLowerCase()).orElse(null))
                .param("grupo", f.grupoOrigen().orElse(null)).param("independiente", f.independiente()).param("original", f.accesoOriginal())
                .param("craap", f.craap().map(c -> MapeadorJson.escribir(new CriteriosCraap(c.actualidad(), c.relevancia(), c.autoridad(),
                        c.exactitud(), c.proposito())).texto()).orElse(null))
                .param("puntaje", f.craap().map(FichaFuente.Craap::puntaje).orElse(null))
                .param("sift", MapeadorJson.escribir(f.sift()).texto())
                .param("documento", f.documentoId().orElse(null)).param("documentoNombre", f.documentoNombre().orElse(null))
                .param("pagina", f.pagina().orElse(null))
                .update();
        jdbc.sql("""
                INSERT INTO evidencia (id, afirmacion_id, fuente_id, fragmento_id, pasaje, postura, fuerza, regla_version_id, etiquetada_por, adoptada)
                SELECT :id, a.id, :fuente, :fragmento, :pasaje, :postura, :fuerza,
                       (SELECT id FROM regla_version WHERE regla = 'R01' AND version = 1), :etiquetadaPor, :adoptada
                FROM afirmacion a WHERE a.id = :afirmacion AND a.usuario_id = :usuario
                ON CONFLICT (id) DO NOTHING
                """)
                .param("id", e.id()).param("afirmacion", e.afirmacionId()).param("usuario", usuarioId).param("fuente", f.id())
                .param("fragmento", e.fragmentoId().orElse(null)).param("pasaje", e.pasaje()).param("postura", e.postura().name().toLowerCase())
                .param("fuerza", e.fuerza()).param("etiquetadaPor", e.etiquetadaPor().name().toLowerCase()).param("adoptada", e.adoptada())
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvidenciaGuardada> deAfirmacion(UUID usuarioId, UUID afirmacionId) {
        return jdbc.sql("SELECT " + COLUMNAS + " WHERE a.usuario_id = :usuario AND e.afirmacion_id = :afirmacion ORDER BY e.creada_en, e.id")
                .param("usuario", usuarioId).param("afirmacion", afirmacionId).query(RepositorioEvidenciasJdbc::fila).list();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EvidenciaGuardada> porId(UUID usuarioId, UUID id) {
        return jdbc.sql("SELECT " + COLUMNAS + " WHERE a.usuario_id = :usuario AND e.id = :id")
                .param("usuario", usuarioId).param("id", id).query(RepositorioEvidenciasJdbc::fila).optional();
    }

    @Override
    public boolean quitar(UUID usuarioId, UUID id) {
        Optional<UUID> fuente = jdbc.sql("""
                DELETE FROM evidencia e USING afirmacion a
                WHERE e.id = :id AND a.id = e.afirmacion_id AND a.usuario_id = :usuario
                RETURNING e.fuente_id
                """).param("id", id).param("usuario", usuarioId).query(UUID.class).optional();
        fuente.ifPresent(f -> jdbc.sql("""
                DELETE FROM fuente WHERE id = :fuente AND usuario_id = :usuario AND NOT EXISTS (SELECT 1 FROM evidencia WHERE fuente_id = :fuente)
                """).param("fuente", f).param("usuario", usuarioId).update());
        return fuente.isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EvidenciaGuardada> deUsuario(UUID usuarioId) {
        return jdbc.sql("SELECT " + COLUMNAS + " WHERE a.usuario_id = :usuario ORDER BY e.creada_en, e.id")
                .param("usuario", usuarioId).query(RepositorioEvidenciasJdbc::fila).list();
    }

    private static EvidenciaGuardada fila(ResultSet rs, int i) throws SQLException {
        String craap = rs.getString("craap");
        Optional<FichaFuente.Craap> criterios = Optional.empty();
        if (craap != null && rs.getObject("puntaje_craap") != null) {
            CriteriosCraap c = MapeadorJson.leer(new Json(craap), CriteriosCraap.class);
            criterios = Optional.of(new FichaFuente.Craap(c.actualidad(), c.relevancia(), c.autoridad(), c.exactitud(), c.proposito(),
                    rs.getInt("puntaje_craap")));
        }
        Date fecha = rs.getDate("fecha");
        String diseno = rs.getString("diseno_estudio");
        Integer pagina = (Integer) rs.getObject("pagina");
        FichaFuente fuente = new FichaFuente(rs.getObject("fuente_id", UUID.class), rs.getString("titulo"), Optional.ofNullable(rs.getString("autor")),
                Optional.ofNullable(fecha).map(Date::toLocalDate), Fuente.TipoFuente.valueOf(rs.getString("tipo").toUpperCase()),
                Optional.ofNullable(diseno).map(d -> Fuente.DisenoEstudio.valueOf(d.toUpperCase())), Optional.ofNullable(rs.getString("grupo_origen")),
                rs.getBoolean("independiente_del_autor"), rs.getBoolean("acceso_original"), criterios,
                MapeadorJson.leer(new Json(rs.getString("sift")), FichaFuente.Sift.class), Optional.ofNullable(rs.getObject("documento_id", UUID.class)),
                Optional.ofNullable(rs.getString("documento_nombre")), Optional.ofNullable(pagina));
        return new EvidenciaGuardada(rs.getObject("id", UUID.class), rs.getObject("afirmacion_id", UUID.class), fuente,
                Optional.ofNullable(rs.getObject("fragmento_id", UUID.class)), rs.getString("pasaje"),
                Evidencia.Postura.valueOf(rs.getString("postura").toUpperCase()), rs.getInt("fuerza"),
                Evidencia.EtiquetadaPor.valueOf(rs.getString("etiquetada_por").toUpperCase()), rs.getBoolean("adoptada"));
    }
}
