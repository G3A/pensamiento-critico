package pensamiento.unidad.web.operador;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import pensamiento.nucleo.Institucion;
import pensamiento.nucleo.Usuario;
import pensamiento.nucleo.puertos.RepositorioOperador;
import pensamiento.testutil.fakes.FakeRegistroAuditoria;
import pensamiento.testutil.fakes.FakeReloj;
import pensamiento.testutil.fakes.FakeRepositorioInstituciones;
import pensamiento.testutil.fakes.FakeRepositorioOperador;
import pensamiento.testutil.fakes.FakeRepositorioUsuarios;
import pensamiento.web.usuarios.CodigoDeUnUso;
import pensamiento.web.operador.ServicioOperador;
import pensamiento.web.usuarios.PoliticaDeContrasenas;
import pensamiento.web.usuarios.ServicioUsuarios;
import pensamiento.web.seguridad.LimiteDeIntentos;
import pensamiento.testutil.TransaccionesSinBase;
import pensamiento.testutil.fakes.FakeRegistroIntentos;

/** Collaboration test del operador: crear, suspender, invitar y canjear la invitación del primer administrador (hito 8). */
class ServicioOperadorTest {

    private final PasswordEncoder argon2 = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private final FakeReloj reloj = new FakeReloj();
    private final FakeRepositorioInstituciones instituciones = new FakeRepositorioInstituciones();
    private final FakeRepositorioUsuarios usuarios = new FakeRepositorioUsuarios();
    private final FakeRepositorioOperador operador = new FakeRepositorioOperador(instituciones, usuarios, reloj);
    /** Azar fijo: el código sale con los índices 7, 8, 9… del alfabeto ("789ABCDE"), luego "FGHJKMNP". */
    private final AtomicInteger siguiente = new AtomicInteger(7);
    private final ServicioOperador servicio = new ServicioOperador(operador, instituciones,
            new ServicioUsuarios(usuarios, new FakeRegistroAuditoria(), argon2, reloj, new TransaccionesSinBase(),
                    new PoliticaDeContrasenas(java.util.Set.of()), CodigoDeUnUso.aleatorio(), id -> { },
                    new LimiteDeIntentos(new FakeRegistroIntentos(), reloj, new LimiteDeIntentos.Tope(300, Duration.ofMinutes(10)),
                            new LimiteDeIntentos.Tope(1000, Duration.ofMinutes(10)))), argon2, reloj,
            new CodigoDeUnUso(n -> siguiente.getAndIncrement() % n));

    private UUID yo() {
        servicio.crearOperadorSiNoHay("una clave larga del despliegue");
        return operador.porNombre(ServicioOperador.NOMBRE_OPERADOR).orElseThrow().id();
    }

    @Test
    void el_operador_se_crea_con_la_clave_hasheada_y_una_sola_vez() {
        assertThat(servicio.crearOperadorSiNoHay("una clave larga del despliegue")).isTrue();
        assertThat(servicio.crearOperadorSiNoHay("otra clave larga distinta")).isFalse();
        String hash = operador.porNombre("operador").orElseThrow().claveHash();
        assertThat(hash).startsWith("$argon2");
        assertThat(argon2.matches("una clave larga del despliegue", hash)).isTrue();
    }

    @Test
    void una_clave_de_menos_de_12_caracteres_no_crea_el_operador() {
        assertThatThrownBy(() -> servicio.crearOperadorSiNoHay("corta")).isInstanceOf(ServicioOperador.DatosInvalidos.class);
        assertThat(operador.porNombre("operador")).isEmpty();
    }

    @Test
    void crear_una_institucion_valida_la_ruta_y_no_acepta_rutas_de_la_app_ni_repetidas() {
        UUID yo = yo();
        UUID id = servicio.crearInstitucion(yo, "  Colegio del Norte ", " colegio-del-norte ");
        assertThat(instituciones.activaPorRuta("colegio-del-norte")).contains(id);
        assertThat(instituciones.porId(id)).map(Institucion::nombre).contains("Colegio del Norte");
        assertThatThrownBy(() -> servicio.crearInstitucion(yo, "Otro", "colegio-del-norte")).isInstanceOf(RepositorioOperador.RutaRepetida.class);
        assertThatThrownBy(() -> servicio.crearInstitucion(yo, "Catálogo", "catalogo")).isInstanceOf(ServicioOperador.DatosInvalidos.class);
        assertThatThrownBy(() -> servicio.crearInstitucion(yo, "Con tilde", "colegió")).isInstanceOf(ServicioOperador.DatosInvalidos.class);
        assertThatThrownBy(() -> servicio.crearInstitucion(yo, "", "sin-nombre")).isInstanceOf(ServicioOperador.DatosInvalidos.class);
    }

    @Test
    void la_invitacion_se_guarda_solo_como_hash_y_caduca_a_los_7_dias() {
        UUID yo = yo();
        UUID id = servicio.crearInstitucion(yo, "Colegio", "colegio");
        ServicioOperador.Invitacion invitacion = servicio.invitar(yo, id);
        assertThat(invitacion.codigo()).isEqualTo("789ABCDE");
        assertThat(invitacion.legible()).isEqualTo("789A-BCDE");
        assertThat(invitacion.caducaEn()).isEqualTo(FakeReloj.INSTANTE_FIJO.plus(Duration.ofDays(7)));
        assertThat(operador.vigentes(id)).singleElement().satisfies(v -> {
            assertThat(v.codigoHash()).doesNotContain("789ABCDE").startsWith("$argon2");
            assertThat(argon2.matches("789ABCDE", v.codigoHash())).isTrue();
        });
    }

    @Test
    void el_canje_crea_el_primer_administrador_y_el_codigo_no_sirve_dos_veces() {
        UUID yo = yo();
        UUID id = servicio.crearInstitucion(yo, "Colegio", "colegio");
        servicio.invitar(yo, id);

        Usuario rectora = servicio.canjear(id, "789a bcde", "Rectora Peña", "la rectora riega las matas");
        assertThat(rectora.esAdministrador()).isTrue();
        assertThat(rectora.institucionId()).isEqualTo(id);
        assertThat(argon2.matches("la rectora riega las matas", rectora.claveHash())).isTrue();
        assertThat(usuarios.contar(id)).isEqualTo(1);
        assertThatThrownBy(() -> servicio.canjear(id, "789A-BCDE", "Otra persona", "clave larga 1357")).isInstanceOf(ServicioOperador.InvitacionNoValida.class);
        assertThat(operador.auditoria(10)).extracting(RepositorioOperador.Evento::accion)
                .containsExactly(RepositorioOperador.Accion.CANJEAR_INVITACION, RepositorioOperador.Accion.INVITAR, RepositorioOperador.Accion.CREAR_INSTITUCION);
    }

    @Test
    void un_codigo_caducado_mal_escrito_o_de_otra_institucion_no_sirve() {
        UUID yo = yo();
        UUID norte = servicio.crearInstitucion(yo, "Norte", "norte");
        UUID sur = servicio.crearInstitucion(yo, "Sur", "sur");
        servicio.invitar(yo, norte);
        assertThatThrownBy(() -> servicio.canjear(sur, "789ABCDE", "Intrusa", "clave larga 1357")).isInstanceOf(ServicioOperador.InvitacionNoValida.class);
        assertThatThrownBy(() -> servicio.canjear(norte, "789ABCDF", "Intrusa", "clave larga 1357")).isInstanceOf(ServicioOperador.InvitacionNoValida.class);
        assertThatThrownBy(() -> servicio.canjear(norte, "no-es-un-codigo", "Intrusa", "clave larga 1357")).isInstanceOf(ServicioOperador.InvitacionNoValida.class);
        reloj.avanzar(Duration.ofDays(7));
        assertThatThrownBy(() -> servicio.canjear(norte, "789ABCDE", "Tarde", "clave larga 1357")).isInstanceOf(ServicioOperador.InvitacionNoValida.class);
        assertThat(usuarios.contar(norte) + usuarios.contar(sur)).isZero();
    }

    @Test
    void una_invitacion_nueva_deja_sin_efecto_la_anterior() {
        UUID yo = yo();
        UUID id = servicio.crearInstitucion(yo, "Colegio", "colegio");
        servicio.invitar(yo, id);
        ServicioOperador.Invitacion segunda = servicio.invitar(yo, id);
        assertThat(segunda.codigo()).isEqualTo("FGHJKMNP");
        assertThatThrownBy(() -> servicio.canjear(id, "789ABCDE", "Primera", "clave larga 1357")).isInstanceOf(ServicioOperador.InvitacionNoValida.class);
        assertThat(servicio.canjear(id, "fghj-kmnp", "Segunda", "clave larga 1357").nombre()).isEqualTo("Segunda");
    }

    @Test
    void un_nombre_o_una_contrasena_invalida_no_gasta_la_invitacion() {
        UUID yo = yo();
        UUID id = servicio.crearInstitucion(yo, "Colegio", "colegio");
        servicio.invitar(yo, id);
        assertThatThrownBy(() -> servicio.canjear(id, "789ABCDE", "Rectora", "corta")).isInstanceOf(ServicioUsuarios.DatosInvalidos.class);
        assertThat(operador.vigentes(id)).hasSize(1);
        assertThat(servicio.canjear(id, "789ABCDE", "Rectora", "clave larga 1234").esAdministrador()).isTrue();
    }

    @Test
    void suspender_cierra_la_entrada_y_las_invitaciones() {
        UUID yo = yo();
        UUID id = servicio.crearInstitucion(yo, "Colegio", "colegio");
        servicio.invitar(yo, id);
        servicio.suspender(yo, id);
        assertThat(instituciones.activaPorRuta("colegio")).isEmpty();
        assertThatThrownBy(() -> servicio.canjear(id, "789ABCDE", "Rectora", "clave larga 1234")).isInstanceOf(ServicioOperador.InvitacionNoValida.class);
        servicio.reactivar(yo, id);
        assertThat(instituciones.activaPorRuta("colegio")).contains(id);
    }

    @Test
    void el_codigo_normaliza_minusculas_guiones_y_letras_que_se_confunden() {
        assertThat(CodigoDeUnUso.normalizar(" 7kq2-m9xd ")).isEqualTo("7KQ2M9XD");
        assertThat(CodigoDeUnUso.normalizar("OIL0-ABCD")).isEqualTo("0110ABCD");
        assertThat(CodigoDeUnUso.normalizar("7KQ2-M9XU")).as("la U no está en el alfabeto").isEmpty();
        assertThat(CodigoDeUnUso.normalizar("7KQ2")).isEmpty();
        assertThat(CodigoDeUnUso.normalizar(null)).isEmpty();
    }
}
