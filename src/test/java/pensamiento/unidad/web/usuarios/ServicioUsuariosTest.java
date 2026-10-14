package pensamiento.unidad.web.usuarios;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import pensamiento.nucleo.Usuario;
import pensamiento.nucleo.puertos.RegistroAuditoria;
import pensamiento.nucleo.puertos.RepositorioUsuarios.TipoCodigo;
import pensamiento.testutil.TransaccionesSinBase;
import pensamiento.testutil.fakes.FakeRegistroAuditoria;
import pensamiento.testutil.fakes.FakeRegistroIntentos;
import pensamiento.testutil.fakes.FakeReloj;
import pensamiento.testutil.fakes.FakeRepositorioUsuarios;
import pensamiento.web.seguridad.LimiteDeIntentos;
import pensamiento.web.usuarios.CodigoDeUnUso;
import pensamiento.web.usuarios.PoliticaDeContrasenas;
import pensamiento.web.usuarios.ServicioUsuarios;

/**
 * Collaboration test de ServicioUsuarios (hito 9): alta con código de un uso guardado como hash, canje que elige la
 * contraseña, restablecer que cierra sesiones, cambiar dando la actual, el PIN viejo que se reemplaza, desactivar y
 * reactivar; todo en la auditoría, nunca la contraseña.
 */
class ServicioUsuariosTest {

    private final FakeRepositorioUsuarios usuarios = new FakeRepositorioUsuarios();
    private final FakeRegistroAuditoria auditoria = new FakeRegistroAuditoria();
    private final PasswordEncoder argon2 = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private final FakeReloj reloj = new FakeReloj();
    private final List<UUID> sesionesCerradas = new ArrayList<>();
    /** Azar fijo: el primer código sale con los índices 7, 8, 9… del alfabeto ("789ABCDE"), el segundo "FGHJKMNP". */
    private final AtomicInteger siguiente = new AtomicInteger(7);
    private final FakeRegistroIntentos intentos = new FakeRegistroIntentos();
    private final ServicioUsuarios servicio = new ServicioUsuarios(usuarios, auditoria, argon2, reloj, new TransaccionesSinBase(),
            new PoliticaDeContrasenas(Set.of("contrasena123", "1234567890")), new CodigoDeUnUso(n -> siguiente.getAndIncrement() % n),
            sesionesCerradas::add, new LimiteDeIntentos(intentos, reloj, new LimiteDeIntentos.Tope(300, Duration.ofMinutes(10)),
                    new LimiteDeIntentos.Tope(1000, Duration.ofMinutes(10))));
    private final UUID colegio = UUID.fromString("0192f000-0000-7000-8000-00000000c01e");

    private Usuario administrador() {
        return servicio.primerAdministrador(colegio, "Rectora", "la rectora riega las matas").orElseThrow();
    }

    @Test
    void el_primer_administrador_tiene_contrasena_de_12_o_mas_hasheada_y_queda_en_auditoria() {
        assertThatThrownBy(() -> servicio.primerAdministrador(colegio, "Rectora", "corta12345")).isInstanceOf(ServicioUsuarios.DatosInvalidos.class)
                .hasMessageContaining("12 caracteres");
        Usuario admin = administrador();
        assertThat(admin.esAdministrador()).isTrue();
        assertThat(admin.tipoClave()).isEqualTo(Usuario.TipoClave.CONTRASENA);
        assertThat(argon2.matches("la rectora riega las matas", admin.claveHash())).isTrue();
        assertThat(servicio.primerAdministrador(colegio, "Otra", "otra clave bien larga")).as("solo si no hay cuentas").isEmpty();
        assertThat(auditoria.todos()).singleElement().satisfies(e -> {
            assertThat(e.accion()).isEqualTo(RegistroAuditoria.Accion.CREAR);
            assertThat(e.fecha()).isEqualTo(FakeReloj.INSTANTE_FIJO);
        });
    }

    @Test
    void crear_una_cuenta_la_deja_sin_clave_con_un_codigo_de_alta_que_solo_se_guarda_como_hash() {
        Usuario admin = administrador();
        ServicioUsuarios.CodigoEmitido emitido = servicio.crear(admin.id(), colegio, "  Ana María  ", Usuario.RolGlobal.PERSONA);

        assertThat(emitido.codigo()).isEqualTo("789ABCDE");
        assertThat(emitido.legible()).isEqualTo("789A-BCDE");
        assertThat(emitido.caducaEn()).isEqualTo(FakeReloj.INSTANTE_FIJO.plus(Duration.ofDays(14)));
        Usuario ana = usuarios.porNombre(colegio, "ana maria").orElseThrow();
        assertThat(ana.nombre()).isEqualTo("Ana María");
        assertThat(ana.tieneClave()).isFalse();
        assertThat(usuarios.codigoPendiente(colegio, ana.id(), reloj.ahora())).contains(TipoCodigo.ALTA);
        assertThat(usuarios.canjearCodigo(colegio, "789ABCDE", reloj.ahora())).as("en claro no sirve: se guardó el hash").isEmpty();
        assertThat(auditoria.deUsuario(admin.id())).extracting(RegistroAuditoria.Evento::accion)
                .contains(RegistroAuditoria.Accion.CREAR, RegistroAuditoria.Accion.CODIGO_ALTA);
    }

    @Test
    void un_nombre_repetido_es_un_dato_invalido_y_no_escribe_nada() {
        Usuario admin = administrador();
        servicio.crear(admin.id(), colegio, "vecina", Usuario.RolGlobal.PERSONA);
        assertThatThrownBy(() -> servicio.crear(admin.id(), colegio, "VECINA", Usuario.RolGlobal.PERSONA))
                .isInstanceOf(ServicioUsuarios.DatosInvalidos.class).hasMessageContaining("Ya existe una persona llamada «VECINA»");
        assertThat(usuarios.contar(colegio)).isEqualTo(2);
        assertThat(servicio.crear(admin.id(), colegio, "vecino", Usuario.RolGlobal.PERSONA).cuenta().nombre()).isEqualTo("vecino");
    }

    @Test
    void canjear_el_codigo_fija_la_contrasena_y_el_mismo_codigo_no_sirve_dos_veces() {
        Usuario admin = administrador();
        servicio.crear(admin.id(), colegio, "Ana", Usuario.RolGlobal.PERSONA);

        Usuario ana = servicio.canjear(colegio, "789a-bcde", "mi perro come mango", "mi perro come mango");

        assertThat(ana.nombre()).isEqualTo("Ana");
        Usuario guardada = usuarios.porId(colegio, ana.id()).orElseThrow();
        assertThat(guardada.tipoClave()).isEqualTo(Usuario.TipoClave.CONTRASENA);
        assertThat(argon2.matches("mi perro come mango", guardada.claveHash())).isTrue();
        assertThatThrownBy(() -> servicio.canjear(colegio, "789ABCDE", "otra frase cualquiera", "otra frase cualquiera"))
                .isInstanceOf(ServicioUsuarios.CodigoNoValido.class);
        assertThat(auditoria.deUsuario(ana.id())).extracting(RegistroAuditoria.Evento::accion).contains(RegistroAuditoria.Accion.CANJEAR_CODIGO);
    }

    @Test
    void una_contrasena_que_no_sirve_no_gasta_el_codigo() {
        Usuario admin = administrador();
        servicio.crear(admin.id(), colegio, "Ana", Usuario.RolGlobal.PERSONA);
        assertThatThrownBy(() -> servicio.canjear(colegio, "789ABCDE", "corta", "corta")).isInstanceOf(ServicioUsuarios.DatosInvalidos.class)
                .hasMessageContaining("10 caracteres");
        assertThatThrownBy(() -> servicio.canjear(colegio, "789ABCDE", "contrasena123", "contrasena123"))
                .isInstanceOf(ServicioUsuarios.DatosInvalidos.class).hasMessageContaining("más usadas");
        assertThatThrownBy(() -> servicio.canjear(colegio, "789ABCDE", "mi perro come mango", "mi perro come manga"))
                .isInstanceOf(ServicioUsuarios.DatosInvalidos.class).hasMessageContaining("no coinciden");
        assertThatThrownBy(() -> servicio.canjear(colegio, "789ABCDE", "aaaaaaaaaaaa", "aaaaaaaaaaaa")).isInstanceOf(ServicioUsuarios.DatosInvalidos.class);
        assertThat(servicio.canjear(colegio, "789ABCDE", "mi perro come mango", "mi perro come mango").nombre()).isEqualTo("Ana");
    }

    @Test
    void un_codigo_vencido_o_mal_escrito_no_sirve() {
        Usuario admin = administrador();
        servicio.crear(admin.id(), colegio, "Ana", Usuario.RolGlobal.PERSONA);
        assertThatThrownBy(() -> servicio.canjear(colegio, "no-es-un-codigo", "mi perro come mango", "mi perro come mango"))
                .isInstanceOf(ServicioUsuarios.CodigoNoValido.class);
        reloj.avanzar(Duration.ofDays(14));
        assertThatThrownBy(() -> servicio.canjear(colegio, "789ABCDE", "mi perro come mango", "mi perro come mango"))
                .isInstanceOf(ServicioUsuarios.CodigoNoValido.class);
    }

    @Test
    void restablecer_genera_un_codigo_de_72_horas_y_cierra_las_sesiones_de_esa_persona() {
        Usuario admin = administrador();
        servicio.crear(admin.id(), colegio, "Ana", Usuario.RolGlobal.PERSONA);
        Usuario ana = servicio.canjear(colegio, "789ABCDE", "mi perro come mango", "mi perro come mango");
        sesionesCerradas.clear();

        ServicioUsuarios.CodigoEmitido emitido = servicio.restablecer(admin.id(), colegio, ana.id());

        assertThat(emitido.tipo()).isEqualTo(TipoCodigo.RESTABLECER);
        assertThat(emitido.codigo()).isEqualTo("FGHJKMNP");
        assertThat(emitido.caducaEn()).isEqualTo(FakeReloj.INSTANTE_FIJO.plus(Duration.ofHours(72)));
        assertThat(sesionesCerradas).containsExactly(ana.id());
        servicio.canjear(colegio, "FGHJ-KMNP", "otra frase para ana", "otra frase para ana");
        assertThat(argon2.matches("otra frase para ana", usuarios.porId(colegio, ana.id()).orElseThrow().claveHash())).isTrue();
        assertThatThrownBy(() -> servicio.restablecer(admin.id(), colegio, admin.id())).isInstanceOf(ServicioUsuarios.DatosInvalidos.class);
    }

    @Test
    void cambiar_la_contrasena_exige_la_actual_y_queda_en_auditoria() {
        Usuario admin = administrador();
        servicio.crear(admin.id(), colegio, "Ana", Usuario.RolGlobal.PERSONA);
        Usuario ana = servicio.canjear(colegio, "789ABCDE", "mi perro come mango", "mi perro come mango");

        assertThatThrownBy(() -> servicio.cambiarContrasena(colegio, ana.id(), "no es esta", "una frase nueva y larga", "una frase nueva y larga"))
                .isInstanceOf(ServicioUsuarios.DatosInvalidos.class).hasMessageContaining("actual no coincide");
        servicio.cambiarContrasena(colegio, ana.id(), "mi perro come mango", "una frase nueva y larga", "una frase nueva y larga");

        assertThat(argon2.matches("una frase nueva y larga", usuarios.porId(colegio, ana.id()).orElseThrow().claveHash())).isTrue();
        assertThat(auditoria.deUsuario(ana.id())).extracting(RegistroAuditoria.Evento::accion).contains(RegistroAuditoria.Accion.CAMBIAR_CLAVE);
        assertThat(auditoria.todos()).noneMatch(e -> e.detalle().map(d -> d.contains("frase")).orElse(false));
    }

    @Test
    void quien_tenia_pin_lo_reemplaza_por_una_contrasena_una_sola_vez() {
        Usuario conPin = usuarios.crear(Usuario.nueva(UUID.randomUUID(), colegio, "vecino", Usuario.RolGlobal.PERSONA)
                .conClave(argon2.encode("2468"), Usuario.TipoClave.PIN)).orElseThrow();
        servicio.reemplazarPin(colegio, conPin.id(), "el vecino barre la acera", "el vecino barre la acera");
        Usuario despues = usuarios.porId(colegio, conPin.id()).orElseThrow();
        assertThat(despues.tipoClave()).isEqualTo(Usuario.TipoClave.CONTRASENA);
        assertThat(argon2.matches("2468", despues.claveHash())).isFalse();
        assertThatThrownBy(() -> servicio.reemplazarPin(colegio, conPin.id(), "otra frase distinta", "otra frase distinta"))
                .isInstanceOf(ServicioUsuarios.DatosInvalidos.class);
    }

    @Test
    void desactivar_cierra_sesiones_y_reactivar_la_devuelve_con_auditoria() {
        Usuario admin = administrador();
        UUID ana = servicio.crear(admin.id(), colegio, "Ana", Usuario.RolGlobal.PERSONA).cuenta().id();
        assertThat(servicio.desactivar(admin.id(), colegio, ana).activo()).isFalse();
        assertThat(sesionesCerradas).contains(ana);
        assertThat(servicio.reactivar(admin.id(), colegio, ana).activo()).isTrue();
        assertThat(usuarios.porId(colegio, ana)).map(Usuario::activo).contains(true);
        assertThat(auditoria.deUsuario(admin.id())).extracting(RegistroAuditoria.Evento::accion)
                .contains(RegistroAuditoria.Accion.DESACTIVAR, RegistroAuditoria.Accion.REACTIVAR);
        assertThatThrownBy(() -> servicio.desactivar(admin.id(), colegio, admin.id())).isInstanceOf(ServicioUsuarios.DatosInvalidos.class);
    }

    @Test
    void levantar_la_espera_deja_intentar_ya_y_queda_en_auditoria() {
        Usuario admin = administrador();
        LimiteDeIntentos limite = new LimiteDeIntentos(intentos, reloj, new LimiteDeIntentos.Tope(300, Duration.ofMinutes(10)),
                new LimiteDeIntentos.Tope(1000, Duration.ofMinutes(10)));
        for (int i = 0; i < 5; i++) {
            limite.fallo(colegio, "Ana María", "10.0.0.7");
        }
        assertThat(servicio.enEspera(colegio)).containsExactly("ana maria");
        servicio.levantarEspera(admin.id(), colegio, "ana maria");
        assertThat(servicio.enEspera(colegio)).isEmpty();
        assertThat(limite.espera(colegio, "ana maria", "10.0.0.8")).isEmpty();
        assertThat(auditoria.deUsuario(admin.id())).anySatisfy(e -> {
            assertThat(e.accion()).isEqualTo(RegistroAuditoria.Accion.LEVANTAR_ESPERA);
            assertThat(e.detalle()).isEqualTo(Optional.of("ana maria"));
        });
    }

    @Test
    void la_busqueda_pagina_de_a_10() {
        Usuario admin = administrador();
        for (int i = 0; i < 12; i++) {
            servicio.crear(admin.id(), colegio, "estudiante " + (char) ('a' + i), Usuario.RolGlobal.PERSONA);
        }
        assertThat(servicio.buscar(colegio, "estudiante", 0).cuentas()).hasSize(10);
        assertThat(servicio.buscar(colegio, "ESTUDIANTE", 1).cuentas()).extracting(Usuario::nombre).containsExactly("estudiante k", "estudiante l");
        assertThat(servicio.buscar(colegio, "", 0).total()).isEqualTo(13);
    }
}
