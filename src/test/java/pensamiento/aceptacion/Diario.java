package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.FormElement;

/**
 * DSL de la aceptación del flujo D (capa 2 de Farley): verbos del Diario de decisiones sobre el driver HTTP. Cada verbo
 * lee el HTML como lo haría el navegador (jsoup) y envía lo mismo que enviaría htmx; los campos que el formulario no trae
 * escritos se agregan como los escribiría la persona.
 */
public final class Diario {

    private static final Pattern RESULTADO_GUARDADO = Pattern.compile("id=\"res-([0-9a-f-]{36})\"");

    private final ClienteApp persona;

    private Diario(ClienteApp persona) {
        this.persona = persona;
    }

    public static Diario de(ClienteApp persona) {
        return new Diario(persona);
    }

    public ClienteApp cliente() {
        return persona;
    }

    private Document abrir(String ruta) {
        ClienteApp.Respuesta r = persona.get(ruta);
        assertThat(r.estado()).as("GET " + ruta).isEqualTo(200);
        return Jsoup.parse(r.cuerpo());
    }

    public Document tablero() {
        return abrir("/diario");
    }

    public Document inicio() {
        return abrir("/");
    }

    /** "Nueva decisión" desde el tablero: devuelve el expediente de la decisión. */
    public UUID nuevaDecision(String titulo) {
        tablero();
        ClienteApp.Respuesta r = persona.postFormulario("/diario/decisiones", Map.of("titulo", titulo), true);
        assertThat(r.estado()).as("nueva decisión").isEqualTo(204);
        String destino = r.cabecera("HX-Redirect").orElseThrow(() -> new AssertionError("Sin HX-Redirect"));
        assertThat(destino).startsWith("/diario/decisiones/");
        return UUID.fromString(destino.substring("/diario/decisiones/".length()));
    }

    /** Abre la técnica de un paso del asistente. */
    public Document paso(UUID decision, int paso, String tecnica) {
        return abrir("/diario/decisiones/" + decision + "?paso=" + paso + "&tecnica=" + tecnica);
    }

    /** El formulario de la técnica en esa página del asistente, con lo que trae escrito. */
    public Taller.Formulario formulario(Document pagina, String tecnica) {
        FormElement form = (FormElement) pagina.getElementById("form-" + tecnica);
        assertThat(form).as("el formulario de " + tecnica + " en el asistente").isNotNull();
        return new Taller.Formulario(tecnica, form);
    }

    /** Escribe los campos dados (agregando los que el formulario no trae, como filas nuevas) y guarda en la decisión. */
    public UUID guardar(Taller.Formulario f, Map<String, String> campos) {
        ClienteApp.Respuesta r = persona.postPares("/tecnicas/" + f.tecnica() + "/ejecuciones", con(f, campos), true);
        assertThat(r.estado()).as("guardar " + f.tecnica() + " en la decisión: " + r.cuerpo()).isEqualTo(200);
        Matcher m = RESULTADO_GUARDADO.matcher(r.cuerpo());
        assertThat(m.find()).as("el resultado guardado trae su identificador").isTrue();
        return UUID.fromString(m.group(1));
    }

    /** Lo mismo que guardar, cuando se espera que el guardado se rechace. */
    public ClienteApp.Respuesta guardarSinPoder(Taller.Formulario f, Map<String, String> campos) {
        return persona.postPares("/tecnicas/" + f.tecnica() + "/ejecuciones", con(f, campos), true);
    }

    private static List<Map.Entry<String, String>> con(Taller.Formulario f, Map<String, String> campos) {
        List<Map.Entry<String, String>> pares = new ArrayList<>(f.pares().stream().filter(p -> !campos.containsKey(p.getKey())).toList());
        campos.forEach((k, v) -> pares.add(new AbstractMap.SimpleEntry<>(k, v)));
        return pares;
    }

    /** "Se cumplió" o "No se cumplió" en una revisión del tablero. */
    public ClienteApp.Respuesta revisar(UUID prediccion, boolean seCumplio) {
        tablero();
        return persona.postFormulario("/diario/predicciones/" + prediccion + "/resolucion",
                Map.of("resultado", seCumplio ? "se_cumplio" : "no_se_cumplio"), true);
    }

    /** El administrador adelanta el reloj de la aplicación (solo con APP_RELOJ_AJUSTABLE=true). */
    public static void adelantarElReloj(ClienteApp administrador, int dias) {
        administrador.get("/usuarios");
        ClienteApp.Respuesta r = administrador.postFormulario("/administracion/reloj", Map.of("dias", String.valueOf(dias)), false);
        assertThat(r.estado()).as("adelantar el reloj: levanta la app con APP_RELOJ_AJUSTABLE=true (" + r.cuerpo() + ")").isEqualTo(200);
    }
}
