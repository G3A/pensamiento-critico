package pensamiento.tecnicas.comun;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import pensamiento.nucleo.ConModelo;
import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Propuesta;
import pensamiento.nucleo.puertos.Clasificacion;
import pensamiento.nucleo.puertos.Ia;
import pensamiento.nucleo.puertos.IaNoDisponible;
import pensamiento.nucleo.puertos.IaRespuestaInvalida;
import pensamiento.nucleo.puertos.IaTiempoAgotado;
import pensamiento.nucleo.puertos.Mensaje;
import pensamiento.nucleo.puertos.PeticionChat;
import pensamiento.nucleo.puertos.PeticionClasificacion;
import pensamiento.nucleo.puertos.RespuestaChat;

/**
 * Cómo piden propuestas las técnicas: tiempos máximos por tarea (chat 60 s, clasificación 20 s, los del documento),
 * hasta dos reintentos si la respuesta no es válida, y caída al modo plantillas con un motivo que se lee en
 * pantalla si el modelo no está, no responde a tiempo o sigue respondiendo mal.
 */
public final class ModeloLocal {

    public static final Duration TIEMPO_CHAT = Duration.ofSeconds(60);
    public static final Duration TIEMPO_CLASIFICACION = Duration.ofSeconds(20);
    /** Máximo dos reintentos (sección 4, streaming): tres intentos en total. */
    public static final int REINTENTOS = 2;

    public static final String NO_DISPONIBLE = "El modelo no está disponible: sigues en modo plantillas.";
    public static final String TIEMPO_AGOTADO = "El modelo tardó más que el tiempo máximo: sigues en modo plantillas.";
    public static final String RESPUESTA_INVALIDA = "El modelo no dio una respuesta válida después de dos reintentos: sigues en modo plantillas.";

    private ModeloLocal() {
    }

    /** Corre el pedido con la IA del contexto; si no hay IA o falla, la caída con su motivo. Nunca lanza por el modelo. */
    public static ConModelo.Propuestas conCaida(Contexto ctx, Function<Ia, List<Propuesta>> pedir) {
        Optional<Ia> ia = ctx.ia();
        if (ia.isEmpty()) {
            return ConModelo.Propuestas.cayo(NO_DISPONIBLE);
        }
        try {
            return ConModelo.Propuestas.de(pedir.apply(ia.get()));
        } catch (IaNoDisponible e) {
            return ConModelo.Propuestas.cayo(NO_DISPONIBLE);
        } catch (IaTiempoAgotado e) {
            return ConModelo.Propuestas.cayo(TIEMPO_AGOTADO);
        } catch (IaRespuestaInvalida e) {
            return ConModelo.Propuestas.cayo(RESPUESTA_INVALIDA);
        }
    }

    /** Clasificación contra un enum cerrado, con hasta dos reintentos si el modelo responde algo que no sirve. */
    public static Clasificacion clasificar(Ia ia, String instruccion, String texto, List<String> etiquetas) {
        IaRespuestaInvalida ultima = null;
        for (int intento = 0; intento <= REINTENTOS; intento++) {
            try {
                return ia.clasificar(new PeticionClasificacion(instruccion, texto, etiquetas, TIEMPO_CLASIFICACION));
            } catch (IaRespuestaInvalida e) {
                ultima = e;
            }
        }
        throw ultima;
    }

    /** Un texto corto redactado por el modelo, con streaming y un validador que decide si sirve. */
    public static RespuestaChat redactar(Ia ia, String sistema, String pedido, Predicate<String> validador, Consumer<String> provisional) {
        PeticionChat peticion = new PeticionChat(List.of(Mensaje.sistema(sistema), Mensaje.usuario(pedido)), TIEMPO_CHAT,
                Optional.of(validador), REINTENTOS);
        return ia.chat(peticion, provisional);
    }

    /** El validador común de los textos cortos: no vacío, dentro del largo y sin pregunta (el modelo no pregunta aquí). */
    public static Predicate<String> textoCorto(int palabrasMaximas) {
        return texto -> !Textos.vacio(texto) && Textos.palabras(texto) <= palabrasMaximas && !texto.contains("?");
    }

    /** Quita comillas y espacios que el modelo suele poner alrededor de un texto corto. */
    public static String limpiar(String texto) {
        String t = texto.strip();
        while (t.length() > 1 && (t.startsWith("\"") || t.startsWith("«") || t.startsWith("“"))
                && (t.endsWith("\"") || t.endsWith("»") || t.endsWith("”"))) {
            t = t.substring(1, t.length() - 1).strip();
        }
        return t;
    }
}
