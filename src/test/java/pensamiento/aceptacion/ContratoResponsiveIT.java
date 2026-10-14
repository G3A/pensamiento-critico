package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Cookie;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import pensamiento.testutil.BaseDatosDePrueba;
import pensamiento.testutil.ReenvioLocal;

/**
 * Hito 9, entregable 10 (innegociables 11 a 15, RNF-11, RNF-12): la matriz de navegadores con Playwright Java y los
 * navegadores de la imagen oficial (ninguno se descarga). En el PR: Chromium a 360×780 y a 1366×768; en el nocturno
 * (MATRIZ_COMPLETA=true) además WebKit a 390×844, Chromium a 412×915 y Firefox a 1366×768. En las 8 pantallas críticas,
 * solo aserciones geométricas: sin desplazamiento horizontal, ningún elemento interactivo bajo 44 px y la cabecera de 56 px
 * o menos. Más los criterios de Usar de T28, de /usuarios con 500 cuentas y del teclado virtual.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ContratoResponsiveIT {

    /** Un navegador y un tamaño de ventana de la matriz. */
    record Configuracion(String navegador, int ancho, int alto) {
        @Override
        public String toString() {
            return navegador + " " + ancho + "×" + alto;
        }

        boolean angosto() {
            return ancho < 600;
        }
    }

    static List<Configuracion> matriz() {
        List<Configuracion> matriz = new ArrayList<>(List.of(new Configuracion("chromium", 360, 780), new Configuracion("chromium", 1366, 768)));
        if ("true".equalsIgnoreCase(System.getenv("MATRIZ_COMPLETA"))) {
            matriz.addAll(List.of(new Configuracion("webkit", 390, 844), new Configuracion("chromium", 412, 915),
                    new Configuracion("firefox", 1366, 768)));
        }
        return matriz;
    }

    /**
     * Los elementos interactivos visibles de menos de 44 px de alto o de ancho. Un radio o casilla cuenta con su etiqueta.
     * Un enlace dentro de una oración (con texto alrededor) queda fuera, como en WCAG 2.5.8.
     */
    static final String PEQUENOS = """
            () => {
              const enOracion = (e) => e.tagName === 'A' && e.parentElement && Array.from(e.parentElement.childNodes)
                .some(n => n !== e && n.nodeType === 3 && n.textContent.trim().length > 0);
              return Array.from(document.querySelectorAll('a[href], button, input:not([type=hidden]), select, textarea, summary, [role=tab]'))
                .filter(e => { const s = getComputedStyle(e); const r = e.getBoundingClientRect();
                               return r.width > 0 && r.height > 0 && r.right > 0 && r.left < window.innerWidth
                                      && s.visibility !== 'hidden' && !e.closest('[hidden]'); })
                .filter(e => !enOracion(e))
                .map(e => { const caja = (e.type === 'radio' || e.type === 'checkbox') && e.closest('label') ? e.closest('label') : e;
                            const r = caja.getBoundingClientRect();
                            return { r: r, d: e.tagName.toLowerCase() + (e.id ? '#' + e.id : '') + (e.className ? '.' + String(e.className).split(' ')[0] : '')
                                        + ' «' + (e.textContent || e.value || '').trim().slice(0, 24) + '» ' + Math.round(r.width) + '×' + Math.round(r.height) }; })
                .filter(x => x.r.height < 43.5 || x.r.width < 43.5)
                .map(x => x.d).slice(0, 15);
            }""";

    private final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private String sesionDePersona;
    private String sesionDeAdministrador;
    private String rutaConVolumen;
    private UUID expediente;
    private String base;

    @BeforeAll
    void sembrar() {
        base = ReenvioLocal.arrancar();
        ClienteApp admin = Instalacion.administrador();
        String persona = Instalacion.personaNueva(admin, "matriz", "2468");
        ClienteApp sesion = Instalacion.entraComo(persona, "2468");
        expediente = Instalacion.creaExpediente(sesion, "Ventas de los sábados");
        sesionDePersona = sesion.cookie("PENSAMIENTO_SESION");

        rutaConVolumen = "volumen-" + UUID.randomUUID().toString().substring(0, 8);
        UUID institucion = Instalacion.sembrarInstitucion(rutaConVolumen, "Colegio con 500 cuentas");
        bd.jdbcAdmin().sql("""
                INSERT INTO usuario (institucion_id, nombre, clave_hash, tipo_clave, rol_global)
                SELECT :i, 'estudiante ' || lpad(n::text, 3, '0'), 'x', 'contrasena', 'persona' FROM generate_series(1, 500) n
                ON CONFLICT DO NOTHING""").param("i", institucion).update();
        sesionDeAdministrador = Instalacion.administradorDe(rutaConVolumen).cookie("PENSAMIENTO_SESION");
    }

    private void conNavegador(Configuracion c, String sesion, java.util.function.BiConsumer<Page, List<String>> prueba) {
        try (Playwright playwright = Playwright.create()) {
            BrowserType tipo = switch (c.navegador()) {
                case "webkit" -> playwright.webkit();
                case "firefox" -> playwright.firefox();
                default -> playwright.chromium();
            };
            try (Browser navegador = tipo.launch(new BrowserType.LaunchOptions().setHeadless(true))) {
                BrowserContext contexto = navegador.newContext(new Browser.NewContextOptions().setViewportSize(c.ancho(), c.alto()));
                if (sesion != null) {
                    contexto.addCookies(List.of(new Cookie("PENSAMIENTO_SESION", sesion).setDomain("localhost").setPath("/")));
                }
                Page pagina = contexto.newPage();
                List<String> errores = new ArrayList<>();
                pagina.onConsoleMessage(m -> {
                    if ("error".equals(m.type())) {
                        errores.add(m.text());
                    }
                });
                prueba.accept(pagina, errores);
            }
        }
    }

    /** Lo que no cumple el contrato en esta pantalla, en palabras; vacío si cumple. */
    private List<String> revisarPantalla(Page pagina, Configuracion c, String nombre) {
        List<String> problemas = new ArrayList<>();
        int ancho = ((Number) pagina.evaluate("document.documentElement.scrollWidth")).intValue();
        if (ancho != c.ancho()) {
            problemas.add(nombre + ": desplazamiento horizontal (scrollWidth " + ancho + ")");
        }
        @SuppressWarnings("unchecked")
        List<String> pequenos = (List<String>) pagina.evaluate(PEQUENOS);
        pequenos.forEach(p -> problemas.add(nombre + ": bajo 44 px " + p));
        double alto = ((Number) pagina.evaluate("document.querySelector('header.cabecera').getBoundingClientRect().height")).doubleValue();
        if (alto > 56.0) {
            problemas.add(nombre + ": cabecera de " + alto + " px");
        }
        return problemas;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matriz")
    void las_8_pantallas_criticas_caben_sin_desplazamiento_horizontal_y_con_objetivos_de_44_px(Configuracion c) {
        List<String> problemas = new ArrayList<>();
        conNavegador(c, null, (pagina, errores) -> {
            pagina.navigate(base + "/" + Instalacion.RUTA);
            problemas.addAll(revisarPantalla(pagina, c, "entrar"));
            pagina.navigate(base + "/" + Instalacion.RUTA + "/codigo");
            problemas.addAll(revisarPantalla(pagina, c, "alta con código"));
            assertThat(errores).as("errores de consola").isEmpty();
        });
        Map<String, String> conSesion = Map.of("Inicio", "/", "catálogo", "/catalogo", "Usar de T28", "/tecnicas/T28?pestana=usar",
                "Expediente", "/expedientes/" + expediente, "Dojo", "/dojo");
        conNavegador(c, sesionDePersona, (pagina, errores) -> {
            for (Map.Entry<String, String> pantalla : conSesion.entrySet()) {
                pagina.navigate(base + pantalla.getValue());
                assertThat(pagina.url()).as(pantalla.getKey()).isEqualTo(base + pantalla.getValue());
                problemas.addAll(revisarPantalla(pagina, c, pantalla.getKey()));
                if (c.angosto()) {
                    assertThat(pagina.locator(".barra-inferior a").allTextContents()).as("barra inferior en %s", pantalla.getKey())
                            .containsExactly("Inicio", "Catálogo", "Practicar", "Más");
                }
            }
            assertThat(errores).as("errores de consola").isEmpty();
        });
        conNavegador(c, sesionDeAdministrador, (pagina, errores) -> {
            pagina.navigate(base + "/usuarios");
            assertThat(pagina.url()).isEqualTo(base + "/usuarios");
            problemas.addAll(revisarPantalla(pagina, c, "/usuarios"));
            assertThat(errores).as("errores de consola").isEmpty();
        });
        assertThat(problemas).as("contrato responsive en %s", c).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matriz")
    void en_angosto_mas_abre_el_menu_en_cuatro_grupos_sin_javascript(Configuracion c) {
        conNavegador(c, sesionDePersona, (pagina, errores) -> {
            pagina.navigate(base + "/catalogo");
            assertThat(pagina.locator("#menu h2.grupo-menu").allTextContents())
                    .containsExactly("Hacer", "Pensar con apoyo", "Practicar y revisar", "Cuenta");
            if (c.angosto()) {
                assertThat(pagina.locator("#menu").isVisible()).as("el menú empieza cerrado").isFalse();
                pagina.locator(".barra-inferior a", new Page.LocatorOptions().setHasText("Más")).click();
                assertThat(pagina.locator("#menu").isVisible()).as("Más abre el menú").isTrue();
                assertThat(pagina.locator("#menu a[href='/dojo']").isVisible()).isTrue();
            } else {
                assertThat(pagina.locator("#menu").isVisible()).as("en medio y ancho el menú está a la vista").isTrue();
            }
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matriz")
    void usar_de_t28_cabe_con_la_configuracion_una_vez_y_se_pliega_al_evaluar(Configuracion c) {
        conNavegador(c, sesionDePersona, (pagina, errores) -> {
            pagina.navigate(base + "/tecnicas/T28?pestana=usar");
            String resumen = pagina.locator("#resumen-config-T28").textContent().strip();
            Object veces = pagina.evaluate("t => document.body.innerText.split(t).length - 1", resumen);
            assertThat(((Number) veces).intValue()).as("la frase de configuración aparece una vez").isEqualTo(1);
            Object y = pagina.evaluate("() => document.querySelector('#form-T28 textarea, #form-T28 input[type=text]').getBoundingClientRect().top");
            if (c.ancho() >= 1024) {
                assertThat(((Number) y).doubleValue()).as("primer campo editable en %s", c).isLessThan(600.0);
            }

            pagina.locator("nav.barra-ejemplos a.ejemplo").first().click();
            pagina.locator(".aviso-ejemplo").waitFor();
            pagina.locator("#form-T28 button[data-accion=evaluar]").click();
            pagina.locator("#resultado-T28 [data-patron=V03a]").waitFor();
            assertThat(pagina.locator("#form-T28 details.entrada").getAttribute("open")).as("la entrada se pliega").isNull();
            assertThat(pagina.locator("#form-T28 details.entrada > summary").textContent()).contains("Editar entrada");
            if (c.angosto()) {
                pagina.waitForTimeout(800);   // el desplazamiento suave hasta el resultado
                Object arriba = pagina.evaluate("() => document.querySelector('#resultado-T28').getBoundingClientRect().top");
                assertThat(((Number) arriba).doubleValue()).as("el resultado empieza en la primera pantalla").isLessThan(c.alto());
            }
            assertThat(pagina.evaluate("document.documentElement.scrollWidth")).isEqualTo(c.ancho());
            assertThat(errores).as("errores de consola").isEmpty();
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matriz")
    void en_angosto_las_pistas_de_t28_van_una_a_la_vez(Configuracion c) {
        if (!c.angosto()) {
            return;
        }
        conNavegador(c, sesionDePersona, (pagina, errores) -> {
            pagina.navigate(base + "/tecnicas/T28?pestana=usar");
            pagina.locator("nav.barra-ejemplos a.ejemplo").first().click();
            pagina.locator(".aviso-ejemplo").waitFor();
            pagina.locator(".una-a-la-vez").waitFor();
            Function<Page, Long> visibles = p -> ((Number) p.evaluate(
                    "() => Array.from(document.querySelectorAll('.una-a-la-vez .filas > li')).filter(li => li.getBoundingClientRect().height > 0).length")).longValue();
            assertThat(visibles.apply(pagina)).as("una pista a la vez").isEqualTo(1);
            String primera = pagina.locator(".una-a-la-vez .filas > li").first().getAttribute("id");
            pagina.locator(".una-a-la-vez .filas > li").first().locator("a", new com.microsoft.playwright.Locator.LocatorOptions().setHasText("Siguiente pista")).click();
            assertThat(visibles.apply(pagina)).isEqualTo(1);
            assertThat(pagina.url()).endsWith("#" + primera.replaceAll("\\d+$", "1"));
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matriz")
    void usuarios_con_500_cuentas_mide_3_pantallas_o_menos_y_pide_confirmar_antes_de_desactivar(Configuracion c) {
        conNavegador(c, sesionDeAdministrador, (pagina, errores) -> {
            pagina.navigate(base + "/usuarios");
            assertThat(pagina.locator(".cuentas > li").count()).isEqualTo(10);
            assertThat(((Number) pagina.evaluate("document.documentElement.scrollHeight")).intValue())
                    .as("/usuarios con 500 cuentas en %s", c).isLessThanOrEqualTo(3 * c.alto());
            pagina.navigate(base + "/usuarios?q=estudiante+25");
            assertThat(pagina.locator(".cuentas > li .cuenta-nombre").allTextContents()).containsExactly("estudiante 250", "estudiante 251",
                    "estudiante 252", "estudiante 253", "estudiante 254", "estudiante 255", "estudiante 256", "estudiante 257", "estudiante 258",
                    "estudiante 259");
            pagina.locator(".cuentas > li").first().locator("summary", new com.microsoft.playwright.Locator.LocatorOptions().setHasText("Desactivar")).click();
            assertThat(pagina.locator(".cuentas > li").first().getByText("Sí, desactivar").isVisible()).as("confirmación antes de desactivar").isTrue();
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("matriz")
    void con_el_teclado_virtual_abierto_el_campo_de_contrasena_queda_visible_y_con_el_foco(Configuracion c) {
        if (!c.angosto()) {
            return;
        }
        conNavegador(new Configuracion(c.navegador(), c.ancho(), 400), null, (pagina, errores) -> {
            pagina.navigate(base + "/" + Instalacion.RUTA);
            pagina.getByLabel("Tu nombre").fill("alguien");
            pagina.keyboard().press("Tab");
            assertThat(pagina.evaluate("document.activeElement.name")).isEqualTo("contrasena");
            Object caja = pagina.evaluate("() => { const r = document.activeElement.getBoundingClientRect(); return r.top >= 0 && r.bottom <= window.innerHeight; }");
            assertThat(caja).as("el campo de contraseña se ve con 400 px de alto").isEqualTo(true);
        });
    }
}
