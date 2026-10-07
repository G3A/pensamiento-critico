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

    /** T01 a T49. */
    IdTecnica id();

    /** Sube con cada cambio de C, E o R. */
    int versionEsquema();

    Validacion validar(C config, E entrada);

    /** ctx: usuario, expediente, reloj, Optional de Ia. */
    Resultado<R> ejecutar(C config, E entrada, Contexto ctx);

    /** Migración perezosa al leer una ejecución guardada con una versión anterior del esquema. */
    R migrar(Json datosViejos, int desdeVersion);
}
