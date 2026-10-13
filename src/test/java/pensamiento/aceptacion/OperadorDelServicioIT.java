package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import pensamiento.testutil.Entorno;

/**
 * Hito 8, entregable 5, por HTTP: el operador del servicio entra por /operador con la clave del secreto, crea una
 * institución, invita a su primer administrador (el código se ve una sola vez) y quien la recibe la canjea en
 * /<ruta>/invitacion y entra por la ruta nueva. El código no sirve dos veces (410). El operador no entra a nada de una
 * institución (403) y al suspenderla su entrada responde 404.
 */
class OperadorDelServicioIT {

    private static final Pattern CODIGO = Pattern.compile("class=\"codigo-invitacion\">([0-9A-Z]{4}-[0-9A-Z]{4})<");

    private final String ruta = "colegio-" + UUID.randomUUID().toString().substring(0, 8);

    @Test
    void el_operador_crea_una_institucion_invita_a_su_primer_administrador_y_este_entra_por_la_ruta_nueva() {
        ClienteApp operador = operador();
        assertThat(operador.postFormulario("/operador/instituciones", Map.of("nombre", "Colegio de la Prueba", "ruta", ruta)).estado())
                .isEqualTo(302);
        String codigo = invitar(operador, ruta);

        assertThat(new ClienteApp(ruta).get("/" + ruta).cuerpo()).as("todavía sin cuentas").contains("Todavía no hay cuentas");
        ClienteApp invitada = new ClienteApp(ruta);
        invitada.csrfDe(invitada.get("/" + ruta + "/invitacion"));
        ClienteApp.Respuesta canje = invitada.postFormulario("/" + ruta + "/invitacion",
                Map.of("codigo", codigo.toLowerCase(), "nombre", "Rectora Peña", "pin", "246810"));
        assertThat(canje.estado()).isEqualTo(302);
        assertThat(canje.cabecera("Location").orElse("")).endsWith("/" + ruta + "?bienvenida");

        ClienteApp otraVez = new ClienteApp(ruta);
        otraVez.csrfDe(otraVez.get("/" + ruta + "/invitacion"));
        ClienteApp.Respuesta segundo = otraVez.postFormulario("/" + ruta + "/invitacion",
                Map.of("codigo", codigo, "nombre", "Colado", "pin", "1357"));
        assertThat(segundo.estado()).as("el mismo código otra vez").isEqualTo(410);

        ClienteApp rectora = Instalacion.entraComo(ruta, "rectora pena", "246810");
        assertThat(rectora.get("/usuarios").estado()).as("es administradora").isEqualTo(200);

        Document panel = Jsoup.parse(operador.get("/operador").cuerpo());
        assertThat(fila(panel, ruta).text()).contains("con cuentas").doesNotContain("Rectora");
        assertThat(panel.select(".lista li").eachText()).anySatisfy(l -> assertThat(l).contains("canjear invitacion").contains("/" + ruta));
        assertThat(panel.text()).as("el código no vuelve a mostrarse").doesNotContain(codigo);
    }

    @Test
    void el_operador_no_entra_a_nada_de_una_institucion() {
        ClienteApp operador = operador();
        ClienteApp.Respuesta expedientes = operador.get("/expedientes");
        assertThat(expedientes.estado()).isEqualTo(403);
        assertThat(expedientes.cuerpo()).as("sin detalles internos").doesNotContain("Whitelabel", "Exception", "Tomcat", "trace");
        assertThat(operador.get("/usuarios").estado()).isEqualTo(403);
        assertThat(operador.get("/mis-datos").estado()).isEqualTo(403);
        assertThat(operador.get("/catalogo").estado()).isEqualTo(403);
    }

    @Test
    void suspender_cierra_la_entrada_y_reactivar_la_abre() {
        ClienteApp operador = operador();
        operador.postFormulario("/operador/instituciones", Map.of("nombre", "Colegio a suspender", "ruta", ruta));
        String id = fila(Jsoup.parse(operador.get("/operador").cuerpo()), ruta).select("form[action$='/suspender']").attr("action")
                .replace("/operador/instituciones/", "").replace("/suspender", "");
        operador.postFormulario("/operador/instituciones/" + id + "/suspender", Map.of());
        assertThat(new ClienteApp(ruta).get("/" + ruta).estado()).isEqualTo(404);
        assertThat(new ClienteApp(ruta).get("/" + ruta + "/invitacion").estado()).isEqualTo(404);
        operador.postFormulario("/operador/instituciones/" + id + "/reactivar", Map.of());
        assertThat(new ClienteApp(ruta).get("/" + ruta).estado()).isEqualTo(200);
    }

    @Test
    void una_ruta_de_la_app_o_repetida_no_crea_institucion() {
        ClienteApp operador = operador();
        assertThat(operador.postFormulario("/operador/instituciones", Map.of("nombre", "Catálogo", "ruta", "catalogo")).estado()).isEqualTo(422);
        operador.postFormulario("/operador/instituciones", Map.of("nombre", "Primera", "ruta", ruta));
        ClienteApp.Respuesta repetida = operador.postFormulario("/operador/instituciones", Map.of("nombre", "Segunda", "ruta", ruta));
        assertThat(repetida.estado()).isEqualTo(422);
        assertThat(repetida.cuerpo()).contains("Ya existe una institución con la ruta");
    }

    @Test
    void una_clave_equivocada_no_abre_el_panel() {
        ClienteApp intruso = new ClienteApp();
        ClienteApp.Respuesta r = intruso.entrarComoOperador("una-clave-que-no-es-la-del-operador");
        assertThat(r.cabecera("Location").orElse("")).endsWith("/operador/entrar?error");
        assertThat(intruso.get("/operador").estado()).isEqualTo(302);
    }

    private static ClienteApp operador() {
        ClienteApp operador = new ClienteApp();
        ClienteApp.Respuesta r = operador.entrarComoOperador(Entorno.claveOperador());
        assertThat(r.cabecera("Location").orElse("")).as("el operador entra").endsWith("/operador");
        return operador;
    }

    private static String invitar(ClienteApp operador, String ruta) {
        Element formulario = fila(Jsoup.parse(operador.get("/operador").cuerpo()), ruta).selectFirst("form[action$='/invitar']");
        assertThat(formulario).as("la institución sin cuentas se puede invitar").isNotNull();
        ClienteApp.Respuesta invitacion = operador.postFormulario(formulario.attr("action"), Map.of());
        assertThat(invitacion.estado()).isEqualTo(200);
        assertThat(invitacion.cuerpo()).contains("/" + ruta + "/invitacion");
        Matcher m = CODIGO.matcher(invitacion.cuerpo());
        assertThat(m.find()).as("el código se muestra").isTrue();
        return m.group(1);
    }

    private static Element fila(Document panel, String ruta) {
        return panel.select("tbody tr").stream().filter(tr -> tr.text().contains("/" + ruta)).findFirst().orElseThrow();
    }
}
