package pensamiento.nucleo;

import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;

/**
 * Una llave de acceso (passkey) registrada (hito 9): lo que WebAuthn necesita para verificar la próxima firma. Guarda la
 * clave pública en formato COSE y el contador de firmas; nunca una clave privada, que no sale del dispositivo.
 */
public record LlaveDeAcceso(byte[] credencialId, byte[] entidadUsuario, byte[] clavePublicaCose, long firmas, boolean uvInicializada,
                            Set<String> transportes, boolean respaldable, boolean respaldada, byte[] atestacion, byte[] datosCliente,
                            String etiqueta, Instant creada, Instant usada) {

    public LlaveDeAcceso {
        transportes = Set.copyOf(transportes);
    }

    /** La misma llave después de una firma: el contador nuevo y cuándo se usó. */
    public LlaveDeAcceso usada(long firmasNuevas, Instant cuando) {
        return new LlaveDeAcceso(credencialId, entidadUsuario, clavePublicaCose, firmasNuevas, uvInicializada, transportes, respaldable,
                respaldada, atestacion, datosCliente, etiqueta, creada, cuando);
    }

    public String credencialHex() {
        return HexFormat.of().formatHex(credencialId);
    }

    @Override
    public boolean equals(Object otro) {
        return otro instanceof LlaveDeAcceso l && Arrays.equals(credencialId, l.credencialId) && Arrays.equals(entidadUsuario, l.entidadUsuario)
                && Arrays.equals(clavePublicaCose, l.clavePublicaCose) && firmas == l.firmas && uvInicializada == l.uvInicializada
                && transportes.equals(l.transportes) && respaldable == l.respaldable && respaldada == l.respaldada
                && Arrays.equals(atestacion, l.atestacion) && Arrays.equals(datosCliente, l.datosCliente) && etiqueta.equals(l.etiqueta)
                && creada.equals(l.creada) && usada.equals(l.usada);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(credencialId), firmas, etiqueta);
    }

    @Override
    public String toString() {
        return "LlaveDeAcceso[" + credencialHex() + ", firmas=" + firmas + ", " + etiqueta + "]";
    }
}
