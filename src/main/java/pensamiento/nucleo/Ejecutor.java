package pensamiento.nucleo;

/**
 * Contrato de una técnica (sección 4 del documento). El ejecutor es dominio puro: recibe configuración
 * y entrada tipadas, devuelve un resultado que declara lo que el Expediente necesita y nunca renderiza.
 *
 * @param <C> configuración de la técnica (esquema versionado)
 * @param <E> entrada de la técnica
 * @param <R> valor del resultado que el renderizador del patrón pinta
 */
public interface Ejecutor<C, E, R> {

    /** Las clases de C, E y R: con ellas la capa web lee y escribe el JSONB sin descubrir tipos por nombre. */
    record Tipos<C, E, R>(Class<C> config, Class<E> entrada, Class<R> resultado) {
    }

    /** T01 a T49. */
    IdTecnica id();

    /** Sube con cada cambio de C, E o R. */
    int versionEsquema();

    Tipos<C, E, R> tipos();

    Validacion validar(C config, E entrada);

    /** ctx: usuario, expediente, reloj, Optional de Ia y generador de identificadores. */
    Resultado<R> ejecutar(C config, E entrada, Contexto ctx);

    /**
     * Migración perezosa al leer una ejecución guardada con una versión anterior del esquema. Quien lee
     * llama aquí solo si desdeVersion es menor que versionEsquema(); la versión vigente se lee directo.
     */
    R migrar(Json datosViejos, int desdeVersion);
}
