package pensamiento.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.SesionConsejero;
import pensamiento.nucleo.TurnoConsejero;
import pensamiento.nucleo.puertos.RepositorioSesiones;

/**
 * Contrato de las sesiones del Consejero: lo guardado se lee igual; no encontrado es vacío; otra persona no ve nada ni
 * puede agregar turnos; los turnos van en orden y sin huecos; completar, proponer y adoptar cambian solo lo suyo; una
 * sesión cerrada no acepta turnos; restaurar importa tal cual y no pisa lo que existe.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class RepositorioSesionesContract {

    /** Dos usuarios distintos de la misma institución, ya existentes en el backend. */
    public record Personas(UUID institucion, UUID usuarioA, UUID usuarioB) {
    }

    protected static final Instant AHORA = Instant.parse("2026-10-09T15:00:00Z");

    protected abstract Personas personas();

    protected abstract RepositorioSesiones comoUsuario(UUID usuarioId);

    /** Un expediente del usuario, ya guardado. */
    protected abstract UUID dadoUnExpediente(UUID usuarioId);

    protected abstract UUID nuevoId(Instant cuando);

    protected SesionConsejero sesion(UUID usuario, String postura, Instant cuando) {
        return new SesionConsejero(nuevoId(cuando), usuario, personas().institucion(), Optional.empty(), SesionConsejero.Modo.DEBATE, postura,
                List.of(new SesionConsejero.Razon("El vendedor dice que bajan los robos un 70%.", "no_se")),
                new Json("{\"intensidad\":2,\"numeroAtaques\":3,\"modo\":\"banco\"}"), true, Optional.of(80), false, SesionConsejero.Estado.ABIERTA,
                Optional.empty(), Optional.empty(), Optional.empty(), cuando, Optional.empty());
    }

    protected TurnoConsejero turno(UUID sesion, int numero, TurnoConsejero.Rol rol, String paso, String texto, Instant cuando) {
        TurnoConsejero.Origen origen = rol == TurnoConsejero.Rol.PERSONA ? TurnoConsejero.Origen.PERSONA : TurnoConsejero.Origen.BANCO;
        TurnoConsejero.Estado estado = rol == TurnoConsejero.Rol.PERSONA ? TurnoConsejero.Estado.LISTO : TurnoConsejero.Estado.REDACTANDO;
        return new TurnoConsejero(nuevoId(cuando), sesion, numero, rol, paso, texto, origen, estado, 0, Optional.empty(), Optional.empty(),
                Optional.empty(), false, cuando);
    }

    @Test
    void lo_creado_se_lee_igual_y_empieza_sin_turnos() {
        Personas p = personas();
        SesionConsejero s = sesion(p.usuarioA(), "Hay que comprar las cámaras, señora.", AHORA);
        RepositorioSesiones repo = comoUsuario(p.usuarioA());

        repo.crear(s);

        assertThat(repo.porId(p.usuarioA(), s.id())).contains(s);
        assertThat(repo.deUsuario(p.usuarioA())).contains(s);
        assertThat(repo.turnos(p.usuarioA(), s.id())).isEmpty();
    }

    @Test
    void un_identificador_que_no_existe_devuelve_vacio() {
        Personas p = personas();
        assertThat(comoUsuario(p.usuarioA()).porId(p.usuarioA(), UUID.randomUUID())).isEmpty();
        assertThat(comoUsuario(p.usuarioA()).turnos(p.usuarioA(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void otra_persona_no_ve_la_sesion_ni_puede_agregarle_turnos() {
        Personas p = personas();
        SesionConsejero s = sesion(p.usuarioA(), "Mi mamá debe venirse a vivir con nosotros.", AHORA);
        comoUsuario(p.usuarioA()).crear(s);
        RepositorioSesiones comoB = comoUsuario(p.usuarioB());

        assertThat(comoB.porId(p.usuarioB(), s.id())).isEmpty();
        assertThat(comoB.deUsuario(p.usuarioB())).extracting(SesionConsejero::id).doesNotContain(s.id());
        assertThatThrownBy(() -> comoB.agregarTurno(p.usuarioB(), p.institucion(), turno(s.id(), 1, TurnoConsejero.Rol.CONSEJERO, "A1", "¿Y?", AHORA)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(comoUsuario(p.usuarioA()).turnos(p.usuarioA(), s.id())).isEmpty();
    }

    @Test
    void los_turnos_van_en_orden_y_uno_fuera_de_turno_se_rechaza() {
        Personas p = personas();
        SesionConsejero s = sesion(p.usuarioA(), "La panadería debe abrir los domingos.", AHORA);
        RepositorioSesiones repo = comoUsuario(p.usuarioA());
        repo.crear(s);
        TurnoConsejero uno = turno(s.id(), 1, TurnoConsejero.Rol.CONSEJERO, "A1", "¿Contaste cuántos lo hacen de verdad?", AHORA);
        TurnoConsejero dos = turno(s.id(), 2, TurnoConsejero.Rol.PERSONA, "A1", "No los conté, señora.", AHORA.plusSeconds(30));

        repo.agregarTurno(p.usuarioA(), p.institucion(), uno);
        repo.agregarTurno(p.usuarioA(), p.institucion(), dos);

        assertThat(repo.turnos(p.usuarioA(), s.id())).containsExactly(uno, dos);
        assertThatThrownBy(() -> repo.agregarTurno(p.usuarioA(), p.institucion(), turno(s.id(), 2, TurnoConsejero.Rol.CONSEJERO, "A2", "¿Otra?", AHORA)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> repo.agregarTurno(p.usuarioA(), p.institucion(), turno(s.id(), 4, TurnoConsejero.Rol.CONSEJERO, "A2", "¿Otra?", AHORA)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repo.turnos(p.usuarioA(), s.id())).hasSize(2);
    }

    @Test
    void completar_un_turno_que_redactaba_cambia_su_texto_su_origen_y_su_registro() {
        Personas p = personas();
        SesionConsejero s = sesion(p.usuarioA(), "Conviene abrir la segunda sucursal.", AHORA);
        RepositorioSesiones repo = comoUsuario(p.usuarioA());
        repo.crear(s);
        TurnoConsejero t = turno(s.id(), 1, TurnoConsejero.Rol.CONSEJERO, "pregunta_sobre_la_pregunta/proposito", "¿Qué quieres lograr?", AHORA);
        repo.agregarTurno(p.usuarioA(), p.institucion(), t);
        Ejecucion.RegistroModelo registro = new Ejecucion.RegistroModelo("qwen3:4b-instruct-2507-q4_K_M", "0edcdef34593", "t08-pregunta.v2", 0.0, 42);

        repo.completarTurno(p.usuarioA(), t.id(), "¿Qué quieres lograr con la sucursal nueva?", TurnoConsejero.Origen.MODELO, 2, Optional.of(registro));

        assertThat(repo.turnos(p.usuarioA(), s.id())).singleElement().satisfies(x -> {
            assertThat(x.texto()).isEqualTo("¿Qué quieres lograr con la sucursal nueva?");
            assertThat(x.origen()).isEqualTo(TurnoConsejero.Origen.MODELO);
            assertThat(x.estado()).isEqualTo(TurnoConsejero.Estado.LISTO);
            assertThat(x.intentos()).isEqualTo(2);
            assertThat(x.modelo()).contains(registro);
        });
    }

    @Test
    void proponer_y_adoptar_un_elemento_queda_en_el_turno_de_la_persona() {
        Personas p = personas();
        SesionConsejero s = sesion(p.usuarioA(), "Debo renunciar.", AHORA);
        RepositorioSesiones repo = comoUsuario(p.usuarioA());
        repo.crear(s);
        repo.agregarTurno(p.usuarioA(), p.institucion(), turno(s.id(), 1, TurnoConsejero.Rol.CONSEJERO, "evidencia/informacion", "¿Qué datos tienes?", AHORA));
        TurnoConsejero respuesta = turno(s.id(), 2, TurnoConsejero.Rol.PERSONA, "evidencia/informacion", "Doy por hecho que me ignora.", AHORA);
        repo.agregarTurno(p.usuarioA(), p.institucion(), respuesta);

        repo.proponerElemento(p.usuarioA(), respuesta.id(), "supuestos", "Dice «doy por hecho».");
        assertThat(repo.turnos(p.usuarioA(), s.id()).get(1)).satisfies(x -> {
            assertThat(x.elementoPropuesto()).contains("supuestos");
            assertThat(x.porquePropuesto()).contains("Dice «doy por hecho».");
            assertThat(x.propuestaAdoptada()).isFalse();
        });
        repo.adoptarElemento(p.usuarioA(), respuesta.id());
        assertThat(repo.turnos(p.usuarioA(), s.id()).get(1).propuestaAdoptada()).isTrue();
    }

    @Test
    void pedir_el_cierre_asociar_y_cerrar_quedan_y_una_sesion_cerrada_no_acepta_turnos() {
        Personas p = personas();
        SesionConsejero s = sesion(p.usuarioA(), "Hay que poner cámaras.", AHORA);
        RepositorioSesiones repo = comoUsuario(p.usuarioA());
        repo.crear(s);
        UUID expediente = dadoUnExpediente(p.usuarioA());

        repo.pedirCierre(p.usuarioA(), s.id());
        repo.asociar(p.usuarioA(), s.id(), Optional.of(expediente));
        repo.cerrar(p.usuarioA(), s.id(), Optional.of("Necesito contar una semana entera."), Optional.of(60), Optional.empty(), AHORA.plusSeconds(600));

        SesionConsejero cerrada = repo.porId(p.usuarioA(), s.id()).orElseThrow();
        assertThat(cerrada.cierrePedido()).isTrue();
        assertThat(cerrada.expedienteId()).contains(expediente);
        assertThat(cerrada.estado()).isEqualTo(SesionConsejero.Estado.CERRADA);
        assertThat(cerrada.reflexion()).contains("Necesito contar una semana entera.");
        assertThat(cerrada.confianzaDespues()).contains(60);
        assertThat(cerrada.cerradaEn()).contains(AHORA.plusSeconds(600));
        assertThatThrownBy(() -> repo.agregarTurno(p.usuarioA(), p.institucion(), turno(s.id(), 1, TurnoConsejero.Rol.PERSONA, "A1", "Tarde.", AHORA)))
                .isInstanceOf(RepositorioSesiones.SesionCerrada.class);
    }

    @Test
    void las_de_la_persona_vienen_de_la_mas_nueva_a_la_mas_vieja() {
        Personas p = personas();
        SesionConsejero vieja = sesion(p.usuarioA(), "Vieja", AHORA.minusSeconds(7200));
        SesionConsejero nueva = sesion(p.usuarioA(), "Nueva", AHORA.plusSeconds(7200));
        RepositorioSesiones repo = comoUsuario(p.usuarioA());
        repo.crear(vieja);
        repo.crear(nueva);

        assertThat(repo.deUsuario(p.usuarioA())).extracting(SesionConsejero::id).containsSubsequence(nueva.id(), vieja.id());
    }

    @Test
    void restaurar_importa_la_sesion_con_sus_turnos_y_no_pisa_lo_que_existe() {
        Personas p = personas();
        SesionConsejero s = sesion(p.usuarioA(), "Importada", AHORA);
        TurnoConsejero t1 = turno(s.id(), 1, TurnoConsejero.Rol.CONSEJERO, "A1", "¿En qué se apoya?", AHORA);
        TurnoConsejero t2 = turno(s.id(), 2, TurnoConsejero.Rol.PERSONA, "A1", "En lo que dijo.", AHORA.plusSeconds(10));
        RepositorioSesiones repo = comoUsuario(p.usuarioA());

        repo.restaurar(p.usuarioA(), p.institucion(), s, List.of(t1, t2));
        SesionConsejero otra = new SesionConsejero(s.id(), s.usuarioId(), s.institucionId(), s.expedienteId(), s.modo(), "Otra cosa", s.razones(),
                s.config(), s.usaModelo(), Optional.of(10), s.cierrePedido(), s.estado(), s.reflexion(), s.confianzaDespues(), s.ejecucionId(),
                s.creadaEn(), s.cerradaEn());
        TurnoConsejero otroT1 = new TurnoConsejero(t1.id(), s.id(), 1, t1.rol(), t1.paso(), "Otro texto", t1.origen(), t1.estado(), 0, Optional.empty(),
                Optional.empty(), Optional.empty(), false, AHORA);
        repo.restaurar(p.usuarioA(), p.institucion(), otra, List.of(otroT1));

        assertThat(repo.porId(p.usuarioA(), s.id())).contains(s);
        assertThat(repo.turnos(p.usuarioA(), s.id())).containsExactly(t1, t2);
    }
}
