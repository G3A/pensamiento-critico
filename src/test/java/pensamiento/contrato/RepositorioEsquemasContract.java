package pensamiento.contrato;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.Test;

import pensamiento.catalogo.CatalogoJson;
import pensamiento.nucleo.Esquema;
import pensamiento.nucleo.puertos.RepositorioEsquemas;

/**
 * Contrato del catálogo de esquemas de Walton: lo que devuelve es exactamente catalogo/esquemas.json (la
 * semilla es la única que escribe la tabla), ordenado por identificador, con preguntas en orden, tildes
 * intactas y vacío para un identificador que no existe.
 */
public abstract class RepositorioEsquemasContract {

    protected abstract RepositorioEsquemas crearSut();

    private final List<Esquema> catalogo = new CatalogoJson().esquemas();

    @Test
    void todos_devuelve_el_catalogo_completo_ordenado_por_identificador() {
        List<Esquema> esperado = catalogo.stream().sorted(Comparator.comparing(Esquema::id)).toList();
        assertThat(crearSut().todos()).containsExactlyElementsOf(esperado);
    }

    @Test
    void cada_pregunta_conserva_su_numero_su_texto_con_tildes_y_su_etiqueta_de_falacia() {
        Esquema autoridad = crearSut().porId("autoridad").orElseThrow();
        assertThat(autoridad.nombre()).isEqualTo("Argumento por la opinión de un experto");
        assertThat(autoridad.preguntas()).extracting(Esquema.PreguntaCritica::numero).containsExactly(1, 2, 3, 4);
        Esquema.PreguntaCritica interes = autoridad.pregunta(2).orElseThrow();
        assertThat(interes.texto()).isEqualTo("¿Quien lo afirma tiene un interés propio en que le creas?");
        assertThat(interes.falacia()).isEqualTo("apelación a una autoridad interesada");
        assertThat(interes.comoResponder()).isNotBlank();
    }

    @Test
    void un_identificador_que_no_existe_devuelve_vacio() {
        assertThat(crearSut().porId("no_existe")).isEmpty();
    }

    @Test
    void leer_dos_veces_devuelve_lo_mismo() {
        RepositorioEsquemas sut = crearSut();
        assertThat(sut.todos()).isEqualTo(sut.todos());
        assertThat(sut.porId("ad_hominem")).isEqualTo(sut.porId("ad_hominem"));
    }
}
