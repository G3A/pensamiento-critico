package pensamiento.contrato.real;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import pensamiento.contrato.RepositorioAulaContract;
import pensamiento.expediente.RepositorioDojoJdbc;
import pensamiento.nucleo.ClaseDeAula;
import pensamiento.nucleo.Competencia;
import pensamiento.nucleo.Grupo;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.IntentoDojo;
import pensamiento.nucleo.PanelGrupo;
import pensamiento.nucleo.puertos.RepositorioAula;
import pensamiento.testutil.BaseDatosDePrueba;
import pensamiento.web.aula.RepositorioAulaJdbc;

/**
 * Contra el PostgreSQL del compose, como rol de aplicación bajo la RLS por rol de V17: cada llamada corre en una transacción
 * con la cuenta del actor fijada (app.usuario y app.institucion); lo de antes de entrar, solo con la institución.
 */
@EnabledIfEnvironmentVariable(named = "CONTRACT_REAL", matches = "true")
class RealRepositorioAulaContractIT extends RepositorioAulaContract {

    private static final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private static Escenario escenario;

    @BeforeAll
    static void sembrar() {
        UUID institucion = bd.crearInstitucion("contrato-aula-" + UUID.randomUUID());
        UUID otra = bd.crearInstitucion("contrato-aula-otra-" + UUID.randomUUID());
        escenario = new Escenario(institucion, bd.crearUsuario(institucion, "administrador", "administrador"),
                bd.crearUsuario(institucion, "docente A", "docente"), bd.crearUsuario(institucion, "docente B", "docente"),
                bd.crearUsuario(institucion, "persona", "persona"), otra, bd.crearUsuario(otra, "administrador", "administrador"));
    }

    @AfterAll
    static void limpiar() {
        bd.borrarInstitucion(escenario.institucion());
        bd.borrarInstitucion(escenario.otraInstitucion());
    }

    @Override
    protected Escenario escenario() {
        return escenario;
    }

    @Override
    protected void responde(UUID estudiante, IntentoDojo intento, Competencia competencia) {
        RepositorioDojoJdbc dojo = new RepositorioDojoJdbc(bd.jdbcApp());
        bd.comoUsuario(estudiante, escenario.institucion(), () -> dojo.guardar(estudiante, escenario.institucion(), intento, competencia));
    }

    @Override
    protected void entro(UUID usuario, Instant cuando) {
        bd.jdbcAdmin().sql("INSERT INTO auditoria (usuario_id, institucion_id, accion, objeto_tipo, objeto_id, fecha) "
                        + "VALUES (:u, :i, 'entrar', 'usuario', :u, :f)")
                .param("u", usuario).param("i", escenario.institucion()).param("f", Timestamp.from(cuando)).update();
    }

    @Override
    protected RepositorioAula aula() {
        RepositorioAulaJdbc real = new RepositorioAulaJdbc(bd.jdbcApp());
        return new RepositorioAula() {
            private <T> T como(Actor actor, Supplier<T> accion) {
                return bd.comoUsuario(actor.id(), actor.institucionId(), accion);
            }

            @Override
            public Optional<Grupo> crearGrupo(Actor actor, Grupo grupo) {
                return como(actor, () -> real.crearGrupo(actor, grupo));
            }

            @Override
            public boolean asignarDocente(Actor actor, UUID grupoId, Optional<UUID> docenteId) {
                return como(actor, () -> real.asignarDocente(actor, grupoId, docenteId));
            }

            @Override
            public List<Grupo> grupos(Actor actor) {
                return como(actor, () -> real.grupos(actor));
            }

            @Override
            public Optional<Grupo> grupo(Actor actor, UUID grupoId) {
                return como(actor, () -> real.grupo(actor, grupoId));
            }

            @Override
            public boolean asignarTema(Actor actor, UUID grupoId, Optional<IdTecnica> tema, Optional<LocalDate> hasta, Instant ahora) {
                return como(actor, () -> real.asignarTema(actor, grupoId, tema, hasta, ahora));
            }

            @Override
            public boolean cambiarOpciones(Actor actor, UUID grupoId, boolean listaEnClase, boolean iaEnClase) {
                return como(actor, () -> real.cambiarOpciones(actor, grupoId, listaEnClase, iaEnClase));
            }

            @Override
            public Optional<UUID> crearEstudiante(Actor actor, UUID grupoId, UUID id, String nombre) {
                return como(actor, () -> real.crearEstudiante(actor, grupoId, id, nombre));
            }

            @Override
            public List<Miembro> miembros(Actor actor, UUID grupoId) {
                return como(actor, () -> real.miembros(actor, grupoId));
            }

            @Override
            public Optional<ClaseDeAula> abrirClase(Actor actor, UUID grupoId, String codigoHash, Instant ahora, Instant cierraEn) {
                return como(actor, () -> real.abrirClase(actor, grupoId, codigoHash, ahora, cierraEn));
            }

            @Override
            public Optional<ClaseDeAula> claseAbierta(Actor actor, UUID grupoId, Instant ahora) {
                return como(actor, () -> real.claseAbierta(actor, grupoId, ahora));
            }

            @Override
            public boolean terminarClase(Actor actor, UUID grupoId, Instant ahora) {
                return como(actor, () -> real.terminarClase(actor, grupoId, ahora));
            }

            @Override
            public Optional<ClasePorCodigo> clasePorCodigo(UUID institucionId, String codigoHash, Instant ahora) {
                return bd.comoInstitucion(institucionId, () -> real.clasePorCodigo(institucionId, codigoHash, ahora));
            }

            @Override
            public List<String> listaDeClase(UUID institucionId, String codigoHash, Instant ahora) {
                return bd.comoInstitucion(institucionId, () -> real.listaDeClase(institucionId, codigoHash, ahora));
            }

            @Override
            public Optional<PanelGrupo> panel(Actor actor, UUID grupoId, Periodo periodo) {
                return como(actor, () -> real.panel(actor, grupoId, periodo));
            }
        };
    }
}
