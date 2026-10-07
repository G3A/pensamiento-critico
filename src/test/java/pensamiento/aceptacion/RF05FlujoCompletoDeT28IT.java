package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

/**
 * Aceptación por HTTP del hito 1 (RF-04 a RF-08 y RF-12), en lenguaje del dominio: la dueña de la panadería
 * entra, llega a T28 · Análisis de hipótesis en competencia (ACH) desde una intención, carga el ejemplo de las
 * ventas de los sábados, evalúa, guarda, lo asocia a un expediente, lo ve en la línea de tiempo y en el historial,
 * exporta e importa sus datos. Y RF-03 extendido: otra persona recibe 404 para su ejecución y su expediente.
 */
class RF05FlujoCompletoDeT28IT {

    private static final String SUCURSAL = "La segunda sucursal de la panadería";

    @Test
    void de_la_intencion_a_la_matriz_al_expediente_al_historial_y_al_respaldo() {
        ClienteApp admin = Instalacion.administrador();
        String duena = Instalacion.personaNueva(admin, "dueña", "1357");
        Taller taller = Taller.de(Instalacion.entraComo(duena, "1357"));

        // RF-04: desde "Entender por qué pasó algo" se llega a T28.
        assertThat(taller.tecnicasParaLaIntencion("Entender por qué pasó algo")).contains("T28");

        // RF-06: cargar un ejemplo llena el formulario con su configuración y sus datos.
        Taller.Formulario ventas = taller.cargarEjemplo("T28", "Las ventas de los sábados");
        assertThat(ventas.form().select("[name=pregunta]").val()).contains("ventas de los sábados");

        // RF-05: evaluar sin guardar pinta V03a con las inconsistencias del ejemplo.
        ClienteApp.Respuesta evaluada = taller.evaluar(ventas);
        assertThat(evaluada.estado()).isEqualTo(200);
        Document matriz = Jsoup.parseBodyFragment(evaluada.cuerpo());
        assertThat(matriz.select("[data-patron=V03a] tfoot td").eachText()).containsExactly("0", "5", "4");
        assertThat(matriz.select(".titular").text()).isEqualTo("Menos refutada: H1, Abrió una feria a dos cuadras los sábados.");
        assertThat(matriz.select(".sin-guardar")).hasSize(1);

        // RF-05: guardar en historial, y el doble clic no duplica.
        UUID ejecucion = taller.guardar(ventas);
        assertThat(taller.guardar(ventas)).as("misma clave de idempotencia").isEqualTo(ejecucion);

        // RF-07: asociarla a un expediente y verla en su línea de tiempo con lo que falta para cerrar.
        UUID expediente = taller.asociarAUnExpedienteNuevo(ejecucion, SUCURSAL);
        Document vista = taller.expediente(expediente);
        assertThat(vista.getElementById("linea-" + ejecucion)).isNotNull();
        assertThat(vista.select(".falta-para-cerrar li").eachText())
                .containsExactly("verificacion Verificar E1: La baja es solo los sábados (hipótesis H1: Abrió una feria a dos cuadras los sábados)");
        assertThat(vista.select(".resumen-familias li.familia-con-ejecuciones").text()).startsWith("F5");

        // RF-06: en el historial de la técnica, una sola ejecución.
        assertThat(taller.historial("T28").select("tr[id^=historial-]").eachAttr("id")).containsExactly("historial-" + ejecucion);

        // RF-12: exportar e importar lo propio no duplica nada.
        String archivo = taller.exportarMisDatos();
        assertThat(archivo).contains(ejecucion.toString()).contains(expediente.toString()).contains("\"rol\" : \"hipotesis\"");
        ClienteApp.Respuesta importada = taller.importarMisDatos(archivo);
        assertThat(importada.estado()).isEqualTo(200);
        assertThat(importada.cuerpo()).contains("0 ejecuciones nuevas, 1 ya estaban");
        assertThat(taller.historial("T28").select("tr[id^=historial-]")).hasSize(1);

        // RF-03 extendido: otra persona no abre ni importa lo de la dueña.
        String secretaria = Instalacion.personaNueva(admin, "secretaria de la junta", "2468");
        Taller otra = Taller.de(Instalacion.entraComo(secretaria, "2468"));
        assertThat(otra.cliente().get("/ejecuciones/" + ejecucion).estado()).isEqualTo(404);
        assertThat(otra.cliente().get("/expedientes/" + expediente).estado()).isEqualTo(404);
        assertThat(otra.cliente().get("/tecnicas/T28?reejecutar=" + ejecucion).estado()).isEqualTo(404);
        ClienteApp.Respuesta ajena = otra.importarMisDatos(archivo);
        assertThat(ajena.estado()).isEqualTo(422);
        assertThat(ajena.cuerpo()).contains("ya son de otra persona");
        assertThat(otra.historial("T28").select("tr[id^=historial-]")).isEmpty();
    }

    @Test
    void un_formulario_incompleto_vuelve_con_422_y_el_error_junto_al_campo() {
        ClienteApp admin = Instalacion.administrador();
        String vecino = Instalacion.personaNueva(admin, "vecino", "8642");
        Taller taller = Taller.de(Instalacion.entraComo(vecino, "8642"));
        Taller.Formulario asamblea = taller.cargarEjemplo("T28", "La asamblea vacía").escribir("hipotesis[1].texto", "");

        ClienteApp.Respuesta r = taller.evaluar(asamblea);

        assertThat(r.estado()).isEqualTo(422);
        assertThat(r.cabecera("HX-Retarget")).contains("#form-T28");
        Document form = Jsoup.parseBodyFragment(r.cuerpo());
        assertThat(form.getElementById("T28-hipotesis-1-texto").attr("aria-describedby")).isEqualTo("T28-hipotesis-1-texto-error");
        assertThat(form.getElementById("T28-hipotesis-1-texto-error").text()).contains("obligatorio");
        assertThat(taller.historial("T28").select("tr[id^=historial-]")).isEmpty();
    }

    @Test
    void la_persona_nueva_ve_el_inicio_vacio_con_la_muestra_y_el_ejemplo_para_empezar() {
        ClienteApp admin = Instalacion.administrador();
        String nueva = Instalacion.personaNueva(admin, "hija mayor", "9753");
        ClienteApp cliente = Instalacion.entraComo(nueva, "9753");

        Document inicio = Jsoup.parse(cliente.get("/").cuerpo());
        assertThat(inicio.select(".intenciones a")).hasSize(6);
        assertThat(inicio.getElementById("empieza-con-un-ejemplo").attr("href")).startsWith("/tecnicas/T28?ejemplo=");
        Document muestra = Jsoup.parse(cliente.get(inicio.getElementById("enlace-muestra").attr("href")).cuerpo());
        assertThat(muestra.select("h1").text()).isEqualTo(SUCURSAL);
        assertThat(muestra.select(".aviso-muestra").text()).contains("solo lectura");
        assertThat(muestra.select("[data-patron=V03a][data-modo=lectura]")).hasSize(2);
        assertThat(muestra.select(".expediente button, .expediente form")).as("la muestra no tiene acciones").isEmpty();
    }
}
