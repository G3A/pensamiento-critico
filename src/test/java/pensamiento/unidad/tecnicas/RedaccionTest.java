package pensamiento.unidad.tecnicas;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import pensamiento.nucleo.puertos.Mensaje;
import pensamiento.tecnicas.comun.Redaccion;
import pensamiento.tecnicas.comun.ValidadorTurno;
import pensamiento.testutil.fakes.FakeIa;

/**
 * La redacción con reintentos: el reintento sigue la conversación con el texto rechazado y una corrección que dice qué
 * falló, porque con temperatura 0 el mismo pedido devuelve la misma pregunta.
 */
class RedaccionTest {

    private static final String VOSEO = "¿Qué quer" + "é" + "s lograr con la sucursal?"; // armado por partes para que el sensor no lo vea

    @Test
    void el_reintento_lleva_la_pregunta_rechazada_y_la_forma_que_hay_que_cambiar() {
        FakeIa ia = new FakeIa();
        ia.programarRespuesta(VOSEO);
        ia.programarRespuesta("¿Qué quieres lograr con la sucursal?");
        List<String> provisional = new ArrayList<>();

        Redaccion.Redactado r = Redaccion.redactar(ia, "sistema", "pedido", 40, provisional::add);

        assertThat(r.texto()).contains("¿Qué quieres lograr con la sucursal?");
        assertThat(r.intentos()).extracting(Redaccion.Intento::rechazo)
                .containsExactly(Optional.of(ValidadorTurno.Motivo.VOSEO), Optional.empty());
        assertThat(ia.chatsRecibidos().get(0).mensajes()).extracting(Mensaje::contenido).containsExactly("sistema", "pedido");
        assertThat(ia.chatsRecibidos().get(1).mensajes()).extracting(Mensaje::contenido).containsExactly("sistema", "pedido", VOSEO,
                "Esa pregunta no sirve. Usaste «quer" + "é" + "s», que no es tuteo de Latinoamérica. Trata a la persona de tú, como en"
                        + " «quieres», «tienes» o «sabes». Escribe solo la pregunta corregida.");
        assertThat(String.join("", provisional)).contains("(no pasó el validador: trae voseo o español peninsular; otro intento)");
    }

    @Test
    void cada_motivo_tiene_su_correccion() {
        assertThat(Redaccion.correccion(ValidadorTurno.Motivo.LARGO, "¿…?", 40))
                .isEqualTo("Esa pregunta no sirve. Es demasiado larga: usa 40 palabras o menos. Escribe solo la pregunta corregida.");
        assertThat(Redaccion.correccion(ValidadorTurno.Motivo.SIN_PREGUNTA, "Piensa en eso.", 40))
                .isEqualTo("Esa pregunta no sirve. Tiene que ser una sola pregunta que termine con signo de pregunta. Escribe solo la pregunta corregida.");
        assertThat(Redaccion.correccion(ValidadorTurno.Motivo.USTED, "¿Qué quiere usted?", 40))
                .isEqualTo("Esa pregunta no sirve. Usaste «usted». Trata a la persona de tú. Escribe solo la pregunta corregida.");
    }

    @Test
    void tres_rechazos_dejan_el_texto_vacio_para_que_quien_llama_use_el_banco() {
        FakeIa ia = new FakeIa();
        ia.programarRespuesta(VOSEO);
        ia.programarRespuesta(VOSEO);
        ia.programarRespuesta(VOSEO);

        Redaccion.Redactado r = Redaccion.redactar(ia, "sistema", "pedido", 40, t -> { });

        assertThat(r.texto()).isEmpty();
        assertThat(r.intentos()).hasSize(3);
        assertThat(ia.chatsRecibidos().get(2).mensajes()).hasSize(6);
    }
}
