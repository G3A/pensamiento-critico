package pensamiento.unidad.biblioteca;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import pensamiento.biblioteca.ImportadorDeDocumentos;
import pensamiento.biblioteca.IndexadorDocumentos;
import pensamiento.biblioteca.VectorizadorDocumentos;
import pensamiento.nucleo.Documento;
import pensamiento.nucleo.Fragmento;
import pensamiento.nucleo.Pasaje;
import pensamiento.nucleo.Trabajo;
import pensamiento.nucleo.puertos.ImportadorDocumentos;
import pensamiento.nucleo.puertos.ProcesadorTrabajo;
import pensamiento.testutil.builders.Contextos;
import pensamiento.testutil.fakes.FakeBiblioteca;
import pensamiento.testutil.fakes.FakeColaTrabajos;
import pensamiento.testutil.fakes.FakeExtractorPdf;
import pensamiento.testutil.fakes.FakeIa;
import pensamiento.testutil.fakes.FakeRegistroAuditoria;
import pensamiento.testutil.fakes.FakeReloj;
import pensamiento.testutil.fakes.FakeTransaccionComoUsuario;

/**
 * Los dos trabajos largos de la biblioteca con Fakes: indexar saca el texto, trocea, deja el documento indexado y encola el
 * vectorizado; un PDF escaneado o ilegible queda en error con su motivo; vectorizar llena los vectores de a poco y, sin
 * Ollama, vuelve a la cola en un minuto.
 */
class IndexadoYVectorizadoTest {

    private final FakeBiblioteca biblioteca = new FakeBiblioteca();
    private final FakeColaTrabajos cola = new FakeColaTrabajos();
    private final FakeReloj reloj = new FakeReloj();
    private final FakeExtractorPdf extractor = new FakeExtractorPdf(300);
    private final FakeTransaccionComoUsuario transaccion = new FakeTransaccionComoUsuario();
    private final FakeIa ia = new FakeIa();
    private final ImportadorDeDocumentos importador = new ImportadorDeDocumentos(biblioteca, cola, new FakeRegistroAuditoria(), reloj);
    private final IndexadorDocumentos indexador = new IndexadorDocumentos(biblioteca, extractor, cola, transaccion, reloj);
    private final VectorizadorDocumentos vectorizador = new VectorizadorDocumentos(biblioteca, ia, transaccion, reloj, Duration.ofSeconds(10));

    private Documento importar(String nombre, byte[] contenido) {
        return ((ImportadorDocumentos.Importado) importador.importar(Contextos.DUENA_DE_LA_PANADERIA, Contextos.INSTITUCION, nombre, contenido))
                .documento();
    }

    private ProcesadorTrabajo.Desenlace correr(ProcesadorTrabajo p) {
        Trabajo t = cola.tomar(reloj.ahora(), Set.of(p.tipo())).orElseThrow();
        return p.procesar(t);
    }

    private Documento leido(Documento d) {
        return biblioteca.porId(Contextos.DUENA_DE_LA_PANADERIA, d.id()).orElseThrow();
    }

    @Test
    void indexar_un_markdown_lo_deja_indexado_con_sus_fragmentos_y_encola_el_vectorizado() {
        Documento d = importar("movilidad.md", DetectorTipoTest.recurso("biblioteca/movilidad-centro-barrio.md"));

        assertThat(correr(indexador)).isEqualTo(new ProcesadorTrabajo.Hecho());

        assertThat(leido(d).estado()).isEqualTo(Documento.Estado.INDEXADO);
        assertThat(leido(d).fragmentos()).isGreaterThan(1);
        assertThat(biblioteca.buscarPorTexto(Contextos.DUENA_DE_LA_PANADERIA, "promedio", 5)).extracting(Pasaje::texto)
                .anySatisfy(t -> assertThat(t).contains("En el centro pasan en promedio 1.200 personas por hora; en el barrio, 300."));
        assertThat(cola.tomar(reloj.ahora(), Set.of("vectorizar"))).isPresent();
    }

    @Test
    void indexar_un_pdf_guarda_la_pagina_de_cada_fragmento_y_las_paginas_del_documento() {
        byte[] pdf = extractor.programar(List.of("Método del conteo.", "En el centro pasan 1.200 personas por hora.", "Advertencias."));
        Documento d = importar("conteo-peatonal-municipio-2025.pdf", pdf);

        correr(indexador);

        assertThat(leido(d).paginas()).contains(3);
        assertThat(biblioteca.fragmentos(Contextos.DUENA_DE_LA_PANADERIA, d.id())).extracting(Fragmento::texto, Fragmento::pagina).containsExactly(
                org.assertj.core.groups.Tuple.tuple("Método del conteo.", java.util.Optional.of(1)),
                org.assertj.core.groups.Tuple.tuple("En el centro pasan 1.200 personas por hora.", java.util.Optional.of(2)),
                org.assertj.core.groups.Tuple.tuple("Advertencias.", java.util.Optional.of(3)));
    }

    @Test
    void un_pdf_escaneado_queda_en_error_porque_la_app_no_hace_ocr() {
        Documento d = importar("contrato-escaneado.pdf", extractor.programar(List.of("")));

        assertThat(correr(indexador)).isEqualTo(new ProcesadorTrabajo.Hecho());

        assertThat(leido(d).estado()).isEqualTo(Documento.Estado.ERROR);
        assertThat(leido(d).error()).contains("Sin texto extraíble (es imagen): la app no hace OCR.");
        assertThat(cola.tomar(reloj.ahora(), Set.of("vectorizar"))).isEmpty();
    }

    @Test
    void un_pdf_que_el_extractor_no_puede_leer_queda_en_error_con_su_motivo() {
        Documento d = importar("roto.pdf", "%PDF-1.4 roto".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));

        correr(indexador);

        assertThat(leido(d).error()).contains("El PDF está dañado, cifrado o no es un PDF.");
    }

    @Test
    void un_documento_borrado_antes_de_indexarse_no_deja_nada() {
        Documento d = importar("borrable.md", "# Notas\n\nHornear a 190 grados.".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        biblioteca.borrar(Contextos.DUENA_DE_LA_PANADERIA, d.id());

        assertThat(correr(indexador)).isEqualTo(new ProcesadorTrabajo.Hecho());
        assertThat(cola.tomar(reloj.ahora(), Set.of("vectorizar"))).isEmpty();
    }

    @Test
    void vectorizar_llena_todos_los_vectores_de_a_lotes() {
        Documento d = importar("acta.txt", DetectorTipoTest.recurso("biblioteca/acta-junta-vecinos-marzo.txt"));
        correr(indexador);

        assertThat(correr(vectorizador)).isEqualTo(new ProcesadorTrabajo.Hecho());

        assertThat(leido(d).porcentajeVectorizado()).isEqualTo(100);
        assertThat(leido(d).conVector()).isEqualTo(leido(d).fragmentos());
    }

    @Test
    void sin_ollama_el_vectorizado_vuelve_a_la_cola_en_un_minuto_y_el_documento_sigue_buscable_por_texto() {
        Documento d = importar("acta.txt", DetectorTipoTest.recurso("biblioteca/acta-junta-vecinos-marzo.txt"));
        correr(indexador);
        ia.apagar();

        assertThat(correr(vectorizador)).isEqualTo(new ProcesadorTrabajo.Reintentar(reloj.ahora().plus(Duration.ofMinutes(1)),
                "Ollama no responde: se reintenta en 1 minuto."));
        assertThat(leido(d).conVector()).isZero();
        assertThat(biblioteca.buscarPorTexto(Contextos.DUENA_DE_LA_PANADERIA, "postes", 5)).isNotEmpty();
    }
}
