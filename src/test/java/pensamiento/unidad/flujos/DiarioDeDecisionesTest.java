package pensamiento.unidad.flujos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import pensamiento.catalogo.MapeadorJson;
import pensamiento.expediente.GuardadoDeEjecuciones;
import pensamiento.flujos.DiarioDeDecisiones;
import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.Expediente;
import pensamiento.nucleo.Prediccion;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.TipoPendiente;
import pensamiento.nucleo.Uuid7;
import pensamiento.tecnicas.f5.EjecutorDiarioDecisiones;
import pensamiento.tecnicas.f5.ResultadoDiario;
import pensamiento.testutil.builders.Contextos;
import pensamiento.testutil.fakes.FakeReloj;
import pensamiento.testutil.fakes.FakeRepositorioArgumentos;
import pensamiento.testutil.fakes.FakeRepositorioEjecucion;
import pensamiento.testutil.fakes.FakeRepositorioExpediente;
import pensamiento.testutil.fakes.FakeRepositorioPredicciones;

/**
 * El flujo D de punta a punta con Fakes y el reloj avanzable (docs/ejemplos/T32.md, ejemplo 1): la dueña ya tiene
 * cuatro predicciones resueltas (Brier 0,13); registra "Abrir en la terminal" con 70% y revisión el 15 de abril de 2027;
 * el 14 todavía no vence, el 15 sí; al revisarla como cumplida el Brier pasa a 0,12, el pendiente se cierra y un segundo
 * intento no cambia nada.
 */
class DiarioDeDecisionesTest {

    private static final UUID DUENA = Contextos.DUENA_DE_LA_PANADERIA;

    private final FakeReloj reloj = new FakeReloj();
    private final FakeRepositorioEjecucion ejecuciones = new FakeRepositorioEjecucion();
    private final FakeRepositorioPredicciones predicciones = new FakeRepositorioPredicciones(ejecuciones);
    private final FakeRepositorioExpediente expedientes = new FakeRepositorioExpediente();
    private final GuardadoDeEjecuciones guardado = new GuardadoDeEjecuciones(ejecuciones, new FakeRepositorioArgumentos(), predicciones);
    private final DiarioDeDecisiones diario = new DiarioDeDecisiones(predicciones, ejecuciones, expedientes, reloj);
    private final EjecutorDiarioDecisiones t32 = new EjecutorDiarioDecisiones();
    private final AtomicLong secuencia = new AtomicLong(1);

    private Contexto contexto() {
        return new Contexto(DUENA, Contextos.INSTITUCION, Optional.empty(), reloj, Optional.empty(),
                () -> new UUID(0x0000000000007000L, 0x8000000000000000L | secuencia.getAndIncrement()));
    }

    /** Registra una decisión con T32 como lo hace "Guardar en historial" y devuelve su predicción. */
    private Prediccion registrar(String decision, String prediccion, int confianza, LocalDate revision, Optional<UUID> expediente) {
        var entrada = new EjecutorDiarioDecisiones.Entrada(decision, "Contexto de prueba.", "Esperar un año.", prediccion, confianza,
                "Tres meses seguidos por debajo de 200 panes por día.", revision.toString());
        Resultado<ResultadoDiario> r = t32.ejecutar(new EjecutorDiarioDecisiones.Config(7), entrada, contexto());
        assertThat(r.bloqueoGuardado()).isEmpty();
        Ejecucion e = new Ejecucion(Uuid7.en(reloj.ahora()), DUENA, Contextos.INSTITUCION, EjecutorDiarioDecisiones.ID, 1, expediente,
                pensamiento.nucleo.Json.VACIO, pensamiento.nucleo.Json.VACIO, MapeadorJson.escribir(r.valor()), r.resumen(), Optional.empty(),
                "clave-" + secuencia.get(), reloj.ahora());
        guardado.guardar(e, r);
        return predicciones.deEjecucion(DUENA, e.id()).getFirst();
    }

    private void historialDeLaPanaderia() {
        LocalDate dentroDeUnMes = reloj.hoy().plusDays(30);
        int[] confianzas = {80, 60, 90, 70};
        boolean[] cumplidas = {true, false, true, true};
        for (int i = 0; i < 4; i++) {
            Prediccion p = registrar("Decisión anterior " + (i + 1), "Predicción anterior " + (i + 1), confianzas[i], dentroDeUnMes, Optional.empty());
            diario.resolver(DUENA, p.id(), cumplidas[i]);
        }
    }

    @Test
    void la_sucursal_de_la_terminal_vence_el_15_de_abril_y_al_revisarla_el_brier_pasa_de_0_13_a_0_12() {
        historialDeLaPanaderia();
        assertThat(diario.tablero(DUENA).calibracion().puntaje()).isEqualTo("0,13");

        Prediccion terminal = registrar("Abrir en la terminal, no en el centro.", "La sucursal de la terminal cubre sus costos en 6 meses.", 70,
                LocalDate.of(2027, 4, 15), Optional.empty());
        DiarioDeDecisiones.Tablero hoy = diario.tablero(DUENA);
        assertThat(hoy.porRevisar()).isEmpty();
        assertThat(hoy.abiertas()).singleElement().satisfies(d -> {
            assertThat(d.decision()).isEqualTo("Abrir en la terminal, no en el centro.");
            assertThat(d.revisarEl()).isEqualTo("15 de abril de 2027");
            assertThat(d.historial()).contains("Tu historial entre 70 y 79%: se cumple el 100% (1 resuelta, provisional).");
        });

        reloj.avanzar(Duration.between(reloj.ahora(), java.time.Instant.parse("2027-04-14T15:00:00Z")));
        assertThat(diario.porRevisar(DUENA)).isEmpty();
        reloj.avanzar(Duration.ofDays(1));
        assertThat(diario.porRevisar(DUENA)).extracting(d -> d.prediccion().id()).containsExactly(terminal.id());
        assertThat(diario.tablero(DUENA).porRevisar()).hasSize(1);

        Prediccion resuelta = diario.resolver(DUENA, terminal.id(), true);

        assertThat(resuelta.estado()).isEqualTo(Prediccion.Estado.ACIERTO);
        DiarioDeDecisiones.Tablero despues = diario.tablero(DUENA);
        assertThat(despues.calibracion().puntaje()).isEqualTo("0,12");
        assertThat(despues.porRevisar()).isEmpty();
        assertThat(despues.resueltas().getFirst().prediccion().id()).isEqualTo(terminal.id());
        assertThat(ejecuciones.pendientes(DUENA)).noneMatch(p -> p.pendiente().tipo() == TipoPendiente.REVISION
                && p.pendiente().objetoId().equals(Optional.of(terminal.afirmacionId())));
    }

    @Test
    void una_prediccion_resuelta_no_se_puede_volver_a_revisar_y_el_brier_no_cambia() {
        Prediccion p = registrar("Llevar la propuesta de cámaras a la asamblea.", "La asamblea aprueba las cámaras.", 95, reloj.hoy().plusDays(7),
                Optional.empty());
        diario.resolver(DUENA, p.id(), false);
        String brier = diario.tablero(DUENA).calibracion().puntaje();

        assertThatThrownBy(() -> diario.resolver(DUENA, p.id(), true)).isInstanceOf(Prediccion.YaResuelta.class)
                .hasMessage("Esta predicción ya está resuelta: no se puede modificar.");
        assertThat(diario.tablero(DUENA).calibracion().puntaje()).isEqualTo(brier).isEqualTo("0,90");
    }

    @Test
    void otra_persona_no_puede_revisar_la_prediccion() {
        Prediccion p = registrar("Mandar a la hija a estudiar afuera.", "Termina el primer año.", 60, reloj.hoy().plusDays(30), Optional.empty());

        assertThatThrownBy(() -> diario.resolver(UUID.randomUUID(), p.id(), true)).isInstanceOf(DiarioDeDecisiones.NoEncontrada.class);
        assertThat(predicciones.porId(DUENA, p.id())).hasValueSatisfying(x -> assertThat(x.resuelta()).isFalse());
    }

    @Test
    void el_asistente_no_deja_pasar_del_paso_0_sin_problema_definido_y_pide_la_lista_antes_del_registro() {
        Expediente x = expedientes.guardar(new Expediente(UUID.randomUUID(), DUENA, Contextos.INSTITUCION, "Decisión: Abrir la segunda sucursal",
                Optional.empty(), Expediente.Estado.ABIERTO, reloj.ahora()));
        DiarioDeDecisiones.Asistente vacio = diario.asistente(DUENA, x.id()).orElseThrow();
        assertThat(vacio.titulo()).isEqualTo("Abrir la segunda sucursal");
        assertThat(vacio.pasoHabilitado(1)).isFalse();
        assertThat(vacio.tecnicaHabilitada(DiarioDeDecisiones.Asistente.T29)).isFalse();
        assertThat(diario.tablero(DUENA).enPreparacion()).extracting(Expediente::id).containsExactly(x.id());

        guardarEnExpediente(x.id(), DiarioDeDecisiones.Asistente.T40);
        guardarEnExpediente(x.id(), DiarioDeDecisiones.Asistente.T41);
        DiarioDeDecisiones.Asistente definido = diario.asistente(DUENA, x.id()).orElseThrow();
        assertThat(definido.pasoHabilitado(4)).isTrue();
        assertThat(definido.tecnicaHabilitada(DiarioDeDecisiones.Asistente.T31)).isTrue();
        assertThat(definido.tecnicaHabilitada(DiarioDeDecisiones.Asistente.T32)).isFalse();

        guardarEnExpediente(x.id(), DiarioDeDecisiones.Asistente.T16);
        assertThat(diario.asistente(DUENA, x.id()).orElseThrow().tecnicaHabilitada(DiarioDeDecisiones.Asistente.T32)).isTrue();
        assertThat(diario.asistente(UUID.randomUUID(), x.id())).isEmpty();
    }

    private void guardarEnExpediente(UUID expediente, pensamiento.nucleo.IdTecnica t) {
        ejecuciones.guardar(new Ejecucion(UUID.randomUUID(), DUENA, Contextos.INSTITUCION, t, 1, Optional.of(expediente), pensamiento.nucleo.Json.VACIO,
                pensamiento.nucleo.Json.VACIO, pensamiento.nucleo.Json.VACIO, "Guardada.", Optional.empty(), "x-" + UUID.randomUUID(), reloj.ahora()));
    }

    @Test
    void lo_guardado_antes_llena_el_formulario_siguiente_la_reformulacion_las_ideas_y_el_ganador_de_la_matriz() {
        Expediente x = expedientes.guardar(new Expediente(UUID.randomUUID(), DUENA, Contextos.INSTITUCION, "Decisión: Robos en la cuadra",
                Optional.empty(), Expediente.Estado.ABIERTO, reloj.ahora()));
        guardarEjemplo(x.id(), new pensamiento.tecnicas.f7.EjecutorDefinicionProblema(), 0);
        guardarEjemplo(x.id(), new pensamiento.tecnicas.f7.EjecutorIshikawa(), 1);
        guardarEjemplo(x.id(), new pensamiento.tecnicas.f7.EjecutorScamper(), 0);
        guardarEjemplo(x.id(), new pensamiento.tecnicas.f5.EjecutorMatrizPonderada(), 0);
        DiarioDeDecisiones.Asistente a = diario.asistente(DUENA, x.id()).orElseThrow();

        assertThat(a.valoresIniciales(DiarioDeDecisiones.Asistente.T41, reloj.hoy()))
                .containsEntry("problema", "¿Cómo reducimos los robos nocturnos sin vigilar a los vecinos?");
        Map<String, Object> mece = a.valoresIniciales(DiarioDeDecisiones.Asistente.T42, reloj.hoy());
        assertThat(mece).containsEntry("raiz", "Robos nocturnos en la cuadra.");
        assertThat((List<?>) mece.get("nodos")).hasSize(10);
        assertThat(a.valoresIniciales(DiarioDeDecisiones.Asistente.T31, reloj.hoy()).get("opciones").toString())
                .contains("Ronda vecinal rotativa con los que pasean perros", "Quitar el seto que tapa la vista");
        Map<String, Object> registro = a.valoresIniciales(DiarioDeDecisiones.Asistente.T32, reloj.hoy());
        assertThat(registro).containsEntry("decision", "Terminal").containsEntry("alternativas", "Esperar un año · Centro")
                .containsEntry("fechaRevision", "2027-01-05");
    }

    private <C, E, R> void guardarEjemplo(UUID expediente, pensamiento.nucleo.Ejecutor<C, E, R> ejecutor, int indice) {
        var ejemplo = new pensamiento.catalogo.CatalogoJson().ejemplosDe(ejecutor.id()).get(indice);
        Resultado<R> r = ejecutor.ejecutar(MapeadorJson.leer(ejemplo.config(), ejecutor.tipos().config()),
                MapeadorJson.leer(ejemplo.datos(), ejecutor.tipos().entrada()), contexto());
        reloj.avanzar(Duration.ofSeconds(1));
        guardado.guardar(new Ejecucion(Uuid7.en(reloj.ahora()), DUENA, Contextos.INSTITUCION, ejecutor.id(), 1, Optional.of(expediente),
                ejemplo.config(), ejemplo.datos(), MapeadorJson.escribir(r.valor()), r.resumen(), Optional.empty(), "ej-" + UUID.randomUUID(),
                reloj.ahora()), r);
    }
}
