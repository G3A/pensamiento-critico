package pensamiento.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pensamiento.testutil.BaseDatosDePrueba;
import pensamiento.testutil.SemillaDeInstitucion;

/**
 * Hito 10, entregable 1 (condición de entrada del aula): la RLS por rol, por SQL. Una estudiante tiene una fila con texto
 * centinela en cada tabla con RLS (SemillaDeInstitucion: Dojo, ejecuciones, expedientes, afirmaciones, argumentos,
 * documentos compartidos, sesiones del Consejero…). Su docente, consultando como él, ve de ella solo la fila de su cuenta
 * (el nombre), el grupo, su fila de miembro y la clase; de todo lo demás, cero filas. Un docente de otro grupo y una persona
 * no ven nada de ella. La lista de tablas sale de pg_class: una tabla nueva con RLS sin su fila sembrada hace fallar
 * AislamientoEntreInstitucionesIT.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RlsPorRolIT {

    /**
     * Lo único que el docente ve de su estudiante: la cuenta (nombre y estado), el hash de su código de alta, el grupo, la
     * pertenencia y la clase (y su institución). Ninguna es texto escrito por ella.
     */
    private static final Set<String> VISIBLES_PARA_SU_DOCENTE = Set.of("institucion", "usuario", "codigo_cuenta", "grupo", "miembro_grupo", "sesion_aula");

    private final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private SemillaDeInstitucion estudiante;
    private UUID docente;
    private UUID otroDocente;
    private UUID persona;
    private UUID administrador;

    @BeforeAll
    void sembrar() {
        estudiante = new SemillaDeInstitucion(bd);
        UUID institucion = estudiante.institucion();
        docente = bd.crearUsuario(institucion, "docente del grupo", "docente");
        otroDocente = bd.crearUsuario(institucion, "docente de otro grupo", "docente");
        persona = bd.crearUsuario(institucion, "una persona", "persona");
        administrador = bd.crearUsuario(institucion, "quien administra", "administrador");
        bd.jdbcAdmin().sql("UPDATE grupo SET docente_id = :d WHERE id = :g").param("d", docente).param("g", estudiante.id("grupo")).update();
        bd.jdbcAdmin().sql("INSERT INTO grupo (institucion_id, nombre, docente_id) VALUES (:i, 'otro grupo', :d)")
                .param("i", institucion).param("d", otroDocente).update();
    }

    @AfterAll
    void limpiar() {
        estudiante.borrar();
    }

    private Map<String, Integer> vistoPor(UUID quien) {
        return bd.comoUsuario(quien, estudiante.institucion(), () -> estudiante.contarMarcasDeLaPersona(bd.jdbcApp()));
    }

    @Test
    void su_docente_recibe_cero_filas_de_todo_texto_de_la_estudiante() {
        Map<String, Integer> visto = vistoPor(docente);

        assertThat(visto).allSatisfy((tabla, filas) -> {
            if (!VISIBLES_PARA_SU_DOCENTE.contains(tabla)) {
                assertThat(filas).as("filas de la estudiante en %s vistas por su docente", tabla).isZero();
            }
        });
        assertThat(visto).containsEntry("usuario", 1).containsEntry("grupo", 1).containsEntry("miembro_grupo", 1).containsEntry("sesion_aula", 1);
    }

    @Test
    void el_documento_compartido_de_una_estudiante_no_lo_ve_nadie_mas() {
        assertThat(vistoPor(docente)).containsEntry("documento", 0).containsEntry("fragmento", 0);
        assertThat(vistoPor(persona)).containsEntry("documento", 0).containsEntry("fragmento", 0);
        assertThat(vistoPor(administrador)).containsEntry("documento", 0).containsEntry("fragmento", 0);
    }

    @Test
    void un_docente_de_otro_grupo_y_una_persona_no_ven_nada_de_ella_ni_de_su_grupo() {
        for (UUID quien : new UUID[] {otroDocente, persona}) {
            Map<String, Integer> visto = vistoPor(quien);
            assertThat(visto).allSatisfy((tabla, filas) -> {
                if (!tabla.equals("institucion")) {
                    assertThat(filas).as("filas de la estudiante en %s vistas por %s", tabla, quien).isZero();
                }
            });
        }
    }

    @Test
    void quien_administra_ve_la_cuenta_y_el_grupo_pero_tampoco_su_texto() {
        Map<String, Integer> visto = vistoPor(administrador);

        assertThat(visto).allSatisfy((tabla, filas) -> {
            if (!VISIBLES_PARA_SU_DOCENTE.contains(tabla)) {
                assertThat(filas).as("filas de la estudiante en %s vistas por quien administra", tabla).isZero();
            }
        });
        assertThat(visto).containsEntry("usuario", 1).containsEntry("grupo", 1);
    }

    @Test
    void la_estudiante_si_ve_todo_lo_suyo_asi_que_la_prueba_no_pasa_en_vacio() {
        assertThat(vistoPor(estudiante.usuario())).isEqualTo(estudiante.sembradas());
    }

    @Test
    void una_persona_solo_ve_su_propia_cuenta_y_un_docente_solo_las_de_sus_estudiantes() {
        long vistasPorPersona = bd.comoUsuario(persona, estudiante.institucion(),
                () -> bd.jdbcApp().sql("SELECT count(*) FROM usuario").query(Long.class).single());
        long vistasPorDocente = bd.comoUsuario(docente, estudiante.institucion(),
                () -> bd.jdbcApp().sql("SELECT count(*) FROM usuario").query(Long.class).single());
        long vistasPorAdministrador = bd.comoUsuario(administrador, estudiante.institucion(),
                () -> bd.jdbcApp().sql("SELECT count(*) FROM usuario").query(Long.class).single());

        assertThat(vistasPorPersona).isEqualTo(1);
        assertThat(vistasPorDocente).as("él y su estudiante").isEqualTo(2);
        assertThat(vistasPorAdministrador).isEqualTo(5);
    }

    @Test
    void sin_usuario_fijado_como_al_entrar_se_ven_las_cuentas_de_la_institucion() {
        long vistas = bd.comoInstitucion(estudiante.institucion(), () -> bd.jdbcApp().sql("SELECT count(*) FROM usuario").query(Long.class).single());
        assertThat(vistas).isEqualTo(5);
    }
}
