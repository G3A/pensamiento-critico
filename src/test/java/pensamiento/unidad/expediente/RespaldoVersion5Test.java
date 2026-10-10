package pensamiento.unidad.expediente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pensamiento.catalogo.MapeadorJson;
import pensamiento.expediente.GuardadoDeEjecuciones;
import pensamiento.expediente.PaqueteDatos;
import pensamiento.expediente.RespaldoDeLaBiblioteca;
import pensamiento.expediente.ServicioRespaldo;
import pensamiento.flujos.FichaDeVerificacion;
import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.Documento;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.EstadoAfirmacion;
import pensamiento.nucleo.Fragmento;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.OrigenAfirmacion;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.SentidoAfirmacion;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.Uuid7;
import pensamiento.nucleo.puertos.Biblioteca;
import pensamiento.tecnicas.f4.EjecutorCraap;
import pensamiento.tecnicas.f4.EjecutorTriangulacion;
import pensamiento.testutil.builders.Contextos;
import pensamiento.testutil.fakes.FakeBiblioteca;
import pensamiento.testutil.fakes.FakeColaTrabajos;
import pensamiento.testutil.fakes.FakeRegistroAuditoria;
import pensamiento.testutil.fakes.FakeRegistroIdentificadores;
import pensamiento.testutil.fakes.FakeReloj;
import pensamiento.testutil.fakes.FakeRepositorioArgumentos;
import pensamiento.testutil.fakes.FakeRepositorioCambiosOpinion;
import pensamiento.testutil.fakes.FakeRepositorioConfiguracion;
import pensamiento.testutil.fakes.FakeRepositorioEjecucion;
import pensamiento.testutil.fakes.FakeRepositorioEsquemas;
import pensamiento.testutil.fakes.FakeRepositorioEvidencias;
import pensamiento.testutil.fakes.FakeRepositorioExpediente;
import pensamiento.testutil.fakes.FakeRepositorioPredicciones;
import pensamiento.testutil.fakes.FakeRepositorioSesiones;
import pensamiento.testutil.fakes.FakeRepositorioVerificaciones;

/**
 * RF-12 con el paquete de datos versión 5: la biblioteca (metadatos y texto, sin el original), las evidencias con sus fuentes,
 * las preguntas marcadas y el veredicto viajan de una instalación a otra; el documento llega indexado, privado, sin original y
 * con su vectorizado en la cola. Una evidencia sobre una afirmación ajena al archivo lo rechaza entero.
 */
class RespaldoVersion5Test {

    private static final UUID YO = Contextos.DUENA_DE_LA_PANADERIA;
    private static final UUID INST = Contextos.INSTITUCION;

    /** Una instalación con sus Fakes. */
    static final class Instalacion {
        final FakeReloj reloj = new FakeReloj();
        final FakeRepositorioEjecucion ejecuciones = new FakeRepositorioEjecucion();
        final FakeBiblioteca biblioteca = new FakeBiblioteca();
        final FakeRepositorioEvidencias evidencias = new FakeRepositorioEvidencias(ejecuciones, biblioteca);
        final FakeRepositorioVerificaciones verificaciones = new FakeRepositorioVerificaciones(ejecuciones);
        final FakeColaTrabajos cola = new FakeColaTrabajos();
        final FakeRepositorioArgumentos argumentos = new FakeRepositorioArgumentos();
        final FakeRepositorioCambiosOpinion cambios = new FakeRepositorioCambiosOpinion(ejecuciones);
        final GuardadoDeEjecuciones guardado = new GuardadoDeEjecuciones(ejecuciones, argumentos, new FakeRepositorioPredicciones(ejecuciones), cambios,
                evidencias, new pensamiento.testutil.fakes.FakeRepositorioConfiguracion());
        final ServicioRespaldo respaldo = new ServicioRespaldo(new FakeRepositorioExpediente(), ejecuciones, argumentos, new FakeRepositorioConfiguracion(),
                new FakeRegistroIdentificadores(), new FakeRegistroAuditoria(), reloj, new FakeRepositorioPredicciones(ejecuciones), cambios,
                new FakeRepositorioSesiones(), new RespaldoDeLaBiblioteca(evidencias, verificaciones, biblioteca, cola, reloj));
        final FichaDeVerificacion fichas = new FichaDeVerificacion(verificaciones, evidencias, argumentos, ejecuciones, new FakeRepositorioEsquemas(),
                biblioteca, guardado, reloj);
    }

    private static final FichaDeVerificacion.Configuracion CONFIG = new FichaDeVerificacion.Configuracion(
            new EjecutorTriangulacion.Config(2, EjecutorTriangulacion.Modo.PLANTILLAS), EjecutorCraap.Config.porDefecto());

    private static UUID afirmacion(Instalacion i, String texto) {
        AfirmacionConRol a = new AfirmacionConRol(Uuid7.en(i.reloj.ahora()), texto, TipoAfirmacion.HECHO, RolAfirmacion.PREMISA, SentidoAfirmacion.PRODUCIDA,
                OrigenAfirmacion.USUARIO);
        i.ejecuciones.guardar(new Ejecucion(Uuid7.en(i.reloj.ahora()), YO, INST, IdTecnica.de("T01"), 1, Optional.empty(), Json.VACIO, Json.VACIO, Json.VACIO,
                "Mapa.", Optional.empty(), "clave-" + UUID.randomUUID(), i.reloj.ahora()), List.of(a), List.of());
        return a.afirmacionId();
    }

    @Test
    void la_ficha_con_su_documento_sus_fuentes_y_su_veredicto_viaja_de_una_instalacion_a_otra() {
        Instalacion origen = new Instalacion();
        UUID trafico = afirmacion(origen, "El centro tiene más tráfico peatonal que el barrio.");
        Documento d = origen.biblioteca.crear(YO, INST, new Biblioteca.NuevoDocumento(UUID.randomUUID(), "conteo-peatonal-municipio-2025.pdf",
                Documento.Tipo.PDF, "hash-conteo", "%PDF-1.4 conteo".getBytes(StandardCharsets.ISO_8859_1)));
        origen.biblioteca.indexar(YO, d.id(), List.of(new Fragmento.Nuevo(0, "En el centro pasan 1.200 personas por hora.", Optional.of(2))), Optional.of(3));
        UUID fragmento = origen.biblioteca.fragmentos(YO, d.id()).getFirst().id();
        origen.fichas.marcarPreguntas(YO, INST, trafico, List.of("¿De cuándo es el dato?"), CONFIG);
        origen.fichas.registrarFuente(YO, INST, trafico, new FichaDeVerificacion.BorradorFuente("Conteo peatonal del municipio", "Oficina de movilidad",
                "2025-03-15", "primaria", "no_aplica", "municipio", true, true, 4, 5, 4, 4, 3, "El municipio.", "", "", "apoya", "", fragmento.toString(),
                "usuario", ""), CONFIG);
        origen.fichas.registrarFuente(YO, INST, trafico, new FichaDeVerificacion.BorradorFuente("Informe de la cámara de comercio", null, "2024-11-01",
                "secundaria", "no_aplica", "cámara de comercio", true, false, 4, 4, 3, 3, 4, "", "", "", "apoya",
                "El centro concentra el mayor flujo de compradores.", "", "usuario", ""), CONFIG);
        origen.fichas.guardarVeredicto(YO, INST, trafico, Optional.of(80), "clave-veredicto", CONFIG);

        String archivo = origen.respaldo.exportarComoTexto(YO, INST, "dueña");
        PaqueteDatos paquete = MapeadorJson.mapper().readValue(archivo, PaqueteDatos.class);
        assertThat(paquete.version()).isEqualTo(5);
        assertThat(paquete.documentos()).singleElement().satisfies(x -> {
            assertThat(x.nombre()).isEqualTo("conteo-peatonal-municipio-2025.pdf");
            assertThat(x.fragmentos()).extracting(PaqueteDatos.FragmentoDatos::texto).containsExactly("En el centro pasan 1.200 personas por hora.");
        });
        assertThat(archivo).doesNotContain("%PDF-1.4 conteo");
        assertThat(paquete.evidencias()).hasSize(2);
        assertThat(paquete.verificaciones()).singleElement().satisfies(v -> assertThat(v.preguntas()).containsExactly("¿De cuándo es el dato?"));
        assertThat(paquete.veredictos()).anySatisfy(v -> {
            assertThat(v.afirmacionId()).isEqualTo(trafico);
            assertThat(v.estado()).isEqualTo("verificada");
            assertThat(v.confianza()).isEqualTo(80);
        });

        Instalacion destino = new Instalacion();
        destino.respaldo.importarTexto(YO, INST, archivo);
        destino.respaldo.importarTexto(YO, INST, archivo);

        assertThat(destino.biblioteca.porId(YO, d.id())).hasValueSatisfying(x -> {
            assertThat(x.estado()).isEqualTo(Documento.Estado.INDEXADO);
            assertThat(x.compartido()).isFalse();
            assertThat(x.conOriginal()).isFalse();
        });
        assertThat(destino.cola.tomar(destino.reloj.ahora(), Set.of("vectorizar"))).isPresent();
        assertThat(destino.evidencias.deAfirmacion(YO, trafico)).hasSize(2).first().satisfies(e -> {
            assertThat(e.fragmentoId()).contains(fragmento);
            assertThat(e.fuente().documentoId()).contains(d.id());
            assertThat(e.fuente().craap()).contains(new pensamiento.nucleo.FichaFuente.Craap(4, 5, 4, 4, 3));
            assertThat(e.fuente().sift().investigue()).isEqualTo("El municipio.");
        });
        FichaDeVerificacion.Ficha ficha = destino.fichas.abrir(YO, trafico, CONFIG).orElseThrow();
        assertThat(ficha.afirmacion().estado()).isEqualTo(EstadoAfirmacion.VERIFICADA);
        assertThat(ficha.afirmacion().confianza()).contains(80);
        assertThat(ficha.preguntas()).filteredOn(FichaDeVerificacion.Pregunta::respondida).extracting(FichaDeVerificacion.Pregunta::texto)
                .containsExactly("¿De cuándo es el dato?");
    }

    @Test
    void una_evidencia_sobre_una_afirmacion_que_no_es_de_sus_ejecuciones_rechaza_el_archivo() {
        Instalacion origen = new Instalacion();
        UUID trafico = afirmacion(origen, "El centro tiene más tráfico.");
        origen.fichas.registrarFuente(YO, INST, trafico, new FichaDeVerificacion.BorradorFuente("Conteo", null, "2025-03-15", "primaria", "no_aplica",
                "municipio", true, true, null, null, null, null, null, "", "", "", "apoya", "Pasan 1.200.", "", "usuario", ""), CONFIG);
        PaqueteDatos paquete = origen.respaldo.exportar(YO, INST, "dueña");
        PaqueteDatos.EvidenciaDatos e = paquete.evidencias().getFirst();
        PaqueteDatos alterado = new PaqueteDatos(paquete.formato(), paquete.version(), paquete.exportadoEn(), paquete.persona(), paquete.configuraciones(),
                paquete.expedientes(), paquete.ejecuciones(), paquete.sesiones(), paquete.documentos(),
                List.of(new PaqueteDatos.EvidenciaDatos(e.id(), UUID.randomUUID(), null, e.pasaje(), e.postura(), e.fuerza(), e.etiquetadaPor(), e.adoptada(),
                        e.fuente())), paquete.verificaciones(), paquete.veredictos());

        assertThatThrownBy(() -> new Instalacion().respaldo.importar(YO, INST, alterado)).isInstanceOf(ServicioRespaldo.ArchivoInvalido.class)
                .hasMessage("Una evidencia del archivo apunta a una afirmación que no es de sus ejecuciones.");
    }
}
