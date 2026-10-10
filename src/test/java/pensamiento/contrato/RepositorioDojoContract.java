package pensamiento.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pensamiento.nucleo.Competencia;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.IntentoDojo;
import pensamiento.nucleo.NivelBloom;
import pensamiento.nucleo.puertos.RepositorioDojo;

/**
 * Contrato de los intentos del Dojo y la competencia (docs/dojo.md): lo guardado se lee igual, con tildes y ñ; el doble clic
 * no duplica ni pisa la competencia; otra persona no ve nada; los intentos van del más viejo al más nuevo; la competencia de
 * un tema se reescribe con cada intento; restaurar no duplica intentos y reemplaza la competencia; un intento y una
 * competencia de temas distintos no se guardan.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class RepositorioDojoContract {

    /** Dos usuarios distintos de la misma institución, ya existentes en el backend. */
    public record Personas(UUID institucion, UUID usuarioA, UUID usuarioB) {
    }

    protected static final Instant RESPONDIDO = Instant.parse("2026-10-07T15:00:00Z");
    protected static final LocalDate HOY = LocalDate.parse("2026-10-07");
    private static final IdTecnica FALACIAS = IdTecnica.de("T13");
    private static final IdTecnica SESGOS = IdTecnica.de("T14");

    protected abstract Personas personas();

    protected abstract RepositorioDojo comoUsuario(UUID usuarioId);

    private static IntentoDojo intento(IdTecnica tecnica, String respuesta, boolean acierto, Instant cuando) {
        return new IntentoDojo(UUID.randomUUID(), "clave-" + UUID.randomUUID(), "generalizacion-c1", tecnica, "T13:generalizacion",
                NivelBloom.CREAR, respuesta, acierto, HOY, cuando);
    }

    @Test
    void lo_guardado_se_lee_igual_con_tildes_y_enes() {
        Personas p = personas();
        RepositorioDojo repo = comoUsuario(p.usuarioA());
        IntentoDojo i = intento(FALACIAS, "Es un solo caso; antes de decidir contaría cuántos panes se venden en un año, señora.", true, RESPONDIDO);
        Competencia c = new Competencia(FALACIAS, NivelBloom.ANALIZAR, 7, 5, RESPONDIDO);

        assertThat(repo.guardar(p.usuarioA(), p.institucion(), i, c)).isTrue();

        assertThat(repo.intentos(p.usuarioA())).contains(i);
        assertThat(repo.competencias(p.usuarioA())).contains(c);
    }

    @Test
    void responder_dos_veces_el_mismo_formulario_guarda_un_solo_intento_y_no_pisa_la_competencia() {
        Personas p = personas();
        RepositorioDojo repo = comoUsuario(p.usuarioA());
        IntentoDojo primero = intento(SESGOS, "a", true, RESPONDIDO);
        IntentoDojo repetido = new IntentoDojo(UUID.randomUUID(), primero.clave(), "anclaje-i1", SESGOS, "T14:anclaje", NivelBloom.IDENTIFICAR,
                "b", false, HOY, RESPONDIDO.plusSeconds(1));
        Competencia antes = new Competencia(SESGOS, NivelBloom.IDENTIFICAR, 1, 1, RESPONDIDO);

        assertThat(repo.guardar(p.usuarioA(), p.institucion(), primero, antes)).isTrue();
        assertThat(repo.guardar(p.usuarioA(), p.institucion(), repetido, new Competencia(SESGOS, NivelBloom.IDENTIFICAR, 2, 1, RESPONDIDO))).isFalse();

        assertThat(repo.intentos(p.usuarioA())).filteredOn(x -> x.clave().equals(primero.clave())).containsExactly(primero);
        assertThat(repo.competencias(p.usuarioA())).filteredOn(x -> x.tecnica().equals(SESGOS)).containsExactly(antes);
    }

    @Test
    void otra_persona_no_ve_los_intentos_ni_la_competencia() {
        Personas p = personas();
        IntentoDojo i = intento(FALACIAS, "c", false, RESPONDIDO);
        comoUsuario(p.usuarioA()).guardar(p.usuarioA(), p.institucion(), i, new Competencia(FALACIAS, NivelBloom.IDENTIFICAR, 1, 0, RESPONDIDO));

        assertThat(comoUsuario(p.usuarioB()).intentos(p.usuarioB())).extracting(IntentoDojo::id).doesNotContain(i.id());
        assertThat(comoUsuario(p.usuarioB()).competencias(p.usuarioB())).extracting(Competencia::tecnica).doesNotContain(FALACIAS);
    }

    @Test
    void los_intentos_vienen_del_mas_viejo_al_mas_nuevo() {
        Personas p = personas();
        RepositorioDojo repo = comoUsuario(p.usuarioA());
        IntentoDojo despues = intento(FALACIAS, "d", true, RESPONDIDO.plusSeconds(7200));
        IntentoDojo antes = intento(FALACIAS, "a", true, RESPONDIDO.plusSeconds(3600));
        repo.guardar(p.usuarioA(), p.institucion(), despues, new Competencia(FALACIAS, NivelBloom.IDENTIFICAR, 1, 1, despues.creadoEn()));
        repo.guardar(p.usuarioA(), p.institucion(), antes, new Competencia(FALACIAS, NivelBloom.IDENTIFICAR, 2, 2, despues.creadoEn()));

        assertThat(repo.intentos(p.usuarioA())).extracting(IntentoDojo::id).containsSubsequence(antes.id(), despues.id());
    }

    @Test
    void la_competencia_de_un_tema_se_reescribe_con_cada_intento() {
        Personas p = personas();
        RepositorioDojo repo = comoUsuario(p.usuarioA());
        IdTecnica datos = IdTecnica.de("T18");
        Competencia ultima = new Competencia(datos, NivelBloom.EVALUAR, 12, 10, RESPONDIDO.plusSeconds(60));
        repo.guardar(p.usuarioA(), p.institucion(), intento(datos, "a", true, RESPONDIDO), new Competencia(datos, NivelBloom.ANALIZAR, 11, 9, RESPONDIDO));
        repo.guardar(p.usuarioA(), p.institucion(), intento(datos, "b", true, RESPONDIDO.plusSeconds(60)), ultima);

        assertThat(repo.competencias(p.usuarioA())).filteredOn(c -> c.tecnica().equals(datos)).containsExactly(ultima);
    }

    @Test
    void restaurar_no_duplica_intentos_y_reemplaza_la_competencia() {
        Personas p = personas();
        RepositorioDojo repo = comoUsuario(p.usuarioA());
        IdTecnica fuentes = IdTecnica.de("T19");
        IntentoDojo importado = intento(fuentes, "Buscaría el aviso original en la página oficial.", true, RESPONDIDO.minusSeconds(86_400));
        Competencia restaurada = new Competencia(fuentes, NivelBloom.CREAR, 30, 28, RESPONDIDO.minusSeconds(86_400));

        repo.restaurar(p.usuarioA(), p.institucion(), importado);
        repo.restaurar(p.usuarioA(), p.institucion(), importado);
        repo.restaurar(p.usuarioA(), p.institucion(), new Competencia(fuentes, NivelBloom.IDENTIFICAR, 1, 0, RESPONDIDO));
        repo.restaurar(p.usuarioA(), p.institucion(), restaurada);

        assertThat(repo.intentos(p.usuarioA())).filteredOn(i -> i.id().equals(importado.id())).containsExactly(importado);
        assertThat(repo.competencias(p.usuarioA())).filteredOn(c -> c.tecnica().equals(fuentes)).containsExactly(restaurada);
    }

    @Test
    void un_intento_y_una_competencia_de_temas_distintos_no_se_guardan() {
        Personas p = personas();
        RepositorioDojo repo = comoUsuario(p.usuarioA());
        IntentoDojo i = intento(FALACIAS, "a", true, RESPONDIDO);

        assertThatThrownBy(() -> repo.guardar(p.usuarioA(), p.institucion(), i, new Competencia(SESGOS, NivelBloom.IDENTIFICAR, 1, 1, RESPONDIDO)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repo.intentos(p.usuarioA())).extracting(IntentoDojo::id).doesNotContain(i.id());
    }
}
