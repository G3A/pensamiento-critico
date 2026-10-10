package pensamiento.unidad.expediente;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pensamiento.expediente.GuardadoDeEjecuciones;
import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.CambioOpinion;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.OrigenAfirmacion;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.SentidoAfirmacion;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.testutil.builders.Contextos;
import pensamiento.testutil.fakes.FakeBiblioteca;
import pensamiento.testutil.fakes.FakeReloj;
import pensamiento.testutil.fakes.FakeRepositorioArgumentos;
import pensamiento.testutil.fakes.FakeRepositorioCambiosOpinion;
import pensamiento.testutil.fakes.FakeRepositorioConfiguracion;
import pensamiento.testutil.fakes.FakeRepositorioEjecucion;
import pensamiento.testutil.fakes.FakeRepositorioEvidencias;
import pensamiento.testutil.fakes.FakeRepositorioPredicciones;

/**
 * El registro automático de T46 · Registro de cambios de opinión (docs/ejemplos/T46.md, regla 2): encendido, los cambios que
 * declara T08 se guardan con la ejecución; apagado, no, y solo quedan los que la persona registra a mano con T46.
 */
class RegistroAutomaticoTest {

    private static final UUID YO = Contextos.DUENA_DE_LA_PANADERIA;
    private final FakeRepositorioEjecucion ejecuciones = new FakeRepositorioEjecucion();
    private final FakeRepositorioCambiosOpinion cambios = new FakeRepositorioCambiosOpinion(ejecuciones);
    private final FakeRepositorioConfiguracion configuraciones = new FakeRepositorioConfiguracion();
    private final FakeReloj reloj = new FakeReloj();
    private final GuardadoDeEjecuciones guardado = new GuardadoDeEjecuciones(ejecuciones, new FakeRepositorioArgumentos(),
            new FakeRepositorioPredicciones(ejecuciones), cambios, new FakeRepositorioEvidencias(ejecuciones, new FakeBiblioteca()), configuraciones);

    private void guardarConUnCambio(String tecnica) {
        UUID afirmacion = UUID.randomUUID();
        Resultado<String> r = new Resultado<>(1, "valor", List.of(new AfirmacionConRol(afirmacion, "Conviene abrir en el centro",
                TipoAfirmacion.JUICIO_DE_VALOR, RolAfirmacion.POSTURA, SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO)), List.of(), "Resumen.",
                List.of(), Optional.empty(), Optional.empty(), List.of(),
                List.of(new CambioOpinion.Declarado(UUID.randomUUID(), afirmacion, 80, 45, CambioOpinion.Causa.EVIDENCIA)));
        guardado.guardar(new Ejecucion(UUID.randomUUID(), YO, Contextos.INSTITUCION, IdTecnica.de(tecnica), 1, Optional.empty(), Json.VACIO, Json.VACIO,
                Json.VACIO, "Resumen.", Optional.empty(), UUID.randomUUID().toString(), reloj.ahora()), r);
    }

    private void apagarRegistroAutomatico() {
        configuraciones.guardar(YO, Contextos.INSTITUCION, IdTecnica.de("T46"), 1,
                new Json("{\"registroAutomatico\":false,\"causas\":[\"manual\"],\"mesesSinRevisar\":12}"));
    }

    @Test
    void encendido_guarda_los_cambios_que_declara_t08() {
        guardarConUnCambio("T08");
        assertThat(cambios.deUsuario(YO)).hasSize(1);
    }

    @Test
    void apagado_no_guarda_los_cambios_de_t08_pero_si_los_registrados_a_mano_con_t46() {
        apagarRegistroAutomatico();

        guardarConUnCambio("T08");
        guardarConUnCambio("T22");
        guardarConUnCambio("T46");

        assertThat(cambios.deUsuario(YO)).singleElement().satisfies(c -> assertThat(c.confianzaDespues()).isEqualTo(45));
        assertThat(ejecuciones.todas()).as("las ejecuciones se guardan igual").hasSize(3);
    }
}
