package pensamiento.unidad.expediente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pensamiento.catalogo.CatalogoJson;
import pensamiento.catalogo.MapeadorJson;
import pensamiento.argdown.ParserArgdown;
import pensamiento.expediente.GuardadoDeEjecuciones;
import pensamiento.expediente.PaqueteDatos;
import pensamiento.expediente.ServicioExpedientes;
import pensamiento.expediente.ServicioRespaldo;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.Ejemplo;
import pensamiento.nucleo.Expediente;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.Uuid7;
import pensamiento.nucleo.puertos.RegistroAuditoria;
import pensamiento.tecnicas.f1.EjecutorMapa;
import pensamiento.tecnicas.f1.ResultadoMapa;
import pensamiento.tecnicas.f5.ConfigAch;
import pensamiento.tecnicas.f5.EjecutorAch;
import pensamiento.tecnicas.f5.EntradaAch;
import pensamiento.tecnicas.f5.ResultadoAch;
import pensamiento.testutil.builders.Contextos;
import pensamiento.testutil.fakes.FakeRegistroAuditoria;
import pensamiento.testutil.fakes.FakeRepositorioArgumentos;
import pensamiento.testutil.fakes.FakeRegistroIdentificadores;
import pensamiento.testutil.fakes.FakeReloj;
import pensamiento.testutil.fakes.FakeRepositorioConfiguracion;
import pensamiento.testutil.fakes.FakeRepositorioEjecucion;
import pensamiento.testutil.fakes.FakeRepositorioExpediente;
import pensamiento.testutil.fakes.FakeRepositorioPredicciones;
import pensamiento.testutil.fakes.FakeRepositorioTecnica;

/**
 * Collaboration test del importador y el exportador (RF-12): la dueña de la panadería exporta lo suyo y lo vuelve
 * a importar sin duplicar; un archivo con identificadores de otra persona se rechaza entero.
 */
class ServicioRespaldoTest {

    private static final UUID INSTITUCION = Contextos.INSTITUCION;
    private static final UUID DUENA = Contextos.DUENA_DE_LA_PANADERIA;
    private static final UUID SECRETARIA_DE_LA_JUNTA = UUID.fromString("00000000-0000-7000-8000-0000000000b2");

    private final FakeRepositorioExpediente expedientes = new FakeRepositorioExpediente();
    private final FakeRepositorioEjecucion ejecuciones = new FakeRepositorioEjecucion();
    private final FakeRepositorioConfiguracion configuraciones = new FakeRepositorioConfiguracion();
    private final FakeRegistroIdentificadores identificadores = new FakeRegistroIdentificadores();
    private final FakeRegistroAuditoria auditoria = new FakeRegistroAuditoria();
    private final FakeReloj reloj = new FakeReloj();
    private final FakeRepositorioArgumentos argumentos = new FakeRepositorioArgumentos();
    private final FakeRepositorioPredicciones predicciones = new FakeRepositorioPredicciones(ejecuciones);
    private final pensamiento.testutil.fakes.FakeRepositorioCambiosOpinion cambios = new pensamiento.testutil.fakes.FakeRepositorioCambiosOpinion(ejecuciones);
    private final pensamiento.testutil.fakes.FakeRepositorioSesiones sesiones = new pensamiento.testutil.fakes.FakeRepositorioSesiones();
    private final ServicioRespaldo respaldo = new ServicioRespaldo(expedientes, ejecuciones, argumentos, configuraciones, identificadores, auditoria, reloj,
            predicciones, cambios, sesiones, sinBiblioteca());
    private final ServicioExpedientes servicioExpedientes = new ServicioExpedientes(expedientes, ejecuciones, new FakeRepositorioTecnica(), auditoria, reloj);

    /** La dueña corre las ventas de los sábados, la guarda en "La segunda sucursal" y personaliza T28. */
    private Ejecucion dadoQueLaDuenaTieneUnExpedienteConUnaEjecucion() {
        Ejemplo ventas = new CatalogoJson().ejemplosDe(EjecutorAch.ID).getFirst();
        Resultado<ResultadoAch> r = new EjecutorAch().ejecutar(MapeadorJson.leer(ventas.config(), ConfigAch.class),
                MapeadorJson.leer(ventas.datos(), EntradaAch.class), Contextos.sinIa());
        Expediente sucursal = servicioExpedientes.crear(DUENA, INSTITUCION, "La segunda sucursal de la panadería");
        Ejecucion e = new Ejecucion(Uuid7.en(reloj.ahora()), DUENA, INSTITUCION, EjecutorAch.ID, 1, Optional.of(sucursal.id()),
                ventas.config(), ventas.datos(), MapeadorJson.escribir(r.valor()), r.resumen(), Optional.empty(), "clave-ventas", reloj.ahora());
        ejecuciones.guardar(e, r.afirmaciones(), r.pendientes());
        configuraciones.guardar(DUENA, INSTITUCION, EjecutorAch.ID, 1, new Json("{\"maxHipotesis\":5,\"escala\":\"cin\",\"pesosActivos\":true}"));
        return e;
    }

    @Test
    void exportar_trae_expedientes_ejecuciones_con_afirmaciones_y_pendientes_y_configuraciones() {
        Ejecucion e = dadoQueLaDuenaTieneUnExpedienteConUnaEjecucion();

        PaqueteDatos paquete = respaldo.exportar(DUENA, INSTITUCION, "dueña de la panadería");

        assertThat(paquete.formato()).isEqualTo(PaqueteDatos.FORMATO);
        assertThat(paquete.expedientes()).extracting(PaqueteDatos.ExpedienteDatos::nombre).containsExactly("La segunda sucursal de la panadería");
        assertThat(paquete.ejecuciones()).singleElement().satisfies(d -> {
            assertThat(d.id()).isEqualTo(e.id());
            assertThat(d.afirmaciones()).hasSize(3).allSatisfy(a -> assertThat(a.rol()).isEqualTo("hipotesis"));
            assertThat(d.pendientes()).extracting(PaqueteDatos.PendienteDatos::descripcion)
                    .containsExactly("Verificar E1: La baja es solo los sábados (hipótesis H1: Abrió una feria a dos cuadras los sábados)");
        });
        assertThat(paquete.configuraciones()).extracting(PaqueteDatos.Configuracion::tecnica).containsExactly("T28");
        assertThat(auditoria.deUsuario(DUENA)).extracting(RegistroAuditoria.Evento::accion).contains(RegistroAuditoria.Accion.EXPORTAR);
    }

    @Test
    void importar_lo_propio_dos_veces_no_duplica_nada() {
        dadoQueLaDuenaTieneUnExpedienteConUnaEjecucion();
        String archivo = respaldo.exportarComoTexto(DUENA, INSTITUCION, "dueña de la panadería");

        ServicioRespaldo.Importacion primera = respaldo.importarTexto(DUENA, INSTITUCION, archivo);
        ServicioRespaldo.Importacion segunda = respaldo.importarTexto(DUENA, INSTITUCION, archivo);

        assertThat(primera).isEqualTo(new ServicioRespaldo.Importacion(0, 1, 0, 1, 1));
        assertThat(segunda).isEqualTo(primera);
        assertThat(ejecuciones.todas()).hasSize(1);
        assertThat(ejecuciones.pendientes(DUENA)).hasSize(1);
        assertThat(expedientes.deUsuario(DUENA)).hasSize(1);
        assertThat(auditoria.deUsuario(DUENA)).extracting(RegistroAuditoria.Evento::accion)
                .filteredOn(a -> a == RegistroAuditoria.Accion.IMPORTAR).hasSize(2);
    }

    @Test
    void importar_en_una_instalacion_vacia_recrea_todo_con_los_mismos_identificadores() {
        Ejecucion original = dadoQueLaDuenaTieneUnExpedienteConUnaEjecucion();
        String archivo = respaldo.exportarComoTexto(DUENA, INSTITUCION, "dueña de la panadería");
        FakeRepositorioExpediente otrosExpedientes = new FakeRepositorioExpediente();
        FakeRepositorioEjecucion otrasEjecuciones = new FakeRepositorioEjecucion();
        FakeRepositorioConfiguracion otrasConfiguraciones = new FakeRepositorioConfiguracion();
        ServicioRespaldo enOtraInstalacion = new ServicioRespaldo(otrosExpedientes, otrasEjecuciones, new FakeRepositorioArgumentos(), otrasConfiguraciones,
                new FakeRegistroIdentificadores(), new FakeRegistroAuditoria(), reloj, new FakeRepositorioPredicciones(new FakeRepositorioEjecucion()),
                new pensamiento.testutil.fakes.FakeRepositorioCambiosOpinion(otrasEjecuciones), new pensamiento.testutil.fakes.FakeRepositorioSesiones(), sinBiblioteca());

        ServicioRespaldo.Importacion r = enOtraInstalacion.importarTexto(DUENA, INSTITUCION, archivo);

        assertThat(r).isEqualTo(new ServicioRespaldo.Importacion(1, 0, 1, 0, 1));
        Ejecucion copia = otrasEjecuciones.porId(DUENA, original.id()).orElseThrow();
        assertThat(copia.expedienteId()).isEqualTo(original.expedienteId());
        assertThat(copia.resumen()).isEqualTo(original.resumen());
        assertThat(otrasEjecuciones.afirmacionesDe(DUENA, original.id())).hasSize(3);
        assertThat(otrasEjecuciones.pendientes(DUENA)).hasSize(1);
        assertThat(otrasConfiguraciones.de(DUENA, IdTecnica.de("T28")).orElseThrow().valores().texto()).contains("\"maxHipotesis\":5");
    }

    @Test
    void un_archivo_con_identificadores_de_otra_persona_se_rechaza_entero() {
        Ejecucion deLaDuena = dadoQueLaDuenaTieneUnExpedienteConUnaEjecucion();
        String archivo = respaldo.exportarComoTexto(DUENA, INSTITUCION, "dueña de la panadería");
        identificadores.existe(deLaDuena.id(), DUENA);

        assertThatThrownBy(() -> respaldo.importarTexto(SECRETARIA_DE_LA_JUNTA, INSTITUCION, archivo))
                .isInstanceOf(ServicioRespaldo.IdentificadorAjeno.class)
                .hasMessageContaining("1 identificador que ya es de otra persona");
        assertThat(expedientes.deUsuario(SECRETARIA_DE_LA_JUNTA)).isEmpty();
        assertThat(ejecuciones.recientes(SECRETARIA_DE_LA_JUNTA, 10)).isEmpty();
    }

    @Test
    void un_archivo_que_no_es_un_respaldo_de_esta_aplicacion_se_rechaza() {
        assertThatThrownBy(() -> respaldo.importarTexto(DUENA, INSTITUCION, "{\"formato\":\"otra-cosa\",\"version\":1}"))
                .isInstanceOf(ServicioRespaldo.ArchivoInvalido.class);
        assertThatThrownBy(() -> respaldo.importarTexto(DUENA, INSTITUCION, "esto no es json"))
                .isInstanceOf(ServicioRespaldo.ArchivoInvalido.class);
        assertThat(auditoria.deUsuario(DUENA)).isEmpty();
    }

    @Test
    void los_identificadores_exportados_son_uuidv7() {
        dadoQueLaDuenaTieneUnExpedienteConUnaEjecucion();
        PaqueteDatos paquete = respaldo.exportar(DUENA, INSTITUCION, "dueña de la panadería");
        List<UUID> ids = new java.util.ArrayList<>();
        paquete.expedientes().forEach(x -> ids.add(x.id()));
        paquete.ejecuciones().forEach(e -> {
            ids.add(e.id());
            e.afirmaciones().forEach(a -> ids.add(a.id()));
        });
        // Las afirmaciones las genera Contextos.sinIa con una secuencia fija de versión 7; el resto, Uuid7.
        assertThat(ids).isNotEmpty().allSatisfy(id -> assertThat(id.version()).isEqualTo(7));
    }

    /** La dueña guarda el mapa de la segunda sucursal (T01, ejemplo 2): cuatro afirmaciones y dos argumentos. */
    private Ejecucion dadoQueLaDuenaGuardoElMapaDeLaSucursal() {
        Ejemplo sucursal = new CatalogoJson().ejemplosDe(EjecutorMapa.ID).get(1);
        Resultado<ResultadoMapa> r = new EjecutorMapa(new ParserArgdown()).ejecutar(MapeadorJson.leer(sucursal.config(), EjecutorMapa.Config.class),
                MapeadorJson.leer(sucursal.datos(), EjecutorMapa.Entrada.class), Contextos.sinIa());
        Ejecucion e = new Ejecucion(Uuid7.en(reloj.ahora()), DUENA, INSTITUCION, EjecutorMapa.ID, 1, Optional.empty(), sucursal.config(),
                sucursal.datos(), MapeadorJson.escribir(r.valor()), r.resumen(), Optional.empty(), "clave-mapa", reloj.ahora());
        return new GuardadoDeEjecuciones(ejecuciones, argumentos, new pensamiento.testutil.fakes.FakeRepositorioPredicciones(ejecuciones), new pensamiento.testutil.fakes.FakeRepositorioCambiosOpinion(ejecuciones),
                new pensamiento.testutil.fakes.FakeRepositorioEvidencias(ejecuciones, new pensamiento.testutil.fakes.FakeBiblioteca()), new pensamiento.testutil.fakes.FakeRepositorioConfiguracion()).guardar(e, r);
    }

    @Test
    void exportar_trae_los_argumentos_de_cada_ejecucion_con_sus_premisas() {
        Ejecucion mapa = dadoQueLaDuenaGuardoElMapaDeLaSucursal();

        PaqueteDatos paquete = respaldo.exportar(DUENA, INSTITUCION, "dueña de la panadería");

        assertThat(paquete.version()).isEqualTo(5);
        assertThat(paquete.ejecuciones()).singleElement().satisfies(d -> {
            assertThat(d.id()).isEqualTo(mapa.id());
            assertThat(d.argumentos()).extracting(PaqueteDatos.ArgumentoDatos::sentido, PaqueteDatos.ArgumentoDatos::peso)
                    .containsExactly(org.assertj.core.groups.Tuple.tuple("pro", 3), org.assertj.core.groups.Tuple.tuple("contra", 1));
            assertThat(d.argumentos().getFirst().premisas()).extracting(PaqueteDatos.PremisaDatos::asumible).containsExactly(true, true);
            assertThat(d.argumentos().getFirst().textoArgdown()).startsWith("[Sucursal]: Conviene abrir la segunda sucursal en el centro.");
        });
    }

    @Test
    void importar_en_una_instalacion_vacia_recrea_los_argumentos_con_los_mismos_identificadores() {
        Ejecucion mapa = dadoQueLaDuenaGuardoElMapaDeLaSucursal();
        List<FakeRepositorioArgumentos.ArgumentoGuardado> originales = argumentos.deEjecucion(DUENA, mapa.id());
        String archivo = respaldo.exportarComoTexto(DUENA, INSTITUCION, "dueña de la panadería");
        FakeRepositorioArgumentos otrosArgumentos = new FakeRepositorioArgumentos();
        ServicioRespaldo enOtraInstalacion = new ServicioRespaldo(new FakeRepositorioExpediente(), new FakeRepositorioEjecucion(), otrosArgumentos,
                new FakeRepositorioConfiguracion(), new FakeRegistroIdentificadores(), new FakeRegistroAuditoria(), reloj,
                new FakeRepositorioPredicciones(new FakeRepositorioEjecucion()), new pensamiento.testutil.fakes.FakeRepositorioCambiosOpinion(new FakeRepositorioEjecucion()),
                new pensamiento.testutil.fakes.FakeRepositorioSesiones(), sinBiblioteca());

        enOtraInstalacion.importarTexto(DUENA, INSTITUCION, archivo);
        enOtraInstalacion.importarTexto(DUENA, INSTITUCION, archivo);

        assertThat(otrosArgumentos.deEjecucion(DUENA, mapa.id())).isEqualTo(originales);
        assertThat(otrosArgumentos.total()).isEqualTo(2);
    }

    @Test
    void un_archivo_de_la_version_1_se_migra_y_se_importa_sin_argumentos() {
        dadoQueLaDuenaTieneUnExpedienteConUnaEjecucion();
        String version3 = respaldo.exportarComoTexto(DUENA, INSTITUCION, "dueña de la panadería");
        // Un archivo exportado en el hito 1: versión 1 y ejecuciones sin los campos argumentos ni predicciones.
        String version1 = version3.replace("\"version\" : 5", "\"version\" : 1").replaceAll(",\\s*\"argumentos\" : \\[ \\]", "")
                .replaceAll(",\\s*\"predicciones\" : \\[ \\]", "").replaceAll(",\\s*\"cambios\" : \\[ \\]", "")
                .replaceAll(",\\s*\"sesiones\" : \\[ \\]", "");
        assertThat(version1).contains("\"version\" : 1").doesNotContain("\"argumentos\"").doesNotContain("\"predicciones\"");
        FakeRepositorioEjecucion otrasEjecuciones = new FakeRepositorioEjecucion();
        ServicioRespaldo enOtraInstalacion = new ServicioRespaldo(new FakeRepositorioExpediente(), otrasEjecuciones, new FakeRepositorioArgumentos(),
                new FakeRepositorioConfiguracion(), new FakeRegistroIdentificadores(), new FakeRegistroAuditoria(), reloj,
                new FakeRepositorioPredicciones(new FakeRepositorioEjecucion()), new pensamiento.testutil.fakes.FakeRepositorioCambiosOpinion(new FakeRepositorioEjecucion()),
                new pensamiento.testutil.fakes.FakeRepositorioSesiones(), sinBiblioteca());

        ServicioRespaldo.Importacion r = enOtraInstalacion.importarTexto(DUENA, INSTITUCION, version1);

        assertThat(r).isEqualTo(new ServicioRespaldo.Importacion(1, 0, 1, 0, 1));
        assertThat(otrasEjecuciones.recientes(DUENA, 10)).hasSize(1);
    }

    @Test
    void una_decision_del_diario_viaja_con_su_prediccion_resuelta_y_al_importarse_sigue_resuelta() {
        var ejemplo = new pensamiento.catalogo.CatalogoJson().ejemplosDe(pensamiento.tecnicas.f5.EjecutorDiarioDecisiones.ID).getFirst();
        var t32 = new pensamiento.tecnicas.f5.EjecutorDiarioDecisiones();
        Resultado<pensamiento.tecnicas.f5.ResultadoDiario> r = t32.ejecutar(
                MapeadorJson.leer(ejemplo.config(), pensamiento.tecnicas.f5.EjecutorDiarioDecisiones.Config.class),
                MapeadorJson.leer(ejemplo.datos(), pensamiento.tecnicas.f5.EjecutorDiarioDecisiones.Entrada.class), Contextos.sinIa());
        Ejecucion e = new Ejecucion(Uuid7.en(reloj.ahora()), DUENA, INSTITUCION, pensamiento.tecnicas.f5.EjecutorDiarioDecisiones.ID, 1, Optional.empty(),
                pensamiento.nucleo.Json.VACIO, pensamiento.nucleo.Json.VACIO, MapeadorJson.escribir(r.valor()), r.resumen(), Optional.empty(), "diario",
                reloj.ahora());
        new GuardadoDeEjecuciones(ejecuciones, argumentos, predicciones, new pensamiento.testutil.fakes.FakeRepositorioCambiosOpinion(ejecuciones),
                new pensamiento.testutil.fakes.FakeRepositorioEvidencias(ejecuciones, new pensamiento.testutil.fakes.FakeBiblioteca()), new pensamiento.testutil.fakes.FakeRepositorioConfiguracion()).guardar(e, r);
        java.util.UUID id = predicciones.deEjecucion(DUENA, e.id()).getFirst().id();
        predicciones.resolver(DUENA, id, true, java.time.Instant.parse("2027-04-15T15:00:00Z"));
        String archivo = respaldo.exportarComoTexto(DUENA, INSTITUCION, "dueña de la panadería");
        assertThat(archivo).contains("\"predicciones\"").contains(id.toString()).contains("\"resultado\" : \"acierto\"");

        FakeRepositorioEjecucion otrasEjecuciones = new FakeRepositorioEjecucion();
        FakeRepositorioPredicciones otrasPredicciones = new FakeRepositorioPredicciones(otrasEjecuciones);
        ServicioRespaldo enOtraInstalacion = new ServicioRespaldo(new FakeRepositorioExpediente(), otrasEjecuciones, new FakeRepositorioArgumentos(),
                new FakeRepositorioConfiguracion(), new FakeRegistroIdentificadores(), new FakeRegistroAuditoria(), reloj, otrasPredicciones,
                new pensamiento.testutil.fakes.FakeRepositorioCambiosOpinion(otrasEjecuciones), new pensamiento.testutil.fakes.FakeRepositorioSesiones(), sinBiblioteca());
        enOtraInstalacion.importarTexto(DUENA, INSTITUCION, archivo);
        enOtraInstalacion.importarTexto(DUENA, INSTITUCION, archivo);

        assertThat(otrasPredicciones.deUsuario(DUENA)).singleElement().satisfies(p -> {
            assertThat(p.id()).isEqualTo(id);
            assertThat(p.texto()).isEqualTo("La sucursal de la terminal cubre sus costos en 6 meses.");
            assertThat(p.estado()).isEqualTo(pensamiento.nucleo.Prediccion.Estado.ACIERTO);
            assertThat(p.resueltaEn()).contains(java.time.Instant.parse("2027-04-15T15:00:00Z"));
            assertThat(p.confianza()).isEqualTo(70);
        });
    }

    @Test
    void un_argumento_de_otra_persona_en_el_archivo_rechaza_la_importacion() {
        Ejecucion mapa = dadoQueLaDuenaGuardoElMapaDeLaSucursal();
        String archivo = respaldo.exportarComoTexto(DUENA, INSTITUCION, "dueña de la panadería");
        identificadores.existe(argumentos.deEjecucion(DUENA, mapa.id()).getFirst().argumento().argumento().id(), DUENA);

        assertThatThrownBy(() -> respaldo.importarTexto(SECRETARIA_DE_LA_JUNTA, INSTITUCION, archivo))
                .isInstanceOf(ServicioRespaldo.IdentificadorAjeno.class);
        assertThat(ejecuciones.recientes(SECRETARIA_DE_LA_JUNTA, 10)).isEmpty();
    }

    @Test
    void la_version_4_lleva_las_sesiones_del_consejero_con_sus_turnos_y_los_cambios_de_opinion_de_ida_y_vuelta() {
        pensamiento.flujos.Consejero consejero = new pensamiento.flujos.Consejero(sesiones, expedientes,
                new GuardadoDeEjecuciones(ejecuciones, argumentos, predicciones, cambios,
                        new pensamiento.testutil.fakes.FakeRepositorioEvidencias(ejecuciones, new pensamiento.testutil.fakes.FakeBiblioteca()), new pensamiento.testutil.fakes.FakeRepositorioConfiguracion()), reloj, new pensamiento.tecnicas.f2.EjecutorPreguntasSocraticas(),
                new pensamiento.tecnicas.f2.EjecutorEscalera(), new pensamiento.tecnicas.f6.EjecutorSeisSombreros(),
                new pensamiento.tecnicas.f6.EjecutorEquipoRojo(new pensamiento.testutil.fakes.FakeRepositorioEsquemas()));
        Json config = new Json("{\"tipos\":[\"supuestos\",\"evidencia\"],\"orden\":\"fijo\",\"turnosMaximos\":4,\"modo\":\"plantillas\"}");
        pensamiento.nucleo.SesionConsejero s = consejero.iniciar(DUENA, INSTITUCION, pensamiento.nucleo.SesionConsejero.Modo.DECISION,
                "Conviene abrir los domingos.", List.of(), config, false, Optional.of(70), Optional.empty(), false).sesion();
        consejero.responder(DUENA, s.id(), "Que los domingos hay clientes.", false);
        consejero.irAlCierre(DUENA, s.id());
        consejero.responder(DUENA, s.id(), "Que dos domingos vendamos menos que un lunes.", false);
        Ejecucion e = consejero.cerrar(DUENA, s.id(), Optional.of("Voy a probar dos domingos."), Optional.of(50), Optional.of("evidencia"), List.of(),
                Contextos.sinIa());

        String archivo = respaldo.exportarComoTexto(DUENA, INSTITUCION, "dueña de la panadería");
        assertThat(archivo).contains("\"version\" : 5", "\"sesiones\"", "\"cambios\"");
        FakeRepositorioEjecucion otrasEjecuciones = new FakeRepositorioEjecucion();
        pensamiento.testutil.fakes.FakeRepositorioCambiosOpinion otrosCambios = new pensamiento.testutil.fakes.FakeRepositorioCambiosOpinion(otrasEjecuciones);
        pensamiento.testutil.fakes.FakeRepositorioSesiones otrasSesiones = new pensamiento.testutil.fakes.FakeRepositorioSesiones();
        FakeRepositorioExpediente otrosExpedientes = new FakeRepositorioExpediente();
        ServicioRespaldo enOtraInstalacion = new ServicioRespaldo(otrosExpedientes, otrasEjecuciones, new FakeRepositorioArgumentos(),
                new FakeRepositorioConfiguracion(), new FakeRegistroIdentificadores(), new FakeRegistroAuditoria(), reloj,
                new FakeRepositorioPredicciones(otrasEjecuciones), otrosCambios, otrasSesiones, sinBiblioteca());

        enOtraInstalacion.importarTexto(DUENA, INSTITUCION, archivo);
        enOtraInstalacion.importarTexto(DUENA, INSTITUCION, archivo);

        assertThat(otrasSesiones.porId(DUENA, s.id())).contains(sesiones.porId(DUENA, s.id()).orElseThrow());
        assertThat(otrasSesiones.turnos(DUENA, s.id())).containsExactlyElementsOf(sesiones.turnos(DUENA, s.id()));
        assertThat(otrosCambios.deEjecucion(DUENA, e.id())).containsExactlyElementsOf(cambios.deEjecucion(DUENA, e.id()));
        assertThat(otrosCambios.deEjecucion(DUENA, e.id())).singleElement().satisfies(c -> {
            assertThat(c.confianzaAntes()).isEqualTo(70);
            assertThat(c.confianzaDespues()).isEqualTo(50);
            assertThat(c.texto()).isEqualTo("Conviene abrir los domingos.");
        });
    }

    @Test
    void una_sesion_de_otra_persona_en_el_archivo_rechaza_todo_el_archivo() {
        pensamiento.flujos.Consejero consejero = new pensamiento.flujos.Consejero(sesiones, expedientes,
                new GuardadoDeEjecuciones(ejecuciones, argumentos, predicciones, cambios,
                        new pensamiento.testutil.fakes.FakeRepositorioEvidencias(ejecuciones, new pensamiento.testutil.fakes.FakeBiblioteca()), new pensamiento.testutil.fakes.FakeRepositorioConfiguracion()), reloj, new pensamiento.tecnicas.f2.EjecutorPreguntasSocraticas(),
                new pensamiento.tecnicas.f2.EjecutorEscalera(), new pensamiento.tecnicas.f6.EjecutorSeisSombreros(),
                new pensamiento.tecnicas.f6.EjecutorEquipoRojo(new pensamiento.testutil.fakes.FakeRepositorioEsquemas()));
        Json config = new Json("{\"tipos\":[\"supuestos\",\"evidencia\"],\"orden\":\"fijo\",\"turnosMaximos\":4,\"modo\":\"plantillas\"}");
        pensamiento.nucleo.SesionConsejero s = consejero.iniciar(DUENA, INSTITUCION, pensamiento.nucleo.SesionConsejero.Modo.DECISION,
                "Conviene abrir los domingos.", List.of(), config, false, Optional.empty(), Optional.empty(), false).sesion();
        String archivo = respaldo.exportarComoTexto(DUENA, INSTITUCION, "dueña de la panadería");
        identificadores.existe(s.id(), DUENA);

        assertThatThrownBy(() -> respaldo.importarTexto(SECRETARIA_DE_LA_JUNTA, INSTITUCION, archivo)).isInstanceOf(ServicioRespaldo.IdentificadorAjeno.class);
        assertThat(sesiones.deUsuario(SECRETARIA_DE_LA_JUNTA)).isEmpty();
    }

    /** El respaldo de la biblioteca vacío: estas pruebas son de las versiones 1 a 4. */
    private static pensamiento.expediente.RespaldoDeLaBiblioteca sinBiblioteca() {
        FakeRepositorioEjecucion sinEjecuciones = new FakeRepositorioEjecucion();
        pensamiento.testutil.fakes.FakeBiblioteca biblioteca = new pensamiento.testutil.fakes.FakeBiblioteca();
        return new pensamiento.expediente.RespaldoDeLaBiblioteca(new pensamiento.testutil.fakes.FakeRepositorioEvidencias(sinEjecuciones, biblioteca),
                new pensamiento.testutil.fakes.FakeRepositorioVerificaciones(sinEjecuciones), biblioteca, new pensamiento.testutil.fakes.FakeColaTrabajos(),
                new pensamiento.testutil.fakes.FakeReloj());
    }
}
