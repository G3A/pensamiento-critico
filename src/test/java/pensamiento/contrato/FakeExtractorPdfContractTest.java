package pensamiento.contrato;

import java.util.List;

import pensamiento.nucleo.puertos.ExtractorPdf;
import pensamiento.testutil.fakes.FakeExtractorPdf;

class FakeExtractorPdfContractTest extends ExtractorPdfContract {

    private final FakeExtractorPdf fake = new FakeExtractorPdf(LIMITE);

    @Override
    protected ExtractorPdf extractor() {
        return fake;
    }

    @Override
    protected byte[] dadoUnPdf(List<String> paginas) {
        return fake.programar(paginas);
    }

    @Override
    protected byte[] dadoUnPdfEscaneado() {
        return fake.programar(List.of(""));
    }
}
