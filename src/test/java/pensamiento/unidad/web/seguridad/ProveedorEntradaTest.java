package pensamiento.unidad.web.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import pensamiento.testutil.TransaccionesSinBase;
import pensamiento.testutil.fakes.FakeRepositorioInstituciones;
import pensamiento.testutil.fakes.FakeRepositorioUsuarios;
import pensamiento.web.seguridad.ProveedorEntrada;
import pensamiento.web.seguridad.UsuarioSesion;

/** Collaboration test: se entra con nombre y PIN dentro de la institución de la ruta, nunca en otra (hito 8). */
class ProveedorEntradaTest {

    private final PasswordEncoder argon2 = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private final FakeRepositorioUsuarios usuarios = new FakeRepositorioUsuarios();
    private final FakeRepositorioInstituciones instituciones = new FakeRepositorioInstituciones();
    private final ProveedorEntrada proveedor = new ProveedorEntrada(usuarios, instituciones, argon2, new TransaccionesSinBase());

    private final Institucion norte = instituciones.agregar(new Institucion(UUID.randomUUID(), "Colegio Norte", "colegio-norte", Institucion.Estado.ACTIVA));
    private final Institucion sur = instituciones.agregar(new Institucion(UUID.randomUUID(), "Colegio Sur", "colegio-sur", Institucion.Estado.ACTIVA));
    private final Institucion cerrado = instituciones.agregar(new Institucion(UUID.randomUUID(), "Colegio Cerrado", "colegio-cerrado", Institucion.Estado.SUSPENDIDA));

    private Authentication entrar(String ruta, String nombre, String pin) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/sesion");
        request.setParameter(ProveedorEntrada.CAMPO_INSTITUCION, ruta);
        UsernamePasswordAuthenticationToken intento = UsernamePasswordAuthenticationToken.unauthenticated(nombre, pin);
        intento.setDetails(proveedor.detalles().buildDetails(request));
        return proveedor.authenticate(intento);
    }

    private void crear(Institucion institucion, String nombre, String pin) {
        usuarios.guardar(new Usuario(UUID.randomUUID(), institucion.id(), nombre, argon2.encode(pin), Usuario.RolGlobal.PERSONA, true));
    }

    @Test
    void entra_con_su_nombre_sin_importar_mayusculas_ni_tildes_y_la_sesion_guarda_su_institucion_y_ruta() {
        crear(norte, "Ana María", "1357");
        Authentication sesion = entrar("colegio-norte", "ana maria", "1357");
        UsuarioSesion yo = (UsuarioSesion) sesion.getPrincipal();
        assertThat(yo.institucionId()).isEqualTo(norte.id());
        assertThat(yo.ruta()).isEqualTo("colegio-norte");
        assertThat(yo.nombre()).isEqualTo("Ana María");
    }

    @Test
    void la_misma_persona_no_entra_por_la_ruta_de_otra_institucion() {
        crear(norte, "Ana María", "1357");
        assertThatThrownBy(() -> entrar("colegio-sur", "Ana María", "1357")).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void con_el_mismo_nombre_en_dos_instituciones_cada_ruta_encuentra_a_la_suya() {
        crear(norte, "rectora", "1111");
        crear(sur, "rectora", "2222");
        assertThat(((UsuarioSesion) entrar("colegio-sur", "rectora", "2222").getPrincipal()).institucionId()).isEqualTo(sur.id());
        assertThatThrownBy(() -> entrar("colegio-sur", "rectora", "1111")).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void nadie_entra_a_una_institucion_suspendida_ni_sin_ruta_ni_por_una_ruta_reservada() {
        crear(cerrado, "docente", "1357");
        assertThatThrownBy(() -> entrar("colegio-cerrado", "docente", "1357")).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> entrar("", "docente", "1357")).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> entrar("catalogo", "docente", "1357")).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void un_pin_equivocado_o_una_cuenta_desactivada_no_entran() {
        crear(norte, "vecino", "2468");
        usuarios.guardar(new Usuario(UUID.randomUUID(), norte.id(), "inactiva", argon2.encode("2468"), Usuario.RolGlobal.PERSONA, false));
        assertThatThrownBy(() -> entrar("colegio-norte", "vecino", "0000")).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> entrar("colegio-norte", "inactiva", "2468")).isInstanceOf(BadCredentialsException.class);
    }
}
