package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.FormElement;

/**
 * DSL de la aceptación del hito 1 (capa 2 de Farley): verbos del taller sobre el driver HTTP. Cada verbo lee el
 * HTML como lo haría el navegador (jsoup) y envía lo mismo que enviaría htmx.
 */
public final class Taller {

    private static final Pattern RESULTADO_GUARDADO = Pattern.compile("id=\"res-([0-9a-f-]{36})\"");

    private final ClienteApp persona;

    private Taller(ClienteApp persona) {
        this.persona = persona;
    }

    public static Taller de(ClienteApp persona) {
        return new Taller(persona);
    }

    public ClienteApp cliente() {
        return persona;
    }

    private Document abrir(String ruta) {
        ClienteApp.Respuesta r = persona.get(ruta);
        assertThat(r.estado()).as("GET " + ruta).isEqualTo(200);
        return Jsoup.parse(r.cuerpo());
    }

    /** Desde el Inicio, elige una intención y devuelve las técnicas que el catálogo ofrece para ella. */
    public List<String> tecnicasParaLaIntencion(String tituloIntencion) {
        Document inicio = abrir("/");
        Element enlace = inicio.select(".intenciones a").stream().filter(a -> a.text().equals(tituloIntencion)).findFirst()
                .orElseThrow(() -> new AssertionError("El Inicio no ofrece la intención " + tituloIntencion));
        return abrir(enlace.attr("href")).select("li.tecnica").eachAttr("id").stream().map(id -> id.substring("tecnica-".length())).toList();
    }

    /** En la pestaña Usar de la técnica, carga el ejemplo por su título y devuelve el formulario lleno. */
    public Formulario cargarEjemplo(String tecnica, String titulo) {
        Document usar = abrir("/tecnicas/" + tecnica + "?pestana=usar");
        Element ejemplo = usar.select("nav.barra-ejemplos a.ejemplo").stream().filter(a -> a.text().endsWith(titulo)).findFirst()
                .orElseThrow(() -> new AssertionError("No está el ejemplo " + titulo));
        Document conEjemplo = abrir(ejemplo.attr("href"));
        return new Formulario(tecnica, (FormElement) conEjemplo.getElementById("form-" + tecnica));
    }

    /** Evalúa sin guardar (htmx) y devuelve la respuesta. */
    public ClienteApp.Respuesta evaluar(Formulario f) {
        return persona.postPares("/tecnicas/" + f.tecnica() + "/evaluar", f.pares(), true);
    }

    /** Guarda en historial (htmx) y devuelve el identificador de la ejecución guardada. */
    public UUID guardar(Formulario f) {
        ClienteApp.Respuesta r = persona.postPares("/tecnicas/" + f.tecnica() + "/ejecuciones", f.pares(), true);
        assertThat(r.estado()).as("guardar en historial").isEqualTo(200);
        assertThat(r.cabecera("HX-Trigger")).contains("ejecucion-guardada");
        Matcher m = RESULTADO_GUARDADO.matcher(r.cuerpo());
        assertThat(m.find()).as("el resultado guardado trae su identificador").isTrue();
        return UUID.fromString(m.group(1));
    }

    /** Asocia la ejecución a un expediente nuevo con ese nombre y devuelve el identificador del expediente. */
    public UUID asociarAUnExpedienteNuevo(UUID ejecucion, String nombre) {
        ClienteApp.Respuesta r = persona.postFormulario("/ejecuciones/" + ejecucion + "/expediente", Map.of("nuevo", nombre), true);
        assertThat(r.estado()).as("asociar a un expediente").isEqualTo(200);
        assertThat(r.cuerpo()).contains("Asociada al expediente");
        Element enlace = abrir("/expedientes").select(".lista-expedientes a").stream().filter(a -> a.text().equals(nombre)).findFirst()
                .orElseThrow(() -> new AssertionError("El expediente " + nombre + " no aparece en la lista"));
        return UUID.fromString(enlace.attr("href").substring("/expedientes/".length()));
    }

    public Document expediente(UUID id) {
        return abrir("/expedientes/" + id);
    }

    public Document historial(String tecnica) {
        return abrir("/tecnicas/" + tecnica + "?pestana=historial");
    }

    public String exportarMisDatos() {
        ClienteApp.Respuesta r = persona.get("/mis-datos/exportar");
        assertThat(r.estado()).as("exportar").isEqualTo(200);
        assertThat(r.cabecera("Content-Disposition").orElse("")).contains("attachment");
        return r.cuerpo();
    }

    public ClienteApp.Respuesta importarMisDatos(String json) {
        persona.get("/mis-datos");
        return persona.postArchivo("/mis-datos/importar", "archivo", "mis-datos.json", json.getBytes(StandardCharsets.UTF_8));
    }

    /** El formulario #form-{tecnica} tal como lo enviaría el navegador: campos con valor, radios marcados, ocultos. */
    public record Formulario(String tecnica, FormElement form) {

        public List<Map.Entry<String, String>> pares() {
            return form.formData().stream().map(kv -> (Map.Entry<String, String>) new AbstractMap.SimpleEntry<>(kv.key(), kv.value())).toList();
        }

        /** Cambia el valor de un campo de texto, como si la persona lo escribiera. */
        public Formulario escribir(String nombre, String valor) {
            Element campo = form.selectFirst("[name=\"" + nombre + "\"]");
            if (campo == null) {
                throw new AssertionError("No hay campo " + nombre);
            }
            if (campo.tagName().equals("textarea")) {
                campo.text(valor);
            } else {
                campo.val(valor);
            }
            return this;
        }
    }
}
