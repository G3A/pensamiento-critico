package pensamiento.contrato;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pensamiento.nucleo.ClaseDeAula;
import pensamiento.nucleo.Competencia;
import pensamiento.nucleo.Grupo;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.IntentoDojo;
import pensamiento.nucleo.NivelBloom;
import pensamiento.nucleo.PanelGrupo;
import pensamiento.nucleo.Usuario.RolGlobal;
import pensamiento.nucleo.puertos.RepositorioAula;
import pensamiento.nucleo.puertos.RepositorioAula.Actor;

/**
 * Contrato del aula con RLS por rol (hito 10, entregable 1, condición de entrada): el administrador ve todos los grupos de su
 * institución; cada docente, solo los suyos; el estudiante, solo los suyos y nunca la lista de sus compañeros; una persona,
 * ninguno. El docente crea estudiantes solo en su grupo y un nombre repetido no lanza. La clase se abre, se encuentra por su
 * código, muestra la lista solo si su docente la activó y se termina. El panel trae solo agregados: el texto centinela que
 * escribió un estudiante no aparece en él, y un docente ajeno o un estudiante no reciben panel.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class RepositorioAulaContract {

    /** Cuentas ya existentes en el backend, cada una con su rol, en dos instituciones. */
    public record Escenario(UUID institucion, UUID administrador, UUID docenteA, UUID docenteB, UUID persona, UUID otraInstitucion,
                            UUID administradorDeOtra) {

        public Actor admin() {
            return new Actor(administrador, institucion, RolGlobal.ADMINISTRADOR);
        }

        public Actor a() {
            return new Actor(docenteA, institucion, RolGlobal.DOCENTE);
        }

        public Actor b() {
            return new Actor(docenteB, institucion, RolGlobal.DOCENTE);
        }

        public Actor alguien() {
            return new Actor(persona, institucion, RolGlobal.PERSONA);
        }

        public Actor estudiante(UUID id) {
            return new Actor(id, institucion, RolGlobal.ESTUDIANTE);
        }
    }

    protected static final Instant AHORA = Instant.parse("2026-10-20T13:00:00Z");
    protected static final LocalDate HOY = LocalDate.parse("2026-10-20");
    private static final IdTecnica FALACIAS = IdTecnica.de("T13");

    protected abstract Escenario escenario();

    protected abstract RepositorioAula aula();

    /** El estudiante responde un reto del Dojo (como él mismo). */
    protected abstract void responde(UUID estudiante, IntentoDojo intento, Competencia competencia);

    /** Queda en la auditoría que esa cuenta entró en ese instante. */
    protected abstract void entro(UUID usuario, Instant cuando);

    private static String unico(String base) {
        return base + " " + UUID.randomUUID().toString().substring(0, 8);
    }

    private Grupo grupoDe(Actor docente) {
        Escenario e = escenario();
        return aula().crearGrupo(e.admin(), Grupo.nuevo(UUID.randomUUID(), e.institucion(), unico("Grupo"), Optional.of(docente.id()))).orElseThrow();
    }

    private UUID estudianteEn(Actor quien, Grupo grupo, String nombre) {
        return aula().crearEstudiante(quien, grupo.id(), UUID.randomUUID(), nombre).orElseThrow();
    }

    @Test
    void el_administrador_crea_grupos_y_un_nombre_repetido_no_lanza_ni_rompe_la_transaccion() {
        Escenario e = escenario();
        String nombre = unico("Décimo A");
        Optional<Grupo> primero = aula().crearGrupo(e.admin(), Grupo.nuevo(UUID.randomUUID(), e.institucion(), nombre, Optional.empty()));
        Optional<Grupo> repetido = aula().crearGrupo(e.admin(), Grupo.nuevo(UUID.randomUUID(), e.institucion(), nombre.toUpperCase(), Optional.empty()));
        Optional<Grupo> despues = aula().crearGrupo(e.admin(), Grupo.nuevo(UUID.randomUUID(), e.institucion(), unico("Once B"), Optional.empty()));

        assertThat(primero).map(Grupo::nombre).contains(nombre);
        assertThat(primero).map(Grupo::listaEnClase).contains(false);
        assertThat(primero).map(Grupo::iaEnClase).contains(false);
        assertThat(repetido).isEmpty();
        assertThat(despues).isPresent();
    }

    @Test
    void solo_el_administrador_crea_grupos_y_asigna_su_docente() {
        Escenario e = escenario();
        Grupo g = grupoDe(e.a());

        assertThat(aula().crearGrupo(e.a(), Grupo.nuevo(UUID.randomUUID(), e.institucion(), unico("Mío"), Optional.of(e.docenteA())))).isEmpty();
        assertThat(aula().asignarDocente(e.a(), g.id(), Optional.of(e.docenteB()))).isFalse();
        assertThat(aula().asignarDocente(e.admin(), g.id(), Optional.of(e.docenteB()))).isTrue();
        assertThat(aula().grupo(e.admin(), g.id())).map(Grupo::docenteId).contains(Optional.of(e.docenteB()));
        assertThat(aula().grupo(e.a(), g.id())).isEmpty();
    }

    @Test
    void cada_rol_ve_solo_sus_grupos() {
        Escenario e = escenario();
        Grupo deA = grupoDe(e.a());
        Grupo deB = grupoDe(e.b());
        UUID ana = estudianteEn(e.a(), deA, unico("Ana"));

        assertThat(aula().grupos(e.admin())).extracting(Grupo::id).contains(deA.id(), deB.id());
        assertThat(aula().grupos(e.a())).extracting(Grupo::id).contains(deA.id()).doesNotContain(deB.id());
        assertThat(aula().grupos(e.b())).extracting(Grupo::id).contains(deB.id()).doesNotContain(deA.id());
        assertThat(aula().grupos(e.estudiante(ana))).extracting(Grupo::id).containsExactly(deA.id());
        assertThat(aula().grupos(e.alguien())).isEmpty();
        assertThat(aula().grupo(e.b(), deA.id())).isEmpty();
    }

    @Test
    void el_docente_crea_estudiantes_solo_en_su_grupo_y_un_nombre_repetido_no_lanza() {
        Escenario e = escenario();
        Grupo deA = grupoDe(e.a());
        Grupo deB = grupoDe(e.b());
        String nombre = unico("María José");

        Optional<UUID> creada = aula().crearEstudiante(e.a(), deA.id(), UUID.randomUUID(), nombre);
        Optional<UUID> repetida = aula().crearEstudiante(e.a(), deA.id(), UUID.randomUUID(), nombre.toUpperCase().replace("Í", "I"));
        Optional<UUID> enAjeno = aula().crearEstudiante(e.a(), deB.id(), UUID.randomUUID(), unico("Pedro"));
        Optional<UUID> despues = aula().crearEstudiante(e.a(), deA.id(), UUID.randomUUID(), unico("Luis"));

        assertThat(creada).isPresent();
        assertThat(repetida).isEmpty();
        assertThat(enAjeno).isEmpty();
        assertThat(despues).isPresent();
        assertThat(aula().miembros(e.a(), deA.id())).extracting(RepositorioAula.Miembro::id).containsExactlyInAnyOrder(creada.get(), despues.get());
        assertThat(aula().miembros(e.a(), deA.id())).allSatisfy(m -> assertThat(m.tieneClave()).isFalse());
        assertThat(aula().miembros(e.admin(), deA.id())).hasSize(2);
        assertThat(aula().miembros(e.b(), deA.id())).isEmpty();
        assertThat(aula().miembros(e.estudiante(creada.get()), deA.id())).as("un estudiante no ve la lista de su grupo").isEmpty();
        assertThat(aula().miembros(e.alguien(), deA.id())).isEmpty();
    }

    @Test
    void el_tema_y_las_opciones_los_cambia_el_docente_del_grupo_o_el_administrador() {
        Escenario e = escenario();
        Grupo g = grupoDe(e.a());
        UUID ana = estudianteEn(e.a(), g, unico("Ana"));
        LocalDate viernes = HOY.plusDays(4);

        assertThat(aula().asignarTema(e.b(), g.id(), Optional.of(FALACIAS), Optional.empty(), AHORA)).isFalse();
        assertThat(aula().asignarTema(e.estudiante(ana), g.id(), Optional.of(FALACIAS), Optional.empty(), AHORA)).isFalse();
        assertThat(aula().asignarTema(e.a(), g.id(), Optional.of(FALACIAS), Optional.of(viernes), AHORA)).isTrue();
        assertThat(aula().cambiarOpciones(e.b(), g.id(), true, true)).isFalse();
        assertThat(aula().cambiarOpciones(e.a(), g.id(), true, false)).isTrue();

        Grupo visto = aula().grupo(e.estudiante(ana), g.id()).orElseThrow();
        assertThat(visto.tema()).contains(FALACIAS);
        assertThat(visto.temaHasta()).contains(viernes);
        assertThat(visto.temaVigente(viernes)).contains(FALACIAS);
        assertThat(visto.temaVigente(viernes.plusDays(1))).isEmpty();
        assertThat(visto.listaEnClase()).isTrue();
        assertThat(visto.iaEnClase()).isFalse();

        assertThat(aula().asignarTema(e.admin(), g.id(), Optional.empty(), Optional.of(viernes), AHORA)).isTrue();
        assertThat(aula().grupo(e.a(), g.id()).orElseThrow().tema()).isEmpty();
        assertThat(aula().grupo(e.a(), g.id()).orElseThrow().temaHasta()).isEmpty();
    }

    @Test
    void la_clase_se_abre_se_ve_desde_el_grupo_y_se_termina() {
        Escenario e = escenario();
        Grupo g = grupoDe(e.a());
        UUID ana = estudianteEn(e.a(), g, unico("Ana"));

        assertThat(aula().abrirClase(e.b(), g.id(), "h-" + UUID.randomUUID(), AHORA, AHORA.plus(Duration.ofMinutes(50)))).isEmpty();
        ClaseDeAula primera = aula().abrirClase(e.a(), g.id(), "h-" + UUID.randomUUID(), AHORA, AHORA.plus(Duration.ofMinutes(50))).orElseThrow();
        ClaseDeAula segunda = aula().abrirClase(e.a(), g.id(), "h-" + UUID.randomUUID(), AHORA.plusSeconds(60), AHORA.plus(Duration.ofMinutes(60))).orElseThrow();

        assertThat(aula().claseAbierta(e.estudiante(ana), g.id(), AHORA.plusSeconds(120))).map(ClaseDeAula::id).contains(segunda.id());
        assertThat(primera.id()).isNotEqualTo(segunda.id());
        assertThat(aula().claseAbierta(e.b(), g.id(), AHORA.plusSeconds(120))).isEmpty();
        assertThat(aula().claseAbierta(e.alguien(), g.id(), AHORA.plusSeconds(120))).isEmpty();
        assertThat(aula().terminarClase(e.b(), g.id(), AHORA.plusSeconds(180))).isFalse();
        assertThat(aula().terminarClase(e.a(), g.id(), AHORA.plusSeconds(180))).isTrue();
        assertThat(aula().terminarClase(e.a(), g.id(), AHORA.plusSeconds(240))).isFalse();
        assertThat(aula().claseAbierta(e.a(), g.id(), AHORA.plusSeconds(240))).isEmpty();
    }

    @Test
    void con_el_codigo_de_la_clase_la_lista_aparece_solo_si_el_docente_la_activo_y_solo_la_de_ese_grupo() {
        Escenario e = escenario();
        Grupo g = grupoDe(e.a());
        Grupo otro = grupoDe(e.b());
        String ana = unico("Ana");
        String beto = unico("Beto");
        estudianteEn(e.a(), g, ana);
        estudianteEn(e.a(), g, beto);
        estudianteEn(e.b(), otro, unico("Carla"));
        String hash = "h-" + UUID.randomUUID();
        aula().abrirClase(e.a(), g.id(), hash, AHORA, AHORA.plus(Duration.ofMinutes(50)));
        aula().abrirClase(e.b(), otro.id(), "h-" + UUID.randomUUID(), AHORA, AHORA.plus(Duration.ofMinutes(50)));

        Optional<RepositorioAula.ClasePorCodigo> clase = aula().clasePorCodigo(e.institucion(), hash, AHORA.plusSeconds(60));
        assertThat(clase).map(RepositorioAula.ClasePorCodigo::grupoId).contains(g.id());
        assertThat(clase).map(RepositorioAula.ClasePorCodigo::lista).contains(false);
        assertThat(aula().listaDeClase(e.institucion(), hash, AHORA.plusSeconds(60))).as("lista apagada por defecto").isEmpty();

        aula().cambiarOpciones(e.a(), g.id(), true, false);
        assertThat(aula().listaDeClase(e.institucion(), hash, AHORA.plusSeconds(60))).containsExactlyInAnyOrder(ana, beto);
        assertThat(aula().clasePorCodigo(e.otraInstitucion(), hash, AHORA.plusSeconds(60))).isEmpty();
        assertThat(aula().listaDeClase(e.otraInstitucion(), hash, AHORA.plusSeconds(60))).isEmpty();
        assertThat(aula().clasePorCodigo(e.institucion(), "h-no-existe", AHORA.plusSeconds(60))).isEmpty();
        assertThat(aula().listaDeClase(e.institucion(), hash, AHORA.plus(Duration.ofMinutes(51)))).as("la clase ya cerró").isEmpty();

        aula().terminarClase(e.a(), g.id(), AHORA.plusSeconds(120));
        assertThat(aula().clasePorCodigo(e.institucion(), hash, AHORA.plusSeconds(180))).isEmpty();
        assertThat(aula().listaDeClase(e.institucion(), hash, AHORA.plusSeconds(180))).isEmpty();
    }

    @Test
    void el_panel_trae_solo_agregados_y_nunca_el_texto_de_un_estudiante() {
        Escenario e = escenario();
        Grupo g = grupoDe(e.a());
        String centinela = "CENTINELA-" + UUID.randomUUID();
        UUID ana = estudianteEn(e.a(), g, unico("Ana"));
        UUID beto = estudianteEn(e.a(), g, unico("Beto"));
        String carla = unico("Carla");
        estudianteEn(e.a(), g, carla);
        responde(ana, intento("generalizacion-i1", "T13:generalizacion", NivelBloom.IDENTIFICAR, centinela, true, HOY), competencia(NivelBloom.IDENTIFICAR, 1, 1));
        responde(ana, intento("generalizacion-c1", "T13:generalizacion", NivelBloom.CREAR, centinela + " reescrito", false, HOY),
                competencia(NivelBloom.ANALIZAR, 2, 1));
        responde(beto, intento("ad-hominem-i1", "T13:ad-hominem", NivelBloom.IDENTIFICAR, centinela, false, HOY.minusDays(5)),
                competencia(NivelBloom.IDENTIFICAR, 1, 0));
        entro(ana, AHORA.minusSeconds(600));
        entro(beto, AHORA.minus(Duration.ofDays(5)));
        Instant comienzoDelDia = AHORA.minus(Duration.ofHours(3));
        RepositorioAula.Periodo periodo = new RepositorioAula.Periodo(comienzoDelDia, Optional.of(AHORA.minusSeconds(900)), HOY, Optional.of(FALACIAS), 3);

        PanelGrupo panel = aula().panel(e.a(), g.id(), periodo).orElseThrow();

        assertThat(panel.miembros()).isEqualTo(3);
        assertThat(panel.entraronHoy()).isEqualTo(1);
        assertThat(panel.entraronEnClase()).isEqualTo(1);
        assertThat(panel.retosHoy()).isEqualTo(2);
        assertThat(panel.respondieronHoy()).isEqualTo(1);
        assertThat(panel.retosDelTema()).isEqualTo(3);
        assertThat(panel.conceptos()).containsExactly(
                new PanelGrupo.Concepto("T13:ad-hominem", false, 1, 0, 1),
                new PanelGrupo.Concepto("T13:generalizacion", false, 1, 1, 1),
                new PanelGrupo.Concepto("T13:generalizacion", true, 1, 0, 1));
        assertThat(panel.bloom()).containsExactlyInAnyOrder(new PanelGrupo.Nivel("analizar", 1), new PanelGrupo.Nivel("identificar", 1),
                new PanelGrupo.Nivel("sin empezar", 1));
        assertThat(panel.sinPracticar()).extracting(PanelGrupo.SinPracticar::nombre).containsExactly(carla, nombreDe(e, g, beto));
        assertThat(panel.sinPracticar()).extracting(PanelGrupo.SinPracticar::dias).containsExactly(Optional.empty(), Optional.of(5));
        assertThat(panel.toString()).as("ningún texto del estudiante en el panel").doesNotContain(centinela);

        assertThat(aula().panel(e.b(), g.id(), periodo)).as("docente ajeno").isEmpty();
        assertThat(aula().panel(e.estudiante(ana), g.id(), periodo)).as("un estudiante").isEmpty();
        assertThat(aula().panel(e.alguien(), g.id(), periodo)).isEmpty();
        assertThat(aula().panel(e.admin(), g.id(), periodo)).contains(panel);
    }

    private String nombreDe(Escenario e, Grupo g, UUID id) {
        return aula().miembros(e.a(), g.id()).stream().filter(m -> m.id().equals(id)).findFirst().orElseThrow().nombre();
    }

    private static IntentoDojo intento(String reto, String concepto, NivelBloom nivel, String respuesta, boolean acierto, LocalDate dia) {
        Instant cuando = dia.atTime(12, 0).toInstant(java.time.ZoneOffset.UTC);
        return new IntentoDojo(UUID.randomUUID(), "clave-" + UUID.randomUUID(), reto, FALACIAS, concepto, nivel, respuesta, acierto, dia, cuando);
    }

    private static Competencia competencia(NivelBloom nivel, int intentos, int aciertos) {
        return new Competencia(FALACIAS, nivel, intentos, aciertos, AHORA);
    }

}
