package pensamiento.contrato;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pensamiento.nucleo.Usuario;
import pensamiento.nucleo.puertos.RepositorioUsuarios;
import pensamiento.nucleo.puertos.RepositorioUsuarios.CodigoCanjeado;
import pensamiento.nucleo.puertos.RepositorioUsuarios.TipoCodigo;

/**
 * Contrato del puerto de cuentas (hito 9): nombre repetido sin distinguir mayúsculas ni tildes que no lanza ni rompe la
 * transacción, búsqueda paginada, clave y estado, y códigos de un uso que caducan.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class RepositorioUsuariosContract {

    protected static final Instant AHORA = Instant.parse("2026-10-14T15:00:00Z");

    protected abstract RepositorioUsuarios crearSut();

    /** Institución limpia donde escribir; el real crea una de prueba. */
    protected abstract UUID institucionDePrueba();

    /** Otra institución, sin personas. */
    protected UUID otraInstitucion() {
        return UUID.fromString("0192f000-0000-7000-8000-0000000000ff");
    }

    /** Corre todo dentro de una sola transacción (el real abre una con la institución fijada; el Fake no tiene). */
    protected <T> T enUnaTransaccion(Supplier<T> accion) {
        return accion.get();
    }

    private Usuario persona(String nombre) {
        return Usuario.nueva(UUID.randomUUID(), institucionDePrueba(), nombre, Usuario.RolGlobal.PERSONA);
    }

    @Test
    void el_nombre_no_distingue_mayusculas_tildes_ni_espacios_repetidos() {
        RepositorioUsuarios sut = crearSut();
        UUID institucion = institucionDePrueba();
        Usuario ana = sut.crear(persona("Ana María Peña")).orElseThrow();
        assertThat(sut.porNombre(institucion, "ana maria pena")).contains(ana);
        assertThat(sut.porNombre(institucion, "  ANA   MARÍA PEÑA ")).contains(ana);
        assertThat(sut.crear(persona("ANA MARIA PENA"))).isEmpty();
        assertThat(sut.contar(institucion)).isEqualTo(1);
    }

    @Test
    void tras_un_nombre_repetido_la_misma_transaccion_sigue_escribiendo() {
        RepositorioUsuarios sut = crearSut();
        UUID institucion = institucionDePrueba();
        Optional<Usuario> segunda = enUnaTransaccion(() -> {
            sut.crear(persona("vecina"));
            assertThat(sut.crear(persona("Vecina"))).isEmpty();
            return sut.crear(persona("vecino"));
        });
        assertThat(segunda).map(Usuario::nombre).contains("vecino");
        assertThat(sut.contar(institucion)).isEqualTo(2);
    }

    @Test
    void el_mismo_nombre_en_otra_institucion_no_es_repetido() {
        RepositorioUsuarios sut = crearSut();
        sut.crear(persona("Rectora"));
        assertThat(sut.porNombre(otraInstitucion(), "rectora")).isEmpty();
    }

    @Test
    void una_cuenta_nueva_no_tiene_clave_y_se_encuentra_por_id() {
        RepositorioUsuarios sut = crearSut();
        Usuario creada = sut.crear(persona("dueña de la panadería")).orElseThrow();
        Usuario leida = sut.porId(institucionDePrueba(), creada.id()).orElseThrow();
        assertThat(leida.tipoClave()).isEqualTo(Usuario.TipoClave.NINGUNA);
        assertThat(leida.claveHash()).isEmpty();
        assertThat(leida.activo()).isTrue();
    }

    @Test
    void una_cuenta_que_no_existe_devuelve_vacio_y_no_cambia_nada() {
        RepositorioUsuarios sut = crearSut();
        UUID nadie = UUID.randomUUID();
        assertThat(sut.porId(institucionDePrueba(), nadie)).isEmpty();
        assertThat(sut.porNombre(institucionDePrueba(), "nadie")).isEmpty();
        assertThat(sut.cambiarClave(institucionDePrueba(), nadie, "$argon2$x", Usuario.TipoClave.CONTRASENA)).isFalse();
        assertThat(sut.cambiarActivo(institucionDePrueba(), nadie, false)).isFalse();
    }

    @Test
    void cambiar_la_clave_y_el_estado_se_lee_despues() {
        RepositorioUsuarios sut = crearSut();
        UUID institucion = institucionDePrueba();
        Usuario empleado = sut.crear(persona("empleado")).orElseThrow();
        assertThat(sut.cambiarClave(institucion, empleado.id(), "$argon2id$hash", Usuario.TipoClave.CONTRASENA)).isTrue();
        assertThat(sut.cambiarActivo(institucion, empleado.id(), false)).isTrue();
        Usuario leido = sut.porNombre(institucion, "EMPLEADO").orElseThrow();
        assertThat(leido.claveHash()).isEqualTo("$argon2id$hash");
        assertThat(leido.tipoClave()).isEqualTo(Usuario.TipoClave.CONTRASENA);
        assertThat(leido.activo()).isFalse();
        assertThat(sut.cambiarActivo(institucion, empleado.id(), true)).isTrue();
        assertThat(sut.porId(institucion, empleado.id())).map(Usuario::activo).contains(true);
    }

    @Test
    void buscar_pagina_por_la_clave_del_nombre_en_orden() {
        RepositorioUsuarios sut = crearSut();
        UUID institucion = institucionDePrueba();
        for (String nombre : new String[] {"Zoe Ruiz", "Ana Ruiz", "Mateo Díaz", "Andrés Ruíz", "Lucía Ruiz"}) {
            sut.crear(persona(nombre));
        }
        RepositorioUsuarios.PaginaDeCuentas primera = sut.buscar(institucion, "RUIZ", 0, 2);
        assertThat(primera.total()).isEqualTo(4);
        assertThat(primera.cuentas()).extracting(Usuario::nombre).containsExactly("Ana Ruiz", "Andrés Ruíz");
        assertThat(sut.buscar(institucion, "ruiz", 1, 2).cuentas()).extracting(Usuario::nombre).containsExactly("Lucía Ruiz", "Zoe Ruiz");
        assertThat(sut.buscar(institucion, "ruiz", 2, 2).cuentas()).isEmpty();
        assertThat(sut.buscar(institucion, "", 0, 10).total()).isEqualTo(5);
        assertThat(sut.buscar(institucion, "50%_", 0, 10).total()).isZero();
        assertThat(sut.buscar(otraInstitucion(), "ruiz", 0, 10).total()).isZero();
    }

    @Test
    void un_codigo_se_canjea_una_sola_vez() {
        RepositorioUsuarios sut = crearSut();
        UUID institucion = institucionDePrueba();
        Usuario ana = sut.crear(persona("Ana")).orElseThrow();
        sut.guardarCodigo(institucion, ana.id(), TipoCodigo.ALTA, "hash-alta-ana", AHORA.plusSeconds(3600), Optional.empty());
        assertThat(sut.codigoPendiente(institucion, ana.id(), AHORA)).contains(TipoCodigo.ALTA);
        assertThat(sut.codigoVigente(institucion, "hash-alta-ana", AHORA)).as("mirarlo no lo gasta").contains(new CodigoCanjeado(ana.id(), TipoCodigo.ALTA));
        assertThat(sut.canjearCodigo(institucion, "hash-alta-ana", AHORA)).contains(new CodigoCanjeado(ana.id(), TipoCodigo.ALTA));
        assertThat(sut.codigoVigente(institucion, "hash-alta-ana", AHORA)).isEmpty();
        assertThat(sut.canjearCodigo(institucion, "hash-alta-ana", AHORA)).isEmpty();
        assertThat(sut.codigoPendiente(institucion, ana.id(), AHORA)).isEmpty();
    }

    @Test
    void un_codigo_vencido_de_otra_institucion_o_de_una_cuenta_desactivada_no_sirve() {
        RepositorioUsuarios sut = crearSut();
        UUID institucion = institucionDePrueba();
        Usuario ana = sut.crear(persona("Ana")).orElseThrow();
        Usuario beto = sut.crear(persona("Beto")).orElseThrow();
        sut.guardarCodigo(institucion, ana.id(), TipoCodigo.ALTA, "hash-ana", AHORA.plusSeconds(60), Optional.empty());
        sut.guardarCodigo(institucion, beto.id(), TipoCodigo.RESTABLECER, "hash-beto", AHORA.plusSeconds(60), Optional.of(ana.id()));
        assertThat(sut.canjearCodigo(institucion, "hash-ana", AHORA.plusSeconds(60))).isEmpty();
        assertThat(sut.codigoVigente(institucion, "hash-ana", AHORA.plusSeconds(60))).isEmpty();
        assertThat(sut.canjearCodigo(otraInstitucion(), "hash-beto", AHORA)).isEmpty();
        assertThat(sut.codigoVigente(otraInstitucion(), "hash-beto", AHORA)).isEmpty();
        sut.cambiarActivo(institucion, beto.id(), false);
        assertThat(sut.codigoVigente(institucion, "hash-beto", AHORA)).isEmpty();
        assertThat(sut.canjearCodigo(institucion, "hash-beto", AHORA)).isEmpty();
        assertThat(sut.canjearCodigo(institucion, "hash-que-nadie-tiene", AHORA)).isEmpty();
    }

    @Test
    void un_codigo_nuevo_deja_sin_efecto_el_anterior_de_la_misma_cuenta() {
        RepositorioUsuarios sut = crearSut();
        UUID institucion = institucionDePrueba();
        Usuario ana = sut.crear(persona("Ana")).orElseThrow();
        sut.guardarCodigo(institucion, ana.id(), TipoCodigo.ALTA, "hash-primero", AHORA.plusSeconds(3600), Optional.empty());
        sut.guardarCodigo(institucion, ana.id(), TipoCodigo.RESTABLECER, "hash-segundo", AHORA.plusSeconds(3600), Optional.empty());
        assertThat(sut.canjearCodigo(institucion, "hash-primero", AHORA)).isEmpty();
        assertThat(sut.codigoPendiente(institucion, ana.id(), AHORA)).contains(TipoCodigo.RESTABLECER);
        assertThat(sut.canjearCodigo(institucion, "hash-segundo", AHORA)).contains(new CodigoCanjeado(ana.id(), TipoCodigo.RESTABLECER));
    }
}
