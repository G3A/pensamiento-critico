package pensamiento.nucleo;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Lo que agrega una técnica con Ollama opcional a su ejecutor (RF-14). El ejecutor sigue siendo determinista
 * sobre su entrada; pedir propuestas es un paso aparte que usa la IA solo por {@link Contexto#ia()} y devuelve
 * propuestas sin adoptar. La estrategia (qué preguntar, contra qué enum, en qué orden) vive aquí, en código; el
 * modelo solo clasifica contra un enum cerrado o redacta un texto corto.
 *
 * @param <C> configuración de la técnica
 * @param <E> entrada de la técnica, que lleva sus propuestas
 */
public interface ConModelo<C, E> {

    /** Lo que devuelve pedir propuestas: las nuevas, o el motivo por el que se siguió en modo plantillas. */
    record Propuestas(List<Propuesta> nuevas, Optional<String> caida) {
        public Propuestas {
            nuevas = List.copyOf(nuevas);
        }

        public static Propuestas cayo(String motivo) {
            return new Propuestas(List.of(), Optional.of(motivo));
        }

        public static Propuestas de(List<Propuesta> nuevas) {
            return new Propuestas(nuevas, Optional.empty());
        }
    }

    /** Si la configuración pide propuestas al modelo (por ejemplo, "plantillas y modelo"). */
    boolean usaModelo(C config);

    /**
     * Pide al modelo propuestas para lo que la persona dejó sin llenar. Nunca lanza por fallas del modelo: si no
     * hay IA, se agota el tiempo o la respuesta no sirve tras los reintentos, devuelve la caída con el motivo.
     *
     * @param provisional  recibe texto provisional (tokens o avance) mientras el modelo trabaja
     * @param primerNumero número del primer código IA libre (los anteriores ya existen en la entrada)
     */
    Propuestas proponer(C config, E entrada, Contexto ctx, Consumer<String> provisional, int primerNumero);

    /** Las propuestas que trae la entrada, adoptadas o no. */
    List<Propuesta> propuestas(E entrada);

    /**
     * Adopta una propuesta: la marca adoptada y la lleva a la parte de la entrada que le corresponde, con origen
     * modelo. Lanza IllegalArgumentException si el código no existe o ya estaba adoptada.
     */
    E adoptar(E entrada, String codigo);
}
