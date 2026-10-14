package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Cookie;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pensamiento.testutil.BaseDatosDePrueba;
import pensamiento.testutil.ReenvioLocal;
import pensamiento.web.usuarios.CodigoDeUnUso;

/**
 * Hito 10, entregable 3, por HTTP: el docente pega la lista de su grupo (35 estudiantes más una repetida, una inválida y una
 * que ya existe), ve la vista previa con esas tres marcadas sin que se cree nada, confirma y recibe una hoja con 35 tarjetas;
 * la base guarda solo los hashes de los códigos. Un CSV funciona igual. La hoja imprime 8 tarjetas por página en A4 y en
 * carta (Chromium, PDF). Un código nuevo para una estudiante cierra su sesión abierta.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AulaAltaEnLoteIT {

    private static final Pattern CODIGO = Pattern.compile("class=\"codigo-grande\"[^>]*>([0-9A-Z]{4}-[0-9A-Z]{4})<");

    private final String sufijo = UUID.randomUUID().toString().substring(0, 6);
    private final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private ClienteApp admin;
    private ClienteApp profe;
    private UUID grupo;
    private List<String> nombres;
    private String lista;

    @BeforeAll
    void preparar() {
        admin = Instalacion.administrador();
        profe = Instalacion.docenteNuevo(admin, "Profe Lote " + sufijo);
        grupo = Aula.crearGrupo(admin, "10.º Lote " + sufijo, "Profe Lote " + sufijo);
        Instalacion.personaLlamada(admin, "Ya Existe " + sufijo, "existe");
        nombres = IntStream.rangeClosed(1, 35).mapToObj(i -> "Estudiante " + String.format("%02d", i) + " " + sufijo).toList();
        lista = String.join("\n", nombres) + "\nESTUDIANTE 01 " + sufijo + "\n" + "x".repeat(61) + "\nYa Existe " + sufijo + "\n";
    }

    static List<String> codigos(String html) {
        Matcher m = CODIGO.matcher(html);
        List<String> codigos = new ArrayList<>();
        while (m.find()) {
            codigos.add(m.group(1));
        }
        return codigos;
    }

    @Test
    void la_vista_previa_marca_las_tres_filas_que_no_se_crean_y_no_crea_nada() {
        ClienteApp.Respuesta previa = profe.postFormulario("/grupos/" + grupo + "/lote/vista-previa", Map.of("texto", lista));

        assertThat(previa.estado()).isEqualTo(200);
        assertThat(previa.cuerpo()).contains("Se van a crear 35 cuentas", "repetida en la lista", "nombre inválido", "ya hay una cuenta con ese nombre");
        assertThat(profe.get("/grupos/" + grupo).cuerpo()).doesNotContain("Estudiante 01 " + sufijo);
    }

    @Test
    void confirmar_crea_35_cuentas_en_el_grupo_con_su_tarjeta_y_solo_el_hash_en_la_base() {
        ClienteApp.Respuesta hoja = profe.postFormulario("/grupos/" + grupo + "/lote", Map.of("texto", lista));

        assertThat(hoja.estado()).isEqualTo(201);
        List<String> codigos = codigos(hoja.cuerpo());
        assertThat(codigos).hasSize(35).doesNotHaveDuplicates();
        assertThat(hoja.cuerpo()).contains("35 cuentas creadas", "3 filas no se crearon");
        assertThat(profe.get("/grupos/" + grupo).cuerpo()).contains(nombres.get(0), nombres.get(34), "sin canjear su código");
        for (String codigo : codigos.subList(0, 3)) {
            String normal = CodigoDeUnUso.normalizar(codigo);
            Long enClaro = bd.jdbcAdmin().sql("SELECT count(*) FROM codigo_cuenta WHERE codigo_hash = :c").param("c", normal).query(Long.class).single();
            Long hasheado = bd.jdbcAdmin().sql("SELECT count(*) FROM codigo_cuenta WHERE codigo_hash = :c").param("c", CodigoDeUnUso.hash(normal))
                    .query(Long.class).single();
            assertThat(enClaro).isZero();
            assertThat(hasheado).isEqualTo(1);
        }
        ClienteApp.Respuesta otraVez = profe.postFormulario("/grupos/" + grupo + "/lote", Map.of("texto", lista));
        assertThat(codigos(otraVez.cuerpo())).as("enviar la misma lista otra vez no crea ni muestra códigos").isEmpty();
    }

    @Test
    void un_csv_con_encabezado_da_la_misma_vista_previa() {
        String csv = "nombre;documento\n" + IntStream.rangeClosed(1, 3).mapToObj(i -> "\"Csv " + i + " " + sufijo + "\";100" + i)
                .collect(Collectors.joining("\n"));

        ClienteApp.Respuesta previa = profe.postArchivo("/grupos/" + grupo + "/lote/vista-previa", "archivo", "curso.csv",
                csv.getBytes(StandardCharsets.UTF_8));

        assertThat(previa.estado()).isEqualTo(200);
        assertThat(previa.cuerpo()).contains("Se van a crear 3 cuentas");
        assertThat(org.jsoup.Jsoup.parse(previa.cuerpo()).select("table.vista-previa tbody td:nth-child(2)").eachText())
                .containsExactly("Csv 1 " + sufijo, "Csv 2 " + sufijo, "Csv 3 " + sufijo);
    }

    @Test
    void la_hoja_imprime_8_tarjetas_por_pagina_en_a4_y_en_carta() {
        String otraLista = IntStream.rangeClosed(1, 35).mapToObj(i -> "Hoja " + i + " " + sufijo).collect(Collectors.joining("\n"));
        String html = profe.postFormulario("/grupos/" + grupo + "/lote", Map.of("texto", otraLista)).cuerpo();
        assertThat(codigos(html)).hasSize(35);
        String base = ReenvioLocal.arrancar();
        try (Playwright playwright = Playwright.create();
             Browser chromium = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            var contexto = chromium.newContext();
            contexto.addCookies(List.of(new Cookie("PENSAMIENTO_SESION", profe.cookie("PENSAMIENTO_SESION")).setDomain("localhost").setPath("/")));
            Page pagina = contexto.newPage();
            pagina.navigate(base + "/grupos/" + grupo);
            pagina.setContent(html);
            pagina.waitForFunction("() => getComputedStyle(document.querySelector('.hoja-tarjetas')).display === 'grid'");
            assertThat(paginas(pagina.pdf(new Page.PdfOptions().setFormat("A4")))).as("A4").isEqualTo(5);
            assertThat(paginas(pagina.pdf(new Page.PdfOptions().setFormat("Letter")))).as("carta").isEqualTo(5);
        }
    }

    @Test
    void un_codigo_nuevo_para_una_estudiante_cierra_su_sesion() {
        String nombre = "Restablecer " + sufijo;
        String codigo = codigos(profe.postFormulario("/grupos/" + grupo + "/lote", Map.of("texto", nombre)).cuerpo()).get(0);
        assertThat(Instalacion.canjear(Instalacion.RUTA, codigo, Instalacion.contrasenaDe("estudiante")).estado()).isEqualTo(302);
        ClienteApp estudiante = Instalacion.entraComo(nombre, "estudiante");
        assertThat(estudiante.get("/dojo").estado()).isEqualTo(200);
        UUID id = bd.jdbcAdmin().sql("SELECT id FROM usuario WHERE nombre = :n").param("n", nombre).query(UUID.class).single();

        ClienteApp.Respuesta hoja = profe.postFormulario("/grupos/" + grupo + "/codigos", Map.of("estudiante", id.toString()));

        assertThat(codigos(hoja.cuerpo())).hasSize(1);
        assertThat(hoja.cuerpo()).contains("Tu código para elegir una contraseña nueva", "las sesiones abiertas de esas personas se cerraron");
        assertThat(estudiante.get("/dojo").estado()).as("la sesión vieja ya no sirve").isIn(302, 401);
    }

    @Test
    void otro_docente_no_puede_dar_de_alta_en_este_grupo() {
        ClienteApp otra = Instalacion.docenteNuevo(admin, "Profe Ajena " + sufijo);
        assertThat(otra.postFormulario("/grupos/" + grupo + "/lote", Map.of("texto", "Intrusa " + sufijo)).estado()).isEqualTo(404);
        assertThat(otra.get("/grupos/" + grupo + "/lote").estado()).isEqualTo(404);
    }

    /** Cuántas páginas tiene un PDF: los objetos /Type /Page (no /Pages). */
    static int paginas(byte[] pdf) {
        Matcher m = Pattern.compile("/Type\\s*/Page[^s]").matcher(new String(pdf, StandardCharsets.ISO_8859_1));
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }
}
