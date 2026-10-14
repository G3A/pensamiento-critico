package pensamiento.unidad.web.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import pensamiento.nucleo.Institucion;
import pensamiento.nucleo.Usuario;
import pensamiento.nucleo.puertos.RegistroAuditoria;
import pensamiento.testutil.TransaccionesSinBase;
import pensamiento.testutil.fakes.FakeRegistroAuditoria;
import pensamiento.testutil.fakes.FakeRegistroIntentos;
import pensamiento.testutil.fakes.FakeReloj;
import pensamiento.testutil.fakes.FakeRepositorioInstituciones;
import pensamiento.testutil.fakes.FakeRepositorioUsuarios;
import pensamiento.web.seguridad.LimiteDeIntentos;
import pensamiento.web.seguridad.ProveedorEntrada;
import pensamiento.web.seguridad.UsuarioSesion;

/**
 * Collaboration test con reloj falso (hitos 8 y 9): se entra con nombre y contraseña dentro de la institución de la ruta;
 * tras 5 fallos el sexto intento se rechaza aunque la contraseña sea correcta, con esperas de 30 s, 1 min, 5 min y 15 min;
 * cuenta igual exista o no la cuenta; cada entrada y fallo queda en la auditoría sin la contraseña.
 */
class ProveedorEntradaTest {

    private final PasswordEncoder argon2 = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private final FakeRepositorioUsuarios usuarios = new FakeRepositorioUsuarios();
    private final FakeRepositorioInstituciones instituciones = new FakeRepositorioInstituciones();
    private final FakeReloj reloj = new FakeReloj();
    private final FakeRegistroAuditoria auditoria = new FakeRegistroAuditoria();
    private final FakeRegistroIntentos intentos = new FakeRegistroIntentos();
    private final LimiteDeIntentos limite = new LimiteDeIntentos(intentos, reloj, new LimiteDeIntentos.Tope(20, Duration.ofMinutes(10)),
            new LimiteDeIntentos.Tope(40, Duration.ofMinutes(10)));
    private final ProveedorEntrada proveedor = new ProveedorEntrada(usuarios, instituciones, argon2, new TransaccionesSinBase(), limite,
            auditoria, reloj, ProveedorEntrada.EtapaInicial.SIN_LLAVE);

    private final Institucion norte = instituciones.agregar(new Institucion(UUID.randomUUID(), "Colegio Norte", "colegio-norte", Institucion.Estado.ACTIVA));
    private final Institucion sur = instituciones.agregar(new Institucion(UUID.randomUUID(), "Colegio Sur", "colegio-sur", Institucion.Estado.ACTIVA));
    private final Institucion cerrado = instituciones.agregar(new Institucion(UUID.randomUUID(), "Colegio Cerrado", "colegio-cerrado", Institucion.Estado.SUSPENDIDA));

    private Authentication entrar(String ruta, String nombre, String contrasena) {
        return entrar(ruta, nombre, contrasena, "10.0.0.7", null);
    }

    private Authentication entrar(String ruta, String nombre, String contrasena, String ip, String persona) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/sesion");
        request.setParameter(ProveedorEntrada.CAMPO_INSTITUCION, ruta);
        request.setRemoteAddr(ip);
        if (persona != null) {
            request.setParameter(ProveedorEntrada.CAMPO_PERSONA, persona);
        }
        UsernamePasswordAuthenticationToken intento = UsernamePasswordAuthenticationToken.unauthenticated(nombre, contrasena);
        intento.setDetails(proveedor.detalles().buildDetails(request));
        return proveedor.authenticate(intento);
    }

    private Usuario crear(Institucion institucion, String nombre, String contrasena) {
        return usuarios.crear(Usuario.nueva(UUID.randomUUID(), institucion.id(), nombre, Usuario.RolGlobal.PERSONA)
                .conClave(argon2.encode(contrasena), Usuario.TipoClave.CONTRASENA)).orElseThrow();
    }

    private void fallar(int veces, String nombre) {
        for (int i = 0; i < veces; i++) {
            assertThatThrownBy(() -> entrar("colegio-norte", nombre, "no es la contraseña")).isInstanceOf(BadCredentialsException.class);
        }
    }

    @Test
    void entra_con_su_nombre_sin_importar_mayusculas_ni_tildes_y_la_sesion_guarda_su_institucion_y_ruta() {
        crear(norte, "Ana María", "mi perro come mango");
        UsuarioSesion yo = (UsuarioSesion) entrar("colegio-norte", "ana maria", "mi perro come mango").getPrincipal();
        assertThat(yo.institucionId()).isEqualTo(norte.id());
        assertThat(yo.ruta()).isEqualTo("colegio-norte");
        assertThat(yo.nombre()).isEqualTo("Ana María");
        assertThat(yo.lista()).isTrue();
        assertThat(yo.getAuthorities()).extracting(Object::toString).containsExactlyInAnyOrder("FACTOR_PASSWORD", "ROLE_PERSONA");
    }

    @Test
    void la_misma_persona_no_entra_por_la_ruta_de_otra_institucion() {
        crear(norte, "Ana María", "mi perro come mango");
        assertThatThrownBy(() -> entrar("colegio-sur", "Ana María", "mi perro come mango")).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void nadie_entra_a_una_institucion_suspendida_ni_sin_ruta_ni_por_una_ruta_reservada() {
        crear(cerrado, "docente", "una frase de docente");
        assertThatThrownBy(() -> entrar("colegio-cerrado", "docente", "una frase de docente")).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> entrar("", "docente", "una frase de docente")).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> entrar("catalogo", "docente", "una frase de docente")).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void una_cuenta_sin_clave_o_desactivada_no_entra() {
        usuarios.crear(Usuario.nueva(UUID.randomUUID(), norte.id(), "sin canjear", Usuario.RolGlobal.PERSONA));
        Usuario inactiva = crear(norte, "inactiva", "una frase cualquiera");
        usuarios.cambiarActivo(norte.id(), inactiva.id(), false);
        assertThatThrownBy(() -> entrar("colegio-norte", "sin canjear", "")).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> entrar("colegio-norte", "inactiva", "una frase cualquiera")).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void el_sexto_intento_espera_30_segundos_aunque_la_contrasena_sea_correcta() {
        crear(norte, "vecino", "el vecino barre la acera");
        fallar(5, "vecino");

        assertThatThrownBy(() -> entrar("colegio-norte", "vecino", "el vecino barre la acera"))
                .isInstanceOfSatisfying(ProveedorEntrada.DemasiadosIntentos.class, e -> assertThat(e.espera()).isEqualTo(Duration.ofSeconds(30)));
        reloj.avanzar(Duration.ofSeconds(29));
        assertThatThrownBy(() -> entrar("colegio-norte", "vecino", "el vecino barre la acera"))
                .isInstanceOfSatisfying(ProveedorEntrada.DemasiadosIntentos.class, e -> assertThat(e.espera()).isEqualTo(Duration.ofSeconds(1)));
        reloj.avanzar(Duration.ofSeconds(1));
        assertThat(entrar("colegio-norte", "vecino", "el vecino barre la acera").isAuthenticated()).isTrue();
    }

    @Test
    void cada_fallo_despues_de_esperar_sube_a_1_5_y_15_minutos_y_no_pasa_de_15() {
        crear(norte, "vecino", "el vecino barre la acera");
        fallar(5, "vecino");
        for (Duration siguiente : new Duration[] {Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofMinutes(15)}) {
            reloj.avanzar(Duration.ofMinutes(15));
            fallar(1, "vecino");
            assertThat(limite.espera(norte.id(), "vecino", "10.0.0.99")).contains(siguiente);
        }
    }

    @Test
    void una_cuenta_que_no_existe_espera_igual_que_una_que_existe() {
        crear(norte, "vecino", "el vecino barre la acera");
        fallar(5, "vecino");
        fallar(5, "nadie se llama asi");
        assertThat(limite.espera(norte.id(), "nadie se llama asi", "10.0.0.99")).isEqualTo(limite.espera(norte.id(), "vecino", "10.0.0.99"))
                .contains(Duration.ofSeconds(30));
    }

    @Test
    void entrar_bien_pone_el_contador_de_la_cuenta_en_cero() {
        crear(norte, "vecino", "el vecino barre la acera");
        fallar(4, "vecino");
        entrar("colegio-norte", "vecino", "el vecino barre la acera");
        fallar(4, "vecino");
        assertThat(entrar("colegio-norte", "vecino", "el vecino barre la acera").isAuthenticated()).isTrue();
    }

    @Test
    void la_ip_de_un_salon_solo_espera_ante_una_rafaga_de_muchos_nombres() {
        for (int i = 0; i < 19; i++) {
            fallar(1, "nombre " + i);
        }
        crear(norte, "vecino", "el vecino barre la acera");
        assertThat(entrar("colegio-norte", "vecino", "el vecino barre la acera").isAuthenticated()).as("19 fallos de 19 nombres: sigue").isTrue();
        fallar(1, "nombre 20");
        assertThatThrownBy(() -> entrar("colegio-norte", "vecino", "el vecino barre la acera"))
                .as("el fallo 20 de la misma IP en 10 minutos").isInstanceOf(ProveedorEntrada.DemasiadosIntentos.class);
        assertThat(entrar("colegio-norte", "vecino", "el vecino barre la acera", "10.0.0.8", null).isAuthenticated())
                .as("desde otra IP entra").isTrue();
    }

    @Test
    void el_tope_global_por_ip_frena_a_quien_prueba_en_muchas_instituciones() {
        crear(sur, "rectora", "la rectora riega las matas");
        for (int i = 0; i < 40; i++) {
            Institucion otra = instituciones.agregar(new Institucion(UUID.randomUUID(), "Colegio " + i, "colegio-" + i, Institucion.Estado.ACTIVA));
            assertThatThrownBy(() -> entrar(otra.ruta(), "rectora", "probando", "10.9.9.9", null)).isInstanceOf(BadCredentialsException.class);
        }
        assertThatThrownBy(() -> entrar("colegio-sur", "rectora", "la rectora riega las matas", "10.9.9.9", null))
                .isInstanceOf(ProveedorEntrada.DemasiadosIntentos.class);
    }

    @Test
    void cada_entrada_fallo_y_espera_queda_en_la_auditoria_sin_la_contrasena() {
        Usuario vecino = crear(norte, "Vecino", "el vecino barre la acera");
        entrar("colegio-norte", "vecino", "el vecino barre la acera");
        fallar(5, "VECINO");
        assertThatThrownBy(() -> entrar("colegio-norte", "vecino", "el vecino barre la acera")).isInstanceOf(ProveedorEntrada.DemasiadosIntentos.class);
        fallar(1, "nadie");

        assertThat(auditoria.todos()).extracting(RegistroAuditoria.Evento::accion).containsExactly(RegistroAuditoria.Accion.ENTRAR,
                RegistroAuditoria.Accion.FALLO_ENTRAR, RegistroAuditoria.Accion.FALLO_ENTRAR, RegistroAuditoria.Accion.FALLO_ENTRAR,
                RegistroAuditoria.Accion.FALLO_ENTRAR, RegistroAuditoria.Accion.FALLO_ENTRAR, RegistroAuditoria.Accion.ESPERA_ENTRAR,
                RegistroAuditoria.Accion.FALLO_ENTRAR);
        assertThat(auditoria.todos().get(1).usuarioId()).contains(vecino.id());
        assertThat(auditoria.todos().get(1).detalle()).contains("vecino");
        assertThat(auditoria.todos().getLast().usuarioId()).as("una cuenta que no existe").isEmpty();
        assertThat(auditoria.todos()).allSatisfy(e -> {
            assertThat(e.institucionId()).isEqualTo(norte.id());
            assertThat(e.detalle().orElse("")).doesNotContain("acera", "no es la contraseña");
        });
    }

    @Test
    void en_el_overlay_solo_entra_la_misma_persona() {
        Usuario ana = crear(norte, "Ana", "mi perro come mango");
        crear(norte, "Beto", "el gato de beto duerme");
        assertThatThrownBy(() -> entrar("colegio-norte", "Beto", "el gato de beto duerme", "10.0.0.7", ana.id().toString()))
                .isInstanceOf(ProveedorEntrada.OtraPersona.class);
        assertThat(entrar("colegio-norte", "ana", "mi perro come mango", "10.0.0.7", ana.id().toString()).isAuthenticated()).isTrue();
    }

    @Test
    void quien_entra_con_su_pin_viejo_queda_en_la_etapa_de_elegir_contrasena_sin_roles_de_la_app() {
        usuarios.crear(Usuario.nueva(UUID.randomUUID(), norte.id(), "administrador", Usuario.RolGlobal.ADMINISTRADOR)
                .conClave(argon2.encode("2468"), Usuario.TipoClave.PIN));
        UsuarioSesion yo = (UsuarioSesion) entrar("colegio-norte", "administrador", "2468").getPrincipal();
        assertThat(yo.etapa()).isEqualTo(UsuarioSesion.Etapa.ELEGIR_CONTRASENA);
        assertThat(yo.getAuthorities()).extracting(Object::toString).containsExactlyInAnyOrder("FACTOR_PASSWORD", "ROLE_PENDIENTE");
    }
}
