package pensamiento.unidad.flujos;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pensamiento.flujos.BuscadorDePasajes;
import pensamiento.nucleo.Documento;
import pensamiento.nucleo.Fragmento;
import pensamiento.nucleo.Pasaje;
import pensamiento.nucleo.puertos.Biblioteca;
import pensamiento.testutil.builders.Contextos;
import pensamiento.testutil.fakes.FakeBiblioteca;
import pensamiento.testutil.fakes.FakeIa;

/** Buscar en la biblioteca: semántica con el modelo y vectores; por palabras si se pide, sin modelo o sin vectores, y lo dice. */
class BuscadorDePasajesTest {

    private static final UUID YO = Contextos.DUENA_DE_LA_PANADERIA;

    private final FakeBiblioteca biblioteca = new FakeBiblioteca();
    private final FakeIa ia = new FakeIa();
    private final BuscadorDePasajes buscador = new BuscadorDePasajes(biblioteca, ia, Duration.ofSeconds(10));

    private Documento indexado(String nombre, List<String> textos, boolean conVectores) {
        Documento d = biblioteca.crear(YO, Contextos.INSTITUCION, new Biblioteca.NuevoDocumento(UUID.randomUUID(), nombre, Documento.Tipo.TEXTO,
                "hash-" + UUID.randomUUID(), String.join("\n\n", textos).getBytes(StandardCharsets.UTF_8)));
        List<Fragmento.Nuevo> nuevos = new java.util.ArrayList<>();
        for (int i = 0; i < textos.size(); i++) {
            nuevos.add(new Fragmento.Nuevo(i, textos.get(i), Optional.empty()));
        }
        biblioteca.indexar(YO, d.id(), nuevos, Optional.empty());
        if (conVectores) {
            for (Fragmento f : biblioteca.fragmentos(YO, d.id())) {
                biblioteca.guardarVectores(YO, Map.of(f.id(), ia.incrustar(new pensamiento.nucleo.puertos.PeticionEmbeddings(List.of(f.texto()),
                        Duration.ofSeconds(1))).getFirst()));
            }
        }
        return d;
    }

    @Test
    void con_el_modelo_y_vectores_la_busqueda_es_semantica() {
        indexado("horno.txt", List.of("Revisar el termostato del horno.", "Limpiar la campana."), true);

        BuscadorDePasajes.Busqueda b = buscador.buscar(YO, "Revisar el termostato del horno.", false);

        assertThat(b.modo()).isEqualTo(Pasaje.Modo.SEMANTICA);
        assertThat(b.pasajes().getFirst().texto()).isEqualTo("Revisar el termostato del horno.");
        assertThat(b.aviso()).isEmpty();
    }

    @Test
    void sin_el_modelo_la_busqueda_es_por_palabras_y_lo_dice() {
        indexado("horno.txt", List.of("Revisar el termostato del horno.", "Limpiar la campana."), true);
        ia.apagar();

        BuscadorDePasajes.Busqueda b = buscador.buscar(YO, "termostato", false);

        assertThat(b.modo()).isEqualTo(Pasaje.Modo.TEXTO_COMPLETO);
        assertThat(b.pasajes()).extracting(Pasaje::texto).containsExactly("Revisar el termostato del horno.");
        assertThat(b.aviso()).contains("El modelo no responde: la búsqueda es por palabras.");
    }

    @Test
    void sin_vectores_la_busqueda_es_por_palabras_y_lo_dice() {
        indexado("horno.txt", List.of("Revisar el termostato del horno."), false);

        BuscadorDePasajes.Busqueda b = buscador.buscar(YO, "termostato", false);

        assertThat(b.modo()).isEqualTo(Pasaje.Modo.TEXTO_COMPLETO);
        assertThat(b.aviso()).contains("Búsqueda por palabras: ningún documento tiene vectores todavía.");
    }

    @Test
    void por_palabras_si_la_persona_lo_pide_y_avisa_de_lo_que_falta_vectorizar_en_la_semantica() {
        indexado("horno.txt", List.of("Revisar el termostato del horno."), true);
        indexado("campana.txt", List.of("Limpiar la campana del horno."), false);

        assertThat(buscador.buscar(YO, "campana", true)).satisfies(b -> {
            assertThat(b.modo()).isEqualTo(Pasaje.Modo.TEXTO_COMPLETO);
            assertThat(b.aviso()).isEmpty();
            assertThat(b.pasajes()).extracting(Pasaje::texto).containsExactly("Limpiar la campana del horno.");
        });
        assertThat(buscador.buscar(YO, "campana", false).aviso()).contains("1 documento todavía se vectoriza: búscalos también por palabras.");
    }

    @Test
    void una_consulta_vacia_no_busca() {
        assertThat(buscador.buscar(YO, "  ", false).pasajes()).isEmpty();
    }
}
