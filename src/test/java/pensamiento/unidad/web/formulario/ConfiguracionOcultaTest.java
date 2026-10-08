package pensamiento.unidad.web.formulario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import pensamiento.catalogo.CatalogoJson;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Tecnica;
import pensamiento.web.formulario.Campo;
import pensamiento.web.formulario.LectorFormulario;
import pensamiento.web.formulario.LenguajeCampos;
import pensamiento.web.tecnicas.VistaFormulario;

/**
 * La configuración viaja oculta en el formulario de Usar y vuelve igual al evaluar. Un conjunto (las partes
 * obligatorias de T02 · Modelo de Toulmin) viaja como parámetro repetido, no como el texto "[afirmacion, datos]".
 */
class ConfiguracionOcultaTest {

    private static final Tecnica T02 = new CatalogoJson().tecnicas().stream().filter(t -> t.id().equals(IdTecnica.de("T02"))).findFirst().orElseThrow();

    @Test
    void un_conjunto_de_la_configuracion_viaja_oculto_y_vuelve_como_la_misma_lista() {
        List<Campo> campos = LenguajeCampos.campos(T02.esquemaConfig());
        Map<String, Object> config = new LinkedHashMap<>(Map.of("nivel", "completo", "obligatorios", List.of("afirmacion", "datos"),
                "exigirFuenteRespaldo", true));

        VistaFormulario f = VistaFormulario.de(T02, campos, LenguajeCampos.campos(T02.esquemaEntrada()), config, Map.of(), Map.of(), "clave", "");
        MultiValueMap<String, String> enviados = new LinkedMultiValueMap<>();
        f.config().forEach(o -> enviados.add(o.nombre(), o.valor()));

        assertThat(enviados.get("config.obligatorios")).containsExactly("afirmacion", "datos");
        Map<String, Object> leida = LectorFormulario.leer(campos, enviados, "config.");
        assertThat(leida.get("obligatorios")).isEqualTo(List.of("afirmacion", "datos"));
        assertThat(f.resumenConfig()).contains("Partes obligatorias para evaluar: Afirmación, Datos");
    }
}
