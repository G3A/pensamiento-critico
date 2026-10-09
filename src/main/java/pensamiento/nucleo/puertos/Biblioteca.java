package pensamiento.nucleo.puertos;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.Documento;
import pensamiento.nucleo.Fragmento;
import pensamiento.nucleo.Pasaje;

/**
 * La biblioteca local (tablas documento y fragmento, pgvector): documentos privados de quien los importa salvo que los
 * comparta con su institución, sus fragmentos y la búsqueda, que siempre devuelve pasajes literales. Todo método recibe el
 * usuario de la sesión; la implementación real además corre bajo RLS. Un documento de otra persona sin compartir, para
 * quien pregunta, no existe.
 */
public interface Biblioteca {

    /** Lo que llega al importar, ya con su tipo detectado por el contenido. */
    record NuevoDocumento(UUID id, String nombre, Documento.Tipo tipo, String hash, byte[] contenido) {
    }

    /** La persona ya importó un archivo con el mismo contenido. */
    final class DocumentoRepetido extends RuntimeException {
        private final String nombre;

        public DocumentoRepetido(String nombre) {
            super("Ya importaste este documento: " + nombre);
            this.nombre = nombre;
        }

        public String nombre() {
            return nombre;
        }
    }

    /** Crea el documento en proceso, privado. Lanza DocumentoRepetido si la persona ya tiene uno con el mismo hash. */
    Documento crear(UUID usuarioId, UUID institucionId, NuevoDocumento nuevo);

    /** Propio o compartido por alguien de la institución; vacío si no existe o si es privado de otra persona. */
    Optional<Documento> porId(UUID usuarioId, UUID id);

    /** Los propios y los compartidos, del más nuevo al más viejo. */
    List<Documento> visibles(UUID usuarioId);

    /** El archivo original tal como se importó; vacío si no lo puede ver. */
    Optional<byte[]> contenido(UUID usuarioId, UUID id);

    /** Guarda los fragmentos y deja el documento indexado. Solo el dueño. */
    void indexar(UUID usuarioId, UUID documentoId, List<Fragmento.Nuevo> fragmentos, Optional<Integer> paginas);

    /** Deja el documento en error con su motivo, sin fragmentos. Solo el dueño. */
    void marcarError(UUID usuarioId, UUID documentoId, String motivo);

    /** Hasta {@code limite} fragmentos del documento que todavía no tienen vector, en orden. Solo el dueño. */
    List<Fragmento> sinVector(UUID usuarioId, UUID documentoId, int limite);

    /** Guarda el vector de cada fragmento. Solo el dueño. */
    void guardarVectores(UUID usuarioId, Map<UUID, float[]> vectores);

    /** Todos los fragmentos de un documento visible, en orden. */
    List<Fragmento> fragmentos(UUID usuarioId, UUID documentoId);

    /** Pasajes que comparten alguna palabra con la consulta (texto completo en español), de mayor a menor coincidencia. */
    List<Pasaje> buscarPorTexto(UUID usuarioId, String consulta, int limite);

    /** Pasajes con vector, de mayor a menor similitud coseno con el vector de la consulta. */
    List<Pasaje> buscarPorVector(UUID usuarioId, float[] consulta, int limite);

    /** Comparte el documento con la institución o deja de compartirlo. Falso si no es del usuario. */
    boolean compartir(UUID usuarioId, UUID documentoId, boolean compartido);

    /** Borra el documento y sus fragmentos. Falso si no es del usuario. */
    boolean borrar(UUID usuarioId, UUID documentoId);
}
