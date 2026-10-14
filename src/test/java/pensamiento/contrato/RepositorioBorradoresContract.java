package pensamiento.contrato;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pensamiento.nucleo.puertos.RepositorioBorradores;
import pensamiento.nucleo.puertos.RepositorioBorradores.Borrador;

/**
 * Contrato de los borradores (hito 10, entregable 8): lo guardado se lee igual, con tildes, ñ y saltos de línea; guardar otra
 * vez el mismo formulario lo reemplaza; borrar lo quita una vez; otra persona, aun su docente, no ve ni borra lo de nadie.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class RepositorioBorradoresContract {

    /** Una institución con una estudiante y su docente (o cualquier otra persona), ya existentes en el backend. */
    public record Personas(UUID institucion, UUID estudiante, UUID otra) {
    }

    protected static final Instant AHORA = Instant.parse("2026-10-20T13:00:00Z");

    protected abstract Personas personas();

    protected abstract RepositorioBorradores comoUsuario(UUID usuarioId);

    @Test
    void lo_guardado_se_lee_igual_con_tildes_enes_y_saltos_de_linea() {
        Personas p = personas();
        String clave = "dojo:generalizacion-c1:" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, String> campos = Map.of("respuesta", "Es un solo caso, señora:\nantes de decidir contaría cuántos panes se venden.");

        comoUsuario(p.estudiante()).guardar(p.estudiante(), p.institucion(), clave, campos, AHORA);

        assertThat(comoUsuario(p.estudiante()).leer(p.estudiante(), clave)).contains(new Borrador(clave, campos, AHORA));
    }

    @Test
    void guardar_otra_vez_el_mismo_formulario_lo_reemplaza_y_borrar_lo_quita_una_vez() {
        Personas p = personas();
        RepositorioBorradores repo = comoUsuario(p.estudiante());
        String clave = "dojo:anclaje-c1:" + UUID.randomUUID().toString().substring(0, 8);
        repo.guardar(p.estudiante(), p.institucion(), clave, Map.of("respuesta", "primera"), AHORA);
        repo.guardar(p.estudiante(), p.institucion(), clave, Map.of("respuesta", "segunda", "nivel", "crear"), AHORA.plusSeconds(20));

        assertThat(repo.leer(p.estudiante(), clave)).contains(new Borrador(clave, Map.of("respuesta", "segunda", "nivel", "crear"), AHORA.plusSeconds(20)));
        assertThat(repo.borrar(p.estudiante(), clave)).isTrue();
        assertThat(repo.borrar(p.estudiante(), clave)).isFalse();
        assertThat(repo.leer(p.estudiante(), clave)).isEmpty();
    }

    @Test
    void otra_persona_no_ve_ni_borra_el_borrador() {
        Personas p = personas();
        String clave = "dojo:tasa-base-c1:" + UUID.randomUUID().toString().substring(0, 8);
        comoUsuario(p.estudiante()).guardar(p.estudiante(), p.institucion(), clave, Map.of("respuesta", "lo mío"), AHORA);

        assertThat(comoUsuario(p.otra()).leer(p.otra(), clave)).isEmpty();
        assertThat(comoUsuario(p.otra()).borrar(p.otra(), clave)).isFalse();
        assertThat(comoUsuario(p.estudiante()).leer(p.estudiante(), clave)).isPresent();
    }
}
