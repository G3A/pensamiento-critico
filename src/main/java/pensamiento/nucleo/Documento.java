package pensamiento.nucleo;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Un documento de la biblioteca (tabla documento, P13): privado de quien lo importa salvo que lo comparta con su
 * institución. Indexado quiere decir que ya tiene sus fragmentos y se encuentra por texto completo; los vectores llegan
 * después, de a poco.
 *
 * @param tamano      en bytes
 * @param paginas     las de un PDF; vacío en los demás tipos
 * @param error       por qué no se pudo indexar, en español
 * @param conOriginal si el archivo original está guardado (un documento restaurado de un respaldo no lo trae)
 * @param fragmentos  cuántos fragmentos tiene
 * @param conVector   cuántos de ellos ya tienen embedding
 */
public record Documento(UUID id, UUID usuarioId, String nombre, Tipo tipo, Estado estado, boolean compartido, String hash, long tamano,
                        Optional<Integer> paginas, Optional<String> error, boolean conOriginal, int fragmentos, int conVector, Instant creadoEn) {

    /** Detectado por el contenido, no por la extensión (RNF-08). */
    public enum Tipo {
        PDF, MARKDOWN, TEXTO, CSV;

        public String enBaseDeDatos() {
            return name().toLowerCase();
        }
    }

    public enum Estado {
        EN_PROCESO, INDEXADO, ERROR;

        public String enBaseDeDatos() {
            return name().toLowerCase();
        }
    }

    public boolean esDe(UUID usuario) {
        return usuarioId.equals(usuario);
    }

    /** Porcentaje de fragmentos con vector, de 0 a 100; 100 si no tiene fragmentos. */
    public int porcentajeVectorizado() {
        return fragmentos == 0 ? 100 : (int) Math.floor(100.0 * conVector / fragmentos);
    }
}
