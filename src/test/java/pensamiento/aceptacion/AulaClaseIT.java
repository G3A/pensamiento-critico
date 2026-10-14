package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import pensamiento.testutil.BaseDatosDePrueba;

/**
 * Hito 10, entregables 4 y 5, por HTTP: el docente abre la clase de su grupo y ve un código corto y un QR (SVG del servidor)
 * que lleva a la entrada con el código; ahí la lista de nombres aparece solo si la activó y solo la de ese grupo; la
 * auditoría distingue las entradas con lista y sin lista; desde la segunda clase 35 estudiantes entran a la vez en menos de 5
 * minutos; "Terminar clase" cierra sus sesiones y el código deja de servir. Fuera de una clase abierta no hay lista.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AulaClaseIT {

    private static final Pattern CODIGO_CLASE = Pattern.compile("class=\"codigo-grande codigo-clase\"[^>]*>([0-9A-Z]{6})<");

    private final String sufijo = UUID.randomUUID().toString().substring(0, 6);
    private final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private ClienteApp profe;
    private UUID grupo;
    private List<String> nombres;
    private String codigo;
    private ClienteApp enClase;

    @BeforeAll
    void preparar() {
        ClienteApp admin = Instalacion.administrador();
        profe = Instalacion.docenteNuevo(admin, "Profe Clase " + sufijo);
        grupo = Aula.crearGrupo(admin, "11.º Clase " + sufijo, "Profe Clase " + sufijo);
        UUID otro = Aula.crearGrupo(admin, "11.º Otro " + sufijo, "Profe Clase " + sufijo);
        profe.postFormulario("/grupos/" + otro + "/lote", Map.of("texto", "Ajena " + sufijo));
        nombres = IntStream.rangeClosed(1, 35).mapToObj(i -> "Clase " + String.format("%02d", i) + " " + sufijo).toList();
        List<String> codigos = AulaAltaEnLoteIT.codigos(profe.postFormulario("/grupos/" + grupo + "/lote", Map.of("texto", String.join("\n", nombres))).cuerpo());
        assertThat(codigos).hasSize(35);
        // Primera clase: cada estudiante canjea su código de la tarjeta y elige su contraseña.
        for (String c : codigos) {
            assertThat(Instalacion.canjear(Instalacion.RUTA, c, Instalacion.contrasenaDe("estudiante")).estado()).isEqualTo(302);
        }
    }

    @Test
    @Order(1)
    void el_docente_abre_la_clase_y_ve_el_codigo_corto_y_el_qr() {
        assertThat(profe.postFormulario("/grupos/" + grupo + "/clase", Map.of("minutos", "50")).estado()).isEqualTo(302);

        String pagina = profe.get("/grupos/" + grupo + "/clase").cuerpo();
        Matcher m = CODIGO_CLASE.matcher(pagina);
        assertThat(m.find()).as("la página trae el código").isTrue();
        codigo = m.group(1);
        assertThat(pagina).contains("<svg", "class=\"qr\"", "/" + Instalacion.RUTA + "?clase=" + codigo, "Terminar clase").doesNotContain("<script>");
        assertThat(profe.get("/grupos/" + grupo + "/clase").cuerpo()).as("recargar no pierde el código").contains(codigo);
        assertThat(profe.get("/grupos/" + grupo).cuerpo()).contains("clase abierta");
    }

    @Test
    @Order(2)
    void con_el_codigo_la_entrada_dice_el_grupo_y_sin_activar_no_muestra_nombres() {
        String entrada = new ClienteApp(Instalacion.RUTA).get("/" + Instalacion.RUTA + "?clase=" + codigo).cuerpo();

        assertThat(entrada).contains("Clase de <strong>11.º Clase " + sufijo).doesNotContain(nombres.get(0));
        assertThat(new ClienteApp(Instalacion.RUTA).get("/" + Instalacion.RUTA + "?clase=ZZZZZZ").cuerpo()).doesNotContain("Clase de <strong>");
    }

    @Test
    @Order(3)
    void activada_la_lista_aparecen_solo_los_nombres_de_ese_grupo() {
        profe.postFormulario("/grupos/" + grupo + "/opciones", Map.of("lista", "si"));

        String entrada = new ClienteApp(Instalacion.RUTA).get("/" + Instalacion.RUTA + "?clase=" + codigo).cuerpo();

        assertThat(entrada).contains(nombres.get(0), nombres.get(34)).doesNotContain("Ajena " + sufijo);
        assertThat(new ClienteApp(Instalacion.RUTA).get("/" + Instalacion.RUTA).cuerpo()).as("sin código, nunca la lista").doesNotContain(nombres.get(0));
        String tocado = new ClienteApp(Instalacion.RUTA).get("/" + Instalacion.RUTA + "?clase=" + codigo + "&lista=si&nombre="
                + java.net.URLEncoder.encode(nombres.get(0), java.nio.charset.StandardCharsets.UTF_8)).cuerpo();
        assertThat(tocado).contains("value=\"" + nombres.get(0) + "\"", "name=\"lista\" value=\"si\"");
    }

    @Test
    @Order(4)
    void la_auditoria_cuenta_las_entradas_con_lista_y_sin_lista() {
        ClienteApp conLista = new ClienteApp(Instalacion.RUTA);
        ClienteApp sinLista = new ClienteApp(Instalacion.RUTA);
        assertThat(conLista.entrarEnClase(nombres.get(0), Instalacion.contrasenaDe("estudiante"), codigo, true).estado()).isEqualTo(302);
        assertThat(sinLista.entrarEnClase(nombres.get(1), Instalacion.contrasenaDe("estudiante"), codigo, false).estado()).isEqualTo(302);
        enClase = conLista;

        assertThat(detalleDeEntrada(nombres.get(0))).isEqualTo("clase:con-lista");
        assertThat(detalleDeEntrada(nombres.get(1))).isEqualTo("clase:sin-lista");
    }

    @Test
    @Order(5)
    void desde_la_segunda_clase_35_estudiantes_entran_a_la_vez_en_menos_de_5_minutos() throws Exception {
        Instant inicio = Instant.now();
        List<Integer> estados;
        try (ExecutorService hilos = Executors.newFixedThreadPool(35)) {
            List<Future<Integer>> entradas = nombres.stream().map(n -> hilos.submit(() ->
                    new ClienteApp(Instalacion.RUTA).entrarEnClase(n, Instalacion.contrasenaDe("estudiante"), codigo, false).estado())).toList();
            estados = entradas.stream().map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }).collect(Collectors.toList());
        }
        Duration tardaron = Duration.between(inicio, Instant.now());

        assertThat(estados).hasSize(35).containsOnly(302);
        assertThat(tardaron).as("35 entradas a la vez: " + tardaron).isLessThan(Duration.ofMinutes(5));
        System.out.println("[medición hito 10] 35 entradas a la vez en " + tardaron.toMillis() + " ms");
    }

    @Test
    @Order(6)
    void terminar_la_clase_cierra_las_sesiones_del_grupo_y_el_codigo_deja_de_servir() {
        assertThat(enClase.get("/dojo").estado()).isEqualTo(200);

        assertThat(profe.postFormulario("/grupos/" + grupo + "/clase/terminar", Map.of()).estado()).isEqualTo(302);

        assertThat(enClase.get("/dojo").estado()).as("la sesión del estudiante se cerró").isIn(302, 401);
        String entrada = new ClienteApp(Instalacion.RUTA).get("/" + Instalacion.RUTA + "?clase=" + codigo).cuerpo();
        assertThat(entrada).doesNotContain("Clase de <strong>").doesNotContain(nombres.get(0));
        assertThat(profe.get("/grupos/" + grupo + "?aviso=terminada").cuerpo()).contains("La clase terminó")
                .doesNotContain("<span class=\"chip chip-ok\">clase abierta</span>");
    }

    private String detalleDeEntrada(String nombre) {
        return bd.jdbcAdmin().sql("""
                SELECT a.detalle FROM auditoria a JOIN usuario u ON u.id = a.usuario_id
                WHERE u.nombre = :n AND a.accion = 'entrar' ORDER BY a.fecha DESC LIMIT 1""").param("n", nombre).query(String.class).single();
    }
}
