package pensamiento.web.aula;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pensamiento.nucleo.ClaseDeAula;
import pensamiento.nucleo.Grupo;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.PanelGrupo;
import pensamiento.nucleo.Usuario;
import pensamiento.nucleo.puertos.RepositorioAula;

/**
 * El aula bajo RLS por rol (V17). Las consultas confían en las políticas para lo que el actor puede ver y además filtran por
 * su institución; lo que cruza personas (crear un estudiante en el grupo, la entrada con el código de clase y el panel) va por
 * funciones SECURITY DEFINER. Nada lanza por un nombre repetido: crear usa ON CONFLICT DO NOTHING.
 */
@Repository
@Transactional
public class RepositorioAulaJdbc implements RepositorioAula {

    private static final String GRUPO = "id, institucion_id, nombre, docente_id, activo, tema_dojo, tema_hasta, lista_en_clase, ia_en_clase";
    private static final String CLASE = "id, grupo_id, abierta_en, cierra_en, terminada_en";

    private final JdbcClient jdbc;

    public RepositorioAulaJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Grupo> crearGrupo(Actor actor, Grupo grupo) {
        if (actor.rol() != Usuario.RolGlobal.ADMINISTRADOR) {
            return Optional.empty();
        }
        return jdbc.sql("INSERT INTO grupo (id, institucion_id, nombre, docente_id) VALUES (:id, :i, :nombre, :docente) "
                        + "ON CONFLICT DO NOTHING RETURNING " + GRUPO)
                .param("id", grupo.id()).param("i", actor.institucionId()).param("nombre", grupo.nombre())
                .param("docente", grupo.docenteId().orElse(null))
                .query(RepositorioAulaJdbc::grupo).optional();
    }

    @Override
    public boolean asignarDocente(Actor actor, UUID grupoId, Optional<UUID> docenteId) {
        if (actor.rol() != Usuario.RolGlobal.ADMINISTRADOR) {
            return false;
        }
        return jdbc.sql("UPDATE grupo SET docente_id = :docente WHERE id = :g AND institucion_id = :i")
                .param("docente", docenteId.orElse(null)).param("g", grupoId).param("i", actor.institucionId()).update() == 1;
    }

    @Override
    public List<Grupo> grupos(Actor actor) {
        return jdbc.sql("SELECT " + GRUPO + " FROM grupo WHERE institucion_id = :i ORDER BY lower(nombre), id")
                .param("i", actor.institucionId()).query(RepositorioAulaJdbc::grupo).list();
    }

    @Override
    public Optional<Grupo> grupo(Actor actor, UUID grupoId) {
        return jdbc.sql("SELECT " + GRUPO + " FROM grupo WHERE institucion_id = :i AND id = :g")
                .param("i", actor.institucionId()).param("g", grupoId).query(RepositorioAulaJdbc::grupo).optional();
    }

    @Override
    public boolean asignarTema(Actor actor, UUID grupoId, Optional<IdTecnica> tema, Optional<LocalDate> hasta, Instant ahora) {
        return jdbc.sql("""
                UPDATE grupo SET tema_dojo = :tema, tema_hasta = :hasta, tema_asignado_en = :ahora
                WHERE id = :g AND institucion_id = :i""")
                .param("tema", tema.map(IdTecnica::valor).orElse(null))
                .param("hasta", tema.isPresent() ? hasta.map(Date::valueOf).orElse(null) : null)
                .param("ahora", Timestamp.from(ahora)).param("g", grupoId).param("i", actor.institucionId()).update() == 1;
    }

    @Override
    public boolean cambiarOpciones(Actor actor, UUID grupoId, boolean listaEnClase, boolean iaEnClase) {
        return jdbc.sql("UPDATE grupo SET lista_en_clase = :lista, ia_en_clase = :ia WHERE id = :g AND institucion_id = :i")
                .param("lista", listaEnClase).param("ia", iaEnClase).param("g", grupoId).param("i", actor.institucionId()).update() == 1;
    }

    @Override
    public Optional<UUID> crearEstudiante(Actor actor, UUID grupoId, UUID id, String nombre) {
        return jdbc.sql("SELECT aula_crear_estudiante(:g, :id, :nombre)")
                .param("g", grupoId).param("id", id).param("nombre", nombre)
                .query((rs, i) -> Optional.ofNullable(rs.getObject(1, UUID.class))).single();
    }

    @Override
    public List<Miembro> miembros(Actor actor, UUID grupoId) {
        return jdbc.sql("""
                SELECT u.id, u.nombre, u.activo, u.tipo_clave <> 'ninguna' AS tiene_clave
                FROM miembro_grupo m JOIN usuario u ON u.id = m.usuario_id
                WHERE m.grupo_id = :g AND u.institucion_id = :i AND app_administra_grupo(:g)
                ORDER BY u.nombre_clave, u.id""")
                .param("g", grupoId).param("i", actor.institucionId())
                .query((rs, i) -> new Miembro(rs.getObject("id", UUID.class), rs.getString("nombre"), rs.getBoolean("activo"),
                        rs.getBoolean("tiene_clave"))).list();
    }

    @Override
    public Optional<ClaseDeAula> abrirClase(Actor actor, UUID grupoId, String codigoHash, Instant ahora, Instant cierraEn) {
        Boolean administra = jdbc.sql("SELECT app_administra_grupo(:g)").param("g", grupoId).query(Boolean.class).single();
        if (!Boolean.TRUE.equals(administra)) {
            return Optional.empty();
        }
        terminarClase(actor, grupoId, ahora);
        return jdbc.sql("INSERT INTO sesion_aula (institucion_id, grupo_id, docente_id, codigo_hash, abierta_en, cierra_en) "
                        + "VALUES (:i, :g, :docente, :hash, :ahora, :cierra) RETURNING " + CLASE)
                .param("i", actor.institucionId()).param("g", grupoId).param("docente", actor.id()).param("hash", codigoHash)
                .param("ahora", Timestamp.from(ahora)).param("cierra", Timestamp.from(cierraEn))
                .query(RepositorioAulaJdbc::clase).optional();
    }

    @Override
    public Optional<ClaseDeAula> claseAbierta(Actor actor, UUID grupoId, Instant ahora) {
        return jdbc.sql("SELECT " + CLASE + " FROM sesion_aula WHERE institucion_id = :i AND grupo_id = :g AND terminada_en IS NULL "
                        + "AND cierra_en > :ahora ORDER BY abierta_en DESC LIMIT 1")
                .param("i", actor.institucionId()).param("g", grupoId).param("ahora", Timestamp.from(ahora))
                .query(RepositorioAulaJdbc::clase).optional();
    }

    @Override
    public boolean terminarClase(Actor actor, UUID grupoId, Instant ahora) {
        return jdbc.sql("UPDATE sesion_aula SET terminada_en = :ahora WHERE institucion_id = :i AND grupo_id = :g "
                        + "AND terminada_en IS NULL AND cierra_en > :ahora")
                .param("ahora", Timestamp.from(ahora)).param("i", actor.institucionId()).param("g", grupoId).update() > 0;
    }

    @Override
    public Optional<ClasePorCodigo> clasePorCodigo(UUID institucionId, String codigoHash, Instant ahora) {
        return jdbc.sql("SELECT sesion_id, grupo_id, grupo, lista, cierra_en FROM aula_clase_por_codigo(:i, :hash, :ahora)")
                .param("i", institucionId).param("hash", codigoHash).param("ahora", Timestamp.from(ahora))
                .query((rs, i) -> new ClasePorCodigo(rs.getObject("sesion_id", UUID.class), rs.getObject("grupo_id", UUID.class),
                        rs.getString("grupo"), rs.getBoolean("lista"), rs.getTimestamp("cierra_en").toInstant()))
                .optional();
    }

    @Override
    public List<String> listaDeClase(UUID institucionId, String codigoHash, Instant ahora) {
        return jdbc.sql("SELECT nombre FROM aula_lista_de_clase(:i, :hash, :ahora)")
                .param("i", institucionId).param("hash", codigoHash).param("ahora", Timestamp.from(ahora))
                .query(String.class).list();
    }

    @Override
    public Optional<PanelGrupo> panel(Actor actor, UUID grupoId, Periodo periodo) {
        String tema = periodo.tema().map(IdTecnica::valor).orElse("");
        Optional<PanelGrupo> resumen = jdbc.sql("SELECT * FROM aula_panel_resumen(:g, :desde, :clase, :tema, :hoy)")
                .param("g", grupoId).param("desde", Timestamp.from(periodo.desdeHoy()))
                .param("clase", periodo.desdeClase().map(Timestamp::from).orElse(null))
                .param("tema", tema).param("hoy", Date.valueOf(periodo.hoy()))
                .query((rs, i) -> new PanelGrupo(rs.getInt("miembros"), rs.getInt("entraron_hoy"), rs.getInt("entraron_en_clase"),
                        rs.getInt("retos_hoy"), rs.getInt("respondieron_hoy"), rs.getInt("retos_del_tema"), List.of(), List.of(), List.of()))
                .optional();
        return resumen.map(r -> new PanelGrupo(r.miembros(), r.entraronHoy(), r.entraronEnClase(), r.retosHoy(), r.respondieronHoy(),
                r.retosDelTema(),
                jdbc.sql("SELECT * FROM aula_panel_conceptos(:g, :tema)").param("g", grupoId).param("tema", tema)
                        .query((rs, i) -> new PanelGrupo.Concepto(rs.getString("concepto"), rs.getBoolean("crear"), rs.getInt("intentos"),
                                rs.getInt("aciertos"), rs.getInt("personas"))).list(),
                jdbc.sql("SELECT * FROM aula_panel_bloom(:g, :tema)").param("g", grupoId).param("tema", tema)
                        .query((rs, i) -> new PanelGrupo.Nivel(rs.getString("nivel"), rs.getInt("personas"))).list(),
                jdbc.sql("SELECT * FROM aula_panel_sin_practicar(:g, :hoy, :dias)").param("g", grupoId)
                        .param("hoy", Date.valueOf(periodo.hoy())).param("dias", periodo.diasSinPracticar())
                        .query((rs, i) -> new PanelGrupo.SinPracticar(rs.getString("nombre"),
                                Optional.ofNullable((Integer) rs.getObject("dias")))).list()));
    }

    static Grupo grupo(ResultSet rs, int i) throws SQLException {
        Date hasta = rs.getDate("tema_hasta");
        String tema = rs.getString("tema_dojo");
        return new Grupo(rs.getObject("id", UUID.class), rs.getObject("institucion_id", UUID.class), rs.getString("nombre"),
                Optional.ofNullable(rs.getObject("docente_id", UUID.class)), rs.getBoolean("activo"),
                Optional.ofNullable(tema).map(IdTecnica::de), Optional.ofNullable(hasta).map(Date::toLocalDate),
                rs.getBoolean("lista_en_clase"), rs.getBoolean("ia_en_clase"));
    }

    static ClaseDeAula clase(ResultSet rs, int i) throws SQLException {
        Timestamp terminada = rs.getTimestamp("terminada_en");
        return new ClaseDeAula(rs.getObject("id", UUID.class), rs.getObject("grupo_id", UUID.class), rs.getTimestamp("abierta_en").toInstant(),
                rs.getTimestamp("cierra_en").toInstant(), Optional.ofNullable(terminada).map(Timestamp::toInstant));
    }
}
