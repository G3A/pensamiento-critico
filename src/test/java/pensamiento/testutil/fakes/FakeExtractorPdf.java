package pensamiento.testutil.fakes;

import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import pensamiento.nucleo.puertos.ExtractorPdf;

/**
 * Fake del extractor de PDF: devuelve las páginas que se le programaron para esos bytes, hasta su límite de páginas. Bytes
 * que no empiezan con %PDF- o que no se programaron no se pueden leer; páginas todas en blanco son un escaneo.
 * Certificado por FakeExtractorPdfContractTest.
 */
public final class FakeExtractorPdf implements ExtractorPdf {

    private final int paginasMaximas;
    private final Map<String, List<String>> programados = new LinkedHashMap<>();

    public FakeExtractorPdf(int paginasMaximas) {
        this.paginasMaximas = paginasMaximas;
    }

    /** Bytes que empiezan como un PDF, únicos por llamada, que el Fake leerá como esas páginas. */
    public byte[] programar(List<String> paginas) {
        byte[] pdf = ("%PDF-fake " + programados.size() + " " + System.nanoTime()).getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
        programados.put(HexFormat.of().formatHex(pdf), List.copyOf(paginas));
        return pdf;
    }

    /** Registra unos bytes ya existentes (por ejemplo, un PDF de verdad) con sus páginas. */
    public void programar(byte[] pdf, List<String> paginas) {
        programados.put(HexFormat.of().formatHex(pdf), List.copyOf(paginas));
    }

    @Override
    public List<String> paginas(byte[] pdf) {
        String clave = HexFormat.of().formatHex(pdf);
        List<String> paginas = programados.get(clave);
        if (paginas == null || !new String(pdf, 0, Math.min(5, pdf.length), java.nio.charset.StandardCharsets.ISO_8859_1).equals("%PDF-")) {
            throw new PdfIlegible("El PDF está dañado, cifrado o no es un PDF.");
        }
        List<String> leidas = paginas.subList(0, Math.min(paginas.size(), paginasMaximas)).stream().map(String::strip).toList();
        if (leidas.stream().allMatch(String::isBlank)) {
            throw new PdfSinTexto();
        }
        return leidas;
    }
}
