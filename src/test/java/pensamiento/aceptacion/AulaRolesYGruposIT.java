package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Hito 10, entregable 2 (RF-19), por HTTP: quien administra crea un docente (que entra con su llave de acceso) y cuatro grupos
 * para él; el docente ve en "Mis grupos" solo los suyos y un grupo ajeno le responde 404; una persona y un estudiante no
 * entran a /grupos (403).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AulaRolesYGruposIT {

    private final String sufijo = UUID.randomUUID().toString().substring(0, 6);
    private ClienteApp admin;
    private ClienteApp profe;
    private ClienteApp otraProfe;
    private UUID ajeno;

    @BeforeAll
    void preparar() {
        admin = Instalacion.administrador();
        profe = Instalacion.docenteNuevo(admin, "Profe Ramírez " + sufijo);
        otraProfe = Instalacion.docenteNuevo(admin, "Profe Díaz " + sufijo);
        for (String curso : new String[] {"10.º A", "10.º B", "11.º A", "11.º B"}) {
            Aula.crearGrupo(admin, curso + " " + sufijo, "Profe Ramírez " + sufijo);
        }
        ajeno = Aula.crearGrupo(admin, "9.º C " + sufijo, "Profe Díaz " + sufijo);
    }

    @Test
    void el_docente_ve_sus_cuatro_grupos_en_mis_grupos_y_ninguno_ajeno() {
        ClienteApp.Respuesta mios = profe.get("/grupos");

        assertThat(mios.estado()).isEqualTo(200);
        assertThat(mios.cuerpo()).contains("Mis grupos", "10.º A " + sufijo, "10.º B " + sufijo, "11.º A " + sufijo, "11.º B " + sufijo)
                .doesNotContain("9.º C " + sufijo);
        assertThat(profe.get("/").cuerpo()).as("el menú tiene la entrada del aula").contains("href=\"/grupos\"");
    }

    @Test
    void un_grupo_ajeno_responde_404_como_uno_que_no_existe() {
        assertThat(profe.get("/grupos/" + ajeno).estado()).isEqualTo(404);
        assertThat(profe.get("/grupos/" + UUID.randomUUID()).estado()).isEqualTo(404);
        assertThat(otraProfe.get("/grupos/" + ajeno).estado()).isEqualTo(200);
    }

    @Test
    void el_docente_no_crea_grupos_ni_cambia_docentes() {
        ClienteApp.Respuesta r = profe.postFormulario("/grupos", java.util.Map.of("nombre", "Mío " + sufijo, "docente", ""));
        assertThat(r.estado()).isEqualTo(404);
        assertThat(admin.get("/grupos").cuerpo()).doesNotContain("Mío " + sufijo);
    }

    @Test
    void una_persona_y_un_estudiante_no_entran_al_aula() {
        ClienteApp persona = Instalacion.entraComo(Instalacion.personaNueva(admin, "persona", "persona"), "persona");
        ClienteApp estudiante = Instalacion.estudianteNuevo(admin, "Estudiante " + sufijo);

        assertThat(persona.get("/grupos").estado()).isEqualTo(403);
        assertThat(estudiante.get("/grupos").estado()).isEqualTo(403);
        assertThat(estudiante.get("/grupos/" + ajeno).estado()).isEqualTo(403);
    }

    @Test
    void quien_administra_ve_todos_los_grupos_con_su_docente() {
        assertThat(admin.get("/grupos").cuerpo()).contains("9.º C " + sufijo, "Profe Díaz " + sufijo, "10.º A " + sufijo, "Profe Ramírez " + sufijo);
    }
}
