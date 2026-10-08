package pensamiento.unidad.tecnicas.f1;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

import pensamiento.argdown.ParserArgdown;
import pensamiento.contrato.ArgdownContract;
import pensamiento.nucleo.EstandarPrueba;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.argdown.DocumentoArgdown;
import pensamiento.tecnicas.f1.EjecutorMapa;
import pensamiento.tecnicas.f1.ResultadoMapa;
import pensamiento.testutil.builders.Contextos;

/** Propiedades de T01 · Mapeo de argumentos sobre árboles Argdown generados al azar. */
class MapaArgumentalPropiedadesTest {

    private final ParserArgdown parser = new ParserArgdown();
    private final EjecutorMapa t01 = new EjecutorMapa(parser);

    private Resultado<ResultadoMapa> mapa(long semilla, EstandarPrueba estandar) {
        DocumentoArgdown d = new ArgdownContract.Generador(new Random(semilla)).documento();
        return t01.ejecutar(new EjecutorMapa.Config(ResultadoMapa.Direccion.ARRIBA_ABAJO, true, true, estandar),
                new EjecutorMapa.Entrada(parser.escribir(d)), Contextos.sinIa());
    }

    @Property(tries = 200)
    void cada_nodo_es_una_afirmacion_distinta_y_cada_premisa_apunta_a_un_nodo(@ForAll long semilla, @ForAll EstandarPrueba estandar) {
        Resultado<ResultadoMapa> r = mapa(semilla, estandar);
        Set<String> codigos = r.valor().nodos().stream().map(ResultadoMapa.Nodo::codigo).collect(Collectors.toSet());

        assertThat(r.afirmaciones()).hasSameSizeAs(r.valor().nodos());
        assertThat(r.afirmaciones().stream().map(a -> a.afirmacionId()).distinct()).hasSameSizeAs(r.afirmaciones());
        assertThat(r.valor().argumentos()).allSatisfy(a -> {
            assertThat(codigos).contains(a.conclusion());
            assertThat(codigos).containsAll(a.premisas());
        });
    }

    @Property(tries = 200)
    void apoyos_y_ataques_suman_los_argumentos_y_una_objecion_sin_responder_deja_un_pendiente(@ForAll long semilla) {
        Resultado<ResultadoMapa> r = mapa(semilla, EstandarPrueba.PREPONDERANCIA);
        ResultadoMapa v = r.valor();

        assertThat(v.apoyos() + v.ataques()).isEqualTo(v.argumentos().size());
        long objeciones = r.pendientes().stream().filter(p -> p.descripcion().startsWith("Responder la objeción: ")).count();
        assertThat(objeciones).isEqualTo(v.objecionesSinResponder().size());
        assertThat(v.objecionesSinResponder()).allSatisfy(c -> assertThat(v.nodo(c).rol()).isEqualTo(ResultadoMapa.Rol.OBJECION));
    }

    @Property(tries = 200)
    void si_algo_es_aceptable_bajo_un_estandar_tambien_lo_es_bajo_los_menos_exigentes(@ForAll long semilla) {
        // R04: los estándares están ordenados de menos a más exigente; cada uno pide lo del anterior y algo más.
        EstandarPrueba[] estandares = EstandarPrueba.values();
        for (int i = 1; i < estandares.length; i++) {
            ResultadoMapa exigente = mapa(semilla, estandares[i]).valor();
            ResultadoMapa menos = mapa(semilla, estandares[i - 1]).valor();
            for (int c = 0; c < exigente.conclusiones().size(); c++) {
                if (exigente.conclusiones().get(c).aceptable()) {
                    assertThat(menos.conclusiones().get(c).aceptable()).as(estandares[i - 1] + " frente a " + estandares[i]).isTrue();
                }
            }
        }
    }

    @Property(tries = 200)
    void las_conclusiones_son_los_enunciados_de_primer_nivel(@ForAll long semilla) {
        DocumentoArgdown d = new ArgdownContract.Generador(new Random(semilla)).documento();
        ResultadoMapa v = t01.ejecutar(new EjecutorMapa.Config(ResultadoMapa.Direccion.ARRIBA_ABAJO, true, false, EstandarPrueba.ESCRUTINIO),
                new EjecutorMapa.Entrada(parser.escribir(d)), Contextos.sinIa()).valor();

        assertThat(v.conclusiones()).hasSize(d.raices().size());
        assertThat(v.argdown()).isEqualTo(parser.escribir(d));
    }
}
