package pensamiento.unidad.web.aula;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pensamiento.nucleo.Grupo;
import pensamiento.nucleo.Usuario;
import pensamiento.nucleo.puertos.RegistroAuditoria;
import pensamiento.nucleo.puertos.RepositorioAula.Actor;
import pensamiento.testutil.TransaccionesSinBase;
import pensamiento.testutil.fakes.FakeRegistroAuditoria;
import pensamiento.testutil.fakes.FakeReloj;
import pensamiento.testutil.fakes.FakeRepositorioAula;
import pensamiento.testutil.fakes.FakeRepositorioDojo;
import pensamiento.testutil.fakes.FakeRepositorioUsuarios;
import pensamiento.web.ObjetoNoEncontrado;
import pensamiento.web.aula.ServicioAula;
import pensamiento.web.usuarios.ServicioUsuarios;

/**
 * Collaboration test de ServicioAula, grupos (hito 10, entregable 2): el administrador crea grupos con nombre único y les
 * asigna un docente activo; el docente ve solo los suyos (varios); un grupo ajeno es 404; todo queda en la auditoría.
 */
class ServicioAulaTest {

    private final UUID colegio = UUID.fromString("0192f000-0000-7000-8000-00000000c01e");
    private final FakeRepositorioUsuarios usuarios = new FakeRepositorioUsuarios();
    private final FakeRegistroAuditoria auditoria = new FakeRegistroAuditoria();
    private final FakeRepositorioAula aula = new FakeRepositorioAula(usuarios, new FakeRepositorioDojo(), auditoria);
    private final ServicioAula servicio = new ServicioAula(aula, usuarios, auditoria, new FakeReloj(), new TransaccionesSinBase());

    private Actor cuenta(String nombre, Usuario.RolGlobal rol) {
        Usuario u = usuarios.crear(Usuario.nueva(UUID.randomUUID(), colegio, nombre, rol)).orElseThrow();
        return new Actor(u.id(), colegio, rol);
    }

    @Test
    void el_administrador_crea_un_grupo_con_su_docente_y_queda_en_la_auditoria() {
        Actor rectora = cuenta("Rectora", Usuario.RolGlobal.ADMINISTRADOR);
        Actor profe = cuenta("Profe Ramírez", Usuario.RolGlobal.DOCENTE);

        Grupo g = servicio.crearGrupo(rectora, "  10.º A · Filosofía  ", Optional.of(profe.id()));

        assertThat(g.nombre()).isEqualTo("10.º A · Filosofía");
        assertThat(g.docenteId()).contains(profe.id());
        assertThat(auditoria.todos()).extracting(RegistroAuditoria.Evento::accion).containsExactly(RegistroAuditoria.Accion.CREAR_GRUPO);
    }

    @Test
    void un_docente_tiene_varios_grupos_y_ve_solo_los_suyos() {
        Actor rectora = cuenta("Rectora", Usuario.RolGlobal.ADMINISTRADOR);
        Actor profe = cuenta("Profe Ramírez", Usuario.RolGlobal.DOCENTE);
        Actor otra = cuenta("Profe Díaz", Usuario.RolGlobal.DOCENTE);
        for (String nombre : new String[] {"10.º A", "10.º B", "11.º A", "11.º B"}) {
            servicio.crearGrupo(rectora, nombre, Optional.of(profe.id()));
        }
        Grupo ajeno = servicio.crearGrupo(rectora, "9.º C", Optional.of(otra.id()));

        assertThat(servicio.grupos(profe)).extracting(Grupo::nombre).containsExactly("10.º A", "10.º B", "11.º A", "11.º B");
        assertThat(servicio.grupos(rectora)).hasSize(5);
        assertThatThrownBy(() -> servicio.grupo(profe, ajeno.id())).isInstanceOf(ObjetoNoEncontrado.class);
        assertThatThrownBy(() -> servicio.miembros(profe, ajeno.id())).isInstanceOf(ObjetoNoEncontrado.class);
    }

    @Test
    void un_nombre_repetido_o_vacio_y_un_docente_que_no_es_docente_se_rechazan_con_su_mensaje() {
        Actor rectora = cuenta("Rectora", Usuario.RolGlobal.ADMINISTRADOR);
        Actor persona = cuenta("Ana", Usuario.RolGlobal.PERSONA);
        servicio.crearGrupo(rectora, "10.º A", Optional.empty());

        assertThatThrownBy(() -> servicio.crearGrupo(rectora, "10.º a", Optional.empty())).isInstanceOf(ServicioUsuarios.DatosInvalidos.class)
                .hasMessageContaining("Ya hay un grupo llamado «10.º a»");
        assertThatThrownBy(() -> servicio.crearGrupo(rectora, "  ", Optional.empty())).isInstanceOf(ServicioUsuarios.DatosInvalidos.class)
                .hasMessageContaining("obligatorio");
        assertThatThrownBy(() -> servicio.crearGrupo(rectora, "11.º B", Optional.of(persona.id()))).isInstanceOf(ServicioUsuarios.DatosInvalidos.class)
                .hasMessage("«Ana» no es una cuenta de docente activa.");
        assertThat(servicio.grupos(rectora)).extracting(Grupo::nombre).containsExactly("10.º A");
    }

    @Test
    void solo_el_administrador_crea_grupos_y_cambia_su_docente() {
        Actor rectora = cuenta("Rectora", Usuario.RolGlobal.ADMINISTRADOR);
        Actor profe = cuenta("Profe Ramírez", Usuario.RolGlobal.DOCENTE);
        Actor otra = cuenta("Profe Díaz", Usuario.RolGlobal.DOCENTE);
        Grupo g = servicio.crearGrupo(rectora, "10.º A", Optional.of(profe.id()));

        assertThatThrownBy(() -> servicio.crearGrupo(profe, "Mío", Optional.of(profe.id()))).isInstanceOf(ObjetoNoEncontrado.class);
        assertThatThrownBy(() -> servicio.asignarDocente(profe, g.id(), Optional.of(otra.id()))).isInstanceOf(ObjetoNoEncontrado.class);

        servicio.asignarDocente(rectora, g.id(), Optional.of(otra.id()));

        assertThat(servicio.grupos(otra)).extracting(Grupo::id).containsExactly(g.id());
        assertThat(servicio.grupos(profe)).isEmpty();
        assertThat(auditoria.todos()).extracting(RegistroAuditoria.Evento::accion)
                .containsExactly(RegistroAuditoria.Accion.CREAR_GRUPO, RegistroAuditoria.Accion.CAMBIAR_GRUPO);
    }

    @Test
    void los_docentes_para_elegir_son_solo_las_cuentas_activas_de_docente() {
        Actor rectora = cuenta("Rectora", Usuario.RolGlobal.ADMINISTRADOR);
        cuenta("Profe Ramírez", Usuario.RolGlobal.DOCENTE);
        Actor retirada = cuenta("Profe Díaz", Usuario.RolGlobal.DOCENTE);
        cuenta("Ana", Usuario.RolGlobal.ESTUDIANTE);
        usuarios.cambiarActivo(colegio, retirada.id(), false);

        assertThat(servicio.docentes(rectora)).extracting(Usuario::nombre).containsExactly("Profe Ramírez");
    }
}
