package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.List;
import java.util.UUID;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import pensamiento.catalogo.CatalogoJson;
import pensamiento.catalogo.MapeadorJson;
import pensamiento.nucleo.Ejemplo;
import pensamiento.nucleo.IdTecnica;

/**
 * Aceptación por HTTP del hito 3 (RF-14, RF-03, RF-07, RF-12), en lenguaje del dominio. Sin Ollama, cada técnica con IA
 * da el resultado del modo plantillas y lo dice; con Ollama, una propuesta del modelo aparece sin contar, se adopta y
 * entonces cuenta, y la ejecución guarda el registro del modelo. Corre igual con el servicio ollama detenido: las
 * pruebas que necesitan el modelo se saltan con una suposición, y la del modo plantillas lo exige.
 */
class RF14TecnicasConModeloIT {

    private static final CatalogoJson CATALOGO = new CatalogoJson();
    private static final List<String> CON_MODELO = List.of("T04", "T07", "T13", "T15", "T17", "T22", "T34");

    record Esperado(String resumen) {
    }

    private static boolean ollamaDisponible() {
        String salud = new ClienteApp().get("/actuator/health").cuerpo();
        return salud.contains("\"ia\":{") && !salud.contains("\"status\":\"PLANTILLAS\"");
    }

    private static Taller nuevaPersona(String rol, String pin) {
        ClienteApp admin = Instalacion.administrador();
        return Taller.de(Instalacion.entraComo(Instalacion.personaNueva(admin, rol, pin), pin));
    }

    @Test
    void cada_tecnica_con_ia_da_el_resultado_del_modo_plantillas_de_sus_ejemplos() {
        Taller taller = nuevaPersona("vecina", "1593");
        for (String tecnica : CON_MODELO) {
            for (Ejemplo e : CATALOGO.ejemplosDe(IdTecnica.de(tecnica))) {
                Taller.Formulario f = taller.cargarEjemplo(tecnica, e.titulo());
                ClienteApp.Respuesta r = taller.evaluar(f);
                assertThat(r.estado()).as(tecnica + " · " + e.titulo()).isEqualTo(200);
                assertThat(Jsoup.parseBodyFragment(r.cuerpo()).select(".tarjeta-resultado .titular").text()).as(tecnica + " · " + e.titulo())
                        .isEqualTo(MapeadorJson.leer(e.resultado(), Esperado.class).resumen());
            }
        }
    }

    @Test
    void sin_ollama_pedir_propuestas_dice_que_sigue_en_modo_plantillas() {
        assumeTrue(!ollamaDisponible(), "Ollama responde: esta prueba es para el servicio detenido");
        Taller taller = nuevaPersona("socia de la panadería", "7531");
        Taller.Formulario camaras = taller.cargarEjemplo("T34", "Los que no quieren cámaras");
        assertThat(camaras.form().select("#pedir-T34 button").hasAttr("disabled")).isTrue();
        assertThat(camaras.form().select("#pedir-T34").text()).contains("El modelo no está disponible: sigues en modo plantillas.");

        Taller.Pedido pedido = taller.pedirPropuestas(camaras);
        assertThat(pedido.turno()).isEmpty();
        assertThat(Jsoup.parseBodyFragment(pedido.cuerpo()).select(".aviso-ia").text()).contains("sigues en modo plantillas");

        Document resultado = Jsoup.parseBodyFragment(taller.evaluar(camaras).cuerpo());
        assertThat(resultado.select(".tarjeta-resultado .titular").text()).isEqualTo("Falta el steelman.");
        assertThat(new ClienteApp().get("/actuator/health").estado()).as("la app sigue sana sin Ollama").isEqualTo(200);
    }

    @Test
    void con_ollama_la_propuesta_aparece_sin_contar_se_adopta_y_entonces_cuenta() {
        assumeTrue(ollamaDisponible(), "Ollama no responde: esta prueba necesita el modelo");
        Taller taller = nuevaPersona("presidenta de la junta", "8462");
        Taller.Formulario camaras = taller.cargarEjemplo("T34", "Los que no quieren cámaras");

        // Pedir abre un turno con su conexión SSE; el evento final trae el formulario con la propuesta sin adoptar.
        Taller.Pedido pedido = taller.pedirPropuestas(camaras);
        assertThat(pedido.turno()).as("se abrió un turno con el modelo").isPresent();
        assertThat(Jsoup.parseBodyFragment(pedido.cuerpo()).select("#pedir-T34 button").hasAttr("disabled")).as("el botón queda deshabilitado").isTrue();
        Document fin = taller.esperarPropuestas(pedido.turno().get());
        assertThat(fin.select(".aviso-ia").text()).contains("propuesta del modelo");
        Taller.Formulario conPropuesta = taller.formularioDe("T34", fin);
        assertThat(conPropuesta.form().select("li.propuesta[data-propuesta=IA1] .chip").text())
                .isEqualTo("propuesta del modelo · sin adoptar · no cuenta");

        // Sin adoptar no cuenta: el resultado es el del modo plantillas.
        Document sinAdoptar = Jsoup.parseBodyFragment(taller.evaluar(conPropuesta).cuerpo());
        assertThat(sinAdoptar.select(".tarjeta-resultado .titular").text()).isEqualTo("Falta el steelman.");
        assertThat(sinAdoptar.select(".propuestas-resultado li[data-propuesta=IA1] .chip").text()).isEqualTo("propuesta del modelo · sin adoptar · no cuenta");

        // Adoptar es una acción explícita: el formulario la dice adoptada y el resultado ya cuenta el steelman.
        Document adoptada = taller.adoptar(conPropuesta, "IA1");
        Taller.Formulario formAdoptado = taller.formularioDe("T34", adoptada);
        assertThat(formAdoptado.form().select("li.propuesta[data-propuesta=IA1] .chip").text()).isEqualTo("adoptada por ti · cuenta");
        assertThat(formAdoptado.form().select("[name=origenSteelman]").val()).isEqualTo("modelo");
        assertThat(adoptada.select("#resultado-T34 .tarjeta-resultado .titular").text()).startsWith("Steelman de ").endsWith("por confirmar.");

        // Guardar deja la afirmación del modelo adoptada y el registro de modelo, digest, prompt, temperatura y semilla.
        UUID ejecucion = taller.guardar(formAdoptado);
        String archivo = taller.exportarMisDatos();
        assertThat(archivo).contains(ejecucion.toString()).contains("\"promptVersion\" : \"t34-steelman.v1\"").contains("\"semilla\" : 42")
                .contains("\"origen\" : \"modelo\"").contains("\"adoptada\" : true");
    }

    @Test
    void guardar_asociar_ver_en_el_expediente_exportar_e_importar_y_otra_persona_recibe_404() {
        Taller taller = nuevaPersona("secretaria de la junta", "9517");
        Taller.Formulario robos = taller.cargarEjemplo("T22", "Los robos de la cuadra");
        UUID ejecucion = taller.guardar(robos);
        assertThat(taller.guardar(robos)).as("el doble clic no duplica").isEqualTo(ejecucion);

        UUID expediente = taller.asociarAUnExpedienteNuevo(ejecucion, "Los robos de la cuadra");
        Document vista = taller.expediente(expediente);
        assertThat(vista.getElementById("linea-" + ejecucion)).isNotNull();
        assertThat(vista.select("[data-patron=V10][data-modo=lectura]")).isNotEmpty();
        assertThat(vista.select(".falta-para-cerrar li").eachText())
                .anySatisfy(t -> assertThat(t).contains("Buscar una fuente independiente para: Los robos en la cuadra subieron este año."));

        String archivo = taller.exportarMisDatos();
        assertThat(archivo).contains(ejecucion.toString()).contains("\"rol\" : \"hipotesis\"");
        ClienteApp.Respuesta importada = taller.importarMisDatos(archivo);
        assertThat(importada.estado()).isEqualTo(200);
        assertThat(importada.cuerpo()).contains("0 ejecuciones nuevas, 1 ya estaban");

        Taller otra = nuevaPersona("vecino del tercer piso", "3579");
        assertThat(otra.cliente().get("/ejecuciones/" + ejecucion).estado()).isEqualTo(404);
        assertThat(otra.cliente().get("/expedientes/" + expediente).estado()).isEqualTo(404);
        assertThat(otra.cliente().get("/tecnicas/T22?reejecutar=" + ejecucion).estado()).isEqualTo(404);
        assertThat(otra.cliente().get("/ia/turnos/" + UUID.randomUUID() + "/flujo").estado()).isEqualTo(404);
    }

    @Test
    void t16_bloquea_el_guardado_si_falta_un_obligatorio_y_lo_dice() {
        Taller taller = nuevaPersona("dueña de la panadería", "2584");
        Taller.Formulario arriendo = taller.cargarEjemplo("T16", "El arriendo del local");
        ClienteApp.Respuesta r = taller.guardarSinPoder(arriendo);
        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.cuerpo()).contains("Guardado bloqueado: faltan 2 ítems obligatorios.");
        assertThat(taller.historial("T16").select("tr[id^=historial-]")).isEmpty();

        Taller.Formulario carro = taller.cargarEjemplo("T16", "El carro usado");
        assertThat(taller.guardar(carro)).isNotNull();
    }

    @Test
    void las_doce_tecnicas_del_hito_3_tienen_la_pestana_usar() {
        Taller taller = nuevaPersona("papá", "1478");
        for (String t : List.of("T03", "T04", "T05", "T07", "T13", "T14", "T15", "T16", "T17", "T18", "T22", "T34")) {
            Document usar = Jsoup.parse(taller.cliente().get("/tecnicas/" + t + "?pestana=usar").cuerpo());
            assertThat(usar.getElementById("form-" + t)).as(t).isNotNull();
            assertThat(usar.select("nav.barra-ejemplos a.ejemplo")).as(t).hasSize(3);
        }
    }
}
