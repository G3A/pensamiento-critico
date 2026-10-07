package pensamiento.biblioteca;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import pensamiento.graficos.ProcesoHijo;

/**
 * Extracción de texto de PDF como proceso hijo (pdftotext de poppler) con tiempo máximo (60 s) y límite
 * de páginas. La biblioteca completa llega en el hito 6; aquí queda el límite de procesos exigido por RNF-05.
 */
public class ExtractorPdfProceso {

    public static class ExtraccionFallida extends RuntimeException {
        public ExtraccionFallida(String mensaje) {
            super(mensaje);
        }
    }

    private final Duration tiempoMaximo;
    private final int paginasMaximas;

    public ExtractorPdfProceso(Duration tiempoMaximo, int paginasMaximas) {
        this.tiempoMaximo = tiempoMaximo;
        this.paginasMaximas = paginasMaximas;
    }

    public String texto(Path pdf) {
        ProcesoHijo.Salida salida;
        try {
            salida = ProcesoHijo.ejecutar(
                    List.of("pdftotext", "-layout", "-f", "1", "-l", String.valueOf(paginasMaximas), pdf.toString(), "-"),
                    null, tiempoMaximo);
        } catch (ProcesoHijo.TiempoAgotado e) {
            throw new ExtraccionFallida("La extracción superó " + tiempoMaximo.toSeconds() + " segundos");
        } catch (ProcesoHijo.NoSePudoEjecutar e) {
            throw new ExtraccionFallida("pdftotext no está disponible");
        }
        if (!salida.exitoso()) {
            throw new ExtraccionFallida("El PDF no tiene texto extraíble o está dañado: " + salida.stderr().strip());
        }
        return salida.stdout();
    }
}
