package pensamiento.web.seguridad;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.IntUnaryOperator;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.webauthn.api.AuthenticatorAssertionResponse;
import org.springframework.security.web.webauthn.api.PublicKeyCredential;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.ImmutablePublicKeyCredentialCreationOptionsRequest;
import org.springframework.security.web.webauthn.management.ImmutablePublicKeyCredentialRequestOptionsRequest;
import org.springframework.security.web.webauthn.management.ImmutableRelyingPartyRegistrationRequest;
import org.springframework.security.web.webauthn.management.RelyingPartyAuthenticationRequest;
import org.springframework.security.web.webauthn.management.RelyingPartyPublicKey;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;

import pensamiento.nucleo.Titular;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.nucleo.puertos.RepositorioLlaves;
import pensamiento.web.usuarios.CodigoDeUnUso;

/**
 * Llave de acceso como segundo factor de los adultos (hito 9). La verificación criptográfica la hace el soporte de WebAuthn
 * de Spring Security 7 (Webauthn4JRelyingPartyOperations, con webauthn4j); este servicio decide de quién es cada operación,
 * genera los códigos de recuperación de un solo uso (10, guardados solo como hash Argon2) y los verifica. Las llamadas a
 * los repositorios corren dentro de la transacción del llamador, con la institución de la sesión fijada.
 */
public class ServicioLlaves {

    public static final int CODIGOS_DE_RECUPERACION = 10;
    public static final int LARGO_RECUPERACION = 10;

    private final RepositorioLlaves llaves;
    private final WebAuthnRelyingPartyOperations operaciones;
    private final PasswordEncoder codificador;
    private final Reloj reloj;
    private final IntUnaryOperator azar;

    public ServicioLlaves(RepositorioLlaves llaves, WebAuthnRelyingPartyOperations operaciones, PasswordEncoder codificador, Reloj reloj,
                          IntUnaryOperator azar) {
        this.llaves = llaves;
        this.operaciones = operaciones;
        this.codificador = codificador;
        this.reloj = reloj;
        this.azar = azar;
    }

    public boolean tieneLlave(Titular titular) {
        return !llaves.de(titular).isEmpty();
    }

    public List<pensamiento.nucleo.LlaveDeAcceso> llaves(Titular titular) {
        return llaves.de(titular);
    }

    public PublicKeyCredentialCreationOptions opcionesDeRegistro(Titular titular, String nombreVisible) {
        return RepositoriosWebAuthn.con(titular, nombreVisible, () ->
                operaciones.createPublicKeyCredentialCreationOptions(new ImmutablePublicKeyCredentialCreationOptionsRequest(autenticacion(titular))));
    }

    /** Verifica y guarda la llave nueva; devuelve los códigos de recuperación nuevos, en claro, para mostrarlos una sola vez. */
    public List<String> registrar(Titular titular, String nombreVisible, PublicKeyCredentialCreationOptions opciones, RelyingPartyPublicKey llave) {
        RepositoriosWebAuthn.con(titular, nombreVisible, () ->
                operaciones.registerCredential(new ImmutableRelyingPartyRegistrationRequest(opciones, llave)));
        List<String> codigos = new ArrayList<>();
        List<String> hashes = new ArrayList<>();
        for (int i = 0; i < CODIGOS_DE_RECUPERACION; i++) {
            String codigo = codigoDeRecuperacion();
            codigos.add(codigo.substring(0, LARGO_RECUPERACION / 2) + "-" + codigo.substring(LARGO_RECUPERACION / 2));
            hashes.add(codificador.encode(codigo));
        }
        llaves.reemplazarRecuperacion(titular, hashes);
        return codigos;
    }

    public PublicKeyCredentialRequestOptions opcionesDeConfirmacion(Titular titular, String nombreVisible) {
        return RepositoriosWebAuthn.con(titular, nombreVisible, () ->
                operaciones.createCredentialRequestOptions(new ImmutablePublicKeyCredentialRequestOptionsRequest(autenticacion(titular))));
    }

    /** true si la firma es válida y de una llave de este titular. */
    public boolean confirmar(Titular titular, String nombreVisible, PublicKeyCredentialRequestOptions opciones,
                             PublicKeyCredential<AuthenticatorAssertionResponse> firma) {
        try {
            PublicKeyCredentialUserEntity quien = RepositoriosWebAuthn.con(titular, nombreVisible, () ->
                    operaciones.authenticate(new RelyingPartyAuthenticationRequest(opciones, firma)));
            return quien != null && titular.nombreParaLlave().equals(quien.getName());
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Gasta un código de recuperación de este titular; true la primera vez que se usa uno válido. */
    public boolean usarRecuperacion(Titular titular, String escrito) {
        String codigo = escrito == null ? "" : CodigoDeUnUso.normalizarLargo(escrito, LARGO_RECUPERACION);
        if (codigo.isEmpty()) {
            return false;
        }
        Optional<RepositorioLlaves.CodigoDeRecuperacion> vigente = llaves.recuperacionVigente(titular).stream()
                .filter(c -> codificador.matches(codigo, c.hash())).findFirst();
        return vigente.isPresent() && llaves.usarRecuperacion(titular, vigente.get().id(), reloj.ahora());
    }

    private String codigoDeRecuperacion() {
        StringBuilder codigo = new StringBuilder(LARGO_RECUPERACION);
        for (int i = 0; i < LARGO_RECUPERACION; i++) {
            codigo.append(CodigoDeUnUso.ALFABETO.charAt(azar.applyAsInt(CodigoDeUnUso.ALFABETO.length())));
        }
        return codigo.toString();
    }

    private static Authentication autenticacion(Titular titular) {
        return UsernamePasswordAuthenticationToken.authenticated(titular.nombreParaLlave(), null, List.of());
    }
}
