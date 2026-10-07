package pensamiento.nucleo;

import java.util.UUID;

/**
 * Ejemplo completo de una técnica: configuración, datos ficticios y resultado escrito a mano. Es contenido
 * compartido del catálogo y, para las técnicas deterministas, el oráculo de sus pruebas.
 *
 * @param orden posición dentro de la técnica (1, 2, 3...), en el orden del archivo del catálogo
 */
public record Ejemplo(UUID id, IdTecnica tecnica, int orden, int versionEsquema, Ambito ambito, String titulo,
                      Json config, Json datos, Json resultado, String nota) {

    public enum Ambito {
        PERSONAL("Personal"), TRABAJO("Trabajo"), COMUNIDAD("Comunidad");

        private final String titulo;

        Ambito(String titulo) {
            this.titulo = titulo;
        }

        public String titulo() {
            return titulo;
        }

        public String enBaseDeDatos() {
            return name().toLowerCase();
        }
    }
}
