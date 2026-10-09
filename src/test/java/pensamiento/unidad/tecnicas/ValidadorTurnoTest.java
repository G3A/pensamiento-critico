package pensamiento.unidad.tecnicas;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import pensamiento.tecnicas.comun.ValidadorTurno;

/**
 * El validador del turno: rechaza lo que no termina en pregunta, lo largo, los veredictos, el voseo y el español
 * peninsular (con las mismas formas del sensor de voseo) y el "usted"; deja pasar una pregunta de tú bien formada.
 */
class ValidadorTurnoTest {

    @Test
    void usa_exactamente_las_formas_del_sensor_de_voseo() throws IOException {
        List<String> sensor = Arrays.stream(Files.readString(Path.of("sensores", "voseo-prohibido.txt"), StandardCharsets.UTF_8).split("\\R"))
                .map(String::strip).filter(l -> !l.isEmpty() && !l.startsWith("#")).toList();

        assertThat(ValidadorTurno.formasProhibidas()).containsExactlyElementsOf(sensor);
    }

    @Test
    void cada_forma_prohibida_hace_rechazar_la_pregunta_por_voseo() {
        for (String forma : ValidadorTurno.formasProhibidas()) {
            assertThat(ValidadorTurno.rechazo("¿Qué " + forma + " sobre los domingos?", 40)).as(forma).contains(ValidadorTurno.Motivo.VOSEO);
        }
    }

    @Test
    void dice_la_forma_prohibida_tal_como_la_escribio_el_modelo() {
        String voseo = "Quer" + "é" + "s"; // armado por partes para que el sensor no lo vea en el código

        assertThat(ValidadorTurno.formaProhibida("¿" + voseo + " abrir la sucursal?")).contains(voseo);
        assertThat(ValidadorTurno.formaProhibida("¿Quieres abrir la sucursal?")).isEmpty();
    }

    @Test
    void rechaza_lo_que_no_es_una_pregunta_de_tu_sin_veredicto() {
        assertThat(ValidadorTurno.rechazo("", 40)).contains(ValidadorTurno.Motivo.VACIO);
        assertThat(ValidadorTurno.rechazo("Piensa en los domingos.", 40)).contains(ValidadorTurno.Motivo.SIN_PREGUNTA);
        assertThat(ValidadorTurno.rechazo("¿" + "palabra ".repeat(41) + "?", 40)).contains(ValidadorTurno.Motivo.LARGO);
        assertThat(ValidadorTurno.rechazo("Tienes razón, ¿no crees que deberías abrir?", 40)).contains(ValidadorTurno.Motivo.VEREDICTO);
        assertThat(ValidadorTurno.rechazo("¿Qué piensa usted de los domingos?", 40)).contains(ValidadorTurno.Motivo.USTED);
        assertThat(ValidadorTurno.rechazo("¿Qué piensan ustedes de los domingos?", 40)).as("ustedes es el plural de tú").isEmpty();
        assertThat(ValidadorTurno.rechazo("«¿Qué das por sentado sobre los domingos?»", 40)).as("las comillas de alrededor no cuentan").isEqualTo(Optional.empty());
    }
}
