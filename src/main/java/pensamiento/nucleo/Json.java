package pensamiento.nucleo;

/** Texto JSON tal como se guarda en una columna JSONB. El núcleo no lo interpreta: lo transporta. */
public record Json(String texto) {

    public static final Json VACIO = new Json("{}");

    public Json {
        if (texto == null || texto.isBlank()) {
            texto = "{}";
        }
    }
}
