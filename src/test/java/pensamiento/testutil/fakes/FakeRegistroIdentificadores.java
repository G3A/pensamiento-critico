package pensamiento.testutil.fakes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import pensamiento.nucleo.puertos.RegistroIdentificadores;

/** Dueños de identificadores en memoria. Certificado por FakeRegistroIdentificadoresContractTest. */
public final class FakeRegistroIdentificadores implements RegistroIdentificadores {

    private final Map<UUID, UUID> duenos = new HashMap<>();

    /** Registra que el identificador ya existe y es de ese usuario. */
    public void existe(UUID id, UUID dueno) {
        duenos.put(id, dueno);
    }

    @Override
    public boolean deOtroUsuario(UUID usuarioId, UUID id) {
        UUID dueno = duenos.get(id);
        return dueno != null && !dueno.equals(usuarioId);
    }
}
