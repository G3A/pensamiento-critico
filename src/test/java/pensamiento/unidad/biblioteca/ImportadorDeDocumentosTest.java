package pensamiento.unidad.biblioteca;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import pensamiento.biblioteca.ImportadorDeDocumentos;
import pensamiento.biblioteca.PayloadDocumento;
import pensamiento.nucleo.Documento;
import pensamiento.nucleo.Trabajo;
import pensamiento.nucleo.puertos.ImportadorDocumentos;
import pensamiento.nucleo.puertos.RegistroAuditoria;
import pensamiento.testutil.PdfMinimo;
import pensamiento.testutil.builders.Contextos;
import pensamiento.testutil.fakes.FakeBiblioteca;
import pensamiento.testutil.fakes.FakeColaTrabajos;
import pensamiento.testutil.fakes.FakeRegistroAuditoria;
import pensamiento.testutil.fakes.FakeReloj;

/**
 * RNF-08, collaboration test del importador: el tipo sale del contenido (no de la extensión), 50 MB como máximo, nada de
 * imágenes ni ejecutables, el documento queda privado y en proceso, con su indexado en la cola y la importación auditada.
 */
class ImportadorDeDocumentosTest {

    private final FakeBiblioteca biblioteca = new FakeBiblioteca();
    private final FakeColaTrabajos cola = new FakeColaTrabajos();
    private final FakeRegistroAuditoria auditoria = new FakeRegistroAuditoria();
    private final FakeReloj reloj = new FakeReloj();
    private final ImportadorDeDocumentos importador = new ImportadorDeDocumentos(biblioteca, cola, auditoria, reloj);

    private ImportadorDocumentos.Importacion importar(String nombre, byte[] contenido) {
        return importador.importar(Contextos.DUENA_DE_LA_PANADERIA, Contextos.INSTITUCION, nombre, contenido);
    }

    @Test
    void un_pdf_llamado_txt_entra_como_pdf_privado_en_proceso_con_su_indexado_en_la_cola_y_la_auditoria() {
        byte[] pdf = PdfMinimo.de(List.of("Conteo peatonal del municipio."));

        ImportadorDocumentos.Importacion i = importar("conteo.txt", pdf);

        assertThat(i).isInstanceOfSatisfying(ImportadorDocumentos.Importado.class, ok -> {
            Documento d = ok.documento();
            assertThat(d.tipo()).isEqualTo(Documento.Tipo.PDF);
            assertThat(d.estado()).isEqualTo(Documento.Estado.EN_PROCESO);
            assertThat(d.compartido()).isFalse();
            assertThat(d.nombre()).isEqualTo("conteo.txt");
            assertThat(cola.tomar(reloj.ahora(), Set.of("indexar"))).hasValueSatisfying(t -> {
                assertThat(PayloadDocumento.de(t.payload())).isEqualTo(
                        new PayloadDocumento(Contextos.DUENA_DE_LA_PANADERIA, Contextos.INSTITUCION, d.id()));
                assertThat(t.estado()).isEqualTo(Trabajo.Estado.EN_PROCESO);
            });
            assertThat(auditoria.todos()).singleElement().satisfies(e -> {
                assertThat(e.accion()).isEqualTo(RegistroAuditoria.Accion.IMPORTAR);
                assertThat(e.objetoTipo()).isEqualTo("documento");
                assertThat(e.objetoId()).contains(d.id());
            });
        });
    }

    @Test
    void markdown_texto_y_csv_se_reconocen_por_su_contenido() {
        assertThat(importar("a", DetectorTipoTest.recurso("biblioteca/encuesta-clientes-panaderia.md")))
                .isInstanceOfSatisfying(ImportadorDocumentos.Importado.class, ok -> assertThat(ok.documento().tipo()).isEqualTo(Documento.Tipo.MARKDOWN));
        assertThat(importar("b", DetectorTipoTest.recurso("biblioteca/acta-junta-vecinos-marzo.txt")))
                .isInstanceOfSatisfying(ImportadorDocumentos.Importado.class, ok -> assertThat(ok.documento().tipo()).isEqualTo(Documento.Tipo.TEXTO));
        assertThat(importar("c", DetectorTipoTest.recurso("biblioteca/ventas-sucursales-2026.csv")))
                .isInstanceOfSatisfying(ImportadorDocumentos.Importado.class, ok -> assertThat(ok.documento().tipo()).isEqualTo(Documento.Tipo.CSV));
    }

    @Test
    void una_imagen_llamada_pdf_se_rechaza_y_no_deja_nada() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 13};

        assertThat(importar("contrato.pdf", png)).isEqualTo(new ImportadorDocumentos.Rechazado("Tipo no permitido: solo PDF, Markdown, texto o CSV."));
        assertThat(biblioteca.visibles(Contextos.DUENA_DE_LA_PANADERIA)).isEmpty();
        assertThat(cola.tomar(reloj.ahora(), Set.of("indexar"))).isEmpty();
        assertThat(auditoria.todos()).isEmpty();
    }

    @Test
    void un_archivo_vacio_o_de_mas_de_50_mb_se_rechaza() {
        byte[] grande = new byte[(int) ImportadorDocumentos.TAMANO_MAXIMO + 1];
        java.util.Arrays.fill(grande, (byte) 'a');

        assertThat(importar("vacio.txt", new byte[0])).isEqualTo(new ImportadorDocumentos.Rechazado("El archivo está vacío."));
        assertThat(importar("grande.txt", grande)).isEqualTo(new ImportadorDocumentos.Rechazado("El archivo pasa de 50 MB."));
    }

    @Test
    void importar_dos_veces_lo_mismo_se_rechaza_con_el_nombre_del_primero() {
        byte[] acta = "Acta de la reunión de la junta.".getBytes(StandardCharsets.UTF_8);
        importar("acta-marzo.txt", acta);

        assertThat(importar("copia.txt", acta)).isEqualTo(new ImportadorDocumentos.Rechazado("Ya importaste este documento: acta-marzo.txt"));
        assertThat(biblioteca.visibles(Contextos.DUENA_DE_LA_PANADERIA)).hasSize(1);
    }

    @Test
    void del_nombre_solo_queda_el_ultimo_tramo_sin_la_ruta() {
        assertThat(importar("C:\\Documentos\\junta\\acta-marzo.txt", "Acta.".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(ImportadorDocumentos.Importado.class, ok -> assertThat(ok.documento().nombre()).isEqualTo("acta-marzo.txt"));
        assertThat(importar("../../etc/passwd", "Otro texto.".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(ImportadorDocumentos.Importado.class, ok -> assertThat(ok.documento().nombre()).isEqualTo("passwd"));
    }
}
