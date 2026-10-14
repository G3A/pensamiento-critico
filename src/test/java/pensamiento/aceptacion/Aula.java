package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.jsoup.Jsoup;

/** DSL de la aceptación del aula (hito 10): verbos del dominio sobre el driver HTTP. */
final class Aula {

    private Aula() {
    }

    /** El administrador crea un grupo con ese docente (por su nombre en la lista) y devuelve su id. */
    static UUID crearGrupo(ClienteApp admin, String nombre, String docente) {
        String id = Jsoup.parse(admin.get("/grupos").cuerpo()).select("#docente-grupo option").stream()
                .filter(o -> o.text().equals(docente)).findFirst().map(o -> o.attr("value")).orElseThrow(() -> new AssertionError("no hay docente " + docente));
        ClienteApp.Respuesta r = admin.postFormulario("/grupos", Map.of("nombre", nombre, "docente", id));
        assertThat(r.estado()).as("crear el grupo " + nombre).isEqualTo(302);
        String location = r.cabecera("Location").orElseThrow();
        return UUID.fromString(location.replaceAll(".*/grupos/([0-9a-f-]{36}).*", "$1"));
    }
}
