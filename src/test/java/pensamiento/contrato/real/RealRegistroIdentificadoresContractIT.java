package pensamiento.contrato.real;

import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import pensamiento.contrato.RegistroIdentificadoresContract;
import pensamiento.expediente.RegistroIdentificadoresJdbc;
import pensamiento.nucleo.puertos.RegistroIdentificadores;
import pensamiento.testutil.BaseDatosDePrueba;

/** Contra el PostgreSQL del compose: la función SECURITY DEFINER de V3, llamada como rol de aplicación bajo RLS. */
@EnabledIfEnvironmentVariable(named = "CONTRACT_REAL", matches = "true")
class RealRegistroIdentificadoresContractIT extends RegistroIdentificadoresContract {

    private static final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private static Personas personas;

    @BeforeAll
    static void sembrar() {
        UUID institucion = bd.crearInstitucion("contrato-identificadores-" + UUID.randomUUID());
        personas = new Personas(institucion, bd.crearUsuario(institucion, "perfil A"), bd.crearUsuario(institucion, "perfil B"));
    }

    @AfterAll
    static void limpiar() {
        bd.borrarInstitucion(personas.institucion());
    }

    @Override
    protected Personas personas() {
        return personas;
    }

    @Override
    protected RegistroIdentificadores comoUsuario(UUID usuarioId) {
        RegistroIdentificadoresJdbc real = new RegistroIdentificadoresJdbc(bd.jdbcApp());
        return (u, id) -> bd.comoUsuario(usuarioId, personas.institucion(), () -> real.deOtroUsuario(u, id));
    }

    @Override
    protected UUID expedienteDe(UUID usuarioId) {
        return bd.crearExpediente(personas.institucion(), usuarioId, "la asamblea de marzo");
    }

    @Override
    protected UUID prediccionDe(UUID usuarioId) {
        UUID ejecucion = ejecucionDe(usuarioId);
        UUID afirmacion = bd.jdbcAdmin().sql("""
                INSERT INTO afirmacion (usuario_id, institucion_id, texto, tipo, origen, adoptada)
                VALUES (:u, :i, 'La sucursal cubre sus costos en seis meses', 'prediccion', 'usuario', true) RETURNING id
                """).param("u", usuarioId).param("i", personas.institucion()).query(UUID.class).single();
        return bd.jdbcAdmin().sql("""
                INSERT INTO prediccion (usuario_id, institucion_id, afirmacion_id, confianza, fecha_revision, ejecucion_id)
                VALUES (:u, :i, :a, 70, DATE '2027-04-15', :e) RETURNING id
                """).param("u", usuarioId).param("i", personas.institucion()).param("a", afirmacion).param("e", ejecucion)
                .query(UUID.class).single();
    }

    @Override
    protected java.util.List<UUID> sesionTurnoYCambioDe(UUID usuarioId) {
        UUID ejecucion = ejecucionDe(usuarioId);
        UUID afirmacion = bd.jdbcAdmin().sql("""
                INSERT INTO afirmacion (usuario_id, institucion_id, texto, tipo, origen, adoptada)
                VALUES (:u, :i, 'Conviene abrir los domingos', 'juicio_de_valor', 'usuario', true) RETURNING id
                """).param("u", usuarioId).param("i", personas.institucion()).query(UUID.class).single();
        UUID cambio = bd.jdbcAdmin().sql("""
                INSERT INTO cambio_opinion (usuario_id, institucion_id, afirmacion_id, confianza_antes, confianza_despues, causa, ejecucion_id)
                VALUES (:u, :i, :a, 80, 60, 'evidencia', :e) RETURNING id
                """).param("u", usuarioId).param("i", personas.institucion()).param("a", afirmacion).param("e", ejecucion).query(UUID.class).single();
        UUID sesion = bd.jdbcAdmin().sql("""
                INSERT INTO sesion_consejero (usuario_id, institucion_id, modo, postura, config)
                VALUES (:u, :i, 'decision', 'Conviene abrir los domingos', '{}') RETURNING id
                """).param("u", usuarioId).param("i", personas.institucion()).query(UUID.class).single();
        UUID turno = bd.jdbcAdmin().sql("""
                INSERT INTO turno_consejero (sesion_id, usuario_id, institucion_id, numero, rol, paso, texto, origen)
                VALUES (:s, :u, :i, 1, 'consejero', 'pregunta_sobre_la_pregunta/proposito', '¿Qué quieres lograr?', 'banco') RETURNING id
                """).param("s", sesion).param("u", usuarioId).param("i", personas.institucion()).query(UUID.class).single();
        return java.util.List.of(sesion, turno, cambio);
    }

    @Override
    protected UUID ejecucionDe(UUID usuarioId) {
        return bd.jdbcAdmin().sql("""
                INSERT INTO ejecucion (usuario_id, institucion_id, tecnica_id, version_esquema, clave_idempotencia)
                VALUES (:u, :i, 'T28', 1, :c) RETURNING id
                """).param("u", usuarioId).param("i", personas.institucion()).param("c", "identificadores-" + UUID.randomUUID())
                .query(UUID.class).single();
    }
}
