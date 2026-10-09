package pensamiento.contrato;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pensamiento.nucleo.Argumento;
import pensamiento.nucleo.ArgumentoProducido;
import pensamiento.nucleo.EstandarPrueba;
import pensamiento.nucleo.puertos.RepositorioArgumentos;

/**
 * Contrato de los argumentos de cada persona: lo guardado se lee igual y en orden (premisas incluidas, con su
 * marca de asumible), no encontrado es vacío, otra persona no ve nada e insistir con los mismos identificadores
 * no duplica.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class RepositorioArgumentosContract {

    /** Dos usuarios distintos de la misma institución, ya existentes en el backend. */
    public record Personas(UUID institucion, UUID usuarioA, UUID usuarioB) {
    }

    /** Una ejecución guardada del usuario con estas afirmaciones (el real necesita las filas por las claves foráneas). */
    public record EjecucionConAfirmaciones(UUID ejecucionId, List<UUID> afirmaciones) {
    }

    protected abstract Personas personas();

    protected abstract RepositorioArgumentos comoUsuario(UUID usuarioId);

    protected abstract EjecucionConAfirmaciones dadaUnaEjecucionConAfirmaciones(UUID usuarioId, int cuantas);

    private static final String SUCURSAL = "[Sucursal]: Conviene abrir la segunda sucursal en el centro.\n  - [Personal]: Falta personal, ¿quién atenderá la ñapa?";

    private List<ArgumentoProducido> dosArgumentos(EjecucionConAfirmaciones e) {
        List<UUID> a = e.afirmaciones();
        ArgumentoProducido pro = new ArgumentoProducido(new Argumento(UUID.randomUUID(), a.get(0),
                List.of(new Argumento.Premisa(a.get(1), 1, false), new Argumento.Premisa(a.get(2), 2, true)), 3, Argumento.Sentido.PRO),
                EstandarPrueba.CLARO_Y_CONVINCENTE, Optional.of("autoridad"), Optional.of(SUCURSAL));
        ArgumentoProducido contra = new ArgumentoProducido(new Argumento(UUID.randomUUID(), a.get(0),
                List.of(new Argumento.Premisa(a.get(3), 1, false)), 1, Argumento.Sentido.CONTRA),
                EstandarPrueba.PREPONDERANCIA, Optional.empty(), Optional.empty());
        return List.of(pro, contra);
    }

    @Test
    void lo_guardado_se_lee_igual_en_orden_con_premisas_marca_de_asumible_y_texto_con_enie() {
        Personas p = personas();
        EjecucionConAfirmaciones e = dadaUnaEjecucionConAfirmaciones(p.usuarioA(), 4);
        List<ArgumentoProducido> argumentos = dosArgumentos(e);

        comoUsuario(p.usuarioA()).guardar(p.usuarioA(), p.institucion(), e.ejecucionId(), argumentos);
        List<RepositorioArgumentos.ArgumentoGuardado> leidos = comoUsuario(p.usuarioA()).deEjecucion(p.usuarioA(), e.ejecucionId());

        assertThat(leidos).extracting(RepositorioArgumentos.ArgumentoGuardado::argumento).containsExactlyElementsOf(argumentos);
        assertThat(leidos).extracting(RepositorioArgumentos.ArgumentoGuardado::orden).containsExactly(1, 2);
        assertThat(leidos).extracting(RepositorioArgumentos.ArgumentoGuardado::ejecucionId).containsOnly(e.ejecucionId());
        assertThat(leidos.getFirst().argumento().textoArgdown()).contains(SUCURSAL);
    }

    @Test
    void por_id_devuelve_el_argumento_y_un_identificador_que_no_existe_devuelve_vacio() {
        Personas p = personas();
        EjecucionConAfirmaciones e = dadaUnaEjecucionConAfirmaciones(p.usuarioA(), 4);
        List<ArgumentoProducido> argumentos = dosArgumentos(e);
        comoUsuario(p.usuarioA()).guardar(p.usuarioA(), p.institucion(), e.ejecucionId(), argumentos);

        assertThat(comoUsuario(p.usuarioA()).porId(p.usuarioA(), argumentos.get(1).argumento().id()))
                .hasValueSatisfying(g -> {
                    assertThat(g.argumento()).isEqualTo(argumentos.get(1));
                    assertThat(g.orden()).isEqualTo(2);
                });
        assertThat(comoUsuario(p.usuarioA()).porId(p.usuarioA(), UUID.randomUUID())).isEmpty();
        assertThat(comoUsuario(p.usuarioA()).deEjecucion(p.usuarioA(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void con_premisa_devuelve_los_argumentos_donde_la_afirmacion_es_premisa_y_nada_a_otra_persona() {
        Personas p = personas();
        EjecucionConAfirmaciones e = dadaUnaEjecucionConAfirmaciones(p.usuarioA(), 4);
        List<ArgumentoProducido> argumentos = dosArgumentos(e);
        comoUsuario(p.usuarioA()).guardar(p.usuarioA(), p.institucion(), e.ejecucionId(), argumentos);

        assertThat(comoUsuario(p.usuarioA()).conPremisa(p.usuarioA(), e.afirmaciones().get(2)))
                .extracting(RepositorioArgumentos.ArgumentoGuardado::argumento).containsExactly(argumentos.getFirst());
        assertThat(comoUsuario(p.usuarioA()).conPremisa(p.usuarioA(), e.afirmaciones().get(3)))
                .extracting(RepositorioArgumentos.ArgumentoGuardado::argumento).containsExactly(argumentos.get(1));
        assertThat(comoUsuario(p.usuarioA()).conPremisa(p.usuarioA(), e.afirmaciones().get(0))).as("la conclusión no es premisa").isEmpty();
        assertThat(comoUsuario(p.usuarioB()).conPremisa(p.usuarioB(), e.afirmaciones().get(2))).isEmpty();
    }

    @Test
    void otra_persona_no_ve_los_argumentos_ni_por_id_ni_por_ejecucion() {
        Personas p = personas();
        EjecucionConAfirmaciones e = dadaUnaEjecucionConAfirmaciones(p.usuarioA(), 4);
        List<ArgumentoProducido> argumentos = dosArgumentos(e);
        comoUsuario(p.usuarioA()).guardar(p.usuarioA(), p.institucion(), e.ejecucionId(), argumentos);

        assertThat(comoUsuario(p.usuarioB()).porId(p.usuarioB(), argumentos.getFirst().argumento().id())).isEmpty();
        assertThat(comoUsuario(p.usuarioB()).deEjecucion(p.usuarioB(), e.ejecucionId())).isEmpty();
    }

    @Test
    void guardar_dos_veces_los_mismos_argumentos_no_duplica() {
        Personas p = personas();
        EjecucionConAfirmaciones e = dadaUnaEjecucionConAfirmaciones(p.usuarioA(), 4);
        List<ArgumentoProducido> argumentos = dosArgumentos(e);
        RepositorioArgumentos repo = comoUsuario(p.usuarioA());

        repo.guardar(p.usuarioA(), p.institucion(), e.ejecucionId(), argumentos);
        repo.guardar(p.usuarioA(), p.institucion(), e.ejecucionId(), argumentos);

        assertThat(repo.deEjecucion(p.usuarioA(), e.ejecucionId())).hasSize(2);
        assertThat(repo.deEjecucion(p.usuarioA(), e.ejecucionId()).getFirst().argumento().argumento().premisas()).hasSize(2);
    }

    @Test
    void una_ejecucion_sin_argumentos_devuelve_una_lista_vacia() {
        Personas p = personas();
        EjecucionConAfirmaciones e = dadaUnaEjecucionConAfirmaciones(p.usuarioA(), 1);

        assertThat(comoUsuario(p.usuarioA()).deEjecucion(p.usuarioA(), e.ejecucionId())).isEmpty();
    }
}
