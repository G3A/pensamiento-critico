package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.FormElement;

/**
 * DSL de la aceptación del flujo C (capa 2 de Farley): verbos del Consejero socrático sobre el driver HTTP. Cada verbo
 * lee el HTML como lo haría el navegador (jsoup) y envía lo mismo que enviaría htmx.
 */
public final class DialogoConsejero {

    private static final Pattern RESULTADO_GUARDADO = Pattern.compile("id=\"res-([0-9a-f-]{36})\"");

    private final ClienteApp persona;

    private DialogoConsejero(ClienteApp persona) {
        this.persona = persona;
    }

    public static DialogoConsejero de(ClienteApp persona) {
        return new DialogoConsejero(persona);
    }

    public ClienteApp cliente() {
        return persona;
    }

    public Document inicio() {
        ClienteApp.Respuesta r = persona.get("/consejero");
        assertThat(r.estado()).as("GET /consejero").isEqualTo(200);
        return Jsoup.parse(r.cuerpo());
    }

    /** "Empezar" una sesión: devuelve su identificador (la redirección a la sesión). */
    public UUID nuevaSesion(String modo, String postura, List<String[]> razones, String confianza, boolean usaModelo) {
        inicio();
        Map<String, String> campos = new LinkedHashMap<>();
        campos.put("modo", modo);
        campos.put("postura", postura);
        for (int i = 0; i < razones.size(); i++) {
            campos.put("razon" + i, razones.get(i)[0]);
            campos.put("apoyo" + i, razones.get(i)[1]);
        }
        campos.put("confianza", confianza);
        if (usaModelo) {
            campos.put("usaModelo", "on");
        }
        ClienteApp.Respuesta r = persona.postFormulario("/consejero/sesiones", campos);
        assertThat(r.estado()).as("empezar sesión: " + r.cuerpo()).isEqualTo(303);
        String destino = r.cabecera("Location").orElseThrow();
        String id = destino.substring(destino.indexOf("/consejero/sesiones/") + "/consejero/sesiones/".length());
        return UUID.fromString(id);
    }

    public Document sesion(UUID id) {
        ClienteApp.Respuesta r = persona.get("/consejero/sesiones/" + id);
        assertThat(r.estado()).as("GET sesión").isEqualTo(200);
        return Jsoup.parse(r.cuerpo());
    }

    public ClienteApp.Respuesta getSesion(UUID id) {
        return persona.get("/consejero/sesiones/" + id);
    }

    /** "Enviar": la respuesta trae la burbuja de la persona, la del Consejero y el panel y la entrada fuera de banda. */
    public Document responder(UUID id, String texto) {
        ClienteApp.Respuesta r = persona.postFormulario("/consejero/sesiones/" + id + "/turnos", Map.of("texto", texto), true);
        assertThat(r.estado()).as("responder: " + r.cuerpo()).isEqualTo(200);
        return Jsoup.parseBodyFragment(r.cuerpo());
    }

    public ClienteApp.Respuesta responderCrudo(UUID id, String texto) {
        return persona.postFormulario("/consejero/sesiones/" + id + "/turnos", Map.of("texto", texto), true);
    }

    public Document irAlCierre(UUID id) {
        ClienteApp.Respuesta r = persona.postFormulario("/consejero/sesiones/" + id + "/cierre", Map.of(), true);
        assertThat(r.estado()).as("ir al cierre: " + r.cuerpo()).isEqualTo(200);
        return Jsoup.parseBodyFragment(r.cuerpo());
    }

    /** "Cerrar y guardar": el servidor redirige a la sesión, que queda cerrada. */
    public void cerrar(UUID id, String reflexion, String confianza, String causa, List<String> comprobados) {
        List<Map.Entry<String, String>> pares = new ArrayList<>();
        pares.add(Map.entry("reflexion", reflexion));
        pares.add(Map.entry("confianzaDespues", confianza));
        pares.add(Map.entry("causa", causa));
        comprobados.forEach(c -> pares.add(Map.entry("comprobados", c)));
        ClienteApp.Respuesta r = persona.postPares("/consejero/sesiones/" + id + "/cerrar", pares, true);
        assertThat(r.estado()).as("cerrar: " + r.cuerpo()).isEqualTo(204);
        assertThat(r.cabecera("HX-Redirect")).contains("/consejero/sesiones/" + id);
    }

    public ClienteApp.Respuesta cerrarCrudo(UUID id) {
        return persona.postPares("/consejero/sesiones/" + id + "/cerrar", List.of(Map.entry("reflexion", "")), true);
    }

    /** Lee el flujo SSE de un turno que redacta el modelo hasta el evento final. */
    public String flujo(UUID turno, Duration tiempoMaximo) {
        ClienteApp.Respuesta r = persona.flujoSse("/consejero/turnos/" + turno + "/flujo", tiempoMaximo);
        assertThat(r.estado()).as("flujo SSE").isEqualTo(200);
        return r.cuerpo();
    }

    /** El formulario de una técnica del debate (T34, T37 o T38), ya lleno con lo de la sesión. */
    public Taller.Formulario tecnicaDelDebate(UUID id, String tecnica) {
        ClienteApp.Respuesta r = persona.getHtmx("/consejero/sesiones/" + id + "/debate?tecnica=" + tecnica);
        assertThat(r.estado()).as("debate " + tecnica).isEqualTo(200);
        Document d = Jsoup.parseBodyFragment(r.cuerpo());
        FormElement form = (FormElement) d.getElementById("form-" + tecnica);
        assertThat(form).as("el formulario de " + tecnica).isNotNull();
        return new Taller.Formulario(tecnica, form);
    }

    /** Guarda la técnica en el expediente de la sesión con los campos dados y devuelve la ejecución. */
    public UUID guardar(Taller.Formulario f, Map<String, String> campos) {
        ClienteApp.Respuesta r = persona.postPares("/tecnicas/" + f.tecnica() + "/ejecuciones", Diario.con(f, campos), true);
        assertThat(r.estado()).as("guardar " + f.tecnica() + ": " + r.cuerpo()).isEqualTo(200);
        Matcher m = RESULTADO_GUARDADO.matcher(r.cuerpo());
        assertThat(m.find()).as("el resultado guardado trae su id").isTrue();
        return UUID.fromString(m.group(1));
    }
}
