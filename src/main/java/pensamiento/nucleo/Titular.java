package pensamiento.nucleo;

import java.util.Optional;
import java.util.UUID;

/**
 * De quién es una llave de acceso o un código de recuperación (hito 9): una cuenta de una institución o el operador del
 * servicio, que no pertenece a ninguna.
 */
public record Titular(Tipo tipo, UUID id, Optional<UUID> institucion) {

    public enum Tipo { CUENTA, OPERADOR }

    public static Titular cuenta(UUID usuario, UUID institucion) {
        return new Titular(Tipo.CUENTA, usuario, Optional.of(institucion));
    }

    public static Titular operador(UUID operador) {
        return new Titular(Tipo.OPERADOR, operador, Optional.empty());
    }

    public Optional<UUID> usuario() {
        return tipo == Tipo.CUENTA ? Optional.of(id) : Optional.empty();
    }

    public Optional<UUID> operador() {
        return tipo == Tipo.OPERADOR ? Optional.of(id) : Optional.empty();
    }

    /** El nombre único con que WebAuthn conoce a este titular ("c:" o "o:" más el id); no se le muestra a nadie. */
    public String nombreParaLlave() {
        return (tipo == Tipo.CUENTA ? "c:" : "o:") + id;
    }
}
