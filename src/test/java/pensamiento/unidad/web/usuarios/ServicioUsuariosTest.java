package pensamiento.unidad.web.usuarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import pensamiento.nucleo.Usuario;
import pensamiento.nucleo.puertos.RegistroAuditoria;
import pensamiento.nucleo.puertos.RepositorioUsuarios;
import pensamiento.testutil.fakes.FakeRegistroAuditoria;
import pensamiento.testutil.fakes.FakeReloj;
import pensamiento.testutil.fakes.FakeRepositorioUsuarios;
import pensamiento.web.usuarios.ServicioUsuarios;

/** Collaboration test de ServicioUsuarios: PIN obligatorio con Argon2, sin autoregistro, auditoría de crear y desactivar. */
class ServicioUsuariosTest {

    private final FakeRepositorioUsuarios usuarios = new FakeRepositorioUsuarios();
    private final FakeRegistroAuditoria auditoria = new FakeRegistroAuditoria();
    private final PasswordEncoder argon2 = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private final FakeReloj reloj = new FakeReloj();
    private final ServicioUsuarios servicio = new ServicioUsuarios(usuarios, auditoria, argon2, reloj);

    @Test
    void el_primer_arranque_crea_la_institucion_y_la_cuenta_administrador_con_el_pin_hasheado() {
        Optional<Usuario> admin = servicio.inicializar("2468");

        assertThat(admin).isPresent();
        assertThat(usuarios.institucionUnica()).isPresent();
        assertThat(admin.get().esAdministrador()).isTrue();
        assertThat(admin.get().pinHash()).startsWith("$argon2");
        assertThat(argon2.matches("2468", admin.get().pinHash())).isTrue();
        assertThat(auditoria.todos()).singleElement().satisfies(e -> {
            assertThat(e.accion()).isEqualTo(RegistroAuditoria.Accion.CREAR);
            assertThat(e.objetoTipo()).isEqualTo("usuario");
            assertThat(e.fecha()).isEqualTo(FakeReloj.INSTANTE_FIJO);
        });
    }

    @Test
    void inicializar_dos_veces_no_crea_una_segunda_cuenta() {
        servicio.inicializar("2468");
        assertThat(servicio.inicializar("1357")).isEmpty();
        assertThat(usuarios.contar(usuarios.institucionUnica().get())).isEqualTo(1);
    }

    @Test
    void el_administrador_crea_una_persona_y_queda_en_auditoria_con_quien_la_creo() {
        Usuario admin = servicio.inicializar("2468").get();
        UUID institucion = admin.institucionId();

        Usuario persona = servicio.crearPersona(admin.id(), institucion, "  perfil A ", "1234");

        assertThat(persona.nombre()).isEqualTo("perfil A");
        assertThat(persona.rol()).isEqualTo(Usuario.RolGlobal.PERSONA);
        assertThat(usuarios.porNombre(institucion, "perfil A")).isPresent();
        assertThat(auditoria.deUsuario(admin.id())).singleElement()
                .satisfies(e -> assertThat(e.objetoId()).contains(persona.id()));
    }

    @ParameterizedTest(name = "PIN inválido: \"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"123", "123456789", "abcd", "12 34"})
    void no_existe_cuenta_sin_pin_de_4_a_8_digitos(String pin) {
        Usuario admin = servicio.inicializar("2468").get();
        assertThatThrownBy(() -> servicio.crearPersona(admin.id(), admin.institucionId(), "perfil B", pin))
                .isInstanceOf(ServicioUsuarios.DatosInvalidos.class)
                .hasMessageContaining("PIN");
        assertThat(usuarios.contar(admin.institucionId())).isEqualTo(1);
    }

    @Test
    void dos_personas_no_pueden_llamarse_igual() {
        Usuario admin = servicio.inicializar("2468").get();
        servicio.crearPersona(admin.id(), admin.institucionId(), "perfil A", "1234");
        assertThatThrownBy(() -> servicio.crearPersona(admin.id(), admin.institucionId(), "perfil A", "9999"))
                .isInstanceOf(RepositorioUsuarios.NombreRepetido.class);
    }

    @Test
    void desactivar_deja_la_cuenta_inactiva_y_lo_registra_en_auditoria() {
        Usuario admin = servicio.inicializar("2468").get();
        Usuario persona = servicio.crearPersona(admin.id(), admin.institucionId(), "perfil A", "1234");

        Usuario desactivada = servicio.desactivar(admin.id(), admin.institucionId(), persona.id());

        assertThat(desactivada.activo()).isFalse();
        assertThat(usuarios.porId(admin.institucionId(), persona.id())).map(Usuario::activo).contains(false);
        assertThat(auditoria.deUsuario(admin.id())).extracting(RegistroAuditoria.Evento::accion)
                .contains(RegistroAuditoria.Accion.DESACTIVAR);
    }

    @Test
    void el_administrador_no_puede_desactivarse_a_si_mismo() {
        Usuario admin = servicio.inicializar("2468").get();
        assertThatThrownBy(() -> servicio.desactivar(admin.id(), admin.institucionId(), admin.id()))
                .isInstanceOf(ServicioUsuarios.DatosInvalidos.class);
        assertThat(usuarios.porId(admin.institucionId(), admin.id())).map(Usuario::activo).contains(true);
    }
}
