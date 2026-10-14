package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * Hito 10, entregable 9, por HTTP: con su clase abierta, el estudiante ve las técnicas sin el asistente de IA (apagado por
 * defecto en el aula), aunque una persona fuera del aula lo tenga; si su docente lo enciende para el grupo, el estudiante ve lo
 * mismo que cualquiera.
 */
class AulaIaIT {

    private static final String SIN_ASISTENTE = "sin asistente de IA";

    @Test
    void en_clase_el_modelo_esta_apagado_hasta_que_el_docente_lo_enciende() {
        String sufijo = UUID.randomUUID().toString().substring(0, 6);
        ClienteApp admin = Instalacion.administrador();
        ClienteApp profe = Instalacion.docenteNuevo(admin, "Profe IA " + sufijo);
        UUID grupo = Aula.crearGrupo(admin, "11.º IA " + sufijo, "Profe IA " + sufijo);
        String nombre = "Estudiante IA " + sufijo;
        String codigo = AulaAltaEnLoteIT.codigos(profe.postFormulario("/grupos/" + grupo + "/lote", Map.of("texto", nombre)).cuerpo()).get(0);
        Instalacion.canjear(Instalacion.RUTA, codigo, Instalacion.contrasenaDe("estudiante"));
        ClienteApp persona = Instalacion.entraComo(Instalacion.personaNueva(admin, "persona", "persona"), "persona");
        boolean fueraDelAulaSinModelo = persona.get("/tecnicas/T13").cuerpo().contains(SIN_ASISTENTE);

        ClienteApp antesDeLaClase = Instalacion.entraComo(nombre, "estudiante");
        assertThat(antesDeLaClase.get("/tecnicas/T13").cuerpo().contains(SIN_ASISTENTE)).as("fuera de clase, como cualquiera").isEqualTo(fueraDelAulaSinModelo);

        profe.postFormulario("/grupos/" + grupo + "/clase", Map.of("minutos", "50"));
        ClienteApp enClase = Instalacion.entraComo(nombre, "estudiante");
        assertThat(enClase.get("/tecnicas/T13").cuerpo()).as("en clase, apagado por defecto").contains(SIN_ASISTENTE);

        profe.postFormulario("/grupos/" + grupo + "/opciones", Map.of("ia", "si"));
        ClienteApp conModelo = Instalacion.entraComo(nombre, "estudiante");
        assertThat(conModelo.get("/tecnicas/T13").cuerpo().contains(SIN_ASISTENTE)).as("encendido por su docente").isEqualTo(fueraDelAulaSinModelo);

        profe.postFormulario("/grupos/" + grupo + "/clase/terminar", Map.of());
    }
}
