package pensamiento.unidad.web.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.webauthn.api.AuthenticatorAssertionResponse;
import org.springframework.security.web.webauthn.api.PublicKeyCredential;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialCreationOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRequestOptions;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.jackson.WebauthnJacksonModule;
import org.springframework.security.web.webauthn.management.RelyingPartyPublicKey;
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import pensamiento.nucleo.Titular;
import pensamiento.testutil.AutenticadorDePrueba;
import pensamiento.testutil.fakes.FakeReloj;
import pensamiento.testutil.fakes.FakeRepositorioLlaves;
import pensamiento.web.seguridad.RepositoriosWebAuthn;
import pensamiento.web.seguridad.ServicioLlaves;

/**
 * Collaboration test de la llave de acceso (hito 9) con la verificación real de Spring Security 7 (webauthn4j) y el puerto
 * en Fake: se registra con un autenticador de software, se confirma con su firma, la llave de otra persona no sirve, y los
 * 10 códigos de recuperación sirven una sola vez cada uno y se guardan solo como hash.
 */
class ServicioLlavesTest {

    private final JsonMapper json = JsonMapper.builder().addModule(new WebauthnJacksonModule()).build();
    private final PasswordEncoder argon2 = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private final FakeRepositorioLlaves llaves = new FakeRepositorioLlaves().aceptarCualquierTitular();
    private final RepositoriosWebAuthn repositorios = new RepositoriosWebAuthn(llaves);
    private final Webauthn4JRelyingPartyOperations operaciones = new Webauthn4JRelyingPartyOperations(repositorios, repositorios,
            PublicKeyCredentialRpEntity.builder().id("localhost").name("Taller").build(), Set.of("http://localhost:8080"));
    /** Azar con semilla fija: los códigos de recuperación salen siempre iguales en cada corrida. */
    private final java.util.Random azar = new java.util.Random(42);
    private final ServicioLlaves servicio = new ServicioLlaves(llaves, operaciones, argon2, new FakeReloj(), azar::nextInt);

    private final UUID colegio = UUID.fromString("0192f000-0000-7000-8000-00000000c01e");
    private final Titular rectora = Titular.cuenta(UUID.randomUUID(), colegio);
    private final Titular coordinador = Titular.cuenta(UUID.randomUUID(), colegio);

    private List<String> registrar(Titular titular, AutenticadorDePrueba llave) {
        PublicKeyCredentialCreationOptions opciones = servicio.opcionesDeRegistro(titular, "Rectora");
        RelyingPartyPublicKey respuesta = json.readValue(llave.registrar(json.writeValueAsString(opciones)), RelyingPartyPublicKey.class);
        return servicio.registrar(titular, "Rectora", opciones, respuesta);
    }

    private boolean confirmar(Titular titular, AutenticadorDePrueba llave) {
        PublicKeyCredentialRequestOptions opciones = servicio.opcionesDeConfirmacion(titular, "Rectora");
        PublicKeyCredential<AuthenticatorAssertionResponse> firma = json.readValue(llave.firmar(json.writeValueAsString(opciones)),
                new TypeReference<PublicKeyCredential<AuthenticatorAssertionResponse>>() { });
        return servicio.confirmar(titular, "Rectora", opciones, firma);
    }

    @Test
    void registrar_la_llave_la_deja_guardada_y_da_10_codigos_de_recuperacion() {
        AutenticadorDePrueba celular = new AutenticadorDePrueba();
        assertThat(servicio.tieneLlave(rectora)).isFalse();

        List<String> codigos = registrar(rectora, celular);

        assertThat(servicio.tieneLlave(rectora)).isTrue();
        assertThat(llaves.de(rectora)).singleElement().satisfies(l -> {
            assertThat(l.etiqueta()).isEqualTo("Autenticador de prueba");
            assertThat(l.entidadUsuario()).containsExactly(RepositoriosWebAuthn.entidad(rectora));
        });
        assertThat(codigos).hasSize(10).doesNotHaveDuplicates().allMatch(c -> c.matches("[0-9A-HJKMNP-TV-Z]{5}-[0-9A-HJKMNP-TV-Z]{5}"));
        assertThat(llaves.recuperacionVigente(rectora)).hasSize(10).allSatisfy(c -> assertThat(c.hash()).startsWith("$argon2"));
    }

    @Test
    void la_firma_de_su_llave_confirma_y_sube_el_contador() {
        AutenticadorDePrueba celular = new AutenticadorDePrueba();
        registrar(rectora, celular);

        assertThat(confirmar(rectora, celular)).isTrue();
        assertThat(confirmar(rectora, celular)).isTrue();
        assertThat(llaves.de(rectora).getFirst().firmas()).isEqualTo(2);
    }

    @Test
    void otra_llave_o_la_llave_de_otra_persona_no_confirman() {
        AutenticadorDePrueba deLaRectora = new AutenticadorDePrueba();
        AutenticadorDePrueba delCoordinador = new AutenticadorDePrueba();
        registrar(rectora, deLaRectora);
        registrar(coordinador, delCoordinador);

        assertThat(confirmar(rectora, delCoordinador)).as("la llave del coordinador para la rectora").isFalse();
        assertThat(confirmar(rectora, new AutenticadorDePrueba())).as("una llave que nadie registró").isFalse();
        assertThat(confirmar(coordinador, delCoordinador)).isTrue();
    }

    @Test
    void una_firma_de_otro_origen_no_confirma() {
        AutenticadorDePrueba impostor = new AutenticadorDePrueba("localhost", "http://sitio-falso.example");
        registrar(rectora, new AutenticadorDePrueba());
        assertThat(confirmar(rectora, impostor)).isFalse();
    }

    @Test
    void cada_codigo_de_recuperacion_sirve_una_sola_vez_y_no_sirve_para_otra_persona() {
        List<String> codigos = registrar(rectora, new AutenticadorDePrueba());
        registrar(coordinador, new AutenticadorDePrueba());

        assertThat(servicio.usarRecuperacion(coordinador, codigos.getFirst())).isFalse();
        assertThat(servicio.usarRecuperacion(rectora, codigos.getFirst().toLowerCase())).isTrue();
        assertThat(servicio.usarRecuperacion(rectora, codigos.getFirst())).isFalse();
        assertThat(servicio.usarRecuperacion(rectora, "no es un código")).isFalse();
        assertThat(llaves.recuperacionVigente(rectora)).hasSize(9);
    }

    @Test
    void registrar_otra_llave_reemplaza_los_codigos_de_recuperacion() {
        List<String> primeros = registrar(rectora, new AutenticadorDePrueba());
        List<String> segundos = registrar(rectora, new AutenticadorDePrueba());
        assertThat(llaves.de(rectora)).hasSize(2);
        assertThat(servicio.usarRecuperacion(rectora, primeros.get(3))).isFalse();
        assertThat(servicio.usarRecuperacion(rectora, segundos.get(3))).isTrue();
    }
}
