package pensamiento.testutil.fakes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.SesionConsejero;
import pensamiento.nucleo.TurnoConsejero;
import pensamiento.nucleo.puertos.RepositorioSesiones;

/** Fake en memoria de las sesiones del Consejero y sus turnos. Certificado por FakeRepositorioSesionesContractTest. */
public final class FakeRepositorioSesiones implements RepositorioSesiones {

    private final Map<UUID, SesionConsejero> sesiones = new LinkedHashMap<>();
    private final Map<UUID, List<TurnoConsejero>> turnos = new LinkedHashMap<>();

    @Override
    public void crear(SesionConsejero sesion) {
        sesiones.putIfAbsent(sesion.id(), sesion);
        turnos.putIfAbsent(sesion.id(), new ArrayList<>());
    }

    @Override
    public Optional<SesionConsejero> porId(UUID usuarioId, UUID sesionId) {
        return Optional.ofNullable(sesiones.get(sesionId)).filter(s -> s.usuarioId().equals(usuarioId));
    }

    @Override
    public List<SesionConsejero> deUsuario(UUID usuarioId) {
        return sesiones.values().stream().filter(s -> s.usuarioId().equals(usuarioId))
                .sorted(Comparator.comparing(SesionConsejero::creadaEn).reversed().thenComparing(SesionConsejero::id)).toList();
    }

    @Override
    public List<TurnoConsejero> turnos(UUID usuarioId, UUID sesionId) {
        return porId(usuarioId, sesionId).map(s -> List.copyOf(turnos.get(sesionId))).orElse(List.of());
    }

    @Override
    public void agregarTurno(UUID usuarioId, UUID institucionId, TurnoConsejero turno) {
        SesionConsejero s = porId(usuarioId, turno.sesionId()).orElseThrow(() -> new IllegalArgumentException("No hay una sesión " + turno.sesionId()));
        if (s.cerrada()) {
            throw new SesionCerrada(s.id());
        }
        List<TurnoConsejero> lista = turnos.get(s.id());
        if (turno.numero() != lista.size() + 1) {
            throw new IllegalArgumentException("El turno " + turno.numero() + " no es el siguiente de la sesión");
        }
        lista.add(turno);
    }

    @Override
    public void completarTurno(UUID usuarioId, UUID turnoId, String texto, TurnoConsejero.Origen origen, int intentos, Optional<Ejecucion.RegistroModelo> modelo) {
        reemplazar(usuarioId, turnoId, t -> new TurnoConsejero(t.id(), t.sesionId(), t.numero(), t.rol(), t.paso(), texto, origen,
                TurnoConsejero.Estado.LISTO, intentos, modelo, t.elementoPropuesto(), t.porquePropuesto(), t.propuestaAdoptada(), t.creadoEn()));
    }

    @Override
    public void proponerElemento(UUID usuarioId, UUID turnoId, String elemento, String porque) {
        reemplazar(usuarioId, turnoId, t -> new TurnoConsejero(t.id(), t.sesionId(), t.numero(), t.rol(), t.paso(), t.texto(), t.origen(), t.estado(),
                t.intentos(), t.modelo(), Optional.of(elemento), Optional.of(porque), false, t.creadoEn()));
    }

    @Override
    public void adoptarElemento(UUID usuarioId, UUID turnoId) {
        reemplazar(usuarioId, turnoId, t -> new TurnoConsejero(t.id(), t.sesionId(), t.numero(), t.rol(), t.paso(), t.texto(), t.origen(), t.estado(),
                t.intentos(), t.modelo(), t.elementoPropuesto(), t.porquePropuesto(), t.elementoPropuesto().isPresent(), t.creadoEn()));
    }

    private void reemplazar(UUID usuarioId, UUID turnoId, java.util.function.UnaryOperator<TurnoConsejero> cambio) {
        for (Map.Entry<UUID, List<TurnoConsejero>> e : turnos.entrySet()) {
            if (porId(usuarioId, e.getKey()).isEmpty()) {
                continue;
            }
            List<TurnoConsejero> lista = e.getValue();
            for (int i = 0; i < lista.size(); i++) {
                if (lista.get(i).id().equals(turnoId)) {
                    lista.set(i, cambio.apply(lista.get(i)));
                    return;
                }
            }
        }
    }

    @Override
    public void pedirCierre(UUID usuarioId, UUID sesionId) {
        cambiar(usuarioId, sesionId, s -> new SesionConsejero(s.id(), s.usuarioId(), s.institucionId(), s.expedienteId(), s.modo(), s.postura(), s.razones(),
                s.config(), s.usaModelo(), s.confianzaAntes(), true, s.estado(), s.reflexion(), s.confianzaDespues(), s.ejecucionId(), s.creadaEn(),
                s.cerradaEn()));
    }

    @Override
    public void asociar(UUID usuarioId, UUID sesionId, Optional<UUID> expedienteId) {
        cambiar(usuarioId, sesionId, s -> new SesionConsejero(s.id(), s.usuarioId(), s.institucionId(), expedienteId, s.modo(), s.postura(), s.razones(),
                s.config(), s.usaModelo(), s.confianzaAntes(), s.cierrePedido(), s.estado(), s.reflexion(), s.confianzaDespues(), s.ejecucionId(),
                s.creadaEn(), s.cerradaEn()));
    }

    @Override
    public void cerrar(UUID usuarioId, UUID sesionId, Optional<String> reflexion, Optional<Integer> confianzaDespues, Optional<UUID> ejecucionId,
                       Instant cuando) {
        cambiar(usuarioId, sesionId, s -> new SesionConsejero(s.id(), s.usuarioId(), s.institucionId(), s.expedienteId(), s.modo(), s.postura(), s.razones(),
                s.config(), s.usaModelo(), s.confianzaAntes(), s.cierrePedido(), SesionConsejero.Estado.CERRADA, reflexion, confianzaDespues, ejecucionId,
                s.creadaEn(), Optional.of(cuando)));
    }

    private void cambiar(UUID usuarioId, UUID sesionId, java.util.function.UnaryOperator<SesionConsejero> cambio) {
        porId(usuarioId, sesionId).ifPresent(s -> sesiones.put(s.id(), cambio.apply(s)));
    }

    @Override
    public void restaurar(UUID usuarioId, UUID institucionId, SesionConsejero sesion, List<TurnoConsejero> lista) {
        if (sesiones.containsKey(sesion.id())) {
            return;
        }
        sesiones.put(sesion.id(), sesion);
        turnos.put(sesion.id(), new ArrayList<>(lista.stream().sorted(Comparator.comparingInt(TurnoConsejero::numero)).toList()));
    }
}
