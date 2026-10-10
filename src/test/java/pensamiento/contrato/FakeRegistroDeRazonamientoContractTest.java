package pensamiento.contrato;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.CambioOpinion;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.Expediente;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.puertos.RegistroDeRazonamiento;
import pensamiento.testutil.fakes.FakeRegistroDeRazonamiento;
import pensamiento.testutil.fakes.FakeRepositorioCambiosOpinion;
import pensamiento.testutil.fakes.FakeRepositorioEjecucion;
import pensamiento.testutil.fakes.FakeRepositorioExpediente;

class FakeRegistroDeRazonamientoContractTest extends RegistroDeRazonamientoContract {

    private final FakeRepositorioEjecucion ejecuciones = new FakeRepositorioEjecucion();
    private final FakeRepositorioExpediente expedientes = new FakeRepositorioExpediente();
    private final FakeRepositorioCambiosOpinion cambios = new FakeRepositorioCambiosOpinion(ejecuciones);
    private final FakeRegistroDeRazonamiento fake = new FakeRegistroDeRazonamiento(ejecuciones, expedientes, cambios);
    private final Personas personas = new Personas(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    @Override
    protected Personas personas() {
        return personas;
    }

    @Override
    protected RegistroDeRazonamiento comoUsuario(UUID usuarioId) {
        return fake;
    }

    @Override
    protected UUID dadaUnaEjecucion(UUID usuarioId, IdTecnica tecnica, String resumen, Optional<UUID> expediente, List<AfirmacionConRol> afirmaciones,
                                    Instant cuando) {
        Ejecucion e = new Ejecucion(UUID.randomUUID(), usuarioId, personas.institucion(), tecnica, 1, expediente, Json.VACIO, Json.VACIO, Json.VACIO,
                resumen, Optional.empty(), "registro-" + UUID.randomUUID(), cuando);
        ejecuciones.guardar(e, afirmaciones, List.of());
        return e.id();
    }

    @Override
    protected UUID dadoUnExpediente(UUID usuarioId, String nombre) {
        Expediente x = new Expediente(UUID.randomUUID(), usuarioId, personas.institucion(), nombre, Optional.empty(), Expediente.Estado.ABIERTO,
                Instant.parse("2031-01-01T00:00:00Z"));
        expedientes.guardar(x);
        return x.id();
    }

    @Override
    protected void borrarExpediente(UUID usuarioId, UUID expedienteId) {
        expedientes.borrar(usuarioId, expedienteId, Instant.parse("2031-12-31T00:00:00Z"));
    }

    @Override
    protected void dadoUnCambio(UUID usuarioId, UUID ejecucionId, CambioOpinion.Declarado cambio, Instant cuando) {
        cambios.guardar(usuarioId, personas.institucion(), ejecucionId, List.of(cambio), cuando);
    }
}
