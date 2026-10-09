package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

/**
 * El lenguaje de la biblioteca y de la ficha de verificación para las pruebas de aceptación (hito 6), sobre el cliente HTTP:
 * importar un documento y esperar a que se indexe, buscar desde una afirmación, registrar fuentes y guardar el veredicto.
 */
public final class Verificacion {

    private final ClienteApp persona;

    private Verificacion(ClienteApp persona) {
        this.persona = persona;
    }

    public static Verificacion de(ClienteApp persona) {
        return new Verificacion(persona);
    }

    public ClienteApp cliente() {
        return persona;
    }

    /** Lo que la persona escribe en la ficha de fuente. */
    public record Fuente(String titulo, String fecha, String tipo, String diseno, String grupo, boolean independiente, boolean original,
                         List<Integer> craap, String postura, String pasaje, String fragmento) {
    }

    // -------------------------------------------------------------------------------------------
    // Biblioteca (P13)
    // -------------------------------------------------------------------------------------------

    public ClienteApp.Respuesta importar(String nombre, byte[] contenido) {
        return persona.postArchivo("/biblioteca", "archivo", nombre, contenido);
    }

    /** Importa y devuelve el identificador del documento por su nombre en la lista. */
    public UUID importarDocumento(String nombre, byte[] contenido) {
        ClienteApp.Respuesta r = importar(nombre, contenido);
        assertThat(r.estado()).as("importar " + nombre + ": " + r.cuerpo()).isEqualTo(200);
        return documento(Jsoup.parseBodyFragment(r.cuerpo()), nombre);
    }

    public Document lista() {
        ClienteApp.Respuesta r = persona.getHtmx("/biblioteca/lista");
        assertThat(r.estado()).isEqualTo(200);
        return Jsoup.parseBodyFragment(r.cuerpo());
    }

    private static UUID documento(Document lista, String nombre) {
        Element li = lista.select("li[data-documento]").stream().filter(x -> x.select(".nombre-documento").text().equals(nombre)).findFirst()
                .orElseThrow(() -> new AssertionError("El documento " + nombre + " no está en la lista"));
        return UUID.fromString(li.attr("data-documento"));
    }

    /** Espera a que el trabajo largo deje el documento en ese estado ("indexado" o "error"); devuelve su fila. */
    public Element esperarEstado(UUID documento, String estado, Duration tiempoMaximo) {
        Instant limite = Instant.now().plus(tiempoMaximo);
        Element fila = null;
        while (Instant.now().isBefore(limite)) {
            fila = lista().selectFirst("li[data-documento=" + documento + "]");
            if (fila != null && fila.attr("data-estado").equals(estado)) {
                return fila;
            }
            pausa();
        }
        throw new AssertionError("El documento " + documento + " no llegó a «" + estado + "»: " + (fila == null ? "no está" : fila.text()));
    }

    private static void pausa() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrumpido", e);
        }
    }

    /** Busca por palabras (determinista) o por similitud, desde una afirmación o desde la biblioteca. */
    public Document buscar(String consulta, boolean porPalabras, UUID afirmacion) {
        String ruta = "/biblioteca/buscar?q=" + URLEncoder.encode(consulta, StandardCharsets.UTF_8) + (porPalabras ? "&palabras=true" : "")
                + (afirmacion == null ? "" : "&afirmacion=" + afirmacion);
        ClienteApp.Respuesta r = persona.getHtmx(ruta);
        assertThat(r.estado()).as("buscar " + consulta).isEqualTo(200);
        return Jsoup.parseBodyFragment(r.cuerpo());
    }

    // -------------------------------------------------------------------------------------------
    // Ficha de verificación (P10) y ficha de fuente (P12)
    // -------------------------------------------------------------------------------------------

    public Document ficha(UUID afirmacion) {
        ClienteApp.Respuesta r = persona.get("/verificar/" + afirmacion);
        assertThat(r.estado()).as("abrir la ficha").isEqualTo(200);
        return Jsoup.parse(r.cuerpo());
    }

    public Document elegirTipo(UUID afirmacion, String tipo) {
        ClienteApp.Respuesta r = persona.postFormulario("/verificar/" + afirmacion + "/tipo", Map.of("tipo", tipo), true);
        assertThat(r.estado()).isEqualTo(200);
        return Jsoup.parseBodyFragment(r.cuerpo());
    }

    public Document marcarPreguntas(UUID afirmacion, List<String> respondidas) {
        List<Map.Entry<String, String>> pares = new ArrayList<>();
        respondidas.forEach(p -> pares.add(new AbstractMap.SimpleEntry<>("respondida", p)));
        ClienteApp.Respuesta r = persona.postPares("/verificar/" + afirmacion + "/preguntas", pares, true);
        assertThat(r.estado()).isEqualTo(200);
        return Jsoup.parseBodyFragment(r.cuerpo());
    }

    /** El formulario de la ficha de fuente, desde la biblioteca si se da el fragmento. */
    public Document nuevaFuente(UUID afirmacion, UUID fragmento) {
        ClienteApp.Respuesta r = persona.get("/verificar/" + afirmacion + "/fuentes/nueva" + (fragmento == null ? "" : "?fragmento=" + fragmento));
        assertThat(r.estado()).isEqualTo(200);
        return Jsoup.parse(r.cuerpo());
    }

    public Document previa(UUID afirmacion, Fuente f) {
        ClienteApp.Respuesta r = persona.postPares("/verificar/" + afirmacion + "/fuentes/previa", pares(f), true);
        assertThat(r.estado()).isEqualTo(200);
        return Jsoup.parseBodyFragment(r.cuerpo());
    }

    /** Guarda la fuente y vuelve a la ficha (htmx: 200 con HX-Redirect). */
    public ClienteApp.Respuesta registrar(UUID afirmacion, Fuente f) {
        return persona.postPares("/verificar/" + afirmacion + "/fuentes", pares(f), true);
    }

    private static List<Map.Entry<String, String>> pares(Fuente f) {
        List<Map.Entry<String, String>> p = new ArrayList<>();
        p.add(par("titulo", f.titulo()));
        p.add(par("fecha", f.fecha()));
        p.add(par("tipoFuente", f.tipo()));
        p.add(par("diseno", f.diseno()));
        p.add(par("grupo", f.grupo()));
        if (f.independiente()) {
            p.add(par("independiente", "true"));
        }
        if (f.original()) {
            p.add(par("original", "true"));
        }
        List<String> criterios = List.of("actualidad", "relevancia", "autoridad", "exactitud", "proposito");
        for (int i = 0; i < f.craap().size(); i++) {
            p.add(par(criterios.get(i), String.valueOf(f.craap().get(i))));
        }
        p.add(par("postura", f.postura()));
        p.add(par("pasaje", f.pasaje()));
        p.add(par("fragmentoId", f.fragmento() == null ? "" : f.fragmento()));
        p.add(par("etiquetadaPor", "usuario"));
        p.add(par("propuesta", ""));
        p.add(par("despues", "ficha"));
        return p;
    }

    private static Map.Entry<String, String> par(String nombre, String valor) {
        return new AbstractMap.SimpleEntry<>(nombre, valor == null ? "" : valor);
    }

    public ClienteApp.Respuesta veredicto(UUID afirmacion, int confianza) {
        return persona.postFormulario("/verificar/" + afirmacion + "/veredicto", Map.of("confianza", String.valueOf(confianza),
                "clave", UUID.randomUUID().toString()), true);
    }
}
