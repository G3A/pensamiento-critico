package pensamiento.graficos;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Locale;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/**
 * Saneador de SVG por lista blanca: solo los elementos y atributos que Graphviz necesita para dibujar.
 * Elimina script, foreignObject, manejadores de eventos (on*), enlaces (href, xlink:href) y estilos con
 * url(). Lo que no está en la lista, se quita. El resultado es SVG inerte listo para insertarse por htmx.
 */
public final class SaneadorSvg {

    static final Set<String> ELEMENTOS = Set.of(
            "svg", "g", "title", "desc", "path", "polygon", "polyline", "ellipse", "circle", "rect", "line", "text", "tspan", "defs");

    static final Set<String> ATRIBUTOS = Set.of(
            "id", "class", "width", "height", "viewbox", "xmlns", "version", "transform", "d", "points", "cx", "cy", "rx", "ry", "r",
            "x", "y", "x1", "y1", "x2", "y2", "fill", "stroke", "stroke-width", "stroke-dasharray", "font-family", "font-size",
            "font-weight", "text-anchor", "dominant-baseline", "opacity", "fill-opacity", "stroke-opacity", "role", "aria-label");

    public static class SvgInvalido extends RuntimeException {
        public SvgInvalido(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }

    private SaneadorSvg() {
    }

    public static String sanear(String svg) {
        Document documento;
        try {
            DocumentBuilderFactory fabrica = DocumentBuilderFactory.newInstance();
            fabrica.setNamespaceAware(false);
            fabrica.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            fabrica.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            fabrica.setFeature("http://xml.org/sax/features/external-general-entities", false);
            fabrica.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            fabrica.setXIncludeAware(false);
            fabrica.setExpandEntityReferences(false);
            DocumentBuilder constructor = fabrica.newDocumentBuilder();
            documento = constructor.parse(new InputSource(new StringReader(sinDoctype(svg))));
        } catch (Exception e) {
            throw new SvgInvalido("El SVG no se pudo interpretar", e);
        }
        Element raiz = documento.getDocumentElement();
        if (raiz == null || !"svg".equalsIgnoreCase(raiz.getTagName())) {
            throw new SvgInvalido("El documento no es un SVG", null);
        }
        limpiar(raiz);
        try {
            Transformer t = TransformerFactory.newInstance().newTransformer();
            t.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            StringWriter salida = new StringWriter();
            t.transform(new DOMSource(raiz), new StreamResult(salida));
            return salida.toString();
        } catch (Exception e) {
            throw new SvgInvalido("No se pudo serializar el SVG saneado", e);
        }
    }

    private static String sinDoctype(String svg) {
        int inicio = svg.indexOf("<!DOCTYPE");
        if (inicio < 0) {
            return svg;
        }
        int fin = svg.indexOf('>', inicio);
        return fin < 0 ? svg : svg.substring(0, inicio) + svg.substring(fin + 1);
    }

    /** Elementos que no dibujan pero envuelven dibujo (enlaces de Graphviz con URL): se quitan y sus hijos suben. */
    static final Set<String> ENVOLTORIOS = Set.of("a", "switch");

    private static void limpiar(Element elemento) {
        NodeList hijos = elemento.getChildNodes();
        for (int i = hijos.getLength() - 1; i >= 0; i--) {
            Node hijo = hijos.item(i);
            if (hijo.getNodeType() == Node.ELEMENT_NODE) {
                Element e = (Element) hijo;
                String nombre = e.getTagName().toLowerCase(Locale.ROOT);
                if (ENVOLTORIOS.contains(nombre)) {
                    limpiar(e);
                    while (e.getFirstChild() != null) {
                        elemento.insertBefore(e.getFirstChild(), e);
                    }
                    elemento.removeChild(e);
                } else if (!ELEMENTOS.contains(nombre)) {
                    elemento.removeChild(e);
                } else {
                    limpiar(e);
                }
            } else if (hijo.getNodeType() != Node.TEXT_NODE) {
                elemento.removeChild(hijo);
            }
        }
        NamedNodeMap atributos = elemento.getAttributes();
        for (int i = atributos.getLength() - 1; i >= 0; i--) {
            Node atributo = atributos.item(i);
            String nombre = atributo.getNodeName().toLowerCase(Locale.ROOT);
            String valor = atributo.getNodeValue().toLowerCase(Locale.ROOT);
            boolean permitido = ATRIBUTOS.contains(nombre) && !valor.contains("url(") && !valor.contains("javascript:")
                    && !valor.contains("&#") && !nombre.startsWith("on");
            if (!permitido) {
                elemento.removeAttributeNode((org.w3c.dom.Attr) atributo);
            }
        }
    }
}
