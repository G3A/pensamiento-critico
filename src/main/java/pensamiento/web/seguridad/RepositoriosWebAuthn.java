package pensamiento.web.seguridad;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.security.web.webauthn.api.AuthenticatorTransport;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutableCredentialRecord;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCose;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialType;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;

import pensamiento.nucleo.LlaveDeAcceso;
import pensamiento.nucleo.Titular;
import pensamiento.nucleo.puertos.RepositorioLlaves;

/**
 * Los repositorios que pide el soporte de WebAuthn de Spring Security, sobre el puerto RepositorioLlaves (hito 9). Solo
 * responden por el titular de la operación en curso (ServicioLlaves lo fija en un ThreadLocal): nadie encuentra la llave
 * de otro. La entidad de usuario de WebAuthn no se guarda: se deriva del titular (17 bytes: el tipo y su UUID).
 */
public final class RepositoriosWebAuthn implements UserCredentialRepository, PublicKeyCredentialUserEntityRepository {

    /** El titular y el nombre que el autenticador muestra, mientras dura una operación de ServicioLlaves. */
    record EnCurso(Titular titular, String nombreVisible) {
    }

    private static final ThreadLocal<EnCurso> EN_CURSO = new ThreadLocal<>();

    private final RepositorioLlaves llaves;

    public RepositoriosWebAuthn(RepositorioLlaves llaves) {
        this.llaves = llaves;
    }

    static <T> T con(Titular titular, String nombreVisible, java.util.function.Supplier<T> accion) {
        EN_CURSO.set(new EnCurso(titular, nombreVisible));
        try {
            return accion.get();
        } finally {
            EN_CURSO.remove();
        }
    }

    /** El "user handle" de WebAuthn de un titular: 1 byte de tipo más los 16 de su id. */
    public static byte[] entidad(Titular titular) {
        return ByteBuffer.allocate(17).put((byte) (titular.tipo() == Titular.Tipo.CUENTA ? 1 : 2))
                .putLong(titular.id().getMostSignificantBits()).putLong(titular.id().getLeastSignificantBits()).array();
    }

    private static Optional<EnCurso> enCurso() {
        return Optional.ofNullable(EN_CURSO.get());
    }

    private static PublicKeyCredentialUserEntity entidadDe(EnCurso e) {
        return ImmutablePublicKeyCredentialUserEntity.builder().name(e.titular().nombreParaLlave()).id(new Bytes(entidad(e.titular())))
                .displayName(e.nombreVisible()).build();
    }

    @Override
    public PublicKeyCredentialUserEntity findById(Bytes id) {
        return enCurso().filter(e -> Arrays.equals(entidad(e.titular()), id.getBytes())).map(RepositoriosWebAuthn::entidadDe).orElse(null);
    }

    @Override
    public PublicKeyCredentialUserEntity findByUsername(String nombre) {
        return enCurso().filter(e -> e.titular().nombreParaLlave().equals(nombre)).map(RepositoriosWebAuthn::entidadDe).orElse(null);
    }

    @Override
    public void save(PublicKeyCredentialUserEntity entidad) {
        // Se deriva del titular: no hay nada que guardar.
    }

    @Override
    public void save(CredentialRecord r) {
        EnCurso e = enCurso().orElseThrow(() -> new IllegalStateException("Sin titular en curso"));
        if (!Arrays.equals(entidad(e.titular()), r.getUserEntityUserId().getBytes())
                || !llaves.guardar(e.titular(), aLlave(r))) {
            throw new IllegalStateException("La llave no es de este titular");
        }
    }

    @Override
    public CredentialRecord findByCredentialId(Bytes credencial) {
        return enCurso().flatMap(e -> llaves.porCredencial(e.titular(), credencial.getBytes())).map(RepositoriosWebAuthn::aRegistro).orElse(null);
    }

    @Override
    public List<CredentialRecord> findByUserId(Bytes usuario) {
        return enCurso().filter(e -> Arrays.equals(entidad(e.titular()), usuario.getBytes()))
                .map(e -> llaves.de(e.titular()).stream().map(RepositoriosWebAuthn::aRegistro).toList()).orElse(List.of());
    }

    /**
     * Las dos interfaces de Spring declaran delete(Bytes): la de entidades (que aquí no se guardan) y la de credenciales.
     * Solo se borra una credencial del titular en curso; el id de la entidad no coincide con ninguna credencial.
     */
    @Override
    public void delete(Bytes credencial) {
        enCurso().ifPresent(e -> llaves.borrar(e.titular(), credencial.getBytes()));
    }

    static LlaveDeAcceso aLlave(CredentialRecord r) {
        return new LlaveDeAcceso(r.getCredentialId().getBytes(), r.getUserEntityUserId().getBytes(), r.getPublicKey().getBytes(),
                r.getSignatureCount(), r.isUvInitialized(),
                r.getTransports() == null ? java.util.Set.of() : r.getTransports().stream().map(AuthenticatorTransport::getValue).collect(Collectors.toSet()),
                r.isBackupEligible(), r.isBackupState(), bytes(r.getAttestationObject()), bytes(r.getAttestationClientDataJSON()),
                r.getLabel() == null || r.getLabel().isBlank() ? "Llave de acceso" : r.getLabel(), r.getCreated(), r.getLastUsed());
    }

    static CredentialRecord aRegistro(LlaveDeAcceso l) {
        return ImmutableCredentialRecord.builder()
                .credentialType(PublicKeyCredentialType.PUBLIC_KEY)
                .credentialId(new Bytes(l.credencialId()))
                .userEntityUserId(new Bytes(l.entidadUsuario()))
                .publicKey(new ImmutablePublicKeyCose(l.clavePublicaCose()))
                .signatureCount(l.firmas())
                .uvInitialized(l.uvInicializada())
                .transports(l.transportes().stream().map(AuthenticatorTransport::valueOf).collect(Collectors.toSet()))
                .backupEligible(l.respaldable())
                .backupState(l.respaldada())
                .attestationObject(l.atestacion() == null ? null : new Bytes(l.atestacion()))
                .attestationClientDataJSON(l.datosCliente() == null ? null : new Bytes(l.datosCliente()))
                .label(l.etiqueta())
                .created(l.creada())
                .lastUsed(l.usada())
                .build();
    }

    private static byte[] bytes(Bytes b) {
        return b == null ? null : b.getBytes();
    }
}
