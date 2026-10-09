package pensamiento.contrato;

import java.util.ArrayList;
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
import pensamiento.nucleo.puertos.RepositorioCambiosOpinion;
import pensamiento.testutil.fakes.FakeRepositorioEjecucion;
import pensamiento.testutil.fakes.FakeRepositorioCambiosOpinion;

class FakeRepositorioCambiosOpinionContractTest extends RepositorioCambiosOpinionContract {

    private final FakeRepositorioEjecucion ejecuciones = new FakeRepositorioEjecucion();
    private final FakeRepositorioCambiosOpinion fake = new FakeRepositorioCambiosOpinion(ejecuciones);
    private final Personas personas = new Personas(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    @Override
    protected Personas personas() {
        return personas;
    }

    @Override
    protected RepositorioCambiosOpinion comoUsuario(UUID usuarioId) {
        return fake;
    }

    @Override
    protected EjecucionConAfirmaciones dadaUnaEjecucionConAfirmaciones(UUID usuarioId, List<String> textos) {
        List<AfirmacionConRol> afirmaciones = new ArrayList<>();
        for (String t : textos) {
            afirmaciones.add(new AfirmacionConRol(UUID.randomUUID(), t, TipoAfirmacion.JUICIO_DE_VALOR, RolAfirmacion.POSTURA,
                    SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO));
        }
        Ejecucion e = new Ejecucion(UUID.randomUUID(), usuarioId, personas.institucion(), IdTecnica.de("T08"), 1, Optional.empty(), Json.VACIO,
                Json.VACIO, Json.VACIO, "Sesión de prueba.", Optional.empty(), "cambios-" + UUID.randomUUID(), CERRADA_EN.minusSeconds(86_400));
        ejecuciones.guardar(e, afirmaciones, List.of());
        return new EjecucionConAfirmaciones(e.id(), afirmaciones.stream().map(AfirmacionConRol::afirmacionId).toList());
    }
}
