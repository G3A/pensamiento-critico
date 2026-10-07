package pensamiento.contrato;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pensamiento.nucleo.Familia;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Tecnica;
import pensamiento.nucleo.puertos.RepositorioTecnica;
import pensamiento.testutil.builders.Tecnicas;

/** Contrato del repositorio de técnicas: no encontrado, orden por identificador, filtro por familia, tildes y ñ, JSONB ida y vuelta. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class RepositorioTecnicaContract {

    protected abstract RepositorioTecnica crearSut();

    /** Deja el catálogo con exactamente estas familias y técnicas (además de lo que ya haya si la implementación es compartida). */
    protected abstract void dadoQueExisten(List<Familia> familias, List<Tecnica> tecnicas);

    @Test
    void una_tecnica_que_no_existe_devuelve_vacio() {
        dadoQueExisten(List.of(), List.of());
        assertThat(crearSut().porId(IdTecnica.de("T49"))).isEmpty();
    }

    @Test
    void una_tecnica_guardada_se_lee_igual_con_tildes_enie_y_jsonb() {
        Tecnica t28 = Tecnicas.t28();
        dadoQueExisten(List.of(new Familia("F5", "Pensamiento probabilístico y decisiones", 5)), List.of(t28));

        Optional<Tecnica> leida = crearSut().porId(IdTecnica.de("T28"));

        assertThat(leida).isPresent();
        assertThat(leida.get().nombre()).isEqualTo("Análisis de hipótesis en competencia (ACH)");
        assertThat(leida.get().usalaCuando()).contains("ñandú", "niño");
        assertThat(leida.get().esquemaConfig().texto()).contains("\"escala\"").contains("\"CIN\"");
        assertThat(leida.get().configDefault().texto()).contains("\"maxHipotesis\"").contains("4");
        assertThat(leida.get().patron()).isEqualTo("V03a");
        assertThat(leida.get().estado()).isEqualTo(Tecnica.Estado.PENDIENTE);
    }

    @Test
    void todas_vienen_ordenadas_por_identificador() {
        dadoQueExisten(List.of(new Familia("F1", "Argumentos", 1), new Familia("F5", "Decisiones", 5)),
                List.of(Tecnicas.pendiente("T28"), Tecnicas.pendiente("T01"), Tecnicas.pendiente("T07")));
        List<String> ids = crearSut().todas().stream().map(t -> t.id().valor()).toList();
        int i01 = ids.indexOf("T01");
        int i07 = ids.indexOf("T07");
        int i28 = ids.indexOf("T28");
        assertThat(i01).isLessThan(i07);
        assertThat(i07).isLessThan(i28);
        assertThat(ids).isSorted();
    }

    @Test
    void por_familia_filtra_y_una_familia_desconocida_devuelve_vacio() {
        dadoQueExisten(List.of(new Familia("F1", "Argumentos", 1), new Familia("F2", "Cuestionamiento", 2)),
                List.of(Tecnicas.pendiente("T01"), Tecnicas.pendiente("T02"), Tecnicas.pendiente("T08")));
        RepositorioTecnica sut = crearSut();
        assertThat(sut.porFamilia("F1").stream().map(t -> t.id().valor())).contains("T01", "T02").doesNotContain("T08");
        assertThat(sut.porFamilia("F9")).isEmpty();
    }

    @Test
    void las_familias_vienen_en_su_orden_de_navegacion() {
        dadoQueExisten(List.of(new Familia("F2", "Cuestionamiento", 2), new Familia("F1", "Argumentos", 1)), List.of());
        List<Familia> familias = crearSut().familias();
        assertThat(familias.stream().map(Familia::orden).toList()).isSorted();
        assertThat(familias.stream().map(Familia::codigo)).contains("F1", "F2");
    }

    @Test
    void contar_coincide_con_el_tamanio_de_todas() {
        dadoQueExisten(List.of(new Familia("F1", "Argumentos", 1)), List.of(Tecnicas.pendiente("T01"), Tecnicas.pendiente("T02")));
        RepositorioTecnica sut = crearSut();
        assertThat(sut.contar()).isEqualTo(sut.todas().size());
    }
}
