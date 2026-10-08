package pensamiento.unidad.flujos;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import pensamiento.argdown.ParserArgdown;
import pensamiento.catalogo.CatalogoJson;
import pensamiento.catalogo.MapeadorJson;
import pensamiento.flujos.TallerDeArgumentos;
import pensamiento.nucleo.Ejemplo;
import pensamiento.tecnicas.f1.EjecutorMapa;
import pensamiento.tecnicas.f1.ResultadoMapa;
import pensamiento.testutil.builders.Contextos;

/** El Taller arma la entrada de T02 y el texto de T13 desde el mapa de T01 (docs/ejemplos/T01.md). */
class TallerDeArgumentosTest {

    private static ResultadoMapa mapa(String titulo) {
        Ejemplo e = new CatalogoJson().ejemplosDe(EjecutorMapa.ID).stream().filter(x -> x.titulo().equals(titulo)).findFirst().orElseThrow();
        return new EjecutorMapa(new ParserArgdown()).ejecutar(MapeadorJson.leer(e.config(), EjecutorMapa.Config.class),
                MapeadorJson.leer(e.datos(), EjecutorMapa.Entrada.class), Contextos.sinIa()).valor();
    }

    @Test
    void del_carro_usado_salen_datos_refutacion_y_su_respuesta_y_lo_que_escribe_la_persona() {
        Map<String, Object> entrada = TallerDeArgumentos.entradaToulmin(mapa("El carro usado"),
                new TallerDeArgumentos.ExtrasToulmin(null, null, "probablemente"));

        assertThat(entrada).containsExactly(
                Map.entry("afirmacion", "Nos conviene comprar un carro usado este año."),
                Map.entry("datos", "Los trayectos al colegio en bus toman hora y media al día. Tenemos ahorrado el 60% del precio de un carro usado."),
                Map.entry("refutacion", "Un carro usado puede salir caro en reparaciones."),
                Map.entry("respuestaRefutacion", "El mecánico de confianza lo revisaría antes de comprarlo."),
                Map.entry("calificador", "probablemente"));
    }

    @Test
    void en_la_sucursal_los_supuestos_a_favor_son_la_garantia_y_la_objecion_queda_sin_respuesta() {
        Map<String, Object> entrada = TallerDeArgumentos.entradaToulmin(mapa("La segunda sucursal"), TallerDeArgumentos.ExtrasToulmin.VACIOS);

        assertThat(entrada).containsExactly(
                Map.entry("afirmacion", "Conviene abrir la segunda sucursal en el centro."),
                Map.entry("garantia", "El centro tiene más tráfico peatonal que el barrio. Más tráfico da más ventas."),
                Map.entry("refutacion", "Falta personal para atender dos locales."));
    }

    @Test
    void el_texto_para_falacias_son_los_enunciados_en_orden_como_oraciones() {
        assertThat(TallerDeArgumentos.textoParaFalacias(mapa("Las cámaras del barrio"))).isEqualTo(
                "La junta debe instalar cámaras en la entrada del barrio. Hubo cuatro robos de bicicletas en el último mes. "
                        + "En el barrio vecino bajaron los robos después de poner cámaras. Las cámaras cuestan lo que la junta recauda en un año.");
    }
}
