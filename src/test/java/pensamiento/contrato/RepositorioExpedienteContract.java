package pensamiento.contrato;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pensamiento.nucleo.Expediente;
import pensamiento.nucleo.puertos.RepositorioExpediente;

/** Contrato de expedientes: no encontrado, ida y vuelta con tildes, orden por fecha y aislamiento por usuario (RF-03). */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class RepositorioExpedienteContract {

    public record Personas(UUID institucion, UUID usuarioA, UUID usuarioB) {
    }

    protected abstract Personas personas();

    protected abstract RepositorioExpediente comoUsuario(UUID usuarioId);

    private Expediente expediente(UUID usuario, String nombre, Instant creado) {
        return new Expediente(UUID.randomUUID(), usuario, personas().institucion(), nombre, Optional.empty(), Expediente.Estado.ABIERTO, creado);
    }

    @Test
    void un_expediente_que_no_existe_devuelve_vacio() {
        Personas p = personas();
        assertThat(comoUsuario(p.usuarioA()).porId(p.usuarioA(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void lo_guardado_se_lee_igual_con_tildes() {
        Personas p = personas();
        Expediente guardado = comoUsuario(p.usuarioA()).guardar(expediente(p.usuarioA(), "La segunda sucursal de la panadería", Instant.parse("2026-10-07T15:00:00Z")));
        assertThat(comoUsuario(p.usuarioA()).porId(p.usuarioA(), guardado.id())).contains(guardado);
    }

    @Test
    void los_expedientes_vienen_del_mas_reciente_al_mas_antiguo() {
        Personas p = personas();
        RepositorioExpediente repo = comoUsuario(p.usuarioA());
        Expediente viejo = repo.guardar(expediente(p.usuarioA(), "cámaras del barrio", Instant.parse("2026-09-01T00:00:00Z")));
        Expediente nuevo = repo.guardar(expediente(p.usuarioA(), "maestría afuera", Instant.parse("2026-10-01T00:00:00Z")));
        assertThat(repo.deUsuario(p.usuarioA()).stream().map(Expediente::id).toList()).containsSubsequence(nuevo.id(), viejo.id());
    }

    @Test
    void el_usuario_b_no_encuentra_el_expediente_del_usuario_a() {
        Personas p = personas();
        Expediente deA = comoUsuario(p.usuarioA()).guardar(expediente(p.usuarioA(), "privado de A", Instant.now()));
        assertThat(comoUsuario(p.usuarioB()).porId(p.usuarioB(), deA.id())).isEmpty();
        assertThat(comoUsuario(p.usuarioB()).deUsuario(p.usuarioB()).stream().map(Expediente::id)).doesNotContain(deA.id());
    }
}
