package pensamiento.contrato;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.Documento;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.OrigenAfirmacion;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.SentidoAfirmacion;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.puertos.Biblioteca;
import pensamiento.nucleo.puertos.RepositorioEvidencias;
import pensamiento.testutil.fakes.FakeBiblioteca;
import pensamiento.testutil.fakes.FakeRepositorioEjecucion;
import pensamiento.testutil.fakes.FakeRepositorioEvidencias;

class FakeRepositorioEvidenciasContractTest extends RepositorioEvidenciasContract {

    private final FakeRepositorioEjecucion ejecuciones = new FakeRepositorioEjecucion();
    private final FakeBiblioteca biblioteca = new FakeBiblioteca();
    private final FakeRepositorioEvidencias fake = new FakeRepositorioEvidencias(ejecuciones, biblioteca);
    private final Personas personas = new Personas(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    @Override
    protected Personas personas() {
        return personas;
    }

    @Override
    protected RepositorioEvidencias comoUsuario(UUID usuarioId) {
        return fake;
    }

    @Override
    protected UUID dadaUnaAfirmacion(UUID usuarioId, String texto) {
        AfirmacionConRol a = new AfirmacionConRol(UUID.randomUUID(), texto, TipoAfirmacion.HECHO, RolAfirmacion.HIPOTESIS, SentidoAfirmacion.PRODUCIDA,
                OrigenAfirmacion.USUARIO);
        Ejecucion e = new Ejecucion(UUID.randomUUID(), usuarioId, personas.institucion(), IdTecnica.de("T22"), 1, Optional.empty(), Json.VACIO,
                Json.VACIO, Json.VACIO, "Triangulación de prueba.", Optional.empty(), "evidencias-" + UUID.randomUUID(), Instant.parse("2026-10-09T15:00:00Z"));
        ejecuciones.guardar(e, List.of(a), List.of());
        return a.afirmacionId();
    }

    @Override
    protected UUID dadoUnDocumento(UUID usuarioId, String nombre) {
        return biblioteca.crear(usuarioId, personas.institucion(), new Biblioteca.NuevoDocumento(UUID.randomUUID(), nombre, Documento.Tipo.PDF,
                "hash-" + UUID.randomUUID(), "%PDF-1.4".getBytes(StandardCharsets.ISO_8859_1))).id();
    }

    @Override
    protected void borrarDocumento(UUID usuarioId, UUID documentoId) {
        biblioteca.borrar(usuarioId, documentoId);
    }
}
