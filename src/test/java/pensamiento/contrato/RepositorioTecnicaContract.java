package pensamiento.contrato;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pensamiento.nucleo.Ejemplo;
import pensamiento.nucleo.Familia;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.RelacionTecnica;
import pensamiento.nucleo.Tecnica;
import pensamiento.nucleo.puertos.RepositorioTecnica;
import pensamiento.testutil.builders.Tecnicas;

/** Contrato del repositorio de técnicas: no encontrado, orden por identificador, filtro por familia, tildes y ñ, JSONB ida y vuelta. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class RepositorioTecnicaContract {

    protected abstract RepositorioTecnica crearSut();

    /** Deja el catálogo con estas familias y técnicas (además de lo que ya haya si la implementación es compartida). */
    protected abstract void dadoQueExisten(List<Familia> familias, List<Tecnica> tecnicas);

    /** Garantiza que la técnica no está (el real la retira temporalmente del catálogo compartido y la restaura al final). */
    protected abstract void dadoQueNoExiste(IdTecnica id);

    @Test
    void una_tecnica_que_no_existe_devuelve_vacio() {
        dadoQueNoExiste(IdTecnica.de("T49"));
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

    /** Deja estos ejemplos en el catálogo (el real los inserta y los retira al terminar). */
    protected abstract void dadoQueExistenEjemplos(List<Ejemplo> ejemplos);

    protected abstract void dadoQueExisteRelacion(RelacionTecnica relacion);

    private static Ejemplo ejemplo(int orden, String titulo, Ejemplo.Ambito ambito) {
        return new Ejemplo(UUID.nameUUIDFromBytes(("contrato/" + titulo).getBytes(java.nio.charset.StandardCharsets.UTF_8)), IdTecnica.de("T28"),
                orden, 1, ambito, titulo, new Json("{\"escala\":\"cin\"}"), new Json("{\"pregunta\":\"¿Por qué el niño no fue?\"}"),
                new Json("{\"menosRefutadas\":[\"H1\"]}"), "Nota con ñ");
    }

    @Test
    void los_ejemplos_vienen_en_su_orden_y_se_leen_igual_con_jsonb() {
        Ejemplo segundo = ejemplo(98, "Contrato: la asamblea del año", Ejemplo.Ambito.COMUNIDAD);
        Ejemplo primero = ejemplo(97, "Contrato: la panadería", Ejemplo.Ambito.TRABAJO);
        dadoQueExistenEjemplos(List.of(segundo, primero));
        RepositorioTecnica sut = crearSut();

        List<Ejemplo> ejemplos = sut.ejemplos(IdTecnica.de("T28"));
        assertThat(ejemplos.stream().map(Ejemplo::orden).toList()).isSorted();
        assertThat(ejemplos.stream().map(Ejemplo::id).toList()).containsSubsequence(primero.id(), segundo.id());
        Ejemplo leido = sut.ejemplo(segundo.id()).orElseThrow();
        assertThat(leido.titulo()).isEqualTo("Contrato: la asamblea del año");
        assertThat(leido.ambito()).isEqualTo(Ejemplo.Ambito.COMUNIDAD);
        assertThat(leido.nota()).isEqualTo("Nota con ñ");
        // JSONB normaliza espacios y orden de claves: se compara el árbol, no el texto.
        assertThat(arbol(leido.datos())).isEqualTo(arbol(segundo.datos()));
        assertThat(arbol(leido.resultado())).isEqualTo(arbol(segundo.resultado()));
        assertThat(sut.ejemplo(UUID.randomUUID())).isEmpty();
    }

    private static tools.jackson.databind.JsonNode arbol(Json json) {
        return pensamiento.catalogo.MapeadorJson.mapper().readTree(json.texto());
    }

    @Test
    void las_relaciones_se_ven_desde_el_origen_y_desde_el_destino() {
        RelacionTecnica r = new RelacionTecnica(IdTecnica.de("T28"), IdTecnica.de("T33"), RelacionTecnica.Tipo.COMPLEMENTA);
        dadoQueExisteRelacion(r);
        RepositorioTecnica sut = crearSut();
        assertThat(sut.relaciones(IdTecnica.de("T28"))).contains(r);
        assertThat(sut.relaciones(IdTecnica.de("T33"))).contains(r);
        assertThat(sut.relaciones(IdTecnica.de("T33")).getFirst().otra(IdTecnica.de("T33"))).isNotEqualTo(IdTecnica.de("T33"));
    }

    @Test
    void contar_coincide_con_el_tamanio_de_todas() {
        dadoQueExisten(List.of(new Familia("F1", "Argumentos", 1)), List.of(Tecnicas.pendiente("T01"), Tecnicas.pendiente("T02")));
        RepositorioTecnica sut = crearSut();
        assertThat(sut.contar()).isEqualTo(sut.todas().size());
    }
}
