package pensamiento.testutil.fakes;

import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import pensamiento.nucleo.Documento;
import pensamiento.nucleo.Fragmento;
import pensamiento.nucleo.Pasaje;
import pensamiento.nucleo.puertos.Biblioteca;

/**
 * Fake en memoria de la biblioteca. El texto completo compara palabras plegadas (sin tildes ni mayúsculas) y salta las
 * palabras vacías más comunes del español; no reduce a raíces como PostgreSQL. La similitud es el coseno exacto.
 * Certificado por FakeBibliotecaContractTest.
 */
public final class FakeBiblioteca implements Biblioteca {

    private static final Set<String> VACIAS = Set.of("el", "la", "los", "las", "lo", "un", "una", "unos", "unas", "de", "del", "al", "a", "en",
            "y", "o", "que", "por", "para", "con", "sin", "se", "su", "sus", "es", "son", "mas", "no", "como", "le", "les", "ya", "muy");

    private static final class Doc {
        final UUID usuario;
        final Instant creado;
        final Biblioteca.NuevoDocumento nuevo;
        Documento.Estado estado = Documento.Estado.EN_PROCESO;
        boolean compartido;
        Optional<Integer> paginas = Optional.empty();
        Optional<String> error = Optional.empty();
        final List<Fragmento> fragmentos = new ArrayList<>();
        final Map<UUID, float[]> vectores = new LinkedHashMap<>();

        Doc(UUID usuario, Instant creado, Biblioteca.NuevoDocumento nuevo) {
            this.usuario = usuario;
            this.creado = creado;
            this.nuevo = nuevo;
        }
    }

    private final Map<UUID, Doc> documentos = new LinkedHashMap<>();
    private Instant reloj = Instant.parse("2026-10-09T12:00:00Z");

    @Override
    public Documento crear(UUID usuarioId, UUID institucionId, NuevoDocumento nuevo) {
        documentos.values().stream().filter(d -> d.usuario.equals(usuarioId) && d.nuevo.hash().equals(nuevo.hash())).findFirst()
                .ifPresent(d -> {
                    throw new DocumentoRepetido(d.nuevo.nombre());
                });
        reloj = reloj.plusMillis(1);
        documentos.put(nuevo.id(), new Doc(usuarioId, reloj, nuevo));
        return porId(usuarioId, nuevo.id()).orElseThrow();
    }

    @Override
    public Optional<Documento> propioPorHash(UUID usuarioId, String hash) {
        return documentos.values().stream().filter(d -> d.usuario.equals(usuarioId) && d.nuevo.hash().equals(hash)).findFirst()
                .map(FakeBiblioteca::documento);
    }

    private Optional<Doc> visible(UUID usuarioId, UUID id) {
        return Optional.ofNullable(documentos.get(id)).filter(d -> d.usuario.equals(usuarioId) || d.compartido);
    }

    private Optional<Doc> propio(UUID usuarioId, UUID id) {
        return Optional.ofNullable(documentos.get(id)).filter(d -> d.usuario.equals(usuarioId));
    }

    private static Documento documento(Doc d) {
        return new Documento(d.nuevo.id(), d.usuario, d.nuevo.nombre(), d.nuevo.tipo(), d.estado, d.compartido, d.nuevo.hash(), d.nuevo.contenido().length,
                d.paginas, d.error, d.fragmentos.size(), d.vectores.size(), d.creado);
    }

    @Override
    public Optional<Documento> porId(UUID usuarioId, UUID id) {
        return visible(usuarioId, id).map(FakeBiblioteca::documento);
    }

    @Override
    public List<Documento> visibles(UUID usuarioId) {
        return documentos.values().stream().filter(d -> d.usuario.equals(usuarioId) || d.compartido)
                .sorted(Comparator.comparing((Doc d) -> d.creado).reversed()).map(FakeBiblioteca::documento).toList();
    }

    @Override
    public Optional<byte[]> contenido(UUID usuarioId, UUID id) {
        return visible(usuarioId, id).map(d -> d.nuevo.contenido().clone());
    }

    @Override
    public void indexar(UUID usuarioId, UUID documentoId, List<Fragmento.Nuevo> fragmentos, Optional<Integer> paginas) {
        Doc d = propio(usuarioId, documentoId).orElseThrow(() -> new IllegalArgumentException("El documento no existe o es de otra persona"));
        d.fragmentos.clear();
        d.vectores.clear();
        for (Fragmento.Nuevo f : fragmentos) {
            d.fragmentos.add(new Fragmento(UUID.randomUUID(), documentoId, f.orden(), f.texto(), f.pagina()));
        }
        d.fragmentos.sort(Comparator.comparingInt(Fragmento::orden));
        d.estado = Documento.Estado.INDEXADO;
        d.error = Optional.empty();
        d.paginas = paginas;
    }

    @Override
    public void marcarError(UUID usuarioId, UUID documentoId, String motivo) {
        propio(usuarioId, documentoId).ifPresent(d -> {
            d.fragmentos.clear();
            d.vectores.clear();
            d.estado = Documento.Estado.ERROR;
            d.error = Optional.of(motivo);
        });
    }

    @Override
    public List<Fragmento> sinVector(UUID usuarioId, UUID documentoId, int limite) {
        return propio(usuarioId, documentoId).map(d -> d.fragmentos.stream().filter(f -> !d.vectores.containsKey(f.id())).limit(limite).toList())
                .orElse(List.of());
    }

    @Override
    public void guardarVectores(UUID usuarioId, Map<UUID, float[]> vectores) {
        for (Doc d : documentos.values()) {
            if (!d.usuario.equals(usuarioId)) {
                continue;
            }
            for (Fragmento f : d.fragmentos) {
                if (vectores.containsKey(f.id())) {
                    d.vectores.put(f.id(), vectores.get(f.id()).clone());
                }
            }
        }
    }

    @Override
    public List<Fragmento> fragmentos(UUID usuarioId, UUID documentoId) {
        return visible(usuarioId, documentoId).map(d -> List.copyOf(d.fragmentos)).orElse(List.of());
    }

    @Override
    public List<Pasaje> buscarPorTexto(UUID usuarioId, String consulta, int limite) {
        Set<String> palabras = palabras(consulta);
        if (palabras.isEmpty()) {
            return List.of();
        }
        record Puntuado(Doc doc, Fragmento fragmento, int coincidencias) {
        }
        List<Puntuado> candidatos = new ArrayList<>();
        for (Doc d : documentos.values()) {
            if (!(d.usuario.equals(usuarioId) || d.compartido)) {
                continue;
            }
            for (Fragmento f : d.fragmentos) {
                Set<String> comunes = new HashSet<>(palabras(f.texto()));
                comunes.retainAll(palabras);
                if (!comunes.isEmpty()) {
                    candidatos.add(new Puntuado(d, f, comunes.size()));
                }
            }
        }
        return candidatos.stream()
                .sorted(Comparator.comparingInt(Puntuado::coincidencias).reversed().thenComparing((Puntuado p) -> p.doc().creado, Comparator.reverseOrder())
                        .thenComparingInt(p -> p.fragmento().orden()))
                .limit(limite)
                .map(p -> new Pasaje(p.fragmento().id(), p.doc().nuevo.id(), p.doc().nuevo.nombre(), p.fragmento().pagina(), p.fragmento().texto(),
                        p.coincidencias(), Pasaje.Modo.TEXTO_COMPLETO))
                .toList();
    }

    @Override
    public List<Pasaje> buscarPorVector(UUID usuarioId, float[] consulta, int limite) {
        record Puntuado(Doc doc, Fragmento fragmento, double similitud) {
        }
        List<Puntuado> candidatos = new ArrayList<>();
        for (Doc d : documentos.values()) {
            if (!(d.usuario.equals(usuarioId) || d.compartido)) {
                continue;
            }
            for (Fragmento f : d.fragmentos) {
                float[] v = d.vectores.get(f.id());
                if (v != null) {
                    candidatos.add(new Puntuado(d, f, coseno(consulta, v)));
                }
            }
        }
        return candidatos.stream().sorted(Comparator.comparingDouble(Puntuado::similitud).reversed()).limit(limite)
                .map(p -> new Pasaje(p.fragmento().id(), p.doc().nuevo.id(), p.doc().nuevo.nombre(), p.fragmento().pagina(), p.fragmento().texto(),
                        p.similitud(), Pasaje.Modo.SEMANTICA))
                .toList();
    }

    @Override
    public boolean compartir(UUID usuarioId, UUID documentoId, boolean compartido) {
        return propio(usuarioId, documentoId).map(d -> {
            d.compartido = compartido;
            return true;
        }).orElse(false);
    }

    @Override
    public boolean borrar(UUID usuarioId, UUID documentoId) {
        return propio(usuarioId, documentoId).map(d -> documentos.remove(documentoId) != null).orElse(false);
    }

    /** Si el documento todavía existe, para el Fake de evidencias ("documento retirado"). */
    public boolean existe(UUID documentoId) {
        return documentos.containsKey(documentoId);
    }

    static Set<String> palabras(String texto) {
        String plegado = Normalizer.normalize(texto.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        Set<String> palabras = new HashSet<>(Arrays.asList(plegado.split("[^\\p{L}\\p{N}]+")));
        palabras.remove("");
        palabras.removeAll(VACIAS);
        return palabras;
    }

    private static double coseno(float[] a, float[] b) {
        double punto = 0;
        double na = 0;
        double nb = 0;
        for (int i = 0; i < a.length; i++) {
            punto += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return na == 0 || nb == 0 ? 0 : punto / Math.sqrt(na * nb);
    }
}
