package pensamiento.web.aula;

import io.nayuki.qrcodegen.QrCode;

/**
 * El QR de la clase como SVG, armado en el servidor (hito 10, entregable 4): sin script ni imagen externa, así cabe en la CSP.
 * Un solo path con un cuadrado por módulo oscuro y un margen de 4 módulos, como pide la norma.
 */
public final class QrSvg {

    private static final int MARGEN = 4;

    private QrSvg() {
    }

    /** El SVG del texto (una dirección), con corrección de errores media y el título accesible dado. */
    public static String de(String texto, String titulo) {
        QrCode qr = QrCode.encodeText(texto, QrCode.Ecc.MEDIUM);
        int lado = qr.size + 2 * MARGEN;
        StringBuilder path = new StringBuilder();
        for (int y = 0; y < qr.size; y++) {
            for (int x = 0; x < qr.size; x++) {
                if (qr.getModule(x, y)) {
                    path.append('M').append(x + MARGEN).append(',').append(y + MARGEN).append("h1v1h-1z");
                }
            }
        }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" class=\"qr\" viewBox=\"0 0 " + lado + " " + lado + "\" role=\"img\" aria-label=\""
                + escapar(titulo) + "\" shape-rendering=\"crispEdges\"><rect width=\"100%\" height=\"100%\" fill=\"#fff\"/><path d=\"" + path
                + "\" fill=\"#000\"/></svg>";
    }

    private static String escapar(String texto) {
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
