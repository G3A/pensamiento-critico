package pensamiento.nucleo.puertos;

import java.util.List;

/**
 * Saca el texto de un PDF, página por página, sin OCR (RNF-08). La implementación real corre pdftotext como proceso hijo con
 * tiempo máximo y límite de páginas (sección 4, modelo de amenazas).
 */
public interface ExtractorPdf {

    /** El PDF no se pudo leer: dañado, cifrado, o la extracción superó el tiempo máximo. */
    class PdfIlegible extends RuntimeException {
        public PdfIlegible(String mensaje) {
            super(mensaje);
        }
    }

    /** El PDF no tiene texto: es imagen (escaneado). La app no hace OCR. */
    class PdfSinTexto extends RuntimeException {
        public PdfSinTexto() {
            super("Sin texto extraíble (es imagen): la app no hace OCR.");
        }
    }

    /**
     * El texto de cada página, en orden, hasta el límite de páginas. Lanza PdfSinTexto si ninguna página tiene texto y
     * PdfIlegible si no se pudo leer.
     */
    List<String> paginas(byte[] pdf);
}
