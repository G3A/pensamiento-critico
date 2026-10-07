package pensamiento.unidad.catalogo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import pensamiento.catalogo.RegistroEjecutores;
import pensamiento.catalogo.VerificadorCatalogo;
import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.Validacion;
import pensamiento.testutil.builders.Tecnicas;
import pensamiento.testutil.fakes.FakeRepositorioTecnica;

/** Collaboration test del verificador: si catálogo y ejecutores no cuadran, la aplicación no arranca. */
class VerificadorCatalogoTest {

    private final FakeRepositorioTecnica repositorio = new FakeRepositorioTecnica();

    private void sembrar49Pendientes() {
        for (int i = 1; i <= 49; i++) {
            repositorio.agregar(Tecnicas.pendiente(String.format("T%02d", i)));
        }
    }

    private static Ejecutor<Object, Object, Object> ejecutorDe(String id) {
        return new Ejecutor<>() {
            @Override public IdTecnica id() { return IdTecnica.de(id); }
            @Override public int versionEsquema() { return 1; }
            @Override public Validacion validar(Object config, Object entrada) { return Validacion.VALIDA; }
            @Override public Resultado<Object> ejecutar(Object config, Object entrada, Contexto ctx) { return new Resultado<>(1, null, List.of(), List.of(), ""); }
            @Override public Object migrar(Json datosViejos, int desdeVersion) { return null; }
        };
    }

    @Test
    void con_49_tecnicas_pendientes_y_ningun_ejecutor_el_catalogo_es_consistente() {
        sembrar49Pendientes();
        VerificadorCatalogo.Informe informe = new VerificadorCatalogo(repositorio, RegistroEjecutores.vacio()).exigirConsistencia();
        assertThat(informe.consistente()).isTrue();
        assertThat(informe.pendientes()).isEqualTo(49);
        assertThat(informe.activas()).isZero();
    }

    @Test
    void una_tecnica_activa_sin_ejecutor_impide_arrancar() {
        sembrar49Pendientes();
        repositorio.agregar(Tecnicas.activa("T28"));
        VerificadorCatalogo verificador = new VerificadorCatalogo(repositorio, RegistroEjecutores.vacio());
        assertThatThrownBy(verificador::exigirConsistencia)
                .isInstanceOf(VerificadorCatalogo.CatalogoInconsistente.class)
                .hasMessageContaining("T28 · Técnica T28 está activa y no tiene ejecutor");
    }

    @Test
    void una_tecnica_activa_con_su_ejecutor_registrado_es_consistente() {
        sembrar49Pendientes();
        repositorio.agregar(Tecnicas.activa("T28"));
        VerificadorCatalogo.Informe informe = new VerificadorCatalogo(repositorio, new RegistroEjecutores(List.of(ejecutorDe("T28")))).exigirConsistencia();
        assertThat(informe.activas()).isEqualTo(1);
        assertThat(informe.pendientes()).isEqualTo(48);
    }

    @Test
    void un_ejecutor_registrado_para_una_tecnica_marcada_pendiente_es_una_inconsistencia() {
        sembrar49Pendientes();
        VerificadorCatalogo verificador = new VerificadorCatalogo(repositorio, new RegistroEjecutores(List.of(ejecutorDe("T01"))));
        assertThat(verificador.verificar().problemas()).anyMatch(p -> p.contains("T01") && p.contains("pendiente"));
    }

    @Test
    void menos_de_49_tecnicas_es_una_inconsistencia() {
        repositorio.agregar(Tecnicas.pendiente("T01"));
        VerificadorCatalogo.Informe informe = new VerificadorCatalogo(repositorio, RegistroEjecutores.vacio()).verificar();
        assertThat(informe.problemas()).contains("se esperaban 49 técnicas y hay 1");
    }

    @Test
    void dos_ejecutores_para_el_mismo_identificador_no_se_pueden_registrar() {
        assertThatThrownBy(() -> new RegistroEjecutores(List.of(ejecutorDe("T28"), ejecutorDe("T28"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("T28");
    }
}
