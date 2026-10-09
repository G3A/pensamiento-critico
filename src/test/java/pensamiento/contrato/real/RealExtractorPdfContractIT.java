package pensamiento.contrato.real;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import pensamiento.biblioteca.ExtractorPdfProceso;
import pensamiento.contrato.ExtractorPdfContract;
import pensamiento.nucleo.puertos.ExtractorPdf;
import pensamiento.testutil.PdfMinimo;

/** Contra pdftotext de poppler como proceso hijo, con PDF de verdad escritos al correr. */
@EnabledIfEnvironmentVariable(named = "CONTRACT_REAL", matches = "true")
class RealExtractorPdfContractIT extends ExtractorPdfContract {

    @Override
    protected ExtractorPdf extractor() {
        return new ExtractorPdfProceso(Duration.ofSeconds(60), LIMITE);
    }

    @Override
    protected byte[] dadoUnPdf(List<String> paginas) {
        return PdfMinimo.de(paginas);
    }

    @Override
    protected byte[] dadoUnPdfEscaneado() {
        return PdfMinimo.de(List.of(""));
    }
}
