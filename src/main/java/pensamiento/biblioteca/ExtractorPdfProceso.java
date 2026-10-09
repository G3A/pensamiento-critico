package pensamiento.biblioteca;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import pensamiento.graficos.ProcesoHijo;
import pensamiento.nucleo.puertos.ExtractorPdf;

/**
 * Texto de un PDF con pdftotext de poppler como proceso hijo (sección 4, modelo de amenazas): tiempo máximo (60 s) y límite
 * de páginas, sin OCR (RNF-08). El archivo pasa por un temporal en /tmp (tmpfs del contenedor) que se borra al terminar.
 * pdftotext separa las páginas con un salto de página; cada página sale sin espacios en los bordes.
 */
public class ExtractorPdfProceso implements ExtractorPdf {

    private final Duration tiempoMaximo;
    private final int paginasMaximas;

    public ExtractorPdfProceso(Duration tiempoMaximo, int paginasMaximas) {
        this.tiempoMaximo = tiempoMaximo;
        this.paginasMaximas = paginasMaximas;
    }

    @Override
    public List<String> paginas(byte[] pdf) {
        Path temporal;
        try {
            temporal = Files.createTempFile("pdf-", ".pdf");
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo crear el temporal del PDF", e);
        }
        try {
            Files.write(temporal, pdf);
            return extraer(temporal);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo escribir el temporal del PDF", e);
        } finally {
            try {
                Files.deleteIfExists(temporal);
            } catch (IOException e) {
                // El temporal vive en el tmpfs del contenedor: se va con él.
            }
        }
    }

    private List<String> extraer(Path pdf) {
        ProcesoHijo.Salida salida;
        try {
            salida = ProcesoHijo.ejecutar(List.of("pdftotext", "-q", "-enc", "UTF-8", "-f", "1", "-l", String.valueOf(paginasMaximas),
                    pdf.toString(), "-"), null, tiempoMaximo);
        } catch (ProcesoHijo.TiempoAgotado e) {
            throw new PdfIlegible("La extracción superó " + tiempoMaximo.toSeconds() + " segundos.");
        } catch (ProcesoHijo.NoSePudoEjecutar e) {
            throw new PdfIlegible("pdftotext no está disponible.");
        }
        if (!salida.exitoso()) {
            throw new PdfIlegible("El PDF está dañado, cifrado o no es un PDF.");
        }
        List<String> paginas = new ArrayList<>();
        String[] partes = salida.stdout().split("\f", -1);
        // pdftotext cierra cada página con un salto de página: el último trozo, vacío, no es una página.
        int cuantas = partes.length > 0 && partes[partes.length - 1].isBlank() ? partes.length - 1 : partes.length;
        for (int i = 0; i < cuantas; i++) {
            paginas.add(partes[i].strip());
        }
        if (paginas.stream().allMatch(String::isBlank)) {
            throw new PdfSinTexto();
        }
        return paginas;
    }
}
