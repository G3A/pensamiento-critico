package pensamiento.unidad.nucleo.reglas;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

import pensamiento.nucleo.EstadoAfirmacion;
import pensamiento.nucleo.Evidencia;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.reglas.R02FuerzaNeta;
import pensamiento.nucleo.reglas.R03EstadoAfirmacion;
import pensamiento.testutil.builders.Evidencias;

/** Oráculo: ejemplos de R03 en la sección 5b. Más la propiedad de totalidad: cualquier combinación da uno de seis estados. */
class R03EstadoAfirmacionTest {

    private final R02FuerzaNeta.Parametros p2 = R02FuerzaNeta.Parametros.v1();
    private final R03EstadoAfirmacion.Parametros p3 = R03EstadoAfirmacion.Parametros.v1();

    private EstadoAfirmacion estado(TipoAfirmacion tipo, List<Evidencia> evidencias) {
        return R03EstadoAfirmacion.estado(tipo, evidencias, R02FuerzaNeta.neta(evidencias, p2), p3);
    }

    @Test
    void dos_fuentes_a_favor_de_grupos_distintos_con_neta_mas_9_queda_verificada() {
        assertThat(estado(TipoAfirmacion.HECHO, List.of(Evidencias.apoya(5, "municipio"), Evidencias.apoya(4, "camara-comercio"))))
                .isEqualTo(EstadoAfirmacion.VERIFICADA);
    }

    @Test
    void una_sola_fuente_a_favor_con_neta_mas_8_queda_en_verificacion() {
        assertThat(estado(TipoAfirmacion.HECHO, List.of(Evidencias.apoya(8, "municipio"))))
                .isEqualTo(EstadoAfirmacion.EN_VERIFICACION);
    }

    @Test
    void dos_fuentes_a_favor_del_mismo_grupo_no_son_independientes_y_queda_en_verificacion() {
        assertThat(estado(TipoAfirmacion.HECHO, List.of(Evidencias.apoya(5, "municipio"), Evidencias.apoya(4, "municipio"))))
                .isEqualTo(EstadoAfirmacion.EN_VERIFICACION);
    }

    @Test
    void apoya_8_y_contradice_7_queda_disputada() {
        assertThat(estado(TipoAfirmacion.HECHO, List.of(Evidencias.apoya(8), Evidencias.contradice(7))))
                .isEqualTo(EstadoAfirmacion.DISPUTADA);
    }

    @Test
    void un_juicio_de_valor_no_es_verificable_aunque_tenga_evidencia() {
        assertThat(estado(TipoAfirmacion.JUICIO_DE_VALOR, List.of(Evidencias.apoya(8), Evidencias.apoya(8))))
                .isEqualTo(EstadoAfirmacion.NO_VERIFICABLE);
    }

    @Test
    void sin_evidencias_adoptadas_queda_sin_verificar() {
        assertThat(estado(TipoAfirmacion.HECHO, List.of(Evidencias.delModeloSinAdoptar(Evidencia.Postura.APOYA, 8))))
                .isEqualTo(EstadoAfirmacion.SIN_VERIFICAR);
    }

    @Test
    void dos_fuentes_en_contra_independientes_con_neta_fuerte_negativa_queda_refutada() {
        assertThat(estado(TipoAfirmacion.DATO_ESTADISTICO, List.of(Evidencias.contradice(6, "a"), Evidencias.contradice(3, "b"))))
                .isEqualTo(EstadoAfirmacion.REFUTADA);
    }

    @Property
    void cualquier_combinacion_de_evidencias_produce_uno_de_los_seis_estados(@ForAll TipoAfirmacion tipo,
                                                                            @ForAll("listasDeEvidencias") List<Evidencia> evidencias) {
        EstadoAfirmacion resultado = estado(tipo, evidencias);
        assertThat(resultado).isIn((Object[]) EstadoAfirmacion.values());
    }

    @Property
    void verificada_exige_al_menos_dos_grupos_de_origen_distintos_a_favor(@ForAll("listasDeEvidencias") List<Evidencia> evidencias) {
        if (estado(TipoAfirmacion.HECHO, evidencias) == EstadoAfirmacion.VERIFICADA) {
            long grupos = evidencias.stream()
                    .filter(e -> e.cuenta() && e.postura() == Evidencia.Postura.APOYA)
                    .map(e -> e.fuente().grupoOrigen().orElse(""))
                    .distinct().count();
            assertThat(grupos).isGreaterThanOrEqualTo(2);
        }
    }

    @Provide
    Arbitrary<List<Evidencia>> listasDeEvidencias() {
        Arbitrary<Evidencia> una = Arbitraries.of(Evidencia.Postura.class).flatMap(postura ->
                Arbitraries.integers().between(0, 8).flatMap(fuerza ->
                        Arbitraries.of("a", "b", "c").flatMap(grupo ->
                                Arbitraries.of(true, false).map(adoptada ->
                                        Evidencias.nueva(postura, fuerza, grupo, adoptada,
                                                adoptada ? Evidencia.EtiquetadaPor.USUARIO : Evidencia.EtiquetadaPor.MODELO)))));
        return una.list().ofMinSize(0).ofMaxSize(6);
    }
}
