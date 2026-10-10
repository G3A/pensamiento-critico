package pensamiento.unidad.expediente;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import pensamiento.argdown.ParserArgdown;
import pensamiento.catalogo.CatalogoJson;
import pensamiento.catalogo.MapeadorJson;
import pensamiento.expediente.GuardadoDeEjecuciones;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.Ejemplo;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.Uuid7;
import pensamiento.tecnicas.f1.EjecutorMapa;
import pensamiento.tecnicas.f1.ResultadoMapa;
import pensamiento.testutil.builders.Contextos;
import pensamiento.testutil.fakes.FakeReloj;
import pensamiento.testutil.fakes.FakeRepositorioArgumentos;
import pensamiento.testutil.fakes.FakeRepositorioEjecucion;

/** Guardar una ejecución del mapa deja ejecución, afirmaciones, pendientes y argumentos; el doble clic no duplica nada. */
class GuardadoDeEjecucionesTest {

    private final FakeRepositorioEjecucion ejecuciones = new FakeRepositorioEjecucion();
    private final FakeRepositorioArgumentos argumentos = new FakeRepositorioArgumentos();
    private final FakeReloj reloj = new FakeReloj();
    private final pensamiento.testutil.fakes.FakeRepositorioPredicciones predicciones = new pensamiento.testutil.fakes.FakeRepositorioPredicciones(ejecuciones);
    private final pensamiento.testutil.fakes.FakeRepositorioEvidencias evidencias =
            new pensamiento.testutil.fakes.FakeRepositorioEvidencias(ejecuciones, new pensamiento.testutil.fakes.FakeBiblioteca());
    private final GuardadoDeEjecuciones guardado = new GuardadoDeEjecuciones(ejecuciones, argumentos, predicciones,
            new pensamiento.testutil.fakes.FakeRepositorioCambiosOpinion(ejecuciones), evidencias, new pensamiento.testutil.fakes.FakeRepositorioConfiguracion());

    @Test
    void guardar_t22_deja_sus_fuentes_y_evidencias_en_sus_tablas_y_el_doble_clic_no_las_repite() {
        Ejemplo trafico = new CatalogoJson().ejemplosDe(pensamiento.tecnicas.f4.EjecutorTriangulacion.ID).getFirst();
        pensamiento.tecnicas.f4.EjecutorTriangulacion t22 = new pensamiento.tecnicas.f4.EjecutorTriangulacion();
        Resultado<pensamiento.tecnicas.f4.ResultadoTriangulacion> r = t22.ejecutar(
                MapeadorJson.leer(trafico.config(), pensamiento.tecnicas.f4.EjecutorTriangulacion.Config.class),
                MapeadorJson.leer(trafico.datos(), pensamiento.tecnicas.f4.EjecutorTriangulacion.Entrada.class), Contextos.sinIa());
        Ejecucion nueva = new Ejecucion(Uuid7.en(reloj.ahora()), Contextos.DUENA_DE_LA_PANADERIA, Contextos.INSTITUCION, t22.id(), 2,
                Optional.empty(), pensamiento.nucleo.Json.VACIO, pensamiento.nucleo.Json.VACIO, MapeadorJson.escribir(r.valor()), r.resumen(),
                Optional.empty(), "clave-trafico", reloj.ahora());

        guardado.guardar(nueva, r);
        guardado.guardar(nueva, r);

        java.util.UUID afirmacion = r.afirmaciones().getFirst().afirmacionId();
        assertThat(evidencias.deAfirmacion(Contextos.DUENA_DE_LA_PANADERIA, afirmacion)).extracting(e -> e.fuente().titulo())
                .containsExactly("Conteo peatonal del municipio", "Conteo propio de tres sábados", "Informe de la cámara de comercio");
    }

    /** T01, ejemplo 3 de docs/ejemplos/T01.md: cuatro afirmaciones, tres argumentos y una objeción sin responder. */
    private Resultado<ResultadoMapa> camarasDelBarrio() {
        Ejemplo camaras = new CatalogoJson().ejemplosDe(EjecutorMapa.ID).get(2);
        return new EjecutorMapa(new ParserArgdown()).ejecutar(MapeadorJson.leer(camaras.config(), EjecutorMapa.Config.class),
                MapeadorJson.leer(camaras.datos(), EjecutorMapa.Entrada.class), Contextos.sinIa());
    }

    private Ejecucion ejecucion(Resultado<ResultadoMapa> r, String clave) {
        return new Ejecucion(Uuid7.en(reloj.ahora()), Contextos.DUENA_DE_LA_PANADERIA, Contextos.INSTITUCION, EjecutorMapa.ID, 1, Optional.empty(),
                pensamiento.nucleo.Json.VACIO, pensamiento.nucleo.Json.VACIO, MapeadorJson.escribir(r.valor()), r.resumen(), Optional.empty(), clave,
                reloj.ahora());
    }

    @Test
    void guardar_el_mapa_deja_sus_afirmaciones_su_pendiente_y_sus_tres_argumentos_ligados_a_la_ejecucion() {
        Resultado<ResultadoMapa> r = camarasDelBarrio();

        Ejecucion guardada = guardado.guardar(ejecucion(r, "clave-camaras"), r);

        assertThat(ejecuciones.afirmacionesDe(Contextos.DUENA_DE_LA_PANADERIA, guardada.id())).hasSize(4);
        assertThat(ejecuciones.pendientes(Contextos.DUENA_DE_LA_PANADERIA)).extracting(p -> p.pendiente().descripcion())
                .containsExactly("Responder la objeción: Las cámaras cuestan lo que la junta recauda en un año.");
        assertThat(argumentos.deEjecucion(Contextos.DUENA_DE_LA_PANADERIA, guardada.id())).hasSize(3);
    }

    @Test
    void el_doble_clic_devuelve_la_primera_ejecucion_y_no_guarda_argumentos_otra_vez() {
        Resultado<ResultadoMapa> primera = camarasDelBarrio();
        Resultado<ResultadoMapa> segunda = camarasDelBarrio();

        Ejecucion a = guardado.guardar(ejecucion(primera, "doble-clic"), primera);
        reloj.avanzar(java.time.Duration.ofSeconds(1));
        Ejecucion b = guardado.guardar(ejecucion(segunda, "doble-clic"), segunda);

        assertThat(b.id()).isEqualTo(a.id());
        assertThat(argumentos.total()).isEqualTo(3);
    }

    /** T32, ejemplo 1 de docs/ejemplos/T32.md: la sucursal de la terminal, 70% y revisión el 15 de abril de 2027. */
    private Resultado<pensamiento.tecnicas.f5.ResultadoDiario> sucursalDeLaTerminal() {
        Ejemplo terminal = new CatalogoJson().ejemplosDe(pensamiento.tecnicas.f5.EjecutorDiarioDecisiones.ID).getFirst();
        return new pensamiento.tecnicas.f5.EjecutorDiarioDecisiones().ejecutar(
                MapeadorJson.leer(terminal.config(), pensamiento.tecnicas.f5.EjecutorDiarioDecisiones.Config.class),
                MapeadorJson.leer(terminal.datos(), pensamiento.tecnicas.f5.EjecutorDiarioDecisiones.Entrada.class), Contextos.sinIa());
    }

    private Ejecucion ejecucionDiario(Resultado<pensamiento.tecnicas.f5.ResultadoDiario> r, String clave) {
        return new Ejecucion(Uuid7.en(reloj.ahora()), Contextos.DUENA_DE_LA_PANADERIA, Contextos.INSTITUCION, pensamiento.tecnicas.f5.EjecutorDiarioDecisiones.ID,
                1, Optional.empty(), pensamiento.nucleo.Json.VACIO, pensamiento.nucleo.Json.VACIO, MapeadorJson.escribir(r.valor()), r.resumen(),
                Optional.empty(), clave, reloj.ahora());
    }

    @Test
    void guardar_una_decision_deja_su_prediccion_pendiente_con_la_confianza_declarada_y_el_doble_clic_no_la_duplica() {
        Resultado<pensamiento.tecnicas.f5.ResultadoDiario> r = sucursalDeLaTerminal();

        Ejecucion guardada = guardado.guardar(ejecucionDiario(r, "terminal"), r);
        guardado.guardar(ejecucionDiario(sucursalDeLaTerminal(), "terminal"), sucursalDeLaTerminal());

        assertThat(predicciones.deEjecucion(Contextos.DUENA_DE_LA_PANADERIA, guardada.id())).singleElement().satisfies(p -> {
            assertThat(p.texto()).isEqualTo("La sucursal de la terminal cubre sus costos en 6 meses.");
            assertThat(p.confianza()).isEqualTo(70);
            assertThat(p.fechaRevision()).isEqualTo(java.time.LocalDate.of(2027, 4, 15));
            assertThat(p.resuelta()).isFalse();
        });
        assertThat(predicciones.deUsuario(Contextos.DUENA_DE_LA_PANADERIA)).hasSize(1);
    }
}
