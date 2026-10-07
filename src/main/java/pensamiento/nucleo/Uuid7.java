package pensamiento.nucleo;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/**
 * Identificadores uuidv7 (RFC 9562): 48 bits de milisegundos desde la época y 74 bits aleatorios. Ordenan por
 * fecha de creación, igual que uuidv7() de PostgreSQL 18, y no chocan al importar un respaldo en otra instalación.
 */
public final class Uuid7 {

    private static final SecureRandom AZAR = new SecureRandom();

    private Uuid7() {
    }

    public static UUID en(Instant instante) {
        long milis = instante.toEpochMilli();
        long alto = (milis << 16) | 0x7000L | (AZAR.nextInt() & 0x0FFFL);
        long bajo = (AZAR.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(alto, bajo);
    }
}
