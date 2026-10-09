package pensamiento.testutil.fakes;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.Afirmacion;
import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.EstadoAfirmacion;
import pensamiento.nucleo.SentidoAfirmacion;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.Verificacion;
import pensamiento.nucleo.puertos.RepositorioVerificaciones;

/**
 * Fake en memoria de la ficha de verificación. Las afirmaciones salen del Fake de ejecuciones (las que produjo cada una);
 * nacen sin verificar, con fuerza 0 y sin confianza, y aquí se guardan los cambios. Certificado por
 * FakeRepositorioVerificacionesContractTest.
 */
public final class FakeRepositorioVerificaciones implements RepositorioVerificaciones {

    private record Producida(Ejecucion ejecucion, AfirmacionConRol afirmacion) {
    }

    private record Estado(TipoAfirmacion tipo, EstadoAfirmacion estado, int fuerzaNeta, Optional<Integer> confianza) {
    }

    private record Fila(UUID usuarioId, Verificacion verificacion) {
    }

    private final FakeRepositorioEjecucion ejecuciones;
    private final Map<UUID, Estado> estados = new LinkedHashMap<>();
    private final Map<UUID, Fila> verificaciones = new LinkedHashMap<>();

    public FakeRepositorioVerificaciones(FakeRepositorioEjecucion ejecuciones) {
        this.ejecuciones = ejecuciones;
    }

    private Optional<Producida> producida(UUID usuarioId, UUID afirmacionId) {
        return ejecuciones.todas().stream().filter(e -> e.usuarioId().equals(usuarioId))
                .sorted(Comparator.comparing(Ejecucion::creadaEn))
                .flatMap(e -> ejecuciones.afirmacionesDe(usuarioId, e.id()).stream()
                        .filter(a -> a.afirmacionId().equals(afirmacionId) && a.sentido() == SentidoAfirmacion.PRODUCIDA)
                        .map(a -> new Producida(e, a)))
                .findFirst();
    }

    @Override
    public Optional<Afirmacion> afirmacion(UUID usuarioId, UUID afirmacionId) {
        return producida(usuarioId, afirmacionId).map(p -> {
            Estado s = estados.getOrDefault(afirmacionId, new Estado(p.afirmacion().tipo(), EstadoAfirmacion.SIN_VERIFICAR, 0, Optional.empty()));
            return new Afirmacion(afirmacionId, usuarioId, p.ejecucion().institucionId(), p.afirmacion().texto(), s.tipo(), p.afirmacion().origen(),
                    p.afirmacion().adoptada(), s.confianza(), s.estado(), s.fuerzaNeta());
        });
    }

    @Override
    public boolean cambiarTipo(UUID usuarioId, UUID afirmacionId, TipoAfirmacion tipo) {
        return afirmacion(usuarioId, afirmacionId).map(a -> {
            estados.put(afirmacionId, new Estado(tipo, a.estado(), a.fuerzaNeta(), a.confianza()));
            return true;
        }).orElse(false);
    }

    @Override
    public Verificacion verificacion(UUID usuarioId, UUID afirmacionId) {
        return Optional.ofNullable(verificaciones.get(afirmacionId)).filter(f -> f.usuarioId().equals(usuarioId)).map(Fila::verificacion)
                .orElse(Verificacion.nueva(afirmacionId));
    }

    @Override
    public void marcarPreguntas(UUID usuarioId, UUID institucionId, UUID afirmacionId, List<String> respondidas, Instant cuando) {
        if (afirmacion(usuarioId, afirmacionId).isEmpty()) {
            throw new IllegalArgumentException("La afirmación no existe o es de otra persona");
        }
        verificaciones.put(afirmacionId, new Fila(usuarioId, new Verificacion(afirmacionId, respondidas, Optional.of(cuando))));
    }

    @Override
    public boolean guardarVeredicto(UUID usuarioId, UUID afirmacionId, Verificacion.Veredicto v) {
        if (afirmacion(usuarioId, afirmacionId).isEmpty()) {
            return false;
        }
        estados.put(afirmacionId, new Estado(v.tipo(), v.estado(), v.fuerzaNeta(), v.confianza()));
        return true;
    }

    @Override
    public Optional<Verificacion.Origen> origen(UUID usuarioId, UUID afirmacionId) {
        return producida(usuarioId, afirmacionId).map(p -> new Verificacion.Origen(p.ejecucion().id(), p.ejecucion().tecnica(), p.ejecucion().expedienteId()));
    }

    @Override
    public List<Verificacion> deUsuario(UUID usuarioId) {
        return verificaciones.values().stream().filter(f -> f.usuarioId().equals(usuarioId)).map(Fila::verificacion)
                .sorted(Comparator.comparing((Verificacion v) -> v.actualizadaEn().orElseThrow()).thenComparing(Verificacion::afirmacionId)).toList();
    }
}
