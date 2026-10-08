package pensamiento.expediente;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.OrigenAfirmacion;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.PendienteGuardado;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.SentidoAfirmacion;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.TipoPendiente;
import pensamiento.nucleo.puertos.RepositorioEjecucion;

/**
 * Historial de ejecuciones. Idempotente por clave_idempotencia; filtra por usuario; RLS por debajo. Guardar
 * escribe ejecución, afirmaciones, ejecucion_afirmacion y pendientes en una sola transacción corta.
 */
@Repository
@Transactional
public class RepositorioEjecucionJdbc implements RepositorioEjecucion {

    private static final String COLUMNAS = """
            id, usuario_id, institucion_id, tecnica_id, version_esquema, expediente_id, config::text AS config,
            datos::text AS datos, resultado::text AS resultado, resumen, modelo, modelo_digest, prompt_version,
            temperatura, semilla, clave_idempotencia, creada_en
            """;

    private final JdbcClient jdbc;

    public RepositorioEjecucionJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Ejecucion guardar(Ejecucion e, List<AfirmacionConRol> afirmaciones, List<Pendiente> pendientes) {
        Optional<Ejecucion> previa = jdbc.sql("SELECT " + COLUMNAS + " FROM ejecucion WHERE clave_idempotencia = :clave")
                .param("clave", e.claveIdempotencia())
                .query(RepositorioEjecucionJdbc::fila).optional();
        if (previa.isPresent()) {
            return previa.get();
        }
        jdbc.sql("""
                INSERT INTO ejecucion (id, usuario_id, institucion_id, tecnica_id, version_esquema, expediente_id, config, datos,
                                       resultado, resumen, modelo, modelo_digest, prompt_version, temperatura, semilla,
                                       clave_idempotencia, creada_en)
                VALUES (:id, :usuario, :institucion, :tecnica, :version, :expediente, :config::jsonb, :datos::jsonb,
                        :resultado::jsonb, :resumen, :modelo, :digest, :prompt, :temperatura, :semilla, :clave, :creada)
                """)
                .param("id", e.id())
                .param("usuario", e.usuarioId())
                .param("institucion", e.institucionId())
                .param("tecnica", e.tecnica().valor())
                .param("version", e.versionEsquema())
                .param("expediente", e.expedienteId().orElse(null))
                .param("config", e.config().texto())
                .param("datos", e.datos().texto())
                .param("resultado", e.resultado().texto())
                .param("resumen", e.resumen())
                .param("modelo", e.modelo().map(Ejecucion.RegistroModelo::modelo).orElse(null))
                .param("digest", e.modelo().map(Ejecucion.RegistroModelo::digest).orElse(null))
                .param("prompt", e.modelo().map(Ejecucion.RegistroModelo::promptVersion).orElse(null))
                .param("temperatura", e.modelo().map(Ejecucion.RegistroModelo::temperatura).orElse(null))
                .param("semilla", e.modelo().map(Ejecucion.RegistroModelo::semilla).orElse(null))
                .param("clave", e.claveIdempotencia())
                .param("creada", Timestamp.from(e.creadaEn()))
                .update();
        for (AfirmacionConRol a : afirmaciones) {
            if (a.sentido() == SentidoAfirmacion.PRODUCIDA) {
                jdbc.sql("""
                        INSERT INTO afirmacion (id, usuario_id, institucion_id, texto, tipo, origen, adoptada, creada_en)
                        VALUES (:id, :usuario, :institucion, :texto, :tipo, :origen, :adoptada, :creada)
                        ON CONFLICT (id) DO NOTHING
                        """)
                        .param("id", a.afirmacionId())
                        .param("usuario", e.usuarioId())
                        .param("institucion", e.institucionId())
                        .param("texto", a.texto())
                        .param("tipo", a.tipo().enBaseDeDatos())
                        .param("origen", a.origen().name().toLowerCase())
                        .param("adoptada", a.adoptada())
                        .param("creada", Timestamp.from(e.creadaEn()))
                        .update();
            }
            jdbc.sql("INSERT INTO ejecucion_afirmacion (ejecucion_id, afirmacion_id, rol, sentido) VALUES (:e, :a, :rol, :sentido)")
                    .param("e", e.id()).param("a", a.afirmacionId())
                    .param("rol", a.rol().name().toLowerCase()).param("sentido", a.sentido().name().toLowerCase())
                    .update();
        }
        for (Pendiente p : pendientes) {
            jdbc.sql("""
                    INSERT INTO pendiente (usuario_id, institucion_id, tipo, objeto_id, vence, ejecucion_id, descripcion)
                    VALUES (:usuario, :institucion, :tipo, :objeto, :vence, :ejecucion, :descripcion)
                    """)
                    .param("usuario", e.usuarioId())
                    .param("institucion", e.institucionId())
                    .param("tipo", p.tipo().name().toLowerCase())
                    .param("objeto", p.objetoId().orElse(null))
                    .param("vence", p.vence().map(Date::valueOf).orElse(null))
                    .param("ejecucion", e.id())
                    .param("descripcion", p.descripcion())
                    .update();
        }
        return e;
    }

    @Override
    public Optional<Ejecucion> porId(UUID usuarioId, UUID id) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM ejecucion WHERE usuario_id = :usuario AND id = :id AND eliminada_en IS NULL")
                .param("usuario", usuarioId).param("id", id)
                .query(RepositorioEjecucionJdbc::fila).optional();
    }

    @Override
    public List<Ejecucion> porTecnica(UUID usuarioId, IdTecnica tecnica) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM ejecucion WHERE usuario_id = :usuario AND tecnica_id = :tecnica AND eliminada_en IS NULL ORDER BY creada_en DESC, id DESC")
                .param("usuario", usuarioId).param("tecnica", tecnica.valor())
                .query(RepositorioEjecucionJdbc::fila).list();
    }

    @Override
    public List<AfirmacionConRol> afirmacionesDe(UUID usuarioId, UUID ejecucionId) {
        return jdbc.sql("""
                SELECT a.id, a.texto, a.tipo, a.origen, a.adoptada, ea.rol, ea.sentido
                FROM ejecucion_afirmacion ea
                JOIN ejecucion e ON e.id = ea.ejecucion_id
                JOIN afirmacion a ON a.id = ea.afirmacion_id
                WHERE e.usuario_id = :usuario AND e.id = :ejecucion
                ORDER BY a.creada_en, a.id
                """)
                .param("usuario", usuarioId).param("ejecucion", ejecucionId)
                .query((rs, i) -> new AfirmacionConRol(
                        rs.getObject("id", UUID.class), rs.getString("texto"),
                        TipoAfirmacion.valueOf(rs.getString("tipo").toUpperCase()),
                        RolAfirmacion.valueOf(rs.getString("rol").toUpperCase()),
                        SentidoAfirmacion.valueOf(rs.getString("sentido").toUpperCase()),
                        OrigenAfirmacion.valueOf(rs.getString("origen").toUpperCase()), rs.getBoolean("adoptada")))
                .list();
    }

    @Override
    public List<PendienteGuardado> pendientes(UUID usuarioId) {
        return jdbc.sql("""
                SELECT p.id, p.ejecucion_id, p.tipo, p.objeto_id, p.vence, p.descripcion, p.resuelto
                FROM pendiente p JOIN ejecucion e ON e.id = p.ejecucion_id
                WHERE p.usuario_id = :usuario AND NOT p.resuelto AND e.eliminada_en IS NULL
                ORDER BY e.creada_en, p.id
                """)
                .param("usuario", usuarioId)
                .query((rs, i) -> new PendienteGuardado(
                        rs.getObject("id", UUID.class), rs.getObject("ejecucion_id", UUID.class),
                        new Pendiente(TipoPendiente.valueOf(rs.getString("tipo").toUpperCase()),
                                Optional.ofNullable(rs.getObject("objeto_id", UUID.class)),
                                Optional.ofNullable(rs.getDate("vence")).map(Date::toLocalDate),
                                rs.getString("descripcion")),
                        rs.getBoolean("resuelto")))
                .list();
    }

    @Override
    public List<Ejecucion> porExpediente(UUID usuarioId, UUID expedienteId) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM ejecucion WHERE usuario_id = :usuario AND expediente_id = :expediente AND eliminada_en IS NULL ORDER BY creada_en DESC, id DESC")
                .param("usuario", usuarioId).param("expediente", expedienteId)
                .query(RepositorioEjecucionJdbc::fila).list();
    }

    @Override
    public List<Ejecucion> recientes(UUID usuarioId, int limite) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM ejecucion WHERE usuario_id = :usuario AND eliminada_en IS NULL ORDER BY creada_en DESC, id DESC LIMIT :limite")
                .param("usuario", usuarioId).param("limite", limite)
                .query(RepositorioEjecucionJdbc::fila).list();
    }

    @Override
    public int cerrarPendientes(UUID usuarioId, TipoPendiente tipo, UUID objetoId) {
        return jdbc.sql("UPDATE pendiente SET resuelto = true WHERE usuario_id = :usuario AND tipo = :tipo AND objeto_id = :objeto AND NOT resuelto")
                .param("usuario", usuarioId).param("tipo", tipo.name().toLowerCase()).param("objeto", objetoId).update();
    }

    @Override
    public boolean asociar(UUID usuarioId, UUID ejecucionId, Optional<UUID> expedienteId) {
        return jdbc.sql("UPDATE ejecucion SET expediente_id = :expediente WHERE usuario_id = :usuario AND id = :id AND eliminada_en IS NULL")
                .param("expediente", expedienteId.orElse(null)).param("usuario", usuarioId).param("id", ejecucionId)
                .update() == 1;
    }

    static Ejecucion fila(ResultSet rs, int i) throws SQLException {
        String modelo = rs.getString("modelo");
        Optional<Ejecucion.RegistroModelo> registro = modelo == null ? Optional.empty()
                : Optional.of(new Ejecucion.RegistroModelo(modelo, rs.getString("modelo_digest"), rs.getString("prompt_version"),
                        rs.getDouble("temperatura"), rs.getLong("semilla")));
        return new Ejecucion(
                rs.getObject("id", UUID.class),
                rs.getObject("usuario_id", UUID.class),
                rs.getObject("institucion_id", UUID.class),
                IdTecnica.de(rs.getString("tecnica_id")),
                rs.getInt("version_esquema"),
                Optional.ofNullable(rs.getObject("expediente_id", UUID.class)),
                new Json(rs.getString("config")),
                new Json(rs.getString("datos")),
                new Json(rs.getString("resultado")),
                rs.getString("resumen"),
                registro,
                rs.getString("clave_idempotencia"),
                rs.getTimestamp("creada_en").toInstant());
    }
}
