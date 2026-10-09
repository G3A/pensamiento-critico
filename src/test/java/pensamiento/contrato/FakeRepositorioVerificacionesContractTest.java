package pensamiento.contrato;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.OrigenAfirmacion;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.SentidoAfirmacion;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.puertos.RepositorioVerificaciones;
import pensamiento.testutil.fakes.FakeRepositorioEjecucion;
import pensamiento.testutil.fakes.FakeRepositorioVerificaciones;

class FakeRepositorioVerificacionesContractTest extends RepositorioVerificacionesContract {

    private final FakeRepositorioEjecucion ejecuciones = new FakeRepositorioEjecucion();
    private final FakeRepositorioVerificaciones fake = new FakeRepositorioVerificaciones(ejecuciones);
    private final Personas personas = new Personas(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    @Override
    protected Personas personas() {
        return personas;
    }

    @Override
    protected RepositorioVerificaciones comoUsuario(UUID usuarioId) {
        return fake;
    }

    @Override
    protected AfirmacionDeEjecucion dadaUnaAfirmacion(UUID usuarioId, String texto, TipoAfirmacion tipo, boolean enExpediente) {
        AfirmacionConRol a = new AfirmacionConRol(UUID.randomUUID(), texto, tipo, RolAfirmacion.PREMISA, SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO);
        Optional<UUID> expediente = enExpediente ? Optional.of(UUID.randomUUID()) : Optional.empty();
        Ejecucion e = new Ejecucion(UUID.randomUUID(), usuarioId, personas.institucion(), IdTecnica.de("T01"), 1, expediente, Json.VACIO, Json.VACIO,
                Json.VACIO, "Mapa de prueba.", Optional.empty(), "verificaciones-" + UUID.randomUUID(), Instant.parse("2026-10-09T15:00:00Z"));
        ejecuciones.guardar(e, List.of(a), List.of());
        return new AfirmacionDeEjecucion(a.afirmacionId(), e.id(), expediente);
    }
}
