package pensamiento.integracion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import pensamiento.expediente.RepositorioEjecucionJdbc;
import pensamiento.expediente.RepositorioPrediccionesJdbc;
import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.OrigenAfirmacion;
import pensamiento.nucleo.PrediccionDeclarada;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.SentidoAfirmacion;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.Uuid7;
import pensamiento.testutil.BaseDatosDePrueba;

/**
 * R05 en la base: una predicción resuelta no cambia ni con un UPDATE directo del rol de aplicación (trigger de V6), y
 * resolver exige la fecha de resolución (restricción de V6).
 */
class PrediccionInmutableIT {

    private static final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private static UUID institucion;
    private static UUID usuario;

    @BeforeAll
    static void sembrar() {
        institucion = bd.crearInstitucion("prediccion-inmutable-" + UUID.randomUUID());
        usuario = bd.crearUsuario(institucion, "dueña de la panadería");
    }

    @AfterAll
    static void limpiar() {
        bd.borrarInstitucion(institucion);
    }

    private UUID prediccionResuelta() {
        Instant ahora = Instant.parse("2026-10-07T15:00:00Z");
        Ejecucion e = new Ejecucion(Uuid7.en(ahora), usuario, institucion, IdTecnica.de("T32"), 1, Optional.empty(), Json.VACIO, Json.VACIO,
                Json.VACIO, "Abrir en la terminal.", Optional.empty(), "inmutable-" + UUID.randomUUID(), ahora);
        AfirmacionConRol p = new AfirmacionConRol(UUID.randomUUID(), "La sucursal de la terminal cubre sus costos en 6 meses.", TipoAfirmacion.PREDICCION,
                RolAfirmacion.PREDICCION, SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO);
        PrediccionDeclarada d = new PrediccionDeclarada(UUID.randomUUID(), p.afirmacionId(), 70, LocalDate.of(2027, 4, 15));
        RepositorioEjecucionJdbc ejecuciones = new RepositorioEjecucionJdbc(bd.jdbcApp());
        RepositorioPrediccionesJdbc predicciones = new RepositorioPrediccionesJdbc(bd.jdbcApp());
        bd.comoUsuario(usuario, institucion, () -> {
            ejecuciones.guardar(e, List.of(p), List.of());
            predicciones.guardar(usuario, institucion, e.id(), List.of(d));
            return predicciones.resolver(usuario, d.id(), true, Instant.parse("2027-04-15T15:00:00Z"));
        });
        return d.id();
    }

    @Test
    void un_update_directo_sobre_una_prediccion_resuelta_falla_y_no_cambia_nada() {
        UUID id = prediccionResuelta();

        assertThatThrownBy(() -> bd.comoUsuario(usuario, institucion, () -> bd.jdbcApp()
                .sql("UPDATE prediccion SET resultado = 'fallo' WHERE id = :id").param("id", id).update()))
                .hasStackTraceContaining("no se puede modificar");

        String resultado = bd.comoUsuario(usuario, institucion, () -> bd.jdbcApp().sql("SELECT resultado FROM prediccion WHERE id = :id")
                .param("id", id).query(String.class).single());
        assertThat(resultado).isEqualTo("acierto");
    }
}
