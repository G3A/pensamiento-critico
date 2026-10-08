package pensamiento.unidad.tecnicas.f3;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import pensamiento.nucleo.ConModelo;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.Propuesta;
import pensamiento.nucleo.Resultado;
import pensamiento.tecnicas.f3.ConfigFalacias;
import pensamiento.tecnicas.f3.EjecutorFalacias;
import pensamiento.tecnicas.f3.EntradaFalacias;
import pensamiento.tecnicas.f3.ReglasFalacias;
import pensamiento.tecnicas.f3.ResultadoFalacias;
import pensamiento.testutil.builders.Contextos;
import pensamiento.testutil.fakes.FakeIa;
import pensamiento.testutil.fakes.FakeRepositorioEsquemas;

/**
 * T13 · Falacias como esquemas fallidos con "reglas y modelo" (docs/ejemplos/T13.md, ejemplo 3 y sección de la
 * clasificación), con el Fake certificado de Ia: la propuesta no cuenta hasta adoptarse y la falacia la confirma la persona.
 */
class EjecutorFalaciasConModeloTest {

    private static final ConfigFalacias CON_MODELO = new ConfigFalacias(ReglasFalacias.ESQUEMAS, ConfigFalacias.Sensibilidad.REGLAS_Y_MODELO, true);
    private static final String MERCADO = "Este mes gastamos más en mercado porque subió el precio del arroz y del aceite, según las facturas de la tienda.";

    private final EjecutorFalacias t13 = new EjecutorFalacias(new FakeRepositorioEsquemas());

    @Test
    void el_enum_cerrado_tiene_una_etiqueta_por_pregunta_critica_mas_ninguna() {
        List<String> etiquetas = t13.etiquetas(CON_MODELO);
        assertThat(etiquetas).startsWith("ad_hominem:1", "ad_hominem:2", "alternativas:1").endsWith("consecuencias:3", "ninguna");
        assertThat(etiquetas).hasSize(30);
    }

    @Test
    void la_propuesta_del_modelo_no_cuenta_se_adopta_y_entonces_se_puede_confirmar() {
        FakeIa ia = new FakeIa();
        ia.programarClasificacionCruda("{\"etiqueta\":\"causa_efecto:2\",\"por_que\":\"atribuye el gasto solo al precio del arroz y del aceite\"}");

        ConModelo.Propuestas propuestas = t13.proponer(CON_MODELO, new EntradaFalacias(MERCADO, List.of()), Contextos.conIa(ia), t -> { }, 1);
        assertThat(propuestas.nuevas()).singleElement().satisfies(p -> {
            assertThat(p.codigo()).isEqualTo("IA1");
            assertThat(p.destino()).isEqualTo("1");
            assertThat(p.valor()).isEqualTo("causa_efecto:2");
            assertThat(p.rotulo()).isEqualTo("Oración 1 · Argumento de causa a efecto, pregunta 2");
            assertThat(p.prompt()).isEqualTo("t13-esquema.v1");
        });

        EntradaFalacias conPropuesta = new EntradaFalacias(MERCADO, List.of(), propuestas.nuevas());
        Resultado<ResultadoFalacias> sinAdoptar = t13.ejecutar(CON_MODELO, conPropuesta, Contextos.sinIa());
        assertThat(sinAdoptar.resumen()).isEqualTo("Sin marcas: las reglas no reconocieron ningún esquema.");
        assertThat(sinAdoptar.pendientes()).isEmpty();
        assertThat(sinAdoptar.valor().delModelo()).extracting(Propuesta::adoptada).containsExactly(false);
        assertThat(sinAdoptar.modelo()).isPresent();

        EntradaFalacias adoptada = t13.adoptar(conPropuesta, "IA1");
        Resultado<ResultadoFalacias> conMarca = t13.ejecutar(CON_MODELO, adoptada, Contextos.sinIa());
        assertThat(conMarca.resumen()).isEqualTo("1 marca: ninguna falacia confirmada y 1 esquema con preguntas sin responder.");
        assertThat(conMarca.pendientes()).extracting(Pendiente::descripcion)
                .containsExactly("Responder la pregunta crítica de IA1 (Argumento de causa a efecto): ¿Hay otra causa que explique el efecto?");
        assertThat(conMarca.valor().marcas()).singleElement().satisfies(m -> {
            assertThat(m.delModelo()).isTrue();
            assertThat(m.porque()).isEqualTo("Según el modelo: atribuye el gasto solo al precio del arroz y del aceite");
        });

        Resultado<ResultadoFalacias> confirmada = t13.ejecutar(CON_MODELO,
                new EntradaFalacias(MERCADO, List.of("IA1"), adoptada.propuestas()), Contextos.sinIa());
        assertThat(confirmada.resumen()).startsWith("1 marca: 1 falacia confirmada (causa única)");
    }

    @Test
    void confirmar_una_propuesta_sin_adoptar_no_hace_nada() {
        Propuesta ia1 = new Propuesta("IA1", "1", "Oración 1", "causa_efecto:2", "", false, "qwen3:4b", "sha256:fake", "t13-esquema.v1");
        Resultado<ResultadoFalacias> r = t13.ejecutar(CON_MODELO, new EntradaFalacias(MERCADO, List.of("IA1"), List.of(ia1)), Contextos.sinIa());
        assertThat(r.valor().marcas()).isEmpty();
    }

    @Test
    void solo_clasifica_las_oraciones_que_las_reglas_no_marcaron() {
        FakeIa ia = new FakeIa();
        ia.programarClasificacionCruda("{\"etiqueta\":\"ninguna\",\"por_que\":\"da un dato con su fuente\"}");
        String texto = "El proveedor dice que su harina nueva rinde un 20% más, así que conviene cambiarnos a ella. " + MERCADO;
        List<String> avance = new java.util.ArrayList<>();

        ConModelo.Propuestas propuestas = t13.proponer(CON_MODELO, new EntradaFalacias(texto, List.of()), Contextos.conIa(ia), avance::add, 1);

        assertThat(propuestas.nuevas()).isEmpty();
        assertThat(avance).containsExactly("Oración 2 de 2… ", "1 oración clasificada.");
    }

    @Test
    void propuestas_de_otro_texto_no_se_aceptan() {
        Propuesta deOtro = new Propuesta("IA1", "5", "Oración 5", "causa_efecto:2", "", true, "qwen3:4b", "sha256:fake", "t13-esquema.v1");
        assertThat(t13.validar(CON_MODELO, new EntradaFalacias(MERCADO, List.of(), List.of(deOtro))).errores())
                .extracting(e -> e.campo()).containsExactly("propuestas");
    }
}
