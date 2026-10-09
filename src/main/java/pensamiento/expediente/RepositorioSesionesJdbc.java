package pensamiento.expediente;

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
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.SesionConsejero;
import pensamiento.nucleo.TurnoConsejero;
import pensamiento.nucleo.puertos.RepositorioSesiones;

/**
 * Sesiones del Consejero y sus turnos en PostgreSQL (V7). Filtra por usuario; RLS por debajo. Agregar un turno primero
 * bloquea la sesión de la persona (así no entra un turno en una sesión ajena por la clave foránea, que no mira RLS) y
 * exige que el número sea el siguiente; la restricción única (sesión, número) es la última barrera ante dos pedidos a la
 * vez.
 */
@Repository
@Transactional
public class RepositorioSesionesJdbc implements RepositorioSesiones {

    private static final String SESION = """
            id, usuario_id, institucion_id, expediente_id, modo, postura, razones, config, usa_modelo, confianza_antes, cierre_pedido, estado,
            reflexion, confianza_despues, ejecucion_id, creada_en, cerrada_en FROM sesion_consejero
            """;
    private static final String TURNO = """
            id, sesion_id, numero, rol, paso, texto, origen, estado, intentos, modelo, digest, prompt_version, elemento_propuesto, porque_propuesto,
            propuesta_adoptada, creado_en FROM turno_consejero
            """;

    private final JdbcClient jdbc;

    public RepositorioSesionesJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void crear(SesionConsejero s) {
        insertarSesion(s);
    }

    private void insertarSesion(SesionConsejero s) {
        jdbc.sql("""
                INSERT INTO sesion_consejero (id, usuario_id, institucion_id, expediente_id, modo, postura, razones, config, usa_modelo, confianza_antes,
                                              cierre_pedido, estado, reflexion, confianza_despues, ejecucion_id, creada_en, cerrada_en)
                VALUES (:id, :usuario, :institucion, :expediente, :modo, :postura, CAST(:razones AS jsonb), :config, :usaModelo, :antes,
                        :cierrePedido, :estado, :reflexion, :despues, :ejecucion, :creada, :cerrada)
                ON CONFLICT (id) DO NOTHING
                """)
                .param("id", s.id()).param("usuario", s.usuarioId()).param("institucion", s.institucionId()).param("expediente", s.expedienteId().orElse(null))
                .param("modo", s.modo().toString()).param("postura", s.postura()).param("razones", MapeadorJson.escribir(s.razones()).texto())
                .param("config", s.config().texto()).param("usaModelo", s.usaModelo()).param("antes", s.confianzaAntes().orElse(null))
                .param("cierrePedido", s.cierrePedido()).param("estado", s.estado().toString()).param("reflexion", s.reflexion().orElse(null))
                .param("despues", s.confianzaDespues().orElse(null)).param("ejecucion", s.ejecucionId().orElse(null))
                .param("creada", Timestamp.from(s.creadaEn())).param("cerrada", s.cerradaEn().map(Timestamp::from).orElse(null))
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SesionConsejero> porId(UUID usuarioId, UUID sesionId) {
        return jdbc.sql("SELECT " + SESION + " WHERE usuario_id = :usuario AND id = :id").param("usuario", usuarioId).param("id", sesionId)
                .query(RepositorioSesionesJdbc::sesion).optional();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SesionConsejero> deUsuario(UUID usuarioId) {
        return jdbc.sql("SELECT " + SESION + " WHERE usuario_id = :usuario ORDER BY creada_en DESC, id").param("usuario", usuarioId)
                .query(RepositorioSesionesJdbc::sesion).list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TurnoConsejero> turnos(UUID usuarioId, UUID sesionId) {
        return jdbc.sql("SELECT " + TURNO + " WHERE usuario_id = :usuario AND sesion_id = :sesion ORDER BY numero").param("usuario", usuarioId)
                .param("sesion", sesionId).query(RepositorioSesionesJdbc::turno).list();
    }

    @Override
    public void agregarTurno(UUID usuarioId, UUID institucionId, TurnoConsejero t) {
        Optional<String> estado = jdbc.sql("SELECT estado FROM sesion_consejero WHERE usuario_id = :usuario AND id = :id FOR UPDATE")
                .param("usuario", usuarioId).param("id", t.sesionId()).query(String.class).optional();
        if (estado.isEmpty()) {
            throw new IllegalArgumentException("No hay una sesión " + t.sesionId());
        }
        if ("cerrada".equals(estado.get())) {
            throw new SesionCerrada(t.sesionId());
        }
        int siguiente = jdbc.sql("SELECT count(*) FROM turno_consejero WHERE usuario_id = :usuario AND sesion_id = :sesion")
                .param("usuario", usuarioId).param("sesion", t.sesionId()).query(Integer.class).single() + 1;
        if (t.numero() != siguiente) {
            throw new IllegalArgumentException("El turno " + t.numero() + " no es el siguiente de la sesión (" + siguiente + ")");
        }
        insertarTurno(usuarioId, institucionId, t);
    }

    private void insertarTurno(UUID usuarioId, UUID institucionId, TurnoConsejero t) {
        jdbc.sql("""
                INSERT INTO turno_consejero (id, sesion_id, usuario_id, institucion_id, numero, rol, paso, texto, origen, estado, intentos, modelo, digest,
                                             prompt_version, elemento_propuesto, porque_propuesto, propuesta_adoptada, creado_en)
                VALUES (:id, :sesion, :usuario, :institucion, :numero, :rol, :paso, :texto, :origen, :estado, :intentos, :modelo, :digest, :prompt,
                        :elemento, :porque, :adoptada, :creado)
                ON CONFLICT (id) DO NOTHING
                """)
                .param("id", t.id()).param("sesion", t.sesionId()).param("usuario", usuarioId).param("institucion", institucionId).param("numero", t.numero())
                .param("rol", t.rol().toString()).param("paso", t.paso()).param("texto", t.texto()).param("origen", t.origen().toString())
                .param("estado", t.estado().toString()).param("intentos", t.intentos())
                .param("modelo", t.modelo().map(Ejecucion.RegistroModelo::modelo).orElse(null))
                .param("digest", t.modelo().map(Ejecucion.RegistroModelo::digest).orElse(null))
                .param("prompt", t.modelo().map(Ejecucion.RegistroModelo::promptVersion).orElse(null))
                .param("elemento", t.elementoPropuesto().orElse(null)).param("porque", t.porquePropuesto().orElse(null))
                .param("adoptada", t.propuestaAdoptada()).param("creado", Timestamp.from(t.creadoEn()))
                .update();
    }

    @Override
    public void completarTurno(UUID usuarioId, UUID turnoId, String texto, TurnoConsejero.Origen origen, int intentos, Optional<Ejecucion.RegistroModelo> modelo) {
        jdbc.sql("""
                UPDATE turno_consejero SET texto = :texto, origen = :origen, estado = 'listo', intentos = :intentos, modelo = :modelo, digest = :digest,
                       prompt_version = :prompt
                WHERE usuario_id = :usuario AND id = :id
                """)
                .param("texto", texto).param("origen", origen.toString()).param("intentos", intentos)
                .param("modelo", modelo.map(Ejecucion.RegistroModelo::modelo).orElse(null)).param("digest", modelo.map(Ejecucion.RegistroModelo::digest).orElse(null))
                .param("prompt", modelo.map(Ejecucion.RegistroModelo::promptVersion).orElse(null)).param("usuario", usuarioId).param("id", turnoId)
                .update();
    }

    @Override
    public void proponerElemento(UUID usuarioId, UUID turnoId, String elemento, String porque) {
        jdbc.sql("UPDATE turno_consejero SET elemento_propuesto = :elemento, porque_propuesto = :porque, propuesta_adoptada = false "
                        + "WHERE usuario_id = :usuario AND id = :id")
                .param("elemento", elemento).param("porque", porque).param("usuario", usuarioId).param("id", turnoId).update();
    }

    @Override
    public void adoptarElemento(UUID usuarioId, UUID turnoId) {
        jdbc.sql("UPDATE turno_consejero SET propuesta_adoptada = true WHERE usuario_id = :usuario AND id = :id AND elemento_propuesto IS NOT NULL")
                .param("usuario", usuarioId).param("id", turnoId).update();
    }

    @Override
    public void pedirCierre(UUID usuarioId, UUID sesionId) {
        jdbc.sql("UPDATE sesion_consejero SET cierre_pedido = true WHERE usuario_id = :usuario AND id = :id")
                .param("usuario", usuarioId).param("id", sesionId).update();
    }

    @Override
    public void asociar(UUID usuarioId, UUID sesionId, Optional<UUID> expedienteId) {
        jdbc.sql("UPDATE sesion_consejero SET expediente_id = :expediente WHERE usuario_id = :usuario AND id = :id")
                .param("expediente", expedienteId.orElse(null)).param("usuario", usuarioId).param("id", sesionId).update();
    }

    @Override
    public void cerrar(UUID usuarioId, UUID sesionId, Optional<String> reflexion, Optional<Integer> confianzaDespues, Optional<UUID> ejecucionId,
                       Instant cuando) {
        jdbc.sql("""
                UPDATE sesion_consejero SET estado = 'cerrada', reflexion = :reflexion, confianza_despues = :despues, ejecucion_id = :ejecucion,
                       cerrada_en = :cuando
                WHERE usuario_id = :usuario AND id = :id AND estado = 'abierta'
                """)
                .param("reflexion", reflexion.orElse(null)).param("despues", confianzaDespues.orElse(null)).param("ejecucion", ejecucionId.orElse(null))
                .param("cuando", Timestamp.from(cuando)).param("usuario", usuarioId).param("id", sesionId).update();
    }

    @Override
    public void restaurar(UUID usuarioId, UUID institucionId, SesionConsejero sesion, List<TurnoConsejero> turnos) {
        boolean existia = jdbc.sql("SELECT count(*) FROM sesion_consejero WHERE id = :id").param("id", sesion.id()).query(Integer.class).single() > 0;
        if (existia) {
            return;
        }
        insertarSesion(sesion);
        turnos.stream().sorted(java.util.Comparator.comparingInt(TurnoConsejero::numero)).forEach(t -> insertarTurno(usuarioId, institucionId, t));
    }

    private static SesionConsejero sesion(ResultSet rs, int i) throws SQLException {
        Timestamp cerrada = rs.getTimestamp("cerrada_en");
        List<SesionConsejero.Razon> razones = MapeadorJson.mapper().readValue(rs.getString("razones"), new TypeReference<List<SesionConsejero.Razon>>() { });
        return new SesionConsejero(rs.getObject("id", UUID.class), rs.getObject("usuario_id", UUID.class), rs.getObject("institucion_id", UUID.class),
                Optional.ofNullable(rs.getObject("expediente_id", UUID.class)), SesionConsejero.Modo.de(rs.getString("modo")), rs.getString("postura"),
                razones, new Json(rs.getString("config")), rs.getBoolean("usa_modelo"), Optional.ofNullable((Integer) rs.getObject("confianza_antes")),
                rs.getBoolean("cierre_pedido"), SesionConsejero.Estado.valueOf(rs.getString("estado").toUpperCase()), Optional.ofNullable(rs.getString("reflexion")),
                Optional.ofNullable((Integer) rs.getObject("confianza_despues")), Optional.ofNullable(rs.getObject("ejecucion_id", UUID.class)),
                rs.getTimestamp("creada_en").toInstant(), Optional.ofNullable(cerrada).map(Timestamp::toInstant));
    }

    private static TurnoConsejero turno(ResultSet rs, int i) throws SQLException {
        String modelo = rs.getString("modelo");
        Optional<Ejecucion.RegistroModelo> registro = modelo == null ? Optional.empty()
                : Optional.of(new Ejecucion.RegistroModelo(modelo, rs.getString("digest"), rs.getString("prompt_version"), pensamiento.nucleo.Propuesta.TEMPERATURA,
                pensamiento.nucleo.Propuesta.SEMILLA));
        return new TurnoConsejero(rs.getObject("id", UUID.class), rs.getObject("sesion_id", UUID.class), rs.getInt("numero"),
                TurnoConsejero.Rol.valueOf(rs.getString("rol").toUpperCase()), rs.getString("paso"), rs.getString("texto"),
                TurnoConsejero.Origen.valueOf(rs.getString("origen").toUpperCase()), TurnoConsejero.Estado.valueOf(rs.getString("estado").toUpperCase()),
                rs.getInt("intentos"), registro, Optional.ofNullable(rs.getString("elemento_propuesto")), Optional.ofNullable(rs.getString("porque_propuesto")),
                rs.getBoolean("propuesta_adoptada"), rs.getTimestamp("creado_en").toInstant());
    }
}
