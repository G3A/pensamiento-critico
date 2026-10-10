package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import pensamiento.catalogo.CatalogoJson;
import pensamiento.nucleo.BancoDojo;

/**
 * DSL de la aceptación del Dojo (capa 2 de Farley): configurar T48 y T49, ver el reto que toca y responderlo bien o mal. Para
 * saber qué es "bien", lee el mismo banco versionado que la app (catalogo/dojo.json): la opción correcta o la respuesta modelo.
 */
public final class Dojo {

    private static final BancoDojo BANCO = new CatalogoJson().bancoDojo();

    private final ClienteApp persona;

    private Dojo(ClienteApp persona) {
        this.persona = persona;
    }

    public static Dojo de(ClienteApp persona) {
        return new Dojo(persona);
    }

    /** Guarda la configuración de una técnica con los campos dados ("cfg.retosPorDia" → "3"); los conjuntos, repetidos. */
    public void configurar(String tecnica, List<Map.Entry<String, String>> campos) {
        persona.get("/tecnicas/" + tecnica + "?pestana=usar");
        ClienteApp.Respuesta r = persona.postPares("/tecnicas/" + tecnica + "/configuracion", campos, true);
        assertThat(r.estado()).as("configurar " + tecnica + ": " + r.cuerpo()).isEqualTo(200);
    }

    /** La pantalla del Dojo con el filtro de tema ("falacias"). */
    public Document pantalla(String tema) {
        ClienteApp.Respuesta r = persona.get("/dojo?tema=" + tema);
        assertThat(r.estado()).as("dojo").isEqualTo(200);
        return Jsoup.parse(r.cuerpo());
    }

    /** El identificador del reto que muestra la pantalla. */
    public static String reto(Document pantalla) {
        String id = pantalla.select("#reto [data-reto]").attr("data-reto");
        assertThat(id).as("la pantalla trae un reto: " + pantalla.select("#reto").text()).isNotBlank();
        return id;
    }

    /** Responde el reto que toca en el tema, bien o mal; comprueba que sea el esperado y devuelve el fragmento de la respuesta. */
    public Document responder(String tema, String retoEsperado, boolean bien) {
        String id = reto(pantalla(tema));
        assertThat(id).as("el reto que toca").isEqualTo(retoEsperado);
        BancoDojo.Reto reto = BANCO.reto(id).orElseThrow();
        List<Map.Entry<String, String>> pares = new ArrayList<>();
        pares.add(Map.entry("_clave", UUID.randomUUID().toString()));
        pares.add(Map.entry("tema", tema));
        pares.add(Map.entry("respuesta", respuesta(reto, bien)));
        ClienteApp.Respuesta r = persona.postPares("/dojo/retos/" + id, pares, true);
        assertThat(r.estado()).as("responder " + id + ": " + r.cuerpo()).isEqualTo(200);
        Document d = Jsoup.parseBodyFragment(r.cuerpo());
        assertThat(d.select("#reto").attr("data-resultado")).as("calificación de " + id).isEqualTo(bien ? "acierto" : "error");
        return d;
    }

    /** Responde con una clave dada (para el doble clic) sin mirar qué reto toca. */
    public ClienteApp.Respuesta responderCon(String retoId, String respuesta, String clave) {
        return persona.postPares("/dojo/retos/" + retoId, List.of(Map.entry("_clave", clave), Map.entry("respuesta", respuesta)), true);
    }

    static String respuesta(BancoDojo.Reto reto, boolean bien) {
        if (reto.esDeEscribir()) {
            return bien ? reto.respuestaModelo() : reto.texto();
        }
        return bien ? reto.correcta() : reto.opciones().stream().map(BancoDojo.Opcion::id).filter(o -> !o.equals(reto.correcta())).findFirst().orElseThrow();
    }

    public Document progreso() {
        ClienteApp.Respuesta r = persona.get("/dojo/progreso");
        assertThat(r.estado()).isEqualTo(200);
        return Jsoup.parse(r.cuerpo());
    }
}
