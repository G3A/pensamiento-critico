package pensamiento.unidad.flujos;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pensamiento.argdown.ParserArgdown;
import pensamiento.catalogo.CatalogoJson;
import pensamiento.catalogo.MapeadorJson;
import pensamiento.expediente.GuardadoDeEjecuciones;
import pensamiento.flujos.FichaDeVerificacion;
import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.Documento;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.Ejemplo;
import pensamiento.nucleo.EstadoAfirmacion;
import pensamiento.nucleo.EstandarPrueba;
import pensamiento.nucleo.Evidencia;
import pensamiento.nucleo.EvidenciaGuardada;
import pensamiento.nucleo.Fragmento;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.PendienteGuardado;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.TipoPendiente;
import pensamiento.nucleo.Uuid7;
import pensamiento.nucleo.puertos.Biblioteca;
import pensamiento.tecnicas.f1.EjecutorMapa;
import pensamiento.tecnicas.f2.EjecutorFalsacion;
import pensamiento.tecnicas.f4.EjecutorCraap;
import pensamiento.tecnicas.f4.EjecutorTriangulacion;
import pensamiento.testutil.builders.Contextos;
import pensamiento.testutil.fakes.FakeBiblioteca;
import pensamiento.testutil.fakes.FakeReloj;
import pensamiento.testutil.fakes.FakeRepositorioArgumentos;
import pensamiento.testutil.fakes.FakeRepositorioCambiosOpinion;
import pensamiento.testutil.fakes.FakeRepositorioEjecucion;
import pensamiento.testutil.fakes.FakeRepositorioEsquemas;
import pensamiento.testutil.fakes.FakeRepositorioEvidencias;
import pensamiento.testutil.fakes.FakeRepositorioPredicciones;
import pensamiento.testutil.fakes.FakeRepositorioVerificaciones;

/**
 * La ficha de verificación de punta a punta con Fakes, con el ejemplo de docs/verificacion.md como oráculo (hoy, 2026-10-07):
 * el tráfico del centro pasa de en verificación a verificada con dos grupos, R04 se vuelve aceptable bajo preponderancia,
 * una fuente fuerte en contra la deja disputada con un cambio de opinión, y desde un pendiente de T11 el veredicto cierra
 * ese pendiente.
 */
class FichaDeVerificacionTest {

    private static final UUID YO = Contextos.DUENA_DE_LA_PANADERIA;
    private static final UUID INST = Contextos.INSTITUCION;
    private static final String MAPA = """
            [Sucursal]: Conviene abrir la segunda sucursal en el centro.
              + <Más ventas>: Con más gente pasando, se vende más. {peso: 3}
                + [Tráfico]: El centro tiene más tráfico peatonal que el barrio.
                + [Conversión]: Más tráfico da más ventas. #asumible
              - [Personal]: Falta personal para atender dos locales. {peso: 2}""";

    private final FakeReloj reloj = new FakeReloj();
    private final FakeRepositorioEjecucion ejecuciones = new FakeRepositorioEjecucion();
    private final FakeRepositorioArgumentos argumentos = new FakeRepositorioArgumentos();
    private final FakeBiblioteca biblioteca = new FakeBiblioteca();
    private final FakeRepositorioEvidencias evidencias = new FakeRepositorioEvidencias(ejecuciones, biblioteca);
    private final FakeRepositorioVerificaciones verificaciones = new FakeRepositorioVerificaciones(ejecuciones);
    private final FakeRepositorioCambiosOpinion cambios = new FakeRepositorioCambiosOpinion(ejecuciones);
    private final GuardadoDeEjecuciones guardado = new GuardadoDeEjecuciones(ejecuciones, argumentos, new FakeRepositorioPredicciones(ejecuciones), cambios,
            evidencias);
    private final FichaDeVerificacion fichas = new FichaDeVerificacion(verificaciones, evidencias, argumentos, ejecuciones, new FakeRepositorioEsquemas(),
            biblioteca, guardado, reloj);
    private final FichaDeVerificacion.Configuracion config = new FichaDeVerificacion.Configuracion(
            new EjecutorTriangulacion.Config(2, EjecutorTriangulacion.Modo.PLANTILLAS), EjecutorCraap.Config.porDefecto());

    private <R> Ejecucion guardar(pensamiento.nucleo.IdTecnica tecnica, Resultado<R> r) {
        Ejecucion e = new Ejecucion(Uuid7.en(reloj.ahora()), YO, INST, tecnica, r.versionEsquema(), Optional.empty(), Json.VACIO, Json.VACIO,
                MapeadorJson.escribir(r.valor()), r.resumen(), Optional.empty(), "clave-" + UUID.randomUUID(), reloj.ahora());
        return guardado.guardar(e, r);
    }

    /** El mapa de la sucursal guardado con T01; devuelve la afirmación de la premisa Tráfico. */
    private UUID trafico() {
        Resultado<?> r = new EjecutorMapa(new ParserArgdown()).ejecutar(new EjecutorMapa.Config(
                pensamiento.tecnicas.f1.ResultadoMapa.Direccion.ARRIBA_ABAJO, true, false, EstandarPrueba.PREPONDERANCIA), new EjecutorMapa.Entrada(MAPA),
                Contextos.sinIa());
        guardar(EjecutorMapa.ID, r);
        return r.afirmaciones().stream().filter(a -> a.texto().equals("El centro tiene más tráfico peatonal que el barrio.")).findFirst().orElseThrow()
                .afirmacionId();
    }

    /** El conteo del municipio en la biblioteca, indexado, con el fragmento de la página 2. */
    private UUID fragmentoDelConteo() {
        Documento d = biblioteca.crear(YO, INST, new Biblioteca.NuevoDocumento(UUID.randomUUID(), "conteo-peatonal-municipio-2025.pdf",
                Documento.Tipo.PDF, "hash-conteo", "%PDF-1.4".getBytes(StandardCharsets.ISO_8859_1)));
        biblioteca.indexar(YO, d.id(), List.of(new Fragmento.Nuevo(0, "Conteo peatonal del municipio, marzo de 2025.", Optional.of(1)),
                new Fragmento.Nuevo(1, "En el centro pasan en promedio 1.200 personas por hora; en el barrio, 300.", Optional.of(2)),
                new Fragmento.Nuevo(2, "En la esquina de la plaza pasan en promedio 1.150 personas entre las 7 y las 10 de la mañana.", Optional.of(3))),
                Optional.of(3));
        return biblioteca.fragmentos(YO, d.id()).get(1).id();
    }

    private static FichaDeVerificacion.BorradorFuente fuente(String titulo, String fecha, String tipo, String diseno, String grupo, boolean independiente,
                                                             boolean original, List<Integer> craap, String postura, String pasaje, String fragmento,
                                                             String etiquetadaPor, String propuesta) {
        return new FichaDeVerificacion.BorradorFuente(titulo, null, fecha, tipo, diseno, grupo, independiente, original,
                craap.isEmpty() ? null : craap.get(0), craap.isEmpty() ? null : craap.get(1), craap.isEmpty() ? null : craap.get(2),
                craap.isEmpty() ? null : craap.get(3), craap.isEmpty() ? null : craap.get(4), "", "", "", postura, pasaje, fragmento, etiquetadaPor, propuesta);
    }

    private FichaDeVerificacion.Ficha ficha(UUID afirmacion) {
        return fichas.abrir(YO, afirmacion, config).orElseThrow();
    }

    @Test
    void el_trafico_del_centro_queda_verificado_con_dos_grupos_y_r04_se_vuelve_aceptable_bajo_preponderancia() {
        UUID trafico = trafico();
        UUID fragmento = fragmentoDelConteo();
        assertThat(ficha(trafico).argumentos()).singleElement().satisfies(r -> {
            assertThat(r.aceptable().get(EstandarPrueba.PREPONDERANCIA)).isFalse();
            assertThat(r.frase()).isEqualTo("No aceptable bajo preponderancia todavía: ningún argumento a favor es aplicable.");
        });

        // Paso 1 y 2: hecho, con el aviso de la tendencia sin cifra; dos preguntas respondidas.
        assertThat(fichas.cambiarTipo(YO, trafico, TipoAfirmacion.HECHO)).isTrue();
        assertThat(ficha(trafico).chequeos()).first().isEqualTo(new FichaDeVerificacion.Chequeo("Cifra y fecha", "aviso",
                "Afirma una comparación o tendencia sin cifra ni fecha."));
        fichas.marcarPreguntas(YO, INST, trafico, List.of("¿Quién lo registró y cómo?", "¿De cuándo es el dato?", "¿Una que no está?"), config);
        assertThat(ficha(trafico).preguntasSinResponder()).isEqualTo(1);

        // Paso 3: F1 de la biblioteca, etiquetada por el modelo y adoptada; antes de guardar, qué aporta.
        FichaDeVerificacion.BorradorFuente f1 = fuente("Conteo peatonal del municipio", "2025-03-15", "primaria", "no_aplica", "municipio", true, true,
                List.of(4, 5, 4, 4, 3), "apoya", "", fragmento.toString(), "modelo", "apoya");
        assertThat(fichas.previsualizar(YO, trafico, f1, config)).hasValueSatisfying(p -> {
            assertThat(p.fuerza()).contains(6);
            assertThat(p.puntajeCraap()).contains(20);
            assertThat(p.texto()).isEqualTo("Aporta fuerza 6 a favor (R01). Con esta fuente, la afirmación pasa de «sin verificar» a «en verificación». "
                    + "Para «verificada» hace falta una fuente de otro grupo de origen.");
        });
        assertThat(fichas.registrarFuente(YO, INST, trafico, f1, config)).hasValueSatisfying(r -> assertThat(r.errores()).isEmpty());
        EvidenciaGuardada guardadaF1 = evidencias.deAfirmacion(YO, trafico).getFirst();
        assertThat(guardadaF1.pasaje()).isEqualTo("En el centro pasan en promedio 1.200 personas por hora; en el barrio, 300.");
        assertThat(guardadaF1.etiquetadaPor()).isEqualTo(Evidencia.EtiquetadaPor.MODELO);
        assertThat(guardadaF1.fuente().documentoNombre()).contains("conteo-peatonal-municipio-2025.pdf");
        assertThat(guardadaF1.fuente().pagina()).contains(2);
        assertThat(ficha(trafico).calculo()).satisfies(c -> {
            assertThat(c.estado()).isEqualTo(EstadoAfirmacion.EN_VERIFICACION);
            assertThat(c.firma()).isEqualTo("En verificación: Fuerza neta +6 (fuerte), pero las fuentes a favor son de 1 grupo de origen: "
                    + "falta una fuente independiente (regla R03).");
        });

        // F2 escrita a mano.
        fichas.registrarFuente(YO, INST, trafico, fuente("Informe de la cámara de comercio", "2024-11-01", "secundaria", "no_aplica", "Cámara de comercio",
                true, false, List.of(4, 4, 3, 3, 4), "apoya", "El centro concentra el mayor flujo de compradores de la ciudad.", "", "usuario", ""), config);
        FichaDeVerificacion.Ficha conDos = ficha(trafico);
        assertThat(conDos.calculo().fuerzas().values()).containsExactly(6, 4);
        assertThat(conDos.calculo().neta()).isEqualTo(10);
        assertThat(conDos.calculo().estado()).isEqualTo(EstadoAfirmacion.VERIFICADA);
        assertThat(conDos.calculo().firma()).isEqualTo("Verificada por ti con 2 fuentes de 2 grupos distintos, bajo R03.");

        // Paso 4: el veredicto con confianza 80; no había confianza, así que no hay cambio de opinión.
        FichaDeVerificacion.VeredictoGuardado v = fichas.guardarVeredicto(YO, INST, trafico, Optional.of(80), "clave-1", config).orElseThrow();
        assertThat(v.error()).isEmpty();
        assertThat(v.cambio()).isEmpty();
        assertThat(v.ejecucion()).hasValueSatisfying(e -> {
            assertThat(e.tecnica()).isEqualTo(EjecutorTriangulacion.ID);
            assertThat(e.resumen()).isEqualTo("Verificada · fuerza neta +10 (fuerte) · 2 fuentes, 2 cuentan.");
            assertThat(ejecuciones.afirmacionesDe(YO, e.id())).extracting(AfirmacionConRol::afirmacionId).containsExactly(trafico);
        });
        FichaDeVerificacion.Ficha tras = ficha(trafico);
        assertThat(tras.afirmacion().estado()).isEqualTo(EstadoAfirmacion.VERIFICADA);
        assertThat(tras.afirmacion().fuerzaNeta()).isEqualTo(10);
        assertThat(tras.afirmacion().confianza()).contains(80);
        assertThat(evidencias.deAfirmacion(YO, trafico)).hasSize(2);
        assertThat(tras.argumentos()).singleElement().satisfies(r -> {
            assertThat(r.aplicable()).isTrue();
            assertThat(r.aceptable().get(EstandarPrueba.PREPONDERANCIA)).isTrue();
            assertThat(r.aceptable().get(EstandarPrueba.CLARO_Y_CONVINCENTE)).isTrue();
            assertThat(r.aceptable().get(EstandarPrueba.MAS_ALLA_DE_DUDA_RAZONABLE)).isFalse();
            assertThat(r.frase()).isEqualTo("Aceptable bajo preponderancia.");
        });

        // Una semana después: F3 fuerte en contra; disputada, cambio de opinión 80 → 50 y revisión pendiente.
        fichas.registrarFuente(YO, INST, trafico, fuente("Estudio de movilidad de la universidad regional", "2026-02-01", "primaria", "no_aplica",
                "universidad regional", true, true, List.of(5, 4, 4, 4, 4), "contradice", "Los sábados, el barrio recibe más peatones que el centro.", "",
                "usuario", ""), config);
        FichaDeVerificacion.VeredictoGuardado disputa = fichas.guardarVeredicto(YO, INST, trafico, Optional.of(50), "clave-2", config).orElseThrow();
        assertThat(disputa.cambio()).hasValueSatisfying(c -> {
            assertThat(c.confianzaAntes()).isEqualTo(80);
            assertThat(c.confianzaDespues()).isEqualTo(50);
        });
        assertThat(cambios.deUsuario(YO)).singleElement().satisfies(c -> assertThat(c.causa()).isEqualTo(pensamiento.nucleo.CambioOpinion.Causa.EVIDENCIA));
        assertThat(disputa.ejecucion()).hasValueSatisfying(e -> assertThat(e.resumen()).isEqualTo("Disputada · fuerza neta +4 (media) · 3 fuentes, 3 cuentan."));
        FichaDeVerificacion.Ficha disputada = ficha(trafico);
        assertThat(disputada.afirmacion().estado()).isEqualTo(EstadoAfirmacion.DISPUTADA);
        assertThat(disputada.pendientes()).extracting(p -> p.pendiente().descripcion())
                .containsExactly("Revisar la evidencia en conflicto sobre: El centro tiene más tráfico peatonal que el barrio.");
        assertThat(disputada.argumentos()).singleElement().satisfies(r -> {
            assertThat(r.aceptable().get(EstandarPrueba.PREPONDERANCIA)).isFalse();
            assertThat(r.aceptable().get(EstandarPrueba.ESCRUTINIO)).isTrue();
        });
    }

    @Test
    void desde_el_pendiente_de_t11_el_veredicto_lo_cierra_y_deja_el_suyo() {
        Ejemplo sucursal = new CatalogoJson().ejemplosDe(EjecutorFalsacion.ID).get(1);
        Resultado<?> r = new EjecutorFalsacion().ejecutar(MapeadorJson.leer(sucursal.config(), EjecutorFalsacion.Config.class),
                MapeadorJson.leer(sucursal.datos(), EjecutorFalsacion.Entrada.class), Contextos.sinIa());
        guardar(EjecutorFalsacion.ID, r);
        PendienteGuardado delT11 = ejecuciones.pendientes(YO).stream()
                .filter(p -> p.pendiente().descripcion().equals("Verificar la condición: Pasan al menos 1.000 personas por la esquina cada mañana."))
                .findFirst().orElseThrow();
        UUID esquina = delT11.pendiente().objetoId().orElseThrow();
        UUID fragmento = fragmentoDelConteo();
        UUID conLaEsquina = biblioteca.fragmentos(YO, biblioteca.cita(YO, fragmento).orElseThrow().documentoId()).get(2).id();

        fichas.cambiarTipo(YO, esquina, TipoAfirmacion.DATO_ESTADISTICO);
        assertThat(ficha(esquina).chequeos()).first().satisfies(c -> assertThat(c.estado()).isEqualTo("sin aviso"));
        fichas.registrarFuente(YO, INST, esquina, fuente("Conteo peatonal del municipio", "2025-03-15", "primaria", "observacional", "municipio", true, true,
                List.of(4, 5, 4, 4, 3), "apoya", "", conLaEsquina.toString(), "usuario", ""), config);
        FichaDeVerificacion.VeredictoGuardado v = fichas.guardarVeredicto(YO, INST, esquina, Optional.of(70), "clave-esquina", config).orElseThrow();

        assertThat(v.cerrados()).isEqualTo(1);
        assertThat(v.ejecucion()).hasValueSatisfying(e -> assertThat(e.resumen()).isEqualTo("En verificación · fuerza neta +6 (fuerte) · 1 fuente, 1 cuenta."));
        assertThat(ejecuciones.pendientes(YO)).extracting(p -> p.pendiente().descripcion())
                .doesNotContain("Verificar la condición: Pasan al menos 1.000 personas por la esquina cada mañana.")
                .contains("Buscar una fuente independiente para: Pasan al menos 1.000 personas por la esquina cada mañana.");
        assertThat(ejecuciones.pendientes(YO).stream().filter(p -> p.pendiente().objetoId().equals(Optional.of(esquina))))
                .singleElement().satisfies(p -> assertThat(p.pendiente().tipo()).isEqualTo(TipoPendiente.VERIFICACION));
    }

    @Test
    void un_pasaje_que_no_esta_tal_cual_en_el_documento_no_se_registra() {
        UUID trafico = trafico();
        UUID fragmento = fragmentoDelConteo();

        FichaDeVerificacion.Registro r = fichas.registrarFuente(YO, INST, trafico, fuente("Conteo", "2025-03-15", "primaria", "no_aplica", "municipio",
                true, true, List.of(), "apoya", "En el centro pasan 5.000 personas por hora.", fragmento.toString(), "usuario", ""), config).orElseThrow();

        assertThat(r.errores()).containsEntry("pasaje", "El pasaje tiene que estar copiado tal cual del documento.");
        assertThat(evidencias.deAfirmacion(YO, trafico)).isEmpty();
    }

    @Test
    void la_etiqueta_es_del_modelo_solo_si_la_postura_es_la_que_propuso() {
        UUID trafico = trafico();
        UUID fragmento = fragmentoDelConteo();

        fichas.registrarFuente(YO, INST, trafico, fuente("Conteo", "2025-03-15", "primaria", "no_aplica", "municipio", true, true, List.of(), "matiza", "",
                fragmento.toString(), "modelo", "apoya"), config);

        assertThat(evidencias.deAfirmacion(YO, trafico)).singleElement()
                .satisfies(e -> assertThat(e.etiquetadaPor()).isEqualTo(Evidencia.EtiquetadaPor.USUARIO));
    }

    @Test
    void sin_fuentes_no_hay_veredicto_salvo_que_no_sea_verificable_y_la_ficha_de_otra_persona_no_existe() {
        UUID trafico = trafico();

        assertThat(fichas.guardarVeredicto(YO, INST, trafico, Optional.empty(), "c", config)).hasValueSatisfying(v ->
                assertThat(v.error()).contains("Registra al menos una fuente antes del veredicto."));
        fichas.cambiarTipo(YO, trafico, TipoAfirmacion.JUICIO_DE_VALOR);
        assertThat(fichas.guardarVeredicto(YO, INST, trafico, Optional.empty(), "c", config)).hasValueSatisfying(v -> assertThat(v.error()).isEmpty());
        assertThat(ficha(trafico).afirmacion().estado()).isEqualTo(EstadoAfirmacion.NO_VERIFICABLE);
        assertThat(fichas.abrir(UUID.randomUUID(), trafico, config)).isEmpty();
    }

    @Test
    void la_ficha_admite_hasta_seis_fuentes() {
        UUID trafico = trafico();
        for (int i = 1; i <= FichaDeVerificacion.FUENTES_MAXIMAS; i++) {
            fichas.registrarFuente(YO, INST, trafico, fuente("Fuente " + i, "", "primaria", "no_aplica", "grupo " + i, false, false, List.of(), "apoya",
                    "Pasaje " + i + ".", "", "usuario", ""), config);
        }

        FichaDeVerificacion.Registro septima = fichas.registrarFuente(YO, INST, trafico, fuente("Fuente 7", "", "primaria", "no_aplica", "grupo 7", false,
                false, List.of(), "apoya", "Pasaje 7.", "", "usuario", ""), config).orElseThrow();

        assertThat(septima.errores()).containsValue("La ficha admite hasta seis fuentes por afirmación, como T22 · Triangulación.");
        assertThat(ficha(trafico).admiteMasFuentes()).isFalse();
    }
}
