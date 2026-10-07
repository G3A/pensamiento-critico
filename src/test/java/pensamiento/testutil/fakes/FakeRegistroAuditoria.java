package pensamiento.testutil.fakes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.puertos.RegistroAuditoria;

/**
 * Auditoría en memoria: solo inserción. Sin actor ve todo (collaboration tests); con vistaDe(usuario) se
 * comporta como el real bajo RLS: solo lee lo propio. Certificado por FakeRegistroAuditoriaContractTest.
 */
public final class FakeRegistroAuditoria implements RegistroAuditoria {

    private final List<Evento> eventos;
    private final Optional<UUID> actor;

    public FakeRegistroAuditoria() {
        this(new ArrayList<>(), Optional.empty());
    }

    private FakeRegistroAuditoria(List<Evento> eventos, Optional<UUID> actor) {
        this.eventos = eventos;
        this.actor = actor;
    }

    /** La misma auditoría vista por un usuario concreto: comparte los eventos, filtra las lecturas. */
    public FakeRegistroAuditoria vistaDe(UUID usuarioId) {
        return new FakeRegistroAuditoria(eventos, Optional.of(usuarioId));
    }

    @Override
    public void registrar(Evento evento) {
        eventos.add(evento);
    }

    @Override
    public List<Evento> deUsuario(UUID usuarioId) {
        if (actor.isPresent() && !actor.get().equals(usuarioId)) {
            return List.of();
        }
        return eventos.stream()
                .filter(e -> e.usuarioId().map(usuarioId::equals).orElse(false))
                .sorted(Comparator.comparing(Evento::fecha).reversed())
                .toList();
    }

    public List<Evento> todos() {
        return List.copyOf(eventos);
    }
}
