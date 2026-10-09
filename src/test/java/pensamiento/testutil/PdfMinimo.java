package pensamiento.testutil;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Escribe un PDF de verdad, pequeño, para las pruebas: una página por texto, cada línea con Helvetica y la codificación
 * WinAnsi (tildes, eñe, comillas latinas). Así los documentos de prueba de la biblioteca son ficticios y se generan al
 * correr, sin binarios en el repositorio. Una página vacía no lleva texto: es lo que deja un PDF escaneado.
 */
public final class PdfMinimo {

    private static final Charset WIN_ANSI = Charset.forName("windows-1252");

    private PdfMinimo() {
    }

    /** Un PDF con una página por elemento; cada página puede tener varias líneas separadas por salto de línea. */
    public static byte[] de(List<String> paginas) {
        List<String> objetos = new ArrayList<>();
        int n = paginas.size();
        // 1 catálogo, 2 páginas, 3 fuente; después, por cada página, su objeto y su contenido.
        objetos.add("<< /Type /Catalog /Pages 2 0 R >>");
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < n; i++) {
            kids.append(4 + 2 * i).append(" 0 R ");
        }
        objetos.add("<< /Type /Pages /Kids [" + kids.toString().strip() + "] /Count " + n + " >>");
        objetos.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        List<byte[]> contenidos = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            objetos.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 3 0 R >> >> /Contents "
                    + (5 + 2 * i) + " 0 R >>");
            byte[] flujo = flujo(paginas.get(i));
            contenidos.add(flujo);
            objetos.add(null);
        }
        ByteArrayOutputStream pdf = new ByteArrayOutputStream();
        escribir(pdf, "%PDF-1.4\n%âãÏÓ\n".getBytes(StandardCharsets.ISO_8859_1));
        List<Integer> posiciones = new ArrayList<>();
        int contenido = 0;
        for (int i = 0; i < objetos.size(); i++) {
            posiciones.add(pdf.size());
            escribir(pdf, ((i + 1) + " 0 obj\n").getBytes(StandardCharsets.ISO_8859_1));
            if (objetos.get(i) == null) {
                byte[] flujo = contenidos.get(contenido++);
                escribir(pdf, ("<< /Length " + flujo.length + " >>\nstream\n").getBytes(StandardCharsets.ISO_8859_1));
                escribir(pdf, flujo);
                escribir(pdf, "\nendstream".getBytes(StandardCharsets.ISO_8859_1));
            } else {
                escribir(pdf, objetos.get(i).getBytes(StandardCharsets.ISO_8859_1));
            }
            escribir(pdf, "\nendobj\n".getBytes(StandardCharsets.ISO_8859_1));
        }
        int xref = pdf.size();
        StringBuilder tabla = new StringBuilder("xref\n0 " + (objetos.size() + 1) + "\n0000000000 65535 f \n");
        for (int p : posiciones) {
            tabla.append(String.format("%010d 00000 n \n", p));
        }
        tabla.append("trailer\n<< /Size ").append(objetos.size() + 1).append(" /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        escribir(pdf, tabla.toString().getBytes(StandardCharsets.ISO_8859_1));
        return pdf.toByteArray();
    }

    /** Un PDF de N páginas: "Página 1", "Página 2"… */
    public static byte[] dePaginas(int n) {
        List<String> paginas = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            paginas.add("Página " + i);
        }
        return de(paginas);
    }

    private static byte[] flujo(String pagina) {
        ByteArrayOutputStream s = new ByteArrayOutputStream();
        if (pagina.isBlank()) {
            // Sin texto: solo un rectángulo, como la imagen de un escaneo.
            escribir(s, "0.9 g 72 72 468 648 re f\n".getBytes(StandardCharsets.ISO_8859_1));
            return s.toByteArray();
        }
        escribir(s, "BT /F1 11 Tf 14 TL 72 720 Td\n".getBytes(StandardCharsets.ISO_8859_1));
        for (String linea : pagina.split("\n")) {
            escribir(s, "(".getBytes(StandardCharsets.ISO_8859_1));
            for (byte b : linea.getBytes(WIN_ANSI)) {
                int c = b & 0xff;
                if (c == '(' || c == ')' || c == '\\') {
                    s.write('\\');
                    s.write(c);
                } else if (c < 32 || c > 126) {
                    escribir(s, String.format("\\%03o", c).getBytes(StandardCharsets.ISO_8859_1));
                } else {
                    s.write(c);
                }
            }
            escribir(s, ") Tj T*\n".getBytes(StandardCharsets.ISO_8859_1));
        }
        escribir(s, "ET".getBytes(StandardCharsets.ISO_8859_1));
        return s.toByteArray();
    }

    private static void escribir(ByteArrayOutputStream destino, byte[] bytes) {
        destino.write(bytes, 0, bytes.length);
    }
}
