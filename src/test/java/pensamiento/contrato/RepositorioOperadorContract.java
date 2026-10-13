package pensamiento.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pensamiento.nucleo.Institucion;
import pensamiento.nucleo.puertos.RepositorioInstituciones;
import pensamiento.nucleo.puertos.RepositorioOperador;
import pensamiento.nucleo.puertos.RepositorioOperador.Accion;

/**
 * Contrato del operador: crea instituciones que se resuelven por su ruta, las suspende y reactiva, invita con un código
 * guardado solo como hash que se canjea una sola vez y caduca, y deja cada acción en su auditoría. Sin operador válido no
 * hace nada.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class RepositorioOperadorContract {

    protected abstract RepositorioOperador crearSut();

    /** Las instituciones vistas por la app, sobre los mismos datos que el operador. */
    protected abstract RepositorioInstituciones instituciones();

    /** El id de un operador que existe (lo crea si hace falta). */
    protected abstract UUID operador();

    /** Agrega una cuenta a la institución (lo que hace el canje de la invitación). */
    protected abstract void agregarCuenta(UUID institucion);

    /** El instante actual para quien guarda las invitaciones. */
    protected abstract Instant ahora();

    protected static String rutaNueva(String prefijo) {
        return prefijo + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    @Test
    void una_institucion_creada_por_el_operador_se_resuelve_por_su_ruta_y_aparece_sin_cuentas() {
        RepositorioOperador sut = crearSut();
        String ruta = rutaNueva("colegio");
        UUID id = sut.crearInstitucion(operador(), "Colegio Nuevo", ruta);
        assertThat(instituciones().activaPorRuta(ruta)).contains(id);
        assertThat(sut.instituciones()).filteredOn(i -> i.institucion().id().equals(id)).singleElement().satisfies(i -> {
            assertThat(i.institucion()).isEqualTo(new Institucion(id, "Colegio Nuevo", ruta, Institucion.Estado.ACTIVA));
            assertThat(i.tieneCuentas()).isFalse();
        });
        agregarCuenta(id);
        assertThat(sut.instituciones()).filteredOn(i -> i.institucion().id().equals(id)).singleElement()
                .satisfies(i -> assertThat(i.tieneCuentas()).isTrue());
    }

    @Test
    void una_ruta_repetida_no_crea_otra_institucion() {
        RepositorioOperador sut = crearSut();
        String ruta = rutaNueva("liceo");
        sut.crearInstitucion(operador(), "Liceo", ruta);
        assertThatThrownBy(() -> sut.crearInstitucion(operador(), "Otro liceo", ruta)).isInstanceOf(RepositorioOperador.RutaRepetida.class);
        assertThat(sut.instituciones()).filteredOn(i -> i.institucion().ruta().equals(ruta)).hasSize(1);
    }

    @Test
    void un_operador_que_no_existe_no_puede_crear_ni_suspender_ni_invitar() {
        RepositorioOperador sut = crearSut();
        UUID id = sut.crearInstitucion(operador(), "Colegio", rutaNueva("colegio"));
        UUID impostor = UUID.randomUUID();
        assertThatThrownBy(() -> sut.crearInstitucion(impostor, "X", rutaNueva("x"))).isInstanceOf(RepositorioOperador.OperadorDesconocido.class);
        assertThatThrownBy(() -> sut.cambiarEstado(impostor, id, Institucion.Estado.SUSPENDIDA)).isInstanceOf(RepositorioOperador.OperadorDesconocido.class);
        assertThatThrownBy(() -> sut.invitar(impostor, id, "hash", ahora().plus(Duration.ofDays(1)))).isInstanceOf(RepositorioOperador.OperadorDesconocido.class);
        assertThat(instituciones().activaPorRuta(sut.instituciones().stream().filter(i -> i.institucion().id().equals(id))
                .findFirst().orElseThrow().institucion().ruta())).contains(id);
    }

    @Test
    void suspender_cierra_la_entrada_y_reactivar_la_abre_de_nuevo() {
        RepositorioOperador sut = crearSut();
        String ruta = rutaNueva("escuela");
        UUID id = sut.crearInstitucion(operador(), "Escuela", ruta);
        assertThat(sut.cambiarEstado(operador(), id, Institucion.Estado.SUSPENDIDA)).isTrue();
        assertThat(instituciones().activaPorRuta(ruta)).isEmpty();
        assertThat(sut.cambiarEstado(operador(), id, Institucion.Estado.ACTIVA)).isTrue();
        assertThat(instituciones().activaPorRuta(ruta)).contains(id);
        assertThat(sut.cambiarEstado(operador(), UUID.randomUUID(), Institucion.Estado.SUSPENDIDA)).isFalse();
    }

    @Test
    void una_invitacion_se_canjea_una_sola_vez() {
        RepositorioOperador sut = crearSut();
        UUID id = sut.crearInstitucion(operador(), "Colegio", rutaNueva("colegio"));
        UUID invitacion = sut.invitar(operador(), id, "$argon2id$hash-del-codigo", ahora().plus(Duration.ofDays(7)));
        assertThat(sut.vigentes(id)).containsExactly(new RepositorioOperador.InvitacionVigente(invitacion, "$argon2id$hash-del-codigo"));
        assertThat(sut.canjear(invitacion)).isTrue();
        assertThat(sut.canjear(invitacion)).as("la segunda vez").isFalse();
        assertThat(sut.vigentes(id)).isEmpty();
    }

    @Test
    void una_invitacion_caducada_o_reemplazada_o_de_una_institucion_suspendida_no_esta_vigente() {
        RepositorioOperador sut = crearSut();
        UUID id = sut.crearInstitucion(operador(), "Colegio", rutaNueva("colegio"));
        UUID caducada = sut.invitar(operador(), id, "h1", ahora().minus(Duration.ofMinutes(1)));
        assertThat(sut.vigentes(id)).isEmpty();
        assertThat(sut.canjear(caducada)).isFalse();

        UUID primera = sut.invitar(operador(), id, "h2", ahora().plus(Duration.ofDays(7)));
        UUID segunda = sut.invitar(operador(), id, "h3", ahora().plus(Duration.ofDays(7)));
        assertThat(sut.vigentes(id)).extracting(RepositorioOperador.InvitacionVigente::id).containsExactly(segunda);
        assertThat(sut.canjear(primera)).as("la reemplazada").isFalse();

        sut.cambiarEstado(operador(), id, Institucion.Estado.SUSPENDIDA);
        assertThat(sut.vigentes(id)).isEmpty();
        assertThat(sut.canjear(UUID.randomUUID())).isFalse();
    }

    @Test
    void cada_accion_queda_en_la_auditoria_del_operador_y_el_canje_sin_operador() {
        RepositorioOperador sut = crearSut();
        UUID yo = operador();
        String ruta = rutaNueva("auditada");
        UUID id = sut.crearInstitucion(yo, "Auditada", ruta);
        UUID invitacion = sut.invitar(yo, id, "h", ahora().plus(Duration.ofDays(1)));
        sut.cambiarEstado(yo, id, Institucion.Estado.SUSPENDIDA);
        sut.cambiarEstado(yo, id, Institucion.Estado.ACTIVA);
        sut.canjear(invitacion);
        List<RepositorioOperador.Evento> deEsta = sut.auditoria(500).stream().filter(e -> id.equals(e.institucion())).toList();
        assertThat(deEsta).extracting(RepositorioOperador.Evento::accion)
                .containsExactly(Accion.CANJEAR_INVITACION, Accion.REACTIVAR, Accion.SUSPENDER, Accion.INVITAR, Accion.CREAR_INSTITUCION);
        assertThat(deEsta).allSatisfy(e -> assertThat(e.ruta()).isEqualTo(ruta));
        assertThat(deEsta.get(0).operador()).isEmpty();
        assertThat(deEsta.subList(1, 5)).allSatisfy(e -> assertThat(e.operador()).contains(yo));
    }

    @Test
    void el_operador_se_crea_una_sola_vez() {
        RepositorioOperador sut = crearSut();
        operador();
        assertThat(sut.crearSiNoHay("otro-operador", "hash")).isFalse();
        assertThat(sut.porNombre("otro-operador")).isEmpty();
        assertThat(sut.porNombre("nadie")).isEmpty();
    }
}
