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
 * vacío y quien llama usa la pregunta del banco. Las fallas del modelo (no disponible, tiempo agotado) se propagan: el
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
        for (int i = 0; i <= ModeloLocal.REINTENTOS; i++) {
            PeticionChat peticion = new PeticionChat(List.of(Mensaje.sistema(sistema), Mensaje.usuario(pedido)), ModeloLocal.TIEMPO_CHAT,
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
            }
        }
        return new Redactado(Optional.empty(), intentos, modelo, digest);
    }
}
