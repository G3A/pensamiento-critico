package pensamiento.testutil.fakes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import pensamiento.nucleo.puertos.RegistroAuditoria;

/** Auditoría en memoria: solo inserción. Certificado por FakeRegistroAuditoriaContractTest. */
public final class FakeRegistroAuditoria implements RegistroAuditoria {

    private final List<Evento> eventos = new ArrayList<>();

    @Override
    public void registrar(Evento evento) {
        eventos.add(evento);
    }

    @Override
    public List<Evento> deUsuario(UUID usuarioId) {
        return eventos.stream()
                .filter(e -> e.usuarioId().map(usuarioId::equals).orElse(false))
                .sorted(Comparator.comparing(Evento::fecha).reversed())
                .toList();
    }

    public List<Evento> todos() {
        return List.copyOf(eventos);
    }
}
