package pensamiento.unidad.nucleo.reglas;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import pensamiento.nucleo.EstadoAfirmacion;
import pensamiento.nucleo.Evidencia;
import pensamiento.nucleo.Fuente;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.reglas.R02FuerzaNeta;
import pensamiento.nucleo.reglas.R03EstadoAfirmacion;

/**
 * R03 y la independencia mutua (hito 6): si todas las fuentes que cuentan son del mismo grupo de origen, por fuertes que sean,
 * la afirmación nunca queda verificada ni refutada; a lo sumo, en verificación o disputada.
 */
class R03MismoGrupoPropiedadesTest {

    @Property
    void con_un_solo_grupo_de_origen_nunca_queda_verificada_ni_refutada(@ForAll TipoAfirmacion tipo, @ForAll("delMismoGrupo") List<Evidencia> evidencias) {
        R02FuerzaNeta.FuerzaNeta neta = R02FuerzaNeta.neta(evidencias, R02FuerzaNeta.Parametros.v1());

        EstadoAfirmacion estado = R03EstadoAfirmacion.estado(tipo, evidencias, neta, R03EstadoAfirmacion.Parametros.v1());

        assertThat(estado).isNotIn(EstadoAfirmacion.VERIFICADA, EstadoAfirmacion.REFUTADA);
    }

    @Provide
    Arbitrary<List<Evidencia>> delMismoGrupo() {
        Arbitrary<String> grupo = Arbitraries.of("junta de vecinos", "municipio", "panadería");
        return grupo.flatMap(g -> Combinators.combine(Arbitraries.of(Evidencia.Postura.values()), Arbitraries.integers().between(0, 8),
                Arbitraries.of(true, false)).as((postura, fuerza, adoptada) -> new Evidencia(UUID.randomUUID(), UUID.randomUUID(),
                new Fuente(UUID.randomUUID(), "Fuente", Fuente.TipoFuente.PRIMARIA, Optional.empty(), Optional.of(LocalDate.of(2026, 1, 1)), Optional.of(g),
                        true, true, Optional.of(20)), "Pasaje.", postura, fuerza, adoptada ? Evidencia.EtiquetadaPor.USUARIO : Evidencia.EtiquetadaPor.MODELO,
                adoptada)).list().ofMinSize(1).ofMaxSize(8));
    }
}
