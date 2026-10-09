package pensamiento.tecnicas.comun;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import pensamiento.nucleo.puertos.Ia;
import pensamiento.nucleo.puertos.Mensaje;
import pensamiento.nucleo.puertos.PeticionChat;
import pensamiento.nucleo.puertos.RespuestaChat;

/**
 * Una pregunta que redacta el modelo y que tiene que pasar el validador del turno antes de llegar a la persona (sección
 * 4, streaming): hasta tres intentos (dos reintentos), cada uno con su motivo de rechazo. Si ninguno pasa, el texto queda
 * vacío y quien llama usa la pregunta del banco. Cada reintento sigue la conversación: lleva el texto rechazado y una
 * corrección que dice qué falló (con temperatura 0, repetir el mismo pedido devuelve la misma pregunta). Las fallas del modelo (no disponible, tiempo agotado) se propagan: el
 * motivo de la caída lo decide quien llama.
 */
public final class Redaccion {

    /** Lo que dijo el modelo en un intento y por qué no pasó, si no pasó. */
    public record Intento(String texto, Optional<ValidadorTurno.Motivo> rechazo) {
    }

    /** El resultado: el texto validado (vacío si ningún intento pasó), los intentos y el registro del modelo. */
    public record Redactado(Optional<String> texto, List<Intento> intentos, String modelo, String digest) {
        public Redactado {
            intentos = List.copyOf(intentos);
        }

        public boolean alPrimerIntento() {
            return texto.isPresent() && intentos.size() == 1;
        }
    }

    public static final String AVISO_REINTENTO = " · (no pasó el validador: %s; otro intento) · ";

    private Redaccion() {
    }

    public static Redactado redactar(Ia ia, String sistema, String pedido, int palabrasMaximas, Consumer<String> provisional) {
        List<Intento> intentos = new ArrayList<>();
        String modelo = "";
        String digest = "";
        List<Mensaje> mensajes = new ArrayList<>(List.of(Mensaje.sistema(sistema), Mensaje.usuario(pedido)));
        for (int i = 0; i <= ModeloLocal.REINTENTOS; i++) {
            PeticionChat peticion = new PeticionChat(List.copyOf(mensajes), ModeloLocal.TIEMPO_CHAT,
                    Optional.empty(), 0);
            RespuestaChat r = ia.chat(peticion, provisional);
            modelo = r.modelo();
            digest = r.digest();
            String texto = ModeloLocal.limpiar(r.texto());
            Optional<ValidadorTurno.Motivo> rechazo = ValidadorTurno.rechazo(texto, palabrasMaximas);
            intentos.add(new Intento(texto, rechazo));
            if (rechazo.isEmpty()) {
                return new Redactado(Optional.of(texto), intentos, modelo, digest);
            }
            if (i < ModeloLocal.REINTENTOS) {
                provisional.accept(String.format(AVISO_REINTENTO, rechazo.get().texto()));
                mensajes.add(Mensaje.asistente(texto));
                mensajes.add(Mensaje.usuario(correccion(rechazo.get(), texto, palabrasMaximas)));
            }
        }
        return new Redactado(Optional.empty(), intentos, modelo, digest);
    }

    /** Lo que se le dice al modelo en el reintento: qué falló y qué tiene que cambiar, sin otra instrucción nueva. */
    public static String correccion(ValidadorTurno.Motivo motivo, String texto, int palabrasMaximas) {
        String cambio = switch (motivo) {
            case VACIO -> "No escribiste nada. Escribe la pregunta.";
            case SIN_PREGUNTA -> "Tiene que ser una sola pregunta que termine con signo de pregunta.";
            case LARGO -> "Es demasiado larga: usa " + palabrasMaximas + " palabras o menos.";
            case VEREDICTO -> "Trae una opinión o un consejo. Pregunta sin decir quién tiene razón ni qué conviene.";
            case VOSEO -> ValidadorTurno.formaProhibida(texto)
                    .map(f -> "Usaste «" + f + "», que no es tuteo de Latinoamérica. Trata a la persona de tú, como en «quieres», «tienes» o «sabes».")
                    .orElse("Trata a la persona de tú, en español latinoamericano neutro.");
            case USTED -> "Usaste «usted». Trata a la persona de tú.";
        };
        return "Esa pregunta no sirve. " + cambio + " Escribe solo la pregunta corregida.";
    }
}
