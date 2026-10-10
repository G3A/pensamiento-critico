package pensamiento.contrato;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.CambioOpinion;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.OrigenAfirmacion;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.SentidoAfirmacion;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.puertos.RegistroDeRazonamiento;
import pensamiento.nucleo.puertos.RegistroDeRazonamiento.CambioRegistrado;
import pensamiento.nucleo.puertos.RegistroDeRazonamiento.EjecucionEnDiario;
import pensamiento.nucleo.puertos.RegistroDeRazonamiento.PosturaRegistrada;

/**
 * Contrato de la lectura del diario de razonamiento y del registro de cambios (T45, T46, P20): las ejecuciones desde una
 * fecha, en orden, con el nombre de su expediente (vacío si no tiene o si se borró) y cuántos cambios registraron; los
 * cambios con la técnica que los registró; las posturas con la fecha de la última ejecución que las usó, sin otros roles;
 * y nada de otra persona. Cada prueba usa su propio tramo de fechas, porque el backend real acumula lo de las demás.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class RegistroDeRazonamientoContract {

    /** Dos usuarios distintos de la misma institución, ya existentes en el backend. */
    public record Personas(UUID institucion, UUID usuarioA, UUID usuarioB) {
    }

    protected abstract Personas personas();

    protected abstract RegistroDeRazonamiento comoUsuario(UUID usuarioId);

    /** Guarda una ejecución del usuario con esas afirmaciones (y su expediente, si lo hay) y devuelve su identificador. */
    protected abstract UUID dadaUnaEjecucion(UUID usuarioId, IdTecnica tecnica, String resumen, Optional<UUID> expediente,
                                             List<AfirmacionConRol> afirmaciones, Instant cuando);

    /** Crea un expediente del usuario con ese nombre y devuelve su identificador. */
    protected abstract UUID dadoUnExpediente(UUID usuarioId, String nombre);

    /** Borra (lógicamente) un expediente del usuario. */
    protected abstract void borrarExpediente(UUID usuarioId, UUID expedienteId);

    /** Guarda un cambio de opinión del usuario sobre una afirmación de esa ejecución. */
    protected abstract void dadoUnCambio(UUID usuarioId, UUID ejecucionId, CambioOpinion.Declarado cambio, Instant cuando);

    private static AfirmacionConRol postura(UUID id, String texto, SentidoAfirmacion sentido) {
        return new AfirmacionConRol(id, texto, TipoAfirmacion.JUICIO_DE_VALOR, RolAfirmacion.POSTURA, sentido, OrigenAfirmacion.USUARIO);
    }

    @Test
    void las_ejecuciones_desde_una_fecha_vienen_en_orden_con_su_expediente_y_sus_cambios() {
        Personas p = personas();
        Instant base = Instant.parse("2031-03-02T12:00:00Z");
        UUID expediente = dadoUnExpediente(p.usuarioA(), "La segunda sucursal, señora");
        UUID antes = dadaUnaEjecucion(p.usuarioA(), IdTecnica.de("T31"), "Matriz: dónde abrir", Optional.of(expediente), List.of(),
                base.minusSeconds(86_400));
        UUID afirmacion = UUID.randomUUID();
        UUID conCambio = dadaUnaEjecucion(p.usuarioA(), IdTecnica.de("T08"), "Sesión socrática: abrir en el centro", Optional.of(expediente),
                List.of(postura(afirmacion, "Conviene abrir en el centro", SentidoAfirmacion.PRODUCIDA)), base);
        dadoUnCambio(p.usuarioA(), conCambio, new CambioOpinion.Declarado(UUID.randomUUID(), afirmacion, 80, 60, CambioOpinion.Causa.EVIDENCIA), base);
        UUID sinExpediente = dadaUnaEjecucion(p.usuarioA(), IdTecnica.de("T14"), "Sesgos: compra del horno", Optional.empty(), List.of(),
                base.plusSeconds(3600));

        List<EjecucionEnDiario> leidas = comoUsuario(p.usuarioA()).ejecucionesDesde(p.usuarioA(), base).stream()
                .filter(e -> List.of(antes, conCambio, sinExpediente).contains(e.id())).toList();

        assertThat(leidas).containsExactly(
                new EjecucionEnDiario(conCambio, IdTecnica.de("T08"), "Sesión socrática: abrir en el centro",
                        Optional.of("La segunda sucursal, señora"), 1, base),
                new EjecucionEnDiario(sinExpediente, IdTecnica.de("T14"), "Sesgos: compra del horno", Optional.empty(), 0, base.plusSeconds(3600)));
    }

    @Test
    void una_ejecucion_cuyo_expediente_se_borro_queda_sin_expediente() {
        Personas p = personas();
        Instant base = Instant.parse("2031-05-04T12:00:00Z");
        UUID expediente = dadoUnExpediente(p.usuarioA(), "Cámaras del barrio");
        UUID ejecucion = dadaUnaEjecucion(p.usuarioA(), IdTecnica.de("T36"), "Equipo rojo: las cámaras", Optional.of(expediente), List.of(), base);
        borrarExpediente(p.usuarioA(), expediente);

        assertThat(comoUsuario(p.usuarioA()).ejecucionesDesde(p.usuarioA(), base)).filteredOn(e -> e.id().equals(ejecucion))
                .extracting(EjecucionEnDiario::expediente).containsExactly(Optional.empty());
    }

    @Test
    void los_cambios_traen_la_tecnica_que_los_registro_del_mas_viejo_al_mas_nuevo() {
        Personas p = personas();
        Instant base = Instant.parse("2031-07-06T12:00:00Z");
        UUID a1 = UUID.randomUUID();
        UUID a2 = UUID.randomUUID();
        UUID verificacion = dadaUnaEjecucion(p.usuarioA(), IdTecnica.de("T22"), "Verificación: tráfico del centro", Optional.empty(),
                List.of(postura(a1, "El centro tiene más tráfico", SentidoAfirmacion.PRODUCIDA)), base);
        UUID registro = dadaUnaEjecucion(p.usuarioA(), IdTecnica.de("T46"), "Registro de cambios", Optional.empty(),
                List.of(postura(a2, "Las cámaras son la solución", SentidoAfirmacion.PRODUCIDA)), base);
        CambioOpinion.Declarado despues = new CambioOpinion.Declarado(UUID.randomUUID(), a2, 90, 60, CambioOpinion.Causa.PRESION);
        CambioOpinion.Declarado antes = new CambioOpinion.Declarado(UUID.randomUUID(), a1, 80, 45, CambioOpinion.Causa.EVIDENCIA);
        dadoUnCambio(p.usuarioA(), registro, despues, base.plusSeconds(120));
        dadoUnCambio(p.usuarioA(), verificacion, antes, base.plusSeconds(60));

        List<CambioRegistrado> leidos = comoUsuario(p.usuarioA()).cambios(p.usuarioA()).stream()
                .filter(c -> List.of(antes.id(), despues.id()).contains(c.cambio().id())).toList();

        assertThat(leidos).containsExactly(
                new CambioRegistrado(new CambioOpinion(antes.id(), a1, "El centro tiene más tráfico", 80, 45, CambioOpinion.Causa.EVIDENCIA,
                        Optional.of(verificacion), base.plusSeconds(60)), Optional.of(IdTecnica.de("T22"))),
                new CambioRegistrado(new CambioOpinion(despues.id(), a2, "Las cámaras son la solución", 90, 60, CambioOpinion.Causa.PRESION,
                        Optional.of(registro), base.plusSeconds(120)), Optional.of(IdTecnica.de("T46"))));
    }

    @Test
    void una_postura_trae_la_fecha_de_la_ultima_ejecucion_que_la_uso_y_los_otros_roles_no_cuentan() {
        Personas p = personas();
        Instant base = Instant.parse("2031-09-08T12:00:00Z");
        UUID masaMadre = UUID.randomUUID();
        UUID premisa = UUID.randomUUID();
        dadaUnaEjecucion(p.usuarioA(), IdTecnica.de("T40"), "Definir el problema del pan", Optional.empty(),
                List.of(postura(masaMadre, "El pan de masa madre no se vende en este barrio", SentidoAfirmacion.PRODUCIDA),
                        new AfirmacionConRol(premisa, "Los vecinos prefieren pan blanco", TipoAfirmacion.HECHO, RolAfirmacion.PREMISA,
                                SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO)), base);
        dadaUnaEjecucion(p.usuarioA(), IdTecnica.de("T08"), "Sesión socrática: el pan de masa madre", Optional.empty(),
                List.of(postura(masaMadre, "El pan de masa madre no se vende en este barrio", SentidoAfirmacion.CONSUMIDA)), base.plusSeconds(86_400));

        List<PosturaRegistrada> leidas = comoUsuario(p.usuarioA()).posturas(p.usuarioA()).stream()
                .filter(x -> List.of(masaMadre, premisa).contains(x.afirmacionId())).toList();

        assertThat(leidas).containsExactly(new PosturaRegistrada(masaMadre, "El pan de masa madre no se vende en este barrio", base.plusSeconds(86_400)));
    }

    @Test
    void otra_persona_no_ve_ni_ejecuciones_ni_cambios_ni_posturas() {
        Personas p = personas();
        Instant base = Instant.parse("2031-11-10T12:00:00Z");
        UUID afirmacion = UUID.randomUUID();
        UUID ejecucion = dadaUnaEjecucion(p.usuarioA(), IdTecnica.de("T08"), "Sesión socrática: estudiar afuera", Optional.empty(),
                List.of(postura(afirmacion, "Mi hija debe estudiar afuera ya", SentidoAfirmacion.PRODUCIDA)), base);
        CambioOpinion.Declarado cambio = new CambioOpinion.Declarado(UUID.randomUUID(), afirmacion, 85, 40, CambioOpinion.Causa.MANUAL);
        dadoUnCambio(p.usuarioA(), ejecucion, cambio, base);
        RegistroDeRazonamiento comoB = comoUsuario(p.usuarioB());

        assertThat(comoB.ejecucionesDesde(p.usuarioB(), base.minusSeconds(60))).extracting(EjecucionEnDiario::id).doesNotContain(ejecucion);
        assertThat(comoB.cambios(p.usuarioB())).extracting(c -> c.cambio().id()).doesNotContain(cambio.id());
        assertThat(comoB.posturas(p.usuarioB())).extracting(PosturaRegistrada::afirmacionId).doesNotContain(afirmacion);
    }
}
