package pensamiento.expediente;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import pensamiento.catalogo.MapeadorJson;
import pensamiento.nucleo.CambioOpinion;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.puertos.RepositorioArgumentos;
import pensamiento.nucleo.puertos.RepositorioCambiosOpinion;
import pensamiento.nucleo.puertos.RepositorioConfiguracion;
import pensamiento.nucleo.puertos.RepositorioEjecucion;
import pensamiento.nucleo.puertos.RepositorioEvidencias;
import pensamiento.nucleo.puertos.RepositorioPredicciones;

/**
 * Guarda una ejecución con todo lo que su resultado declara: afirmaciones con rol, pendientes, argumentos con sus
 * premisas, predicciones con su confianza (T32), cambios de opinión (R05) y evidencias con su fuente (T22). La transacción corta la abre quien llama,
 * así que todo queda o nada queda. Idempotente: si la clave ya existía (doble clic), devuelve la ejecución de antes y no
 * escribe nada más. Si la persona apagó el registro automático en T46 · Registro de cambios de opinión, solo guarda los
 * cambios que registra a mano con T46.
 */
@Service
public class GuardadoDeEjecuciones {

    static final IdTecnica REGISTRO = IdTecnica.de("T46");

    /** Lo único que se lee de la configuración de T46 aquí. */
    record RegistroAutomatico(Boolean registroAutomatico) {
    }

    private final RepositorioEjecucion ejecuciones;
    private final RepositorioArgumentos argumentos;
    private final RepositorioPredicciones predicciones;
    private final RepositorioCambiosOpinion cambios;
    private final RepositorioEvidencias evidencias;
    private final RepositorioConfiguracion configuraciones;

    public GuardadoDeEjecuciones(RepositorioEjecucion ejecuciones, RepositorioArgumentos argumentos, RepositorioPredicciones predicciones,
                                 RepositorioCambiosOpinion cambios, RepositorioEvidencias evidencias, RepositorioConfiguracion configuraciones) {
        this.ejecuciones = ejecuciones;
        this.argumentos = argumentos;
        this.predicciones = predicciones;
        this.cambios = cambios;
        this.evidencias = evidencias;
        this.configuraciones = configuraciones;
    }

    public Ejecucion guardar(Ejecucion nueva, Resultado<?> resultado) {
        Ejecucion guardada = ejecuciones.guardar(nueva, resultado.afirmaciones(), resultado.pendientes());
        if (!guardada.id().equals(nueva.id())) {
            return guardada;
        }
        if (!resultado.argumentos().isEmpty()) {
            argumentos.guardar(nueva.usuarioId(), nueva.institucionId(), guardada.id(), resultado.argumentos());
        }
        if (!resultado.predicciones().isEmpty()) {
            predicciones.guardar(nueva.usuarioId(), nueva.institucionId(), guardada.id(), resultado.predicciones());
        }
        List<CambioOpinion.Declarado> declarados = resultado.cambios();
        if (!declarados.isEmpty() && (nueva.tecnica().equals(REGISTRO) || registroAutomatico(nueva.usuarioId()))) {
            cambios.guardar(nueva.usuarioId(), nueva.institucionId(), guardada.id(), declarados, nueva.creadaEn());
        }
        for (pensamiento.nucleo.EvidenciaGuardada e : resultado.evidencias()) {
            evidencias.guardar(nueva.usuarioId(), nueva.institucionId(), e);
        }
        return guardada;
    }

    /** Verdadero salvo que la persona lo haya apagado en su configuración de T46. */
    private boolean registroAutomatico(UUID usuarioId) {
        return configuraciones.de(usuarioId, REGISTRO)
                .map(g -> MapeadorJson.leer(g.valores(), RegistroAutomatico.class).registroAutomatico())
                .map(b -> !Boolean.FALSE.equals(b)).orElse(true);
    }
}
