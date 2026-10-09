package pensamiento.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pensamiento.nucleo.puertos.ExtractorPdf;

/**
 * Contrato del extractor de PDF (RNF-08, sin OCR): cada página sale en orden, con tildes, eñe y comillas latinas, sin
 * espacios en los bordes; no pasa del límite de páginas; un PDF sin texto (escaneado) y un archivo que no es PDF se
 * rechazan con su motivo.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class ExtractorPdfContract {

    /** Cuántas páginas lee como máximo el extractor de esta prueba. */
    protected static final int LIMITE = 3;

    protected abstract ExtractorPdf extractor();

    /** Un PDF con una página por texto (cada texto, una línea). */
    protected abstract byte[] dadoUnPdf(List<String> paginas);

    /** Un PDF de una página sin texto: solo una imagen, como un escaneo. */
    protected abstract byte[] dadoUnPdfEscaneado();

    @Test
    void cada_pagina_sale_en_orden_con_tildes_y_enie() {
        List<String> paginas = List.of("Conteo peatonal del municipio, marzo de 2025.",
                "En el centro pasan en promedio 1.200 personas por hora; en el barrio, 300.",
                "Advertencias: «los conteos no distinguen» entre quien compra y quien pasa (año del muestreo).");

        assertThat(extractor().paginas(dadoUnPdf(paginas))).containsExactlyElementsOf(paginas);
    }

    @Test
    void no_pasa_del_limite_de_paginas() {
        List<String> seis = List.of("Página uno.", "Página dos.", "Página tres.", "Página cuatro.", "Página cinco.", "Página seis.");

        assertThat(extractor().paginas(dadoUnPdf(seis))).containsExactly("Página uno.", "Página dos.", "Página tres.");
    }

    @Test
    void un_pdf_escaneado_no_tiene_texto_extraible() {
        assertThatThrownBy(() -> extractor().paginas(dadoUnPdfEscaneado())).isInstanceOf(ExtractorPdf.PdfSinTexto.class)
                .hasMessage("Sin texto extraíble (es imagen): la app no hace OCR.");
    }

    @Test
    void lo_que_no_es_pdf_no_se_puede_leer() {
        assertThatThrownBy(() -> extractor().paginas("Esto es texto plano, no un PDF.".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(ExtractorPdf.PdfIlegible.class);
        assertThatThrownBy(() -> extractor().paginas(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 13}))
                .isInstanceOf(ExtractorPdf.PdfIlegible.class);
    }
}
