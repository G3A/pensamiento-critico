package pensamiento.flujos;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import pensamiento.nucleo.Documento;
import pensamiento.nucleo.Pasaje;
import pensamiento.nucleo.puertos.Biblioteca;
import pensamiento.nucleo.puertos.ExcepcionIa;
import pensamiento.nucleo.puertos.Ia;
import pensamiento.nucleo.puertos.PeticionEmbeddings;

/**
 * Buscar en la biblioteca (P11 y P13): con Ollama y algún fragmento vectorizado, por similitud con bge-m3; si no, o si se
 * pide, por texto completo. Siempre pasajes literales, nunca un resumen. Corre fuera de toda transacción: el modelo no
 * retiene una conexión a la base mientras incrusta la consulta.
 */
@Service
public class BuscadorDePasajes {

    public static final int LIMITE = 5;

    /**
     * @param modo  cómo se buscó
     * @param aviso lo que la persona tiene que saber de la búsqueda; vacío si nada
     */
    public record Busqueda(String consulta, List<Pasaje> pasajes, Pasaje.Modo modo, Optional<String> aviso) {
        public Busqueda {
            pasajes = List.copyOf(pasajes);
        }
    }

    private final Biblioteca biblioteca;
    private final Ia ia;
    private final Duration tiempoMaximo;

    public BuscadorDePasajes(Biblioteca biblioteca, Ia ia, @Value("${app.ia.timeout-embeddings:10s}") Duration tiempoMaximo) {
        this.biblioteca = biblioteca;
        this.ia = ia;
        this.tiempoMaximo = tiempoMaximo;
    }

    /** @param porPalabras verdadero si la persona pidió buscar por texto completo */
    public Busqueda buscar(UUID usuarioId, String consulta, boolean porPalabras) {
        String q = consulta == null ? "" : consulta.strip();
        if (q.isEmpty()) {
            return new Busqueda(q, List.of(), Pasaje.Modo.TEXTO_COMPLETO, Optional.of("Escribe qué buscas."));
        }
        List<Documento> visibles = biblioteca.visibles(usuarioId);
        long sinTerminar = visibles.stream().filter(d -> d.estado() == Documento.Estado.INDEXADO && d.conVector() < d.fragmentos()).count();
        boolean hayVectores = visibles.stream().anyMatch(d -> d.conVector() > 0);
        Optional<String> aviso = Optional.empty();
        if (!porPalabras && hayVectores) {
            try {
                float[] vector = ia.incrustar(new PeticionEmbeddings(List.of(q), tiempoMaximo)).getFirst();
                List<Pasaje> pasajes = biblioteca.buscarPorVector(usuarioId, vector, LIMITE);
                Optional<String> pendientes = sinTerminar == 0 ? Optional.empty()
                        : Optional.of((sinTerminar == 1 ? "1 documento todavía se vectoriza" : sinTerminar + " documentos todavía se vectorizan")
                        + ": búscalos también por palabras.");
                return new Busqueda(q, pasajes, Pasaje.Modo.SEMANTICA, pendientes);
            } catch (ExcepcionIa e) {
                aviso = Optional.of("El modelo no responde: la búsqueda es por palabras.");
            }
        } else if (!porPalabras) {
            aviso = Optional.of("Búsqueda por palabras: ningún documento tiene vectores todavía.");
        }
        return new Busqueda(q, biblioteca.buscarPorTexto(usuarioId, q, LIMITE), Pasaje.Modo.TEXTO_COMPLETO, aviso);
    }
}
