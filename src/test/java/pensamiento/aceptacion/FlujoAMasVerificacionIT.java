package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.Test;

import pensamiento.catalogo.MapeadorJson;
import pensamiento.expediente.PaqueteDatos;
import pensamiento.testutil.PdfMinimo;

/**
 * Aceptación por HTTP del hito 6 (flujo A+, RNF-08, RF-03 extendido, RF-12), con el ejemplo de docs/verificacion.md: la dueña
 * de la panadería importa el conteo del municipio en PDF, lo encuentra desde la premisa del tráfico en la ficha de
 * verificación, registra dos fuentes de grupos distintos y el veredicto recalcula R04; desde el pendiente de T11 el veredicto
 * cierra ese pendiente. Otra persona recibe 404 y no ve el documento privado hasta que se comparte.
 */
class FlujoAMasVerificacionIT {

    private static final String MAPA = """
            [Sucursal]: Conviene abrir la segunda sucursal en el centro.
              + <Más ventas>: Con más gente pasando, se vende más. {peso: 3}
                + [Tráfico]: El centro tiene más tráfico peatonal que el barrio.
                + [Conversión]: Más tráfico da más ventas. #asumible
              - [Personal]: Falta personal para atender dos locales. {peso: 2}""";
    private static final String TRAFICO = "El centro tiene más tráfico peatonal que el barrio.";
    private static final String ESQUINA = "Pasan al menos 1.000 personas por la esquina cada mañana.";
    private static final Duration ESPERA = Duration.ofSeconds(90);

    static byte[] conteoDelMunicipio(String marca) {
        return PdfMinimo.de(List.of("Conteo peatonal del municipio, marzo de 2025. " + marca,
                "En el centro pasan en promedio 1.200 personas por hora; en el barrio, 300.",
                "En la esquina de la plaza pasan en promedio 1.150 personas entre las 7 y las 10 de la mañana."));
    }

    @Test
    void del_pdf_importado_a_la_premisa_verificada_y_al_estandar_de_prueba_recalculado() {
        ClienteApp admin = Instalacion.administrador();
        String duena = Instalacion.personaNueva(admin, "dueña", "1357");
        ClienteApp persona = Instalacion.entraComo(duena, "1357");
        Taller taller = Taller.de(persona);
        Verificacion verificacion = Verificacion.de(persona);

        // El mapa de la sucursal, guardado en el Taller con estándar preponderancia.
        List<UUID> guardadas = taller.guardarEnElTaller(taller.evaluarEnElTaller(taller.tallerDeArgumentos().escribirArgumento(MAPA)
                .elegirEstandar("preponderancia")));
        PaqueteDatos antes = MapeadorJson.mapper().readValue(taller.exportarMisDatos(), PaqueteDatos.class);
        UUID masVentas = antes.ejecuciones().stream().filter(e -> e.id().equals(guardadas.getFirst())).findFirst().orElseThrow()
                .argumentos().getFirst().id();
        Document argumento = Jsoup.parse(persona.get("/argumentos/" + masVentas).cuerpo());
        assertThat(argumento.select(".r04 [data-aceptable]").attr("data-aceptable")).as("antes de verificar").isEqualTo("false");
        Element premisa = argumento.select("li[data-premisa]").stream().filter(li -> li.text().contains(TRAFICO)).findFirst().orElseThrow();
        assertThat(premisa.select(".chip").text()).contains("sin verificar");
        UUID trafico = UUID.fromString(premisa.attr("data-premisa"));
        assertThat(premisa.select("a").attr("href")).isEqualTo("/verificar/" + trafico);

        // P13: importar el PDF (privado, en proceso) y esperar a que el trabajo largo lo indexe.
        UUID conteo = verificacion.importarDocumento("conteo-peatonal-municipio-2025.pdf", conteoDelMunicipio(UUID.randomUUID().toString()));
        Element indexado = verificacion.esperarEstado(conteo, "indexado", ESPERA);
        assertThat(indexado.text()).contains("PDF · 3 páginas", "privado");

        // P10, pasos 1 y 2: hecho, con el aviso de la tendencia sin cifra; dos preguntas respondidas.
        Document ficha = verificacion.ficha(trafico);
        assertThat(ficha.select("blockquote.afirmacion-ficha").text()).contains(TRAFICO);
        Document conTipo = verificacion.elegirTipo(trafico, "hecho");
        assertThat(conTipo.select("[data-chequeo=Cifra y fecha]").text()).contains("aviso", "Afirma una comparación o tendencia sin cifra ni fecha.");
        verificacion.marcarPreguntas(trafico, List.of("¿Quién lo registró y cómo?", "¿De cuándo es el dato?"));

        // P11: el pasaje del PDF aparece buscando desde la premisa, con su documento y su página.
        Document resultados = verificacion.buscar(TRAFICO, true, trafico);
        Element pasaje = resultados.select("li[data-fragmento]").stream()
                .filter(li -> li.text().contains("En el centro pasan en promedio 1.200 personas por hora")).findFirst()
                .orElseThrow(() -> new AssertionError("El pasaje del PDF no aparece: " + resultados.text()));
        assertThat(pasaje.text()).contains("conteo-peatonal-municipio-2025.pdf, p. 2");
        assertThat(pasaje.select("a").attr("href")).startsWith("/verificar/" + trafico + "/fuentes/nueva?fragmento=");
        UUID fragmento = UUID.fromString(pasaje.attr("data-fragmento"));

        // P12: la ficha de fuente llega con el pasaje y la ubicación; antes de guardar dice qué aporta.
        Document formulario = verificacion.nuevaFuente(trafico, fragmento);
        assertThat(formulario.select("#f-pasaje").text()).isEqualTo("En el centro pasan en promedio 1.200 personas por hora; en el barrio, 300.");
        assertThat(formulario.text()).contains("Ubicación: biblioteca, conteo-peatonal-municipio-2025.pdf, p. 2");
        Verificacion.Fuente f1 = new Verificacion.Fuente("Conteo peatonal del municipio", "2025-03-15", "primaria", "no_aplica", "municipio", true, true,
                List.of(4, 5, 4, 4, 3), "apoya", "", fragmento.toString());
        assertThat(verificacion.previa(trafico, f1).text()).contains("CRAAP 20 de 25", "Aporta fuerza");
        ClienteApp.Respuesta guardadaF1 = verificacion.registrar(trafico, f1);
        assertThat(guardadaF1.estado()).isEqualTo(200);
        assertThat(guardadaF1.cabecera("HX-Redirect")).contains("/verificar/" + trafico);
        assertThat(verificacion.registrar(trafico, new Verificacion.Fuente("Informe de la cámara de comercio", "2024-11-01", "secundaria", "no_aplica",
                "cámara de comercio", true, false, List.of(4, 4, 3, 3, 4), "apoya", "El centro concentra el mayor flujo de compradores de la ciudad.", null))
                .estado()).isEqualTo(200);

        // P10, paso 4: el cálculo dice verificada, firmada por la persona; guardar el veredicto recalcula R04.
        Document conDos = verificacion.ficha(trafico);
        assertThat(conDos.select("[data-estado-calculado]").text()).isEqualTo("verificada");
        assertThat(conDos.select(".firma").text()).isEqualTo("Verificada por ti con 2 fuentes de 2 grupos distintos, bajo R03.");
        assertThat(conDos.text().toLowerCase()).doesNotContain("verdadero");
        ClienteApp.Respuesta veredicto = verificacion.veredicto(trafico, 80);
        assertThat(veredicto.estado()).isEqualTo(200);
        Document tras = Jsoup.parseBodyFragment(veredicto.cuerpo());
        assertThat(tras.select(".mensaje-ficha").text()).contains("Veredicto guardado: verificada.");
        assertThat(tras.select(".r04 [data-aceptable]").attr("data-aceptable")).isEqualTo("true");
        assertThat(tras.select(".r04 tr[data-estandar=mas_alla_de_duda_razonable] td").text()).isEqualTo("no");
        Document argumentoDespues = Jsoup.parse(persona.get("/argumentos/" + masVentas).cuerpo());
        assertThat(argumentoDespues.select(".r04 [data-aceptable]").attr("data-aceptable")).as("el argumento ya es aceptable").isEqualTo("true");

        // Desde el pendiente de T11: el veredicto lo cierra y deja el pendiente concreto de T22.
        Taller.Formulario t11 = taller.cargarEjemplo("T11", "La sucursal que se paga sola");
        taller.guardar(t11);
        Element enInicio = Jsoup.parse(persona.get("/").cuerpo()).select(".pendientes a").stream()
                .filter(a -> a.text().equals("Verificar la condición: " + ESQUINA)).findFirst().orElseThrow();
        UUID esquina = UUID.fromString(enInicio.attr("href").substring("/verificar/".length()));
        verificacion.elegirTipo(esquina, "dato_estadistico");
        UUID fragmentoEsquina = UUID.fromString(verificacion.buscar("esquina de la plaza", true, esquina).select("li[data-fragmento]").stream()
                .filter(li -> li.text().contains("1.150 personas")).findFirst().orElseThrow().attr("data-fragmento"));
        verificacion.registrar(esquina, new Verificacion.Fuente("Conteo peatonal del municipio", "2025-03-15", "primaria", "observacional", "municipio",
                true, true, List.of(4, 5, 4, 4, 3), "apoya", "", fragmentoEsquina.toString()));
        ClienteApp.Respuesta delPendiente = verificacion.veredicto(esquina, 70);
        assertThat(Jsoup.parseBodyFragment(delPendiente.cuerpo()).select(".mensaje-ficha").text())
                .contains("Veredicto guardado: en verificación.", "Se cerró 1 pendiente de verificación.");
        List<String> pendientes = Jsoup.parse(persona.get("/").cuerpo()).select(".pendientes a").eachText();
        assertThat(pendientes).doesNotContain("Verificar la condición: " + ESQUINA).contains("Buscar una fuente independiente para: " + ESQUINA);

        // RF-12: el respaldo versión 5 lleva el documento sin el original, las evidencias y el veredicto.
        String archivo = taller.exportarMisDatos();
        assertThat(archivo).contains("\"version\" : 5", "conteo-peatonal-municipio-2025.pdf", "En la esquina de la plaza pasan en promedio 1.150",
                "\"estado\" : \"verificada\"");
        PaqueteDatos paquete = MapeadorJson.mapper().readValue(archivo, PaqueteDatos.class);
        assertThat(paquete.evidencias()).hasSize(3);
        assertThat(taller.importarMisDatos(archivo).estado()).as("importar lo propio no duplica").isEqualTo(200);
        assertThat(verificacion.ficha(trafico).select("li[data-evidencia]")).hasSize(2);

        // RF-03 extendido: otra persona recibe 404 y no encuentra el documento privado; compartirlo lo hace visible.
        String secretaria = Instalacion.personaNueva(admin, "secretaria de la junta", "2468");
        ClienteApp otra = Instalacion.entraComo(secretaria, "2468");
        Verificacion ajena = Verificacion.de(otra);
        assertThat(otra.get("/verificar/" + trafico).estado()).isEqualTo(404);
        assertThat(otra.get("/verificar/" + trafico + "/fuentes/nueva").estado()).isEqualTo(404);
        assertThat(ajena.veredicto(trafico, 10).estado()).isEqualTo(404);
        assertThat(otra.get("/biblioteca/" + conteo + "/original").estado()).isEqualTo(404);
        assertThat(ajena.buscar("1.200 personas por hora", true, null).select("li[data-fragmento]")).isEmpty();
        assertThat(ajena.lista().select("li[data-documento=" + conteo + "]")).isEmpty();
        assertThat(otra.postFormulario("/biblioteca/" + conteo + "/borrar", java.util.Map.of(), true).estado()).isEqualTo(404);

        assertThat(persona.postFormulario("/biblioteca/" + conteo + "/compartir", java.util.Map.of("compartido", "true"), true).estado()).isEqualTo(200);
        assertThat(ajena.lista().select("li[data-documento=" + conteo + "]").text()).contains("de otra persona");
        assertThat(ajena.buscar("1.200 personas por hora", true, null).select("li[data-fragmento]")).isNotEmpty();
        assertThat(otra.postFormulario("/biblioteca/" + conteo + "/borrar", java.util.Map.of(), true).estado()).as("no puede borrar lo ajeno").isEqualTo(404);
        assertThat(persona.get("/biblioteca/" + conteo + "/original").cabecera("Content-Disposition")).hasValueSatisfying(c -> assertThat(c).contains("attachment"));
    }

    @Test
    void un_pdf_escaneado_queda_en_error_y_una_imagen_o_un_repetido_se_rechazan_con_su_motivo() {
        ClienteApp admin = Instalacion.administrador();
        String vecino = Instalacion.personaNueva(admin, "vecino", "8642");
        Verificacion verificacion = Verificacion.de(Instalacion.entraComo(vecino, "8642"));

        UUID escaneado = verificacion.importarDocumento("contrato-escaneado.pdf", PdfMinimo.de(List.of("")));
        assertThat(verificacion.esperarEstado(escaneado, "error", ESPERA).text()).contains("Sin texto extraíble (es imagen): la app no hace OCR.");

        ClienteApp.Respuesta imagen = verificacion.importar("foto.pdf", new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n', 0, 0, 0, 13});
        assertThat(imagen.estado()).isEqualTo(422);
        assertThat(imagen.cuerpo()).contains("Tipo no permitido: solo PDF, Markdown, texto o CSV.");

        byte[] acta = "Acta de la junta de vecinos.\n\nSe aprobó esperar a la próxima reunión.".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        verificacion.importarDocumento("acta.txt", acta);
        ClienteApp.Respuesta repetido = verificacion.importar("copia-del-acta.txt", acta);
        assertThat(repetido.estado()).isEqualTo(422);
        assertThat(repetido.cuerpo()).contains("Ya importaste este documento: acta.txt");
    }
}
