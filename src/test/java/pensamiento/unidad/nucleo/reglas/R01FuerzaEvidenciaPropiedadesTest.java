package pensamiento.unidad.nucleo.reglas;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

import pensamiento.nucleo.Fuente;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.reglas.R01FuerzaEvidencia;

/** Propiedad: para cualquier tipo de afirmación y cualquier fuente, la fuerza queda entre 0 y 8. */
class R01FuerzaEvidenciaPropiedadesTest {

    @Property
    void la_fuerza_siempre_queda_entre_0_y_8(@ForAll TipoAfirmacion tipo, @ForAll("fuentes") Fuente fuente, @ForAll("fechas") LocalDate hoy) {
        int fuerza = R01FuerzaEvidencia.fuerza(tipo, fuente, hoy, R01FuerzaEvidencia.Parametros.v1());
        assertThat(fuerza).isBetween(0, 8);
    }

    @Property
    void agregar_independencia_nunca_baja_la_fuerza(@ForAll TipoAfirmacion tipo, @ForAll("fuentes") Fuente fuente, @ForAll("fechas") LocalDate hoy) {
        Fuente conIndependencia = new Fuente(fuente.id(), fuente.titulo(), fuente.tipo(), fuente.disenoEstudio(), fuente.fecha(),
                fuente.grupoOrigen(), true, fuente.accesoOriginal(), fuente.puntajeCraap());
        R01FuerzaEvidencia.Parametros p = R01FuerzaEvidencia.Parametros.v1();
        assertThat(R01FuerzaEvidencia.fuerza(tipo, conIndependencia, hoy, p))
                .isGreaterThanOrEqualTo(R01FuerzaEvidencia.fuerza(tipo, fuente, hoy, p));
    }

    @Provide
    Arbitrary<LocalDate> fechas() {
        return Arbitraries.integers().between(0, 365 * 60).map(d -> LocalDate.of(1990, 1, 1).plusDays(d));
    }

    @Provide
    Arbitrary<Fuente> fuentes() {
        Arbitrary<Optional<Fuente.DisenoEstudio>> diseno = Arbitraries.of(Fuente.DisenoEstudio.class).optional();
        Arbitrary<Optional<LocalDate>> fecha = fechas().optional();
        Arbitrary<Optional<Integer>> craap = Arbitraries.integers().between(0, 25).optional();
        return Combinators.combine(Arbitraries.of(Fuente.TipoFuente.class), diseno, fecha, Arbitraries.of(true, false),
                        Arbitraries.of(true, false), craap)
                .as((tipo, d, f, indep, acceso, c) -> new Fuente(UUID.randomUUID(), "fuente", tipo, d, f, Optional.empty(), indep, acceso, c));
    }
}
