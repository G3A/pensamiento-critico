package pensamiento.unidad.web.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import pensamiento.web.seguridad.RespuestasSesion;

/** Tras entrar, solo se vuelve a rutas del propio sitio: nunca a otro dominio ni a esquemas raros. */
class RespuestasSesionTest {

    @ParameterizedTest(name = "volver={0} → {1}")
    @CsvSource({
            "/, /",
            "/catalogo, /catalogo",
            "/catalogo?familia=F5&intencion=decision, /catalogo?familia=F5&intencion=decision",
            "/expedientes/0192f3a1-7b2c-7d3e-8f4a-5b6c7d8e9f0a, /expedientes/0192f3a1-7b2c-7d3e-8f4a-5b6c7d8e9f0a"
    })
    void una_ruta_relativa_del_sitio_se_conserva(String volver, String esperado) {
        assertThat(RespuestasSesion.destinoSeguro(volver)).isEqualTo(esperado);
    }

    @ParameterizedTest(name = "volver={0} → /")
    @NullAndEmptySource
    @ValueSource(strings = {"//otro-sitio.example", "https://otro-sitio.example/", "javascript:alert(1)", "catalogo", "/catalogo\r\nSet-Cookie: x", "/\\\\otro", "/catalogo<script>"})
    void todo_lo_que_no_sea_una_ruta_del_sitio_vuelve_al_inicio(String volver) {
        assertThat(RespuestasSesion.destinoSeguro(volver)).isEqualTo("/");
    }
}
