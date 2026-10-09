package pensamiento.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pensamiento.nucleo.Documento;
import pensamiento.nucleo.Fragmento;
import pensamiento.nucleo.Pasaje;
import pensamiento.nucleo.puertos.Biblioteca;

/**
 * Contrato de la biblioteca (sección 9: "biblioteca vectorial"): un documento nace en proceso y privado; el original se lee
 * igual; un contenido repetido se rechaza; indexar guarda los fragmentos en orden y re-indexar los reemplaza; el error deja
 * el motivo; los vectores se cuentan; la búsqueda devuelve pasajes literales, por palabra o por similitud (de mayor a menor),
 * sin palabras vacías y con su límite; lo privado nunca aparece para otra persona y lo compartido sí; borrar quita todo.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BibliotecaContract {

    /** Dos usuarios distintos de la misma institución, ya existentes en el backend. */
    public record Personas(UUID institucion, UUID usuarioA, UUID usuarioB) {
    }

    protected static final int DIMENSIONES = 1024;

    protected abstract Personas personas();

    protected abstract Biblioteca comoUsuario(UUID usuarioId);

    protected static Biblioteca.NuevoDocumento nuevo(String nombre, Documento.Tipo tipo, String contenido) {
        return new Biblioteca.NuevoDocumento(UUID.randomUUID(), nombre, tipo, "hash-" + UUID.randomUUID(), contenido.getBytes(StandardCharsets.UTF_8));
    }

    /** Un vector unitario sobre un eje, o la mezcla normalizada de dos ejes. */
    protected static float[] eje(int... ejes) {
        float[] v = new float[DIMENSIONES];
        float valor = (float) (1 / Math.sqrt(ejes.length));
        for (int e : ejes) {
            v[e] = valor;
        }
        return v;
    }

    private Documento indexado(UUID usuario, String nombre, List<String> textos) {
        Personas p = personas();
        Biblioteca repo = comoUsuario(usuario);
        Documento d = repo.crear(usuario, p.institucion(), nuevo(nombre, Documento.Tipo.MARKDOWN, String.join("\n\n", textos)));
        java.util.ArrayList<Fragmento.Nuevo> fragmentos = new java.util.ArrayList<>();
        for (int i = 0; i < textos.size(); i++) {
            fragmentos.add(new Fragmento.Nuevo(i, textos.get(i), Optional.empty()));
        }
        repo.indexar(usuario, d.id(), fragmentos, Optional.empty());
        return d;
    }

    @Test
    void un_documento_nace_en_proceso_privado_y_su_original_se_lee_igual() {
        Personas p = personas();
        Biblioteca.NuevoDocumento n = nuevo("encuesta-clientes-señora.md", Documento.Tipo.MARKDOWN, "# Encuesta\n\nEl pan integral se agota.");

        Documento d = comoUsuario(p.usuarioA()).crear(p.usuarioA(), p.institucion(), n);

        assertThat(d.id()).isEqualTo(n.id());
        assertThat(d.nombre()).isEqualTo("encuesta-clientes-señora.md");
        assertThat(d.tipo()).isEqualTo(Documento.Tipo.MARKDOWN);
        assertThat(d.estado()).isEqualTo(Documento.Estado.EN_PROCESO);
        assertThat(d.compartido()).isFalse();
        assertThat(d.tamano()).isEqualTo(n.contenido().length);
        assertThat(d.fragmentos()).isZero();
        assertThat(d.esDe(p.usuarioA())).isTrue();
        assertThat(comoUsuario(p.usuarioA()).contenido(p.usuarioA(), d.id())).hasValueSatisfying(b -> assertThat(b).isEqualTo(n.contenido()));
        assertThat(comoUsuario(p.usuarioA()).porId(p.usuarioA(), d.id())).contains(d);
    }

    @Test
    void importar_dos_veces_el_mismo_contenido_se_rechaza_con_el_nombre_del_primero() {
        Personas p = personas();
        Biblioteca.NuevoDocumento n = nuevo("acta-marzo.txt", Documento.Tipo.TEXTO, "Acta de la reunión.");
        Biblioteca repo = comoUsuario(p.usuarioA());
        repo.crear(p.usuarioA(), p.institucion(), n);

        assertThatThrownBy(() -> repo.crear(p.usuarioA(), p.institucion(),
                new Biblioteca.NuevoDocumento(UUID.randomUUID(), "copia.txt", Documento.Tipo.TEXTO, n.hash(), n.contenido())))
                .isInstanceOf(Biblioteca.DocumentoRepetido.class).hasMessage("Ya importaste este documento: acta-marzo.txt");
        assertThat(repo.propioPorHash(p.usuarioA(), n.hash())).map(Documento::nombre).contains("acta-marzo.txt");
        assertThat(comoUsuario(p.usuarioB()).propioPorHash(p.usuarioB(), n.hash())).isEmpty();
        assertThat(repo.propioPorHash(p.usuarioA(), "hash-que-no-existe")).isEmpty();
    }

    @Test
    void indexar_guarda_los_fragmentos_en_orden_y_volver_a_indexar_los_reemplaza() {
        Personas p = personas();
        Biblioteca repo = comoUsuario(p.usuarioA());
        Documento d = repo.crear(p.usuarioA(), p.institucion(), nuevo("conteo.pdf", Documento.Tipo.PDF, "%PDF-1.4 conteo"));

        repo.indexar(p.usuarioA(), d.id(), List.of(new Fragmento.Nuevo(0, "Método del conteo.", Optional.of(1)),
                new Fragmento.Nuevo(1, "En el centro pasan 1.200 personas por hora.", Optional.of(2))), Optional.of(3));
        assertThat(repo.porId(p.usuarioA(), d.id())).hasValueSatisfying(x -> {
            assertThat(x.estado()).isEqualTo(Documento.Estado.INDEXADO);
            assertThat(x.paginas()).contains(3);
            assertThat(x.fragmentos()).isEqualTo(2);
            assertThat(x.conVector()).isZero();
        });
        assertThat(repo.fragmentos(p.usuarioA(), d.id())).extracting(Fragmento::orden, Fragmento::texto, Fragmento::pagina)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(0, "Método del conteo.", Optional.of(1)),
                        org.assertj.core.groups.Tuple.tuple(1, "En el centro pasan 1.200 personas por hora.", Optional.of(2)));

        repo.indexar(p.usuarioA(), d.id(), List.of(new Fragmento.Nuevo(0, "Solo un fragmento.", Optional.of(1))), Optional.of(1));
        assertThat(repo.fragmentos(p.usuarioA(), d.id())).extracting(Fragmento::texto).containsExactly("Solo un fragmento.");
    }

    @Test
    void un_error_deja_el_motivo_y_ningun_fragmento() {
        Personas p = personas();
        Biblioteca repo = comoUsuario(p.usuarioA());
        Documento d = repo.crear(p.usuarioA(), p.institucion(), nuevo("contrato-escaneado.pdf", Documento.Tipo.PDF, "%PDF-1.4 imagen"));

        repo.marcarError(p.usuarioA(), d.id(), "Sin texto extraíble (es imagen): la app no hace OCR.");

        assertThat(repo.porId(p.usuarioA(), d.id())).hasValueSatisfying(x -> {
            assertThat(x.estado()).isEqualTo(Documento.Estado.ERROR);
            assertThat(x.error()).contains("Sin texto extraíble (es imagen): la app no hace OCR.");
            assertThat(x.fragmentos()).isZero();
        });
    }

    @Test
    void los_vectores_se_guardan_de_a_poco_y_se_cuentan() {
        Personas p = personas();
        Documento d = indexado(p.usuarioA(), "tres.md", List.of("uno", "dos", "tres"));
        Biblioteca repo = comoUsuario(p.usuarioA());

        List<Fragmento> primeros = repo.sinVector(p.usuarioA(), d.id(), 2);
        assertThat(primeros).extracting(Fragmento::texto).containsExactly("uno", "dos");
        repo.guardarVectores(p.usuarioA(), Map.of(primeros.get(0).id(), eje(0), primeros.get(1).id(), eje(1)));

        assertThat(repo.sinVector(p.usuarioA(), d.id(), 2)).extracting(Fragmento::texto).containsExactly("tres");
        assertThat(repo.porId(p.usuarioA(), d.id())).hasValueSatisfying(x -> {
            assertThat(x.conVector()).isEqualTo(2);
            assertThat(x.porcentajeVectorizado()).isEqualTo(66);
        });
    }

    @Test
    void la_busqueda_por_texto_devuelve_el_pasaje_literal_que_comparte_alguna_palabra() {
        Personas p = personas();
        Documento d = indexado(p.usuarioA(), "manual-horno.txt", List.of("Revisar el termostato del horno cada mes.", "Nunca encender sin la campana."));

        List<Pasaje> pasajes = comoUsuario(p.usuarioA()).buscarPorTexto(p.usuarioA(), "termostato quemado", 5);

        assertThat(pasajes).singleElement().satisfies(x -> {
            assertThat(x.texto()).isEqualTo("Revisar el termostato del horno cada mes.");
            assertThat(x.documentoId()).isEqualTo(d.id());
            assertThat(x.documento()).isEqualTo("manual-horno.txt");
            assertThat(x.modo()).isEqualTo(Pasaje.Modo.TEXTO_COMPLETO);
        });
        assertThat(comoUsuario(p.usuarioA()).buscarPorTexto(p.usuarioA(), "bicicleta", 5)).isEmpty();
    }

    @Test
    void las_palabras_vacias_no_cuentan_y_el_limite_se_respeta() {
        Personas p = personas();
        indexado(p.usuarioA(), "levadura.md", List.of("La levadura fresca.", "La levadura seca.", "La levadura vieja."));

        assertThat(comoUsuario(p.usuarioA()).buscarPorTexto(p.usuarioA(), "el de la que", 5)).isEmpty();
        assertThat(comoUsuario(p.usuarioA()).buscarPorTexto(p.usuarioA(), "levadura", 2)).hasSize(2)
                .allSatisfy(x -> assertThat(x.texto()).contains("levadura"));
    }

    @Test
    void la_busqueda_por_vector_ordena_por_similitud_y_salta_los_fragmentos_sin_vector() {
        Personas p = personas();
        Documento d = indexado(p.usuarioA(), "similitud.md", List.of("idéntico", "parecido", "lejano", "sin vector"));
        Biblioteca repo = comoUsuario(p.usuarioA());
        List<Fragmento> f = repo.fragmentos(p.usuarioA(), d.id());
        Map<UUID, float[]> vectores = new HashMap<>();
        vectores.put(f.get(0).id(), eje(7));
        vectores.put(f.get(1).id(), eje(7, 8));
        vectores.put(f.get(2).id(), eje(7, 9, 10));
        repo.guardarVectores(p.usuarioA(), vectores);

        List<Pasaje> pasajes = repo.buscarPorVector(p.usuarioA(), eje(7), 3);

        assertThat(pasajes).extracting(Pasaje::texto).containsExactly("idéntico", "parecido", "lejano");
        assertThat(pasajes.get(0).puntaje()).isCloseTo(1.0, within(1e-4));
        assertThat(pasajes.get(1).puntaje()).isCloseTo(Math.sqrt(0.5), within(1e-4));
        assertThat(pasajes.get(2).puntaje()).isCloseTo(Math.sqrt(1.0 / 3), within(1e-4));
        assertThat(pasajes).allSatisfy(x -> assertThat(x.modo()).isEqualTo(Pasaje.Modo.SEMANTICA));
        assertThat(repo.buscarPorVector(p.usuarioA(), eje(7), 1)).extracting(Pasaje::texto).containsExactly("idéntico");
        assertThat(repo.buscarPorVector(p.usuarioA(), eje(7), 50)).extracting(Pasaje::texto).doesNotContain("sin vector");
    }

    @Test
    void un_documento_privado_nunca_aparece_para_otra_persona() {
        Personas p = personas();
        Documento d = indexado(p.usuarioA(), "privado-tahona.md", List.of("La tahona del barrio abre a las seis."));
        Biblioteca repo = comoUsuario(p.usuarioA());
        repo.guardarVectores(p.usuarioA(), Map.of(repo.fragmentos(p.usuarioA(), d.id()).getFirst().id(), eje(42)));
        Biblioteca comoB = comoUsuario(p.usuarioB());

        assertThat(comoB.porId(p.usuarioB(), d.id())).isEmpty();
        assertThat(comoB.visibles(p.usuarioB())).extracting(Documento::id).doesNotContain(d.id());
        assertThat(comoB.contenido(p.usuarioB(), d.id())).isEmpty();
        assertThat(comoB.fragmentos(p.usuarioB(), d.id())).isEmpty();
        assertThat(comoB.buscarPorTexto(p.usuarioB(), "tahona", 5)).isEmpty();
        assertThat(comoB.buscarPorVector(p.usuarioB(), eje(42), 5)).extracting(Pasaje::documentoId).doesNotContain(d.id());
        assertThat(comoB.compartir(p.usuarioB(), d.id(), true)).isFalse();
        assertThat(comoB.borrar(p.usuarioB(), d.id())).isFalse();
        assertThatThrownBy(() -> comoB.indexar(p.usuarioB(), d.id(), List.of(new Fragmento.Nuevo(0, "intruso", Optional.empty())), Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repo.fragmentos(p.usuarioA(), d.id())).extracting(Fragmento::texto).containsExactly("La tahona del barrio abre a las seis.");
    }

    @Test
    void compartir_lo_hace_visible_para_la_institucion_y_dejar_de_compartir_lo_vuelve_privado() {
        Personas p = personas();
        Documento d = indexado(p.usuarioA(), "compartido-hojaldre.md", List.of("El hojaldre necesita mantequilla fría."));
        Biblioteca repo = comoUsuario(p.usuarioA());
        Biblioteca comoB = comoUsuario(p.usuarioB());

        assertThat(repo.compartir(p.usuarioA(), d.id(), true)).isTrue();
        assertThat(comoB.porId(p.usuarioB(), d.id())).hasValueSatisfying(x -> {
            assertThat(x.compartido()).isTrue();
            assertThat(x.esDe(p.usuarioB())).isFalse();
        });
        assertThat(comoB.visibles(p.usuarioB())).extracting(Documento::id).contains(d.id());
        assertThat(comoB.buscarPorTexto(p.usuarioB(), "hojaldre", 5)).extracting(Pasaje::texto).containsExactly("El hojaldre necesita mantequilla fría.");
        assertThat(comoB.borrar(p.usuarioB(), d.id())).isFalse();

        assertThat(repo.compartir(p.usuarioA(), d.id(), false)).isTrue();
        assertThat(comoB.porId(p.usuarioB(), d.id())).isEmpty();
        assertThat(comoB.buscarPorTexto(p.usuarioB(), "hojaldre", 5)).isEmpty();
    }

    @Test
    void los_visibles_van_del_mas_nuevo_al_mas_viejo() {
        Personas p = personas();
        Biblioteca repo = comoUsuario(p.usuarioA());
        Documento viejo = repo.crear(p.usuarioA(), p.institucion(), nuevo("viejo.txt", Documento.Tipo.TEXTO, "viejo " + UUID.randomUUID()));
        Documento nuevo = repo.crear(p.usuarioA(), p.institucion(), nuevo("nuevo.txt", Documento.Tipo.TEXTO, "nuevo " + UUID.randomUUID()));

        assertThat(repo.visibles(p.usuarioA())).extracting(Documento::id).containsSubsequence(nuevo.id(), viejo.id());
    }

    @Test
    void borrar_quita_el_documento_y_sus_fragmentos() {
        Personas p = personas();
        Documento d = indexado(p.usuarioA(), "borrable-croissant.md", List.of("El croissant se hornea a 190 grados."));
        Biblioteca repo = comoUsuario(p.usuarioA());

        assertThat(repo.borrar(p.usuarioA(), d.id())).isTrue();

        assertThat(repo.porId(p.usuarioA(), d.id())).isEmpty();
        assertThat(repo.fragmentos(p.usuarioA(), d.id())).isEmpty();
        assertThat(repo.buscarPorTexto(p.usuarioA(), "croissant", 5)).isEmpty();
        assertThat(repo.borrar(p.usuarioA(), d.id())).isFalse();
    }
}
