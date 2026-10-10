package pensamiento.unidad.flujos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pensamiento.expediente.GuardadoDeEjecuciones;
import pensamiento.flujos.RegistroDeCambios;
import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.CambioOpinion;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.Expediente;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.OrigenAfirmacion;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.SentidoAfirmacion;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.tecnicas.f8.EjecutorCambiosOpinion;
import pensamiento.tecnicas.f8.EjecutorDiarioRazonamiento;
import pensamiento.tecnicas.f8.ResultadoCambiosOpinion;
import pensamiento.tecnicas.f8.ResultadoDiarioRazonamiento;
import pensamiento.testutil.builders.Contextos;
import pensamiento.testutil.fakes.FakeBiblioteca;
import pensamiento.testutil.fakes.FakeRegistroDeRazonamiento;
import pensamiento.testutil.fakes.FakeReloj;
import pensamiento.testutil.fakes.FakeRepositorioArgumentos;
import pensamiento.testutil.fakes.FakeRepositorioCambiosOpinion;
import pensamiento.testutil.fakes.FakeRepositorioConfiguracion;
import pensamiento.testutil.fakes.FakeRepositorioEjecucion;
import pensamiento.testutil.fakes.FakeRepositorioEvidencias;
import pensamiento.testutil.fakes.FakeRepositorioExpediente;
import pensamiento.testutil.fakes.FakeRepositorioPredicciones;

/**
 * P20 junta en una sola línea de tiempo los cambios de opinión que guardan T08, el cierre del Consejero (aquí, T36 en el
 * debate) y la ficha de verificación (T22), más el registrado a mano con T46; el diario de razonamiento reúne las
 * ejecuciones de la ventana. Hoy es el miércoles 7 de octubre de 2026 y cada guardado pasa un día después del anterior.
 */
class RegistroDeCambiosTest {

    private static final UUID YO = Contextos.DUENA_DE_LA_PANADERIA;
    private static final UUID INST = Contextos.INSTITUCION;
    private static final EjecutorCambiosOpinion.Config CONFIG = new EjecutorCambiosOpinion.Config(true, List.of(CambioOpinion.Causa.values()), 12);

    private final FakeReloj reloj = new FakeReloj();
    private final FakeRepositorioEjecucion ejecuciones = new FakeRepositorioEjecucion();
    private final FakeRepositorioExpediente expedientes = new FakeRepositorioExpediente();
    private final FakeRepositorioCambiosOpinion cambios = new FakeRepositorioCambiosOpinion(ejecuciones);
    private final GuardadoDeEjecuciones guardado = new GuardadoDeEjecuciones(ejecuciones, new FakeRepositorioArgumentos(),
            new FakeRepositorioPredicciones(ejecuciones), cambios, new FakeRepositorioEvidencias(ejecuciones, new FakeBiblioteca()),
            new FakeRepositorioConfiguracion());
    private final RegistroDeCambios registro = new RegistroDeCambios(new FakeRegistroDeRazonamiento(ejecuciones, expedientes, cambios), guardado, reloj);

    /** Guarda una ejecución de esa técnica con una postura y un cambio de confianza, en el expediente dado. */
    private void guardarCambio(String tecnica, String resumen, String postura, int antes, int despues, CambioOpinion.Causa causa,
                               Optional<UUID> expediente) {
        UUID afirmacion = UUID.randomUUID();
        Resultado<String> r = new Resultado<>(1, "valor", List.of(new AfirmacionConRol(afirmacion, postura, TipoAfirmacion.JUICIO_DE_VALOR,
                RolAfirmacion.POSTURA, SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO)), List.of(), resumen, List.of(), Optional.empty(),
                Optional.empty(), List.of(), List.of(new CambioOpinion.Declarado(UUID.randomUUID(), afirmacion, antes, despues, causa)));
        guardado.guardar(new Ejecucion(UUID.randomUUID(), YO, INST, IdTecnica.de(tecnica), 1, expediente, Json.VACIO, Json.VACIO, Json.VACIO, resumen,
                Optional.empty(), UUID.randomUUID().toString(), reloj.ahora()), r);
    }

    private UUID expediente(String nombre) {
        Expediente x = new Expediente(UUID.randomUUID(), YO, INST, nombre, Optional.empty(), Expediente.Estado.ABIERTO, reloj.ahora());
        expedientes.guardar(x);
        return x.id();
    }

    @Test
    void los_cambios_de_t08_del_consejero_y_de_la_ficha_aparecen_en_una_sola_linea_de_tiempo_con_el_registrado_a_mano() {
        UUID sucursal = expediente("La segunda sucursal");
        guardarCambio("T08", "Sesión socrática: abrir en el centro", "Conviene abrir en el centro", 80, 60, CambioOpinion.Causa.EVIDENCIA,
                Optional.of(sucursal));
        reloj.avanzar(Duration.ofDays(1));
        guardarCambio("T36", "Equipo rojo: las cámaras son la solución", "Las cámaras son la solución", 90, 70, CambioOpinion.Causa.STEELMAN,
                Optional.empty());
        reloj.avanzar(Duration.ofDays(1));
        guardarCambio("T22", "Verificación: tráfico del centro", "El centro tiene más tráfico", 70, 40, CambioOpinion.Causa.EVIDENCIA,
                Optional.of(sucursal));
        reloj.avanzar(Duration.ofDays(1));
        registro.registrarAMano(YO, INST, CONFIG, "Los vecinos nuevos no participan", 70, 50, CambioOpinion.Causa.PRESION, "clave-1");

        ResultadoCambiosOpinion r = registro.registro(YO, CONFIG).cambios();

        assertThat(r.linea()).extracting(c -> c.fecha() + " " + c.tecnica() + " " + c.antes() + "→" + c.despues() + " " + c.causa())
                .containsExactly("2026-10-10 T46 70→50 presion", "2026-10-09 T22 70→40 evidencia", "2026-10-08 T36 90→70 steelman",
                        "2026-10-07 T08 80→60 evidencia");
        assertThat(r.total()).isEqualTo(4);
        assertThat(r.lectura()).isEqualTo("La mayoría de tus cambios tuvo una razón; por presión social: 1.");
        assertThat(registro.registro(YO, CONFIG).posturas()).contains("Los vecinos nuevos no participan", "Conviene abrir en el centro");
    }

    @Test
    void registrar_dos_veces_con_la_misma_clave_guarda_un_solo_cambio() {
        registro.registrarAMano(YO, INST, CONFIG, "Subir el precio baja las ventas", 80, 60, CambioOpinion.Causa.MANUAL, "misma-clave");
        registro.registrarAMano(YO, INST, CONFIG, "Subir el precio baja las ventas", 80, 60, CambioOpinion.Causa.MANUAL, "misma-clave");

        assertThat(cambios.deUsuario(YO)).hasSize(1);
    }

    @Test
    void registrar_con_una_causa_apagada_o_sin_postura_no_guarda_nada() {
        var soloEvidencia = new EjecutorCambiosOpinion.Config(true, List.of(CambioOpinion.Causa.EVIDENCIA), 12);

        assertThatThrownBy(() -> registro.registrarAMano(YO, INST, soloEvidencia, "Las cámaras son la solución", 90, 60, CambioOpinion.Causa.PRESION,
                "k")).isInstanceOf(RegistroDeCambios.NoPermitido.class).hasMessage("Elige una causa de las que tienes activas.");
        assertThatThrownBy(() -> registro.registrarAMano(YO, INST, CONFIG, " ", 90, 60, CambioOpinion.Causa.MANUAL, "k2"))
                .isInstanceOf(RegistroDeCambios.NoPermitido.class).hasMessage("Escribe la postura que cambió.");
        assertThat(ejecuciones.todas()).isEmpty();
    }

    @Test
    void el_diario_reune_las_ejecuciones_de_la_ventana_con_su_expediente_y_sus_cambios() {
        UUID sucursal = expediente("La segunda sucursal");
        guardarCambio("T08", "Sesión socrática: abrir en el centro", "Conviene abrir en el centro", 80, 60, CambioOpinion.Causa.EVIDENCIA,
                Optional.of(sucursal));
        reloj.avanzar(Duration.ofDays(1));
        guardarCambio("T22", "Verificación: tráfico del centro", "El centro tiene más tráfico", 70, 40, CambioOpinion.Causa.EVIDENCIA, Optional.empty());

        ResultadoDiarioRazonamiento d = registro.diario(YO, new EjecutorDiarioRazonamiento.Config(List.of("F2", "F4"), true, 1)).orElseThrow();

        assertThat(d.semanas()).singleElement().satisfies(s -> {
            assertThat(s.titulo()).isEqualTo("Semana del 5 al 11 de octubre de 2026");
            assertThat(s.resumen()).isEqualTo("2 ejecuciones, 1 expediente, 2 cambios de opinión.");
            assertThat(s.registros()).extracting(ResultadoDiarioRazonamiento.Registro::tecnica).containsExactly("T22", "T08");
        });
        assertThat(registro.diario(UUID.randomUUID(), new EjecutorDiarioRazonamiento.Config(List.of("F2"), true, 4))).isEmpty();
    }
}
