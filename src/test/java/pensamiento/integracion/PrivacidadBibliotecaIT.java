package pensamiento.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.lifecycle.AfterContainer;
import net.jqwik.api.lifecycle.BeforeContainer;

import pensamiento.biblioteca.BibliotecaPgvector;
import pensamiento.nucleo.Documento;
import pensamiento.nucleo.Fragmento;
import pensamiento.nucleo.Pasaje;
import pensamiento.nucleo.puertos.Biblioteca;
import pensamiento.testutil.BaseDatosDePrueba;

/**
 * Propiedad contra el PostgreSQL del compose, bajo RLS: un documento privado de una persona nunca aparece en la búsqueda de
 * otra, ni por palabras ni por similitud; uno compartido sí puede aparecer. Las dos cerraduras juntas: el filtro por persona
 * del adaptador y la política de la tabla.
 */
class PrivacidadBibliotecaIT {

    private static final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private static final List<String> PALABRAS = List.of("tahona", "hojaldre", "levadura", "croissant", "masa", "horno", "brioche", "centeno");
    private static UUID institucion;
    private static UUID duena;
    private static UUID vecina;

    @BeforeContainer
    static void sembrar() {
        institucion = bd.crearInstitucion("privacidad-biblioteca-" + UUID.randomUUID());
        duena = bd.crearUsuario(institucion, "dueña");
        vecina = bd.crearUsuario(institucion, "vecina");
    }

    @AfterContainer
    static void limpiar() {
        bd.borrarInstitucion(institucion);
    }

    /** Un documento de la dueña: su palabra y si lo comparte. */
    record DeLaDuena(String palabra, boolean compartido) {
    }

    @Property(tries = 15)
    void un_documento_privado_nunca_aparece_en_la_busqueda_de_otra_persona(@ForAll("documentos") List<DeLaDuena> documentos,
                                                                          @ForAll("consultas") String consulta) {
        BibliotecaPgvector biblioteca = new BibliotecaPgvector(bd.jdbcApp());
        Set<UUID> privados = new java.util.HashSet<>();
        float[] eje = new float[1024];
        eje[3] = 1;
        for (DeLaDuena d : documentos) {
            UUID id = UUID.randomUUID();
            bd.comoUsuario(duena, institucion, () -> {
                biblioteca.crear(duena, institucion, new Biblioteca.NuevoDocumento(id, d.palabra() + ".md", Documento.Tipo.MARKDOWN, "hash-" + id,
                        ("La " + d.palabra() + " del barrio.").getBytes(StandardCharsets.UTF_8)));
                biblioteca.indexar(duena, id, List.of(new Fragmento.Nuevo(0, "La " + d.palabra() + " del barrio.", Optional.empty())), Optional.empty());
                UUID fragmento = biblioteca.fragmentos(duena, id).getFirst().id();
                biblioteca.guardarVectores(duena, java.util.Map.of(fragmento, eje));
                biblioteca.compartir(duena, id, d.compartido());
                return null;
            });
            if (!d.compartido()) {
                privados.add(id);
            }
        }

        List<Pasaje> porPalabras = bd.comoUsuario(vecina, institucion, () -> biblioteca.buscarPorTexto(vecina, consulta, 50));
        List<Pasaje> porSimilitud = bd.comoUsuario(vecina, institucion, () -> biblioteca.buscarPorVector(vecina, eje, 50));
        Set<UUID> vistos = java.util.stream.Stream.concat(porPalabras.stream(), porSimilitud.stream()).map(Pasaje::documentoId).collect(Collectors.toSet());

        assertThat(vistos).noneMatch(privados::contains);
        assertThat(bd.comoUsuario(vecina, institucion, () -> biblioteca.visibles(vecina))).extracting(Documento::id).noneMatch(privados::contains);
    }

    @Provide
    Arbitrary<List<DeLaDuena>> documentos() {
        return net.jqwik.api.Combinators.combine(Arbitraries.of(PALABRAS), Arbitraries.of(true, false)).as(DeLaDuena::new).list().ofMinSize(1).ofMaxSize(4);
    }

    @Provide
    Arbitrary<String> consultas() {
        return Arbitraries.of(PALABRAS).list().ofMinSize(1).ofMaxSize(3).map(l -> String.join(" ", l));
    }
}
