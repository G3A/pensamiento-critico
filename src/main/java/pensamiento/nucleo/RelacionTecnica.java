package pensamiento.nucleo;

/** Arista tipada entre dos técnicas del catálogo (sección 5b). */
public record RelacionTecnica(IdTecnica origen, IdTecnica destino, Tipo tipo) {

    public enum Tipo {
        PRERREQUISITO("es prerrequisito de"),
        PRODUCE_ENTRADA("produce entrada para"),
        VARIANTE("es variante de"),
        COMPLEMENTA("complementa"),
        CONTRASTA("contrasta con");

        private final String frase;

        Tipo(String frase) {
            this.frase = frase;
        }

        public String frase() {
            return frase;
        }

        public String enBaseDeDatos() {
            return name().toLowerCase();
        }
    }

    /** La otra técnica de la relación, vista desde la dada. */
    public IdTecnica otra(IdTecnica desde) {
        return origen.equals(desde) ? destino : origen;
    }
}
