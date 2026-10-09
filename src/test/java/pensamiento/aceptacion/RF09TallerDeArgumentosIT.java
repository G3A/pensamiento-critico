package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import pensamiento.catalogo.MapeadorJson;
import pensamiento.expediente.PaqueteDatos;

/**
 * Aceptación por HTTP del hito 2 (RF-09, RF-10, RF-12 y RF-03 extendido), en lenguaje del dominio: la dueña de la
 * panadería escribe en el Taller el argumento de la segunda sucursal, ve el mapa con un nodo por afirmación, el
 * panel Toulmin con el respaldo que falta y una falacia propuesta que confirma; guarda, asocia a un expediente,
 * lo ve en la línea de tiempo en modo lectura, exporta e importa con los argumentos. Otra persona recibe 404.
 */
class RF09TallerDeArgumentosIT {

    private static final String SUCURSAL = """
            [Sucursal]: Conviene abrir la segunda sucursal en el centro.
              + <Más ventas>: Con más gente pasando, se vende más. {peso: 3}
                + [Tráfico]: El centro tiene más tráfico peatonal que el barrio. #asumible
                + [Conversión]: Más tráfico da más ventas. #asumible
              - [Personal]: Falta personal para atender dos locales.
                - [Perezoso]: El empleado que lo dice es un perezoso, así que su objeción no sirve.""";

    private static final String EXPEDIENTE = "La segunda sucursal de la panadería";

    @Test
    void del_texto_al_mapa_al_toulmin_a_la_falacia_confirmada_al_expediente_y_al_respaldo() {
        ClienteApp admin = Instalacion.administrador();
        String duena = Instalacion.personaNueva(admin, "dueña", "1357");
        Taller taller = Taller.de(Instalacion.entraComo(duena, "1357"));

        // Escribir el argumento de la panadería y evaluar.
        Taller.FormularioTaller f = taller.evaluarEnElTaller(taller.tallerDeArgumentos().escribirArgumento(SUCURSAL).elegirEstandar("preponderancia"));

        // RF-09: el mapa con un nodo por afirmación, con id de afirmación y clase por rol.
        Element mapa = f.form().selectFirst("[data-patron=V01]");
        assertThat(mapa).as("panel del mapa").isNotNull();
        List<String> afirmaciones = mapa.select("button.nodo-lista").eachAttr("data-nodo");
        assertThat(afirmaciones).hasSize(5);
        assertThat(mapa.select("svg g.node").eachAttr("id")).containsExactlyInAnyOrderElementsOf(afirmaciones);
        assertThat(mapa.getElementById(afirmaciones.get(0)).classNames()).contains("conclusion");
        assertThat(mapa.getElementById(afirmaciones.get(3)).classNames()).contains("objecion");
        assertThat(mapa.select(".lista-argumentos li").first().text()).contains("aplicable", "Más ventas");

        // RF-09, panel Toulmin: el respaldo falta y lo dice con texto.
        Element toulmin = f.form().selectFirst("[data-patron=V02]");
        assertThat(toulmin.select("li[data-parte=respaldo] .chip").text()).isEqualTo("falta");
        assertThat(toulmin.select("li[data-parte=respaldo] .falta").text()).startsWith("¿Qué respalda la garantía?");

        // RF-10: una falacia propuesta por reglas, que la persona confirma.
        Element falacias = f.form().selectFirst("[data-patron=V05]");
        assertThat(falacias.select("mark.propuesta").text()).contains("El empleado que lo dice es un perezoso");
        assertThat(falacias.select(".lista-marcas li").first().text()).contains("Ataque a la persona", "sin confirmar");
        Taller.FormularioTaller confirmado = taller.evaluarEnElTaller(f.confirmar("M1"));
        assertThat(confirmado.form().select("[data-patron=V05] mark.confirmada")).hasSize(1);
        assertThat(confirmado.form().select("[data-patron=V05] .etiqueta-falacia").text()).isEqualTo("Falacia: ad hominem.");

        // Guardar: una ejecución por técnica; el doble clic no duplica.
        List<UUID> guardadas = taller.guardarEnElTaller(confirmado);
        assertThat(guardadas).hasSize(3);
        assertThat(taller.guardarEnElTaller(confirmado)).as("misma clave de idempotencia").isEqualTo(guardadas);

        // Asociar a un expediente y ver el mapa en la línea de tiempo, en modo lectura.
        UUID expediente = taller.asociarLoDelTallerAUnExpedienteNuevo(guardadas, EXPEDIENTE);
        Document vista = taller.expediente(expediente);
        guardadas.forEach(e -> assertThat(vista.getElementById("linea-" + e)).as("en la línea de tiempo: " + e).isNotNull());
        assertThat(vista.select("[data-patron=V01][data-modo=lectura]")).hasSize(1);
        assertThat(vista.select(".falta-para-cerrar li").eachText())
                .anySatisfy(t -> assertThat(t).contains("Buscar respaldo para la garantía"));
        assertThat(taller.historial("T13").select("tr[id^=historial-]")).hasSize(1);

        // RF-12: exportar trae los argumentos con su ejecución; importar lo propio no duplica nada.
        String archivo = taller.exportarMisDatos();
        PaqueteDatos paquete = MapeadorJson.mapper().readValue(archivo, PaqueteDatos.class);
        assertThat(paquete.version()).as("versión 4 desde el hito 5").isEqualTo(4);
        PaqueteDatos.EjecucionDatos delMapa = paquete.ejecuciones().stream().filter(e -> e.id().equals(guardadas.getFirst())).findFirst().orElseThrow();
        assertThat(delMapa.argumentos()).hasSize(3);
        UUID argumento = delMapa.argumentos().getFirst().id();
        ClienteApp.Respuesta importada = taller.importarMisDatos(archivo);
        assertThat(importada.estado()).isEqualTo(200);
        assertThat(importada.cuerpo()).contains("0 ejecuciones nuevas, 3 ya estaban");
        // Un archivo del hito 1: versión 1 y ejecuciones sin los campos argumentos ni predicciones.
        tools.jackson.databind.node.ObjectNode viejo = (tools.jackson.databind.node.ObjectNode) MapeadorJson.mapper().readTree(archivo);
        viejo.put("version", 1);
        viejo.get("ejecuciones").forEach(e -> ((tools.jackson.databind.node.ObjectNode) e).remove(java.util.List.of("argumentos", "predicciones")));
        String version1 = viejo.toString();
        assertThat(taller.importarMisDatos(version1).estado()).as("un archivo de la versión 1 se migra al importarlo").isEqualTo(200);

        // El argumento se abre por su identificador; RF-03 extendido: otra persona recibe 404.
        ClienteApp.Respuesta propio = taller.cliente().get("/argumentos/" + argumento);
        assertThat(propio.estado()).isEqualTo(200);
        assertThat(propio.cuerpo()).contains("Conviene abrir la segunda sucursal en el centro.");
        String secretaria = Instalacion.personaNueva(admin, "secretaria de la junta", "2468");
        Taller otra = Taller.de(Instalacion.entraComo(secretaria, "2468"));
        ClienteApp.Respuesta ajeno = otra.cliente().get("/argumentos/" + argumento);
        assertThat(ajeno.estado()).isEqualTo(404);
        assertThat(ajeno.cuerpo()).doesNotContain("Conviene abrir la segunda sucursal");
        assertThat(otra.cliente().get("/argumentos/" + UUID.randomUUID()).estado()).isEqualTo(404);
        assertThat(otra.cliente().get("/ejecuciones/" + guardadas.getFirst()).estado()).isEqualTo(404);
    }

    @Test
    void en_la_ficha_cada_ejemplo_de_las_cuatro_tecnicas_se_carga_y_se_evalua_con_su_patron() {
        ClienteApp admin = Instalacion.administrador();
        String hija = Instalacion.personaNueva(admin, "hija mayor", "3691");
        Taller taller = Taller.de(Instalacion.entraComo(hija, "3691"));
        java.util.Map<String, List<String>> ejemplos = java.util.Map.of(
                "T01", List.of("El carro usado", "La segunda sucursal", "Las cámaras del barrio"),
                "T02", List.of("Las vacaciones con los abuelos", "El reductor frente al parque", "La segunda sucursal"),
                "T06", List.of("Venderá más en el centro", "El cambio de colegio", "La calle cerrada los domingos"),
                "T13", List.of("Los que se oponen a las cámaras", "La harina del proveedor", "El mercado del mes"));
        java.util.Map<String, String> patron = java.util.Map.of("T01", "V01", "T02", "V02", "T06", "V01", "T13", "V05");

        ejemplos.forEach((tecnica, titulos) -> titulos.forEach(titulo -> {
            ClienteApp.Respuesta r = taller.evaluar(taller.cargarEjemplo(tecnica, titulo));
            assertThat(r.estado()).as(tecnica + " · " + titulo + ": " + org.jsoup.Jsoup.parseBodyFragment(r.cuerpo()).select(".error-campo").eachText()).isEqualTo(200);
            Document d = org.jsoup.Jsoup.parseBodyFragment(r.cuerpo());
            assertThat(d.select("[data-patron=" + patron.get(tecnica) + "]")).as(tecnica + " · " + titulo).hasSize(1);
        }));
    }

    @Test
    void un_texto_fuera_del_subconjunto_vuelve_con_422_y_el_error_con_linea_y_columna_junto_al_editor() {
        ClienteApp admin = Instalacion.administrador();
        String vecino = Instalacion.personaNueva(admin, "vecino", "8642");
        Taller taller = Taller.de(Instalacion.entraComo(vecino, "8642"));

        ClienteApp.Respuesta r = taller.cliente().postPares("/taller/evaluar",
                taller.tallerDeArgumentos().escribirArgumento("La junta debe cerrar la calle.\n  * con viñeta").pares(), true);

        assertThat(r.estado()).isEqualTo(422);
        Document form = org.jsoup.Jsoup.parseBodyFragment(r.cuerpo());
        assertThat(form.getElementById("taller-argdown").attr("aria-describedby")).contains("taller-argdown-error");
        assertThat(form.getElementById("taller-argdown-error").text()).contains("Línea 2, columna 3");
    }
}
