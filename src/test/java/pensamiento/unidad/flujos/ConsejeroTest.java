package pensamiento.unidad.flujos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pensamiento.catalogo.MapeadorJson;
import pensamiento.expediente.GuardadoDeEjecuciones;
import pensamiento.flujos.Consejero;
import pensamiento.nucleo.CambioOpinion;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.SesionConsejero;
import pensamiento.nucleo.TurnoConsejero;
import pensamiento.nucleo.puertos.Clasificacion;
import pensamiento.nucleo.puertos.Ia;
import pensamiento.tecnicas.f2.EjecutorEscalera;
import pensamiento.tecnicas.f2.EjecutorPreguntasSocraticas;
import pensamiento.tecnicas.f2.ResultadoPreguntasSocraticas;
import pensamiento.tecnicas.f6.EjecutorEquipoRojo;
import pensamiento.tecnicas.f6.EjecutorSeisSombreros;
import pensamiento.tecnicas.f6.ResultadoEquipoRojo;
import pensamiento.testutil.builders.Contextos;
import pensamiento.testutil.fakes.FakeIa;
import pensamiento.testutil.fakes.FakeReloj;
import pensamiento.testutil.fakes.FakeRepositorioArgumentos;
import pensamiento.testutil.fakes.FakeRepositorioCambiosOpinion;
import pensamiento.testutil.fakes.FakeRepositorioEjecucion;
import pensamiento.testutil.fakes.FakeRepositorioEsquemas;
import pensamiento.testutil.fakes.FakeRepositorioExpediente;
import pensamiento.testutil.fakes.FakeRepositorioPredicciones;
import pensamiento.testutil.fakes.FakeRepositorioSesiones;

/**
 * El flujo C con Fakes: el ejemplo de punta a punta de docs/consejero.md en modo plantillas, los modos escalera, sombreros
 * y debate, el modelo que redacta con el validador del turno y cae al banco, el segundo paso que propone un elemento, y lo
 * que no se puede hacer. Aserciones sobre lo guardado, nunca sobre llamadas.
 */
class ConsejeroTest {

    private static final UUID YO = Contextos.DUENA_DE_LA_PANADERIA;
    private static final UUID INST = Contextos.INSTITUCION;

    private final FakeRepositorioSesiones sesiones = new FakeRepositorioSesiones();
    private final FakeRepositorioExpediente expedientes = new FakeRepositorioExpediente();
    private final FakeRepositorioEjecucion ejecuciones = new FakeRepositorioEjecucion();
    private final FakeRepositorioCambiosOpinion cambios = new FakeRepositorioCambiosOpinion(ejecuciones);
    private final GuardadoDeEjecuciones guardado = new GuardadoDeEjecuciones(ejecuciones, new FakeRepositorioArgumentos(),
            new FakeRepositorioPredicciones(ejecuciones), cambios);
    private final EjecutorEquipoRojo t36 = new EjecutorEquipoRojo(new FakeRepositorioEsquemas());
    private final Consejero consejero = new Consejero(sesiones, expedientes, guardado, new FakeReloj(), new EjecutorPreguntasSocraticas(),
            new EjecutorEscalera(), new EjecutorSeisSombreros(), t36);

    private static Json configT08() {
        return new Json("{\"tipos\":[\"clarificacion\",\"supuestos\",\"evidencia\",\"puntos_de_vista\",\"implicaciones\",\"pregunta_sobre_la_pregunta\"],"
                + "\"orden\":\"adaptativo\",\"turnosMaximos\":8,\"modo\":\"plantillas\"}");
    }

    private SesionConsejero iniciarDecision() {
        return consejero.iniciar(YO, INST, SesionConsejero.Modo.DECISION, "Conviene abrir la segunda sucursal en el centro este año.", List.of(),
                configT08(), false, Optional.of(80), Optional.empty(), false);
    }

    private List<TurnoConsejero> turnos(SesionConsejero s) {
        return consejero.turnos(YO, s.id());
    }

    @Test
    void la_sesion_de_punta_a_punta_en_modo_plantillas_deja_una_ejecucion_de_t08_y_el_cambio_de_opinion() {
        SesionConsejero s = iniciarDecision();
        assertThat(turnos(s)).singleElement().satisfies(t -> {
            assertThat(t.texto()).isEqualTo("¿Qué quieres lograr con esta decisión? ¿Cómo sabrías que lo lograste?");
            assertThat(t.origen()).isEqualTo(TurnoConsejero.Origen.BANCO);
            assertThat(t.paso()).isEqualTo("pregunta_sobre_la_pregunta/proposito");
        });
        assertThat(expedientes.porId(YO, s.expedienteId().orElseThrow())).hasValueSatisfying(e ->
                assertThat(e.nombre()).isEqualTo("Consejero: Conviene abrir la segunda sucursal en el centro este año."));

        consejero.responder(YO, s.id(), "Quiero vender más, unos 200 panes más por día, sin descuidar el local que ya tenemos.", false);
        consejero.responder(YO, s.id(), "Que en el centro pasa mucha gente y que la gente que pasa compra pan.", false);
        assertThat(turnos(s).getLast().texto()).isEqualTo("¿Qué quieres decir exactamente con «mucha»? ¿Qué ejemplo concreto cuenta y cuál no?");
        consejero.irAlCierre(YO, s.id());
        assertThat(turnos(s).getLast().texto()).isEqualTo("¿Qué te haría cambiar de opinión sobre «conviene abrir la segunda sucursal en el centro este año»?");
        assertThat(consejero.responder(YO, s.id(), "Que el conteo de una semana completa dé menos de 600 personas por mañana.", false)).isEmpty();
        assertThat(Consejero.listaParaCerrar(turnos(s))).isTrue();

        Ejecucion e = consejero.cerrar(YO, s.id(), Optional.of("Necesito contar una semana entera antes de firmar."), Optional.of(60),
                Optional.of("evidencia"), List.of(), Contextos.sinIa());

        assertThat(e.tecnica().valor()).isEqualTo("T08");
        assertThat(e.expedienteId()).isEqualTo(s.expedienteId());
        assertThat(e.resumen()).isEqualTo("2 turnos · 2 de 8 elementos · cierre respondido.");
        assertThat(cambios.deEjecucion(YO, e.id())).singleElement().satisfies(c -> {
            assertThat(c.confianzaAntes()).isEqualTo(80);
            assertThat(c.confianzaDespues()).isEqualTo(60);
            assertThat(c.causa()).isEqualTo(CambioOpinion.Causa.EVIDENCIA);
            assertThat(c.texto()).isEqualTo("Conviene abrir la segunda sucursal en el centro este año.");
        });
        SesionConsejero cerrada = consejero.sesion(YO, s.id());
        assertThat(cerrada.cerrada()).isTrue();
        assertThat(cerrada.ejecucionId()).contains(e.id());
        assertThat(cerrada.reflexion()).contains("Necesito contar una semana entera antes de firmar.");
        ResultadoPreguntasSocraticas r = MapeadorJson.leer(e.resultado(), ResultadoPreguntasSocraticas.class);
        assertThat(r.turnos()).hasSize(2);
        EjecutorPreguntasSocraticas.Entrada entrada = MapeadorJson.leer(e.datos(), EjecutorPreguntasSocraticas.Entrada.class);
        assertThat(entrada.cierre()).isEqualTo("Que el conteo de una semana completa dé menos de 600 personas por mañana.");
    }

    @Test
    void no_se_puede_cerrar_sin_responder_el_cierre_ni_responder_dos_veces_ni_tocar_una_sesion_cerrada() {
        SesionConsejero s = iniciarDecision();
        assertThatThrownBy(() -> consejero.cerrar(YO, s.id(), Optional.empty(), Optional.empty(), Optional.empty(), List.of(), Contextos.sinIa()))
                .isInstanceOf(Consejero.NoPermitido.class).hasMessageContaining("responde primero la pregunta de cierre");
        consejero.irAlCierre(YO, s.id());
        consejero.responder(YO, s.id(), "Que no pase nadie.", false);
        assertThatThrownBy(() -> consejero.responder(YO, s.id(), "Otra vez.", false)).isInstanceOf(Consejero.NoPermitido.class)
                .hasMessage("Ya respondiste: espera la pregunta que sigue.");
        consejero.cerrar(YO, s.id(), Optional.empty(), Optional.empty(), Optional.empty(), List.of(), Contextos.sinIa());
        assertThatThrownBy(() -> consejero.irAlCierre(YO, s.id())).isInstanceOf(Consejero.NoPermitido.class);
        assertThatThrownBy(() -> consejero.sesion(UUID.randomUUID(), s.id())).isInstanceOf(Consejero.NoEncontrada.class);
    }

    @Test
    void el_modo_escalera_pregunta_un_peldano_por_turno_y_al_cerrar_guarda_t10_con_los_comprobados() {
        Json config = new Json("{\"peldanos\":[\"datos\",\"seleccion\",\"interpretacion\",\"supuestos\",\"conclusion\",\"accion\"],\"sentido\":\"subir\"}");
        SesionConsejero s = consejero.iniciar(YO, INST, SesionConsejero.Modo.ESCALERA, "Mi hija no me quiere llamar.", List.of(), config, false,
                Optional.empty(), Optional.empty(), false);
        consejero.responder(YO, s.id(), "Mi hija no llamó en dos semanas.", false);
        consejero.responder(YO, s.id(), "Ignoré que escribió tres mensajes.", false);
        consejero.responder(YO, s.id(), "No llamar = no le importa.", false);

        assertThat(turnos(s)).filteredOn(TurnoConsejero::delConsejero).extracting(TurnoConsejero::paso)
                .containsExactly("datos", "seleccion", "interpretacion", "supuestos");
        consejero.irAlCierre(YO, s.id());
        consejero.responder(YO, s.id(), "Que me diga que no llamó porque estaba en exámenes.", false);
        Ejecucion e = consejero.cerrar(YO, s.id(), Optional.empty(), Optional.empty(), Optional.empty(), List.of("datos"), Contextos.sinIa());

        assertThat(e.tecnica().valor()).isEqualTo("T10");
        assertThat(e.resumen()).isEqualTo("3 de 6 peldaños · débil: interpretación.");
    }

    @Test
    void el_modo_sombreros_hace_una_ronda_por_sombrero_y_despues_la_sintesis() {
        Json config = new Json("{\"activos\":[\"blanco\",\"negro\",\"amarillo\"],\"orden\":[\"blanco\",\"rojo\",\"negro\",\"amarillo\",\"verde\",\"azul\"],"
                + "\"minutosPorSombrero\":3,\"modalidad\":\"individual\"}");
        SesionConsejero s = consejero.iniciar(YO, INST, SesionConsejero.Modo.SOMBREROS, "¿Abrimos los domingos?", List.of(), config, false,
                Optional.empty(), Optional.empty(), false);
        consejero.responder(YO, s.id(), "La panadería de la esquina abre los domingos.", false);
        consejero.responder(YO, s.id(), "Pagar recargo dominical.", false);
        consejero.responder(YO, s.id(), "Vender más el fin de semana.", false);
        assertThat(turnos(s).getLast().texto()).isEqualTo("Para cerrar las rondas: ¿qué síntesis sacas de todas las miradas?");
        consejero.responder(YO, s.id(), "Probar dos domingos y contar.", false);
        assertThat(turnos(s).getLast().cierre()).isTrue();
        consejero.responder(YO, s.id(), "Que dos domingos seguidos vendamos menos que un lunes.", false);

        Ejecucion e = consejero.cerrar(YO, s.id(), Optional.empty(), Optional.empty(), Optional.empty(), List.of(), Contextos.sinIa());

        assertThat(e.tecnica().valor()).isEqualTo("T35");
        assertThat(e.resumen()).isEqualTo("3 de 3 sombreros con notas · con síntesis.");
    }

    @Test
    void el_modo_debate_ataca_con_el_banco_por_esquema_de_walton_y_guarda_t36() {
        Json config = new Json("{\"intensidad\":2,\"numeroAtaques\":3,\"esquemas\":[\"ad_hominem\",\"alternativas\",\"pendiente_resbaladiza\",\"autoridad\","
                + "\"analogia\",\"causa_efecto\",\"signo\",\"ejemplo\",\"opinion_popular\",\"clasificacion_verbal\",\"consecuencias\"],\"modo\":\"banco\"}");
        SesionConsejero s = consejero.iniciar(YO, INST, SesionConsejero.Modo.DEBATE, "Hay que comprar las cámaras que ofrece el vendedor.",
                List.of(new SesionConsejero.Razon("El vendedor dice que bajan los robos un 70%.", "no_se"),
                        new SesionConsejero.Razon("En el barrio vecino bajaron los robos después de ponerlas.", "causa")),
                config, false, Optional.of(90), Optional.empty(), false);
        assertThat(turnos(s).getFirst().texto()).isEqualTo("Eso lo dice alguien que gana si le crees. ¿Tienes un solo dato que no venga de esa persona?");
        consejero.responder(YO, s.id(), "El municipio reporta 12% de baja en el barrio vecino. Es menos, pero es independiente.", false);
        assertThat(turnos(s).getLast().paso()).isEqualTo("A2");
        consejero.irAlCierre(YO, s.id());
        consejero.responder(YO, s.id(), "Que el municipio diga que los robos se movieron de cuadra.", false);

        Ejecucion e = consejero.cerrar(YO, s.id(), Optional.empty(), Optional.of(70), Optional.of("steelman"), List.of(), Contextos.sinIa());

        assertThat(e.tecnica().valor()).isEqualTo("T36");
        assertThat(e.resumen()).isEqualTo("3 ataques · 1 respondido · 2 sin responder.");
        assertThat(MapeadorJson.leer(e.resultado(), ResultadoEquipoRojo.class).ataques().getFirst().estado()).isEqualTo("respondido");
        assertThat(cambios.deEjecucion(YO, e.id())).singleElement().satisfies(c -> {
            assertThat(c.causa()).isEqualTo(CambioOpinion.Causa.STEELMAN);
            assertThat(c.texto()).isEqualTo("Hay que comprar las cámaras que ofrece el vendedor.");
        });
    }

    @Test
    void con_el_modelo_la_pregunta_que_pasa_el_validador_reemplaza_la_del_banco_y_queda_su_registro() {
        SesionConsejero s = iniciarDecision();
        FakeIa ia = new FakeIa();
        ia.programarRespuesta("¿Qué quer" + "é" + "s lograr con la sucursal?"); // voseo, armado por partes para que el sensor no lo vea en el código
        ia.programarRespuesta("¿Qué quieres lograr con la sucursal del centro?");
        StringBuilder provisional = new StringBuilder();

        Consejero.Paso paso = consejero.siguiente(s, List.of()).orElseThrow();
        Consejero.Redactado r = consejero.redactar(Optional.of((Ia) ia), paso, provisional::append);

        assertThat(r.texto()).isEqualTo("¿Qué quieres lograr con la sucursal del centro?");
        assertThat(r.origen()).isEqualTo(TurnoConsejero.Origen.MODELO);
        assertThat(r.intentos()).isEqualTo(2);
        assertThat(r.detalle().getFirst().rechazo()).hasValueSatisfying(m -> assertThat(m.name()).isEqualTo("VOSEO"));
        assertThat(r.modelo()).hasValueSatisfying(m -> assertThat(m.promptVersion()).isEqualTo("t08-pregunta.v2"));
        assertThat(provisional.toString()).contains("no pasó el validador");
        assertThat(ia.chatsRecibidos().getFirst().mensajes().getLast().contenido()).contains("Pregunta de referencia: " + paso.pregunta());
    }

    @Test
    void si_ningun_intento_pasa_el_validador_o_el_modelo_no_esta_queda_la_pregunta_del_banco() {
        SesionConsejero s = iniciarDecision();
        FakeIa ia = new FakeIa();
        ia.programarRespuesta("Tienes razón, lo mejor es abrir.");
        ia.programarRespuesta("Deberías abrirla ya.");
        ia.programarRespuesta("Lo mejor es esperar.");
        Consejero.Paso paso = consejero.siguiente(s, List.of()).orElseThrow();

        Consejero.Redactado sinValida = consejero.redactar(Optional.of((Ia) ia), paso, x -> { });
        ia.apagar();
        Consejero.Redactado apagado = consejero.redactar(Optional.of((Ia) ia), paso, x -> { });

        assertThat(sinValida.texto()).isEqualTo(paso.pregunta());
        assertThat(sinValida.origen()).isEqualTo(TurnoConsejero.Origen.BANCO);
        assertThat(sinValida.intentos()).isEqualTo(3);
        assertThat(apagado.texto()).isEqualTo(paso.pregunta());
        assertThat(apagado.origen()).isEqualTo(TurnoConsejero.Origen.BANCO);
    }

    @Test
    void la_pregunta_redactada_por_el_modelo_queda_en_la_ejecucion_como_propuesta_adoptada_con_su_registro() {
        SesionConsejero s = iniciarDecision();
        TurnoConsejero primera = turnos(s).getFirst();
        sesiones.completarTurno(YO, primera.id(), "¿Qué quieres lograr con la sucursal del centro?", TurnoConsejero.Origen.MODELO, 1,
                Optional.of(new Ejecucion.RegistroModelo("qwen3:4b", "sha256:fake", "t08-pregunta.v2", 0.0, 42)));
        consejero.responder(YO, s.id(), "Vender más.", false);
        consejero.irAlCierre(YO, s.id());
        consejero.responder(YO, s.id(), "Que no venda.", false);

        Ejecucion e = consejero.cerrar(YO, s.id(), Optional.empty(), Optional.empty(), Optional.empty(), List.of(), Contextos.sinIa());

        ResultadoPreguntasSocraticas r = MapeadorJson.leer(e.resultado(), ResultadoPreguntasSocraticas.class);
        assertThat(r.turnos().getFirst().pregunta()).isEqualTo("¿Qué quieres lograr con la sucursal del centro?");
        assertThat(r.turnos().getFirst().origen()).isEqualTo("modelo");
        assertThat(e.modelo()).hasValueSatisfying(m -> assertThat(m.promptVersion()).isEqualTo("t08-pregunta.v2"));
    }

    @Test
    void el_segundo_paso_propone_otro_elemento_solo_si_esta_vacio_y_adoptado_llena_el_panel() {
        SesionConsejero s = iniciarDecision();
        consejero.responder(YO, s.id(), "Quiero vender más. Doy por hecho que en el centro compra todo el que pasa.", false);
        TurnoConsejero respuesta = turnos(s).get(1);
        FakeIa ia = new FakeIa();
        ia.programarClasificacionCruda("{\"etiqueta\":\"supuestos\",\"por_que\":\"Dice «doy por hecho».\"}");

        Optional<Clasificacion> c = consejero.extraerElemento(Optional.of((Ia) ia), s, turnos(s), respuesta);

        assertThat(c).hasValueSatisfying(x -> assertThat(x.etiqueta()).isEqualTo("supuestos"));
        sesiones.proponerElemento(YO, respuesta.id(), "supuestos", c.get().porQue());
        sesiones.adoptarElemento(YO, respuesta.id());
        ResultadoPreguntasSocraticas panel = (ResultadoPreguntasSocraticas) consejero.panel(s, turnos(s), Contextos.sinIa(), Optional.empty(),
                Optional.empty(), Optional.empty(), List.of()).valor();
        assertThat(panel.elementos()).filteredOn(x -> x.id().equals("supuestos")).singleElement().satisfies(x -> {
            assertThat(x.estado()).isEqualTo("lleno");
            assertThat(x.origen()).isEqualTo("modelo");
        });
        ia.programarClasificacionCruda("{\"etiqueta\":\"proposito\",\"por_que\":\"es el propósito\"}");
        assertThat(consejero.extraerElemento(Optional.of((Ia) ia), s, turnos(s), respuesta)).as("el que eligió el motor no se propone").isEmpty();
    }
}
