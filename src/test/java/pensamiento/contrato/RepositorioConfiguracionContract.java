package pensamiento.contrato;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.puertos.RepositorioConfiguracion;

/**
 * Contrato de la configuración por usuario: sin fila no hay nada, guardar reemplaza, restablecer borra, el JSONB
 * hace ida y vuelta con tildes y ñ, y un usuario no ve ni pisa la configuración de otro.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class RepositorioConfiguracionContract {

    private static final IdTecnica T28 = IdTecnica.de("T28");

    /** Dos usuarios distintos de la misma institución, ya existentes en el backend. */
    public record Personas(UUID institucion, UUID usuarioA, UUID usuarioB) {
    }

    protected abstract Personas personas();

    protected abstract RepositorioConfiguracion comoUsuario(UUID usuarioId);

    @Test
    void sin_configuracion_guardada_devuelve_vacio() {
        Personas p = personas();
        assertThat(comoUsuario(p.usuarioA()).de(p.usuarioA(), IdTecnica.de("T01"))).isEmpty();
    }

    @Test
    void guardar_dos_veces_reemplaza_y_se_lee_igual() {
        Personas p = personas();
        RepositorioConfiguracion repo = comoUsuario(p.usuarioA());
        repo.guardar(p.usuarioA(), p.institucion(), T28, 1, new Json("{\"escala\":\"cin\",\"maxHipotesis\":4,\"nota\":\"año\"}"));
        repo.guardar(p.usuarioA(), p.institucion(), T28, 1, new Json("{\"escala\":\"numerica\",\"maxHipotesis\":6,\"nota\":\"señal\"}"));

        RepositorioConfiguracion.Guardada leida = repo.de(p.usuarioA(), T28).orElseThrow();
        assertThat(leida.versionEsquema()).isEqualTo(1);
        assertThat(leida.valores().texto()).contains("numerica").contains("señal").doesNotContain("año");
        assertThat(repo.todas(p.usuarioA())).containsKey(T28);
    }

    @Test
    void restablecer_vuelve_a_la_del_catalogo() {
        Personas p = personas();
        RepositorioConfiguracion repo = comoUsuario(p.usuarioA());
        repo.guardar(p.usuarioA(), p.institucion(), IdTecnica.de("T31"), 1, new Json("{\"x\":1}"));
        repo.restablecer(p.usuarioA(), IdTecnica.de("T31"));
        assertThat(repo.de(p.usuarioA(), IdTecnica.de("T31"))).isEmpty();
    }

    @Test
    void el_usuario_b_no_ve_ni_borra_la_configuracion_del_usuario_a() {
        Personas p = personas();
        comoUsuario(p.usuarioA()).guardar(p.usuarioA(), p.institucion(), IdTecnica.de("T29"), 1, new Json("{\"causasMinimas\":3}"));

        assertThat(comoUsuario(p.usuarioB()).de(p.usuarioB(), IdTecnica.de("T29"))).isEmpty();
        comoUsuario(p.usuarioB()).restablecer(p.usuarioB(), IdTecnica.de("T29"));
        assertThat(comoUsuario(p.usuarioA()).de(p.usuarioA(), IdTecnica.de("T29"))).isPresent();
    }
}
