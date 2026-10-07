package pensamiento.unidad.nucleo.reglas;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import org.junit.jupiter.api.Test;

import pensamiento.nucleo.Argumento;
import pensamiento.nucleo.EstadoAfirmacion;
import pensamiento.nucleo.EstandarPrueba;
import pensamiento.nucleo.reglas.R04Aceptabilidad;

/** Oráculo: el ejemplo de R04 en la sección 5b (la segunda sucursal). Más propiedades sobre ciclos y estándares. */
class R04AceptabilidadTest {

    private final R04Aceptabilidad.Parametros p = R04Aceptabilidad.Parametros.v1();

    // Afirmaciones del ejemplo
    private final UUID convieneAbrir = UUID.randomUUID();
    private final UUID centroTieneMasTrafico = UUID.randomUUID();   // verificada
    private final UUID masTraficoDaMasVentas = UUID.randomUUID();   // asumible, sin objeción
    private final UUID faltaPersonal = UUID.randomUUID();           // en verificación

    private List<Argumento> grafoDelEjemplo() {
        Argumento pro = new Argumento(UUID.randomUUID(), convieneAbrir,
                List.of(new Argumento.Premisa(centroTieneMasTrafico, 1, false), new Argumento.Premisa(masTraficoDaMasVentas, 2, true)),
                3, Argumento.Sentido.PRO);
        Argumento contra = new Argumento(UUID.randomUUID(), convieneAbrir,
                List.of(new Argumento.Premisa(faltaPersonal, 1, false)), 2, Argumento.Sentido.CONTRA);
        return List.of(pro, contra);
    }

    private Map<UUID, R04Aceptabilidad.EstadoPremisa> premisasDelEjemplo() {
        return Map.of(
                centroTieneMasTrafico, R04Aceptabilidad.EstadoPremisa.de(EstadoAfirmacion.VERIFICADA),
                masTraficoDaMasVentas, R04Aceptabilidad.EstadoPremisa.de(EstadoAfirmacion.SIN_VERIFICAR),
                faltaPersonal, R04Aceptabilidad.EstadoPremisa.de(EstadoAfirmacion.EN_VERIFICACION));
    }

    @Test
    void conviene_abrir_la_sucursal_es_aceptable_por_preponderancia() {
        // Documento: pro aplicable con peso 3; el contra "falta personal" está en verificación y no aplica → aceptable
        assertThat(R04Aceptabilidad.aceptable(convieneAbrir, EstandarPrueba.PREPONDERANCIA, grafoDelEjemplo(), premisasDelEjemplo(), p)).isTrue();
    }

    @Test
    void conviene_abrir_la_sucursal_no_es_aceptable_mas_alla_de_duda_razonable_por_la_premisa_asumible() {
        assertThat(R04Aceptabilidad.aceptable(convieneAbrir, EstandarPrueba.MAS_ALLA_DE_DUDA_RAZONABLE, grafoDelEjemplo(), premisasDelEjemplo(), p)).isFalse();
    }

    @Test
    void una_premisa_asumible_con_objecion_bloquea_el_argumento_salvo_en_escrutinio() {
        Map<UUID, R04Aceptabilidad.EstadoPremisa> premisas = new HashMap<>(premisasDelEjemplo());
        premisas.put(masTraficoDaMasVentas, new R04Aceptabilidad.EstadoPremisa(EstadoAfirmacion.SIN_VERIFICAR, false, true));
        assertThat(R04Aceptabilidad.aceptable(convieneAbrir, EstandarPrueba.PREPONDERANCIA, grafoDelEjemplo(), premisas, p)).isFalse();
        assertThat(R04Aceptabilidad.aceptable(convieneAbrir, EstandarPrueba.ESCRUTINIO, grafoDelEjemplo(), premisas, p)).isTrue();
    }

    @Test
    void si_el_contra_se_vuelve_aplicable_y_pesa_mas_deja_de_ser_aceptable_por_preponderancia() {
        Map<UUID, R04Aceptabilidad.EstadoPremisa> premisas = new HashMap<>(premisasDelEjemplo());
        premisas.put(faltaPersonal, R04Aceptabilidad.EstadoPremisa.de(EstadoAfirmacion.VERIFICADA));
        List<Argumento> grafo = new ArrayList<>(grafoDelEjemplo());
        grafo.set(1, new Argumento(UUID.randomUUID(), convieneAbrir, List.of(new Argumento.Premisa(faltaPersonal, 1, false)), 4, Argumento.Sentido.CONTRA));
        assertThat(R04Aceptabilidad.aceptable(convieneAbrir, EstandarPrueba.PREPONDERANCIA, grafo, premisas, p)).isFalse();
    }

    @Test
    void una_premisa_de_valor_no_verificable_cuenta_solo_si_el_usuario_la_adopto() {
        UUID debeCuidarALosEmpleados = UUID.randomUUID();
        Argumento pro = new Argumento(UUID.randomUUID(), convieneAbrir, List.of(new Argumento.Premisa(debeCuidarALosEmpleados, 1, false)), 3, Argumento.Sentido.PRO);
        Map<UUID, R04Aceptabilidad.EstadoPremisa> sinAdoptar = Map.of(debeCuidarALosEmpleados, R04Aceptabilidad.EstadoPremisa.de(EstadoAfirmacion.NO_VERIFICABLE));
        Map<UUID, R04Aceptabilidad.EstadoPremisa> adoptada = Map.of(debeCuidarALosEmpleados, new R04Aceptabilidad.EstadoPremisa(EstadoAfirmacion.NO_VERIFICABLE, true, false));
        assertThat(R04Aceptabilidad.aceptable(convieneAbrir, EstandarPrueba.PREPONDERANCIA, List.of(pro), sinAdoptar, p)).isFalse();
        assertThat(R04Aceptabilidad.aceptable(convieneAbrir, EstandarPrueba.PREPONDERANCIA, List.of(pro), adoptada, p)).isTrue();
    }

    @Test
    void un_argumento_cuya_conclusion_es_premisa_de_si_mismo_no_aporta_peso() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        // a se apoya en b, y b se apoya en a: ciclo. Ninguno aporta.
        Argumento aDesdeB = new Argumento(UUID.randomUUID(), a, List.of(new Argumento.Premisa(b, 1, true)), 5, Argumento.Sentido.PRO);
        Argumento bDesdeA = new Argumento(UUID.randomUUID(), b, List.of(new Argumento.Premisa(a, 1, true)), 5, Argumento.Sentido.PRO);
        assertThat(R04Aceptabilidad.aceptable(a, EstandarPrueba.ESCRUTINIO, List.of(aDesdeB, bDesdeA), Map.of(), p)).isFalse();
    }

    @Property
    void una_afirmacion_sin_argumentos_pro_nunca_es_aceptable(@ForAll EstandarPrueba estandar, @ForAll("grafosSoloContra") List<Argumento> contra) {
        UUID conclusion = contra.isEmpty() ? UUID.randomUUID() : contra.getFirst().conclusionId();
        assertThat(R04Aceptabilidad.aceptable(conclusion, estandar, contra, Map.of(), p)).isFalse();
    }

    @Property
    void agregar_argumentos_ciclicos_no_cambia_la_aceptabilidad(@ForAll EstandarPrueba estandar, @ForAll("grafosAciclicos") List<Argumento> grafo) {
        UUID conclusion = grafo.getFirst().conclusionId();
        Map<UUID, R04Aceptabilidad.EstadoPremisa> premisas = new HashMap<>();
        grafo.forEach(a -> a.premisas().forEach(pr -> premisas.put(pr.afirmacionId(), R04Aceptabilidad.EstadoPremisa.de(EstadoAfirmacion.VERIFICADA))));
        boolean sinCiclos = R04Aceptabilidad.aceptable(conclusion, estandar, grafo, premisas, p);

        // Un ciclo de peso enorme: conclusión → x → conclusión
        UUID x = UUID.randomUUID();
        List<Argumento> conCiclo = new ArrayList<>(grafo);
        conCiclo.add(new Argumento(UUID.randomUUID(), conclusion, List.of(new Argumento.Premisa(x, 1, false)), 100, Argumento.Sentido.PRO));
        conCiclo.add(new Argumento(UUID.randomUUID(), x, List.of(new Argumento.Premisa(conclusion, 1, false)), 100, Argumento.Sentido.PRO));
        premisas.put(x, R04Aceptabilidad.EstadoPremisa.de(EstadoAfirmacion.VERIFICADA));
        premisas.put(conclusion, R04Aceptabilidad.EstadoPremisa.de(EstadoAfirmacion.VERIFICADA));

        assertThat(R04Aceptabilidad.aceptable(conclusion, estandar, conCiclo, premisas, p)).isEqualTo(sinCiclos);
    }

    @Provide
    Arbitrary<List<Argumento>> grafosSoloContra() {
        UUID conclusion = UUID.randomUUID();
        return Arbitraries.integers().between(0, 10).map(peso ->
                new Argumento(UUID.randomUUID(), conclusion, List.of(new Argumento.Premisa(UUID.randomUUID(), 1, true)), peso, Argumento.Sentido.CONTRA))
                .list().ofMaxSize(4);
    }

    /** Argumentos pro y contra de una misma conclusión, con premisas frescas (sin ciclos). */
    @Provide
    Arbitrary<List<Argumento>> grafosAciclicos() {
        UUID conclusion = UUID.randomUUID();
        Arbitrary<Argumento> uno = Arbitraries.integers().between(0, 6).flatMap(peso ->
                Arbitraries.of(Argumento.Sentido.class).flatMap(sentido ->
                        Arbitraries.of(true, false).map(asumible ->
                                new Argumento(UUID.randomUUID(), conclusion, List.of(new Argumento.Premisa(UUID.randomUUID(), 1, asumible)), peso, sentido))));
        return uno.list().ofMinSize(1).ofMaxSize(5);
    }
}
