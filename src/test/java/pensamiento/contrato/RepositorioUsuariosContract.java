package pensamiento.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pensamiento.nucleo.Usuario;
import pensamiento.nucleo.puertos.RepositorioUsuarios;

/** Contrato de cuentas: nombre repetido sin distinguir mayúsculas ni tildes, no encontrado, orden por nombre, actualización por id. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class RepositorioUsuariosContract {

    protected abstract RepositorioUsuarios crearSut();

    /** Institución limpia donde escribir; el real crea una de prueba. */
    protected abstract UUID institucionDePrueba();

    /** Otra institución, sin personas. */
    protected UUID otraInstitucion() {
        return UUID.fromString("0192f000-0000-7000-8000-0000000000ff");
    }

    @Test
    void el_nombre_no_distingue_mayusculas_tildes_ni_espacios_repetidos() {
        RepositorioUsuarios sut = crearSut();
        UUID institucion = institucionDePrueba();
        Usuario ana = new Usuario(UUID.randomUUID(), institucion, "Ana María Peña", "h", Usuario.RolGlobal.PERSONA, true);
        sut.guardar(ana);
        assertThat(sut.porNombre(institucion, "ana maria pena")).contains(ana);
        assertThat(sut.porNombre(institucion, "  ANA   MARÍA PEÑA ")).contains(ana);
        assertThatThrownBy(() -> sut.guardar(new Usuario(UUID.randomUUID(), institucion, "ANA MARIA PENA", "h", Usuario.RolGlobal.PERSONA, true)))
                .isInstanceOf(RepositorioUsuarios.NombreRepetido.class);
        assertThat(sut.contar(institucion)).isEqualTo(1);
    }

    @Test
    void el_mismo_nombre_en_otra_institucion_no_es_repetido() {
        RepositorioUsuarios sut = crearSut();
        UUID institucion = institucionDePrueba();
        sut.guardar(new Usuario(UUID.randomUUID(), institucion, "Rectora", "h", Usuario.RolGlobal.PERSONA, true));
        assertThat(sut.porNombre(otraInstitucion(), "rectora")).isEmpty();
    }

    @Test
    void una_persona_guardada_se_encuentra_por_id_y_por_nombre_con_tildes() {
        RepositorioUsuarios sut = crearSut();
        UUID institucion = institucionDePrueba();
        Usuario persona = new Usuario(UUID.randomUUID(), institucion, "dueña de la panadería", "$argon2$hash", Usuario.RolGlobal.PERSONA, true);
        sut.guardar(persona);
        assertThat(sut.porId(institucion, persona.id())).contains(persona);
        assertThat(sut.porNombre(institucion, "dueña de la panadería")).contains(persona);
    }

    @Test
    void una_persona_que_no_existe_devuelve_vacio() {
        assertThat(crearSut().porId(institucionDePrueba(), UUID.randomUUID())).isEmpty();
        assertThat(crearSut().porNombre(institucionDePrueba(), "nadie")).isEmpty();
    }

    @Test
    void dos_personas_con_el_mismo_nombre_en_la_misma_institucion_es_nombre_repetido() {
        RepositorioUsuarios sut = crearSut();
        UUID institucion = institucionDePrueba();
        sut.guardar(new Usuario(UUID.randomUUID(), institucion, "vecina", "h", Usuario.RolGlobal.PERSONA, true));
        assertThatThrownBy(() -> sut.guardar(new Usuario(UUID.randomUUID(), institucion, "vecina", "h", Usuario.RolGlobal.PERSONA, true)))
                .isInstanceOf(RepositorioUsuarios.NombreRepetido.class);
    }

    @Test
    void guardar_con_el_mismo_id_actualiza_en_vez_de_duplicar() {
        RepositorioUsuarios sut = crearSut();
        UUID institucion = institucionDePrueba();
        Usuario activa = new Usuario(UUID.randomUUID(), institucion, "empleado", "h", Usuario.RolGlobal.PERSONA, true);
        sut.guardar(activa);
        sut.guardar(new Usuario(activa.id(), institucion, "empleado", "h", Usuario.RolGlobal.PERSONA, false));
        assertThat(sut.contar(institucion)).isEqualTo(1);
        assertThat(sut.porId(institucion, activa.id())).map(Usuario::activo).contains(false);
    }

    @Test
    void todos_viene_ordenado_por_nombre() {
        RepositorioUsuarios sut = crearSut();
        UUID institucion = institucionDePrueba();
        sut.guardar(new Usuario(UUID.randomUUID(), institucion, "zapatero", "h", Usuario.RolGlobal.PERSONA, true));
        sut.guardar(new Usuario(UUID.randomUUID(), institucion, "administrador", "h", Usuario.RolGlobal.ADMINISTRADOR, true));
        assertThat(sut.todos(institucion).stream().map(Usuario::nombre).toList()).isSorted();
    }
}
