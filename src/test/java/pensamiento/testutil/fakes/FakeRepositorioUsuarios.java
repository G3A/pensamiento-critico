package pensamiento.testutil.fakes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import pensamiento.nucleo.Usuario;
import pensamiento.nucleo.puertos.RepositorioUsuarios;

/** Cuentas y códigos en memoria, en espejo del adaptador JDBC. Certificado por FakeRepositorioUsuariosContractTest. */
public final class FakeRepositorioUsuarios implements RepositorioUsuarios {

    private record Codigo(UUID institucion, UUID usuario, TipoCodigo tipo, String hash, Instant caduca, Optional<Instant> usado, long orden) {
    }

    private final Map<UUID, Usuario> usuarios = new LinkedHashMap<>();
    private final List<Codigo> codigos = new ArrayList<>();
    private long orden;

    @Override
    public Optional<Usuario> crear(Usuario usuario) {
        boolean repetido = usuarios.containsKey(usuario.id()) || usuarios.values().stream()
                .anyMatch(u -> u.institucionId().equals(usuario.institucionId()) && clave(u).equals(clave(usuario)));
        if (repetido) {
            return Optional.empty();
        }
        usuarios.put(usuario.id(), usuario);
        return Optional.of(usuario);
    }

    @Override
    public Optional<Usuario> porId(UUID institucionId, UUID id) {
        return Optional.ofNullable(usuarios.get(id)).filter(u -> u.institucionId().equals(institucionId));
    }

    @Override
    public Optional<Usuario> porNombre(UUID institucionId, String nombre) {
        return usuarios.values().stream()
                .filter(u -> u.institucionId().equals(institucionId) && clave(u).equals(Usuario.claveDeNombre(nombre))).findFirst();
    }

    @Override
    public PaginaDeCuentas buscar(UUID institucionId, String texto, int pagina, int tamano) {
        String buscado = Usuario.claveDeNombre(texto == null ? "" : texto);
        List<Usuario> todas = usuarios.values().stream()
                .filter(u -> u.institucionId().equals(institucionId) && clave(u).contains(buscado))
                .sorted(Comparator.comparing(FakeRepositorioUsuarios::clave).thenComparing(Usuario::id)).toList();
        int desde = Math.min(Math.max(pagina, 0) * tamano, todas.size());
        return new PaginaDeCuentas(todas.subList(desde, Math.min(desde + tamano, todas.size())), todas.size());
    }

    @Override
    public long contar(UUID institucionId) {
        return usuarios.values().stream().filter(u -> u.institucionId().equals(institucionId)).count();
    }

    @Override
    public boolean cambiarClave(UUID institucionId, UUID id, String claveHash, Usuario.TipoClave tipo) {
        return porId(institucionId, id).map(u -> usuarios.put(id, u.conClave(claveHash, tipo))).isPresent();
    }

    @Override
    public boolean cambiarActivo(UUID institucionId, UUID id, boolean activo) {
        return porId(institucionId, id).map(u -> usuarios.put(id, u.conActivo(activo))).isPresent();
    }

    @Override
    public void guardarCodigo(UUID institucionId, UUID usuarioId, TipoCodigo tipo, String codigoHash, Instant caducaEn, Optional<UUID> creadoPor) {
        codigos.replaceAll(c -> c.usuario().equals(usuarioId) && c.usado().isEmpty()
                ? new Codigo(c.institucion(), c.usuario(), c.tipo(), c.hash(), Instant.MIN, c.usado(), c.orden()) : c);
        codigos.add(new Codigo(institucionId, usuarioId, tipo, codigoHash, caducaEn, Optional.empty(), orden++));
    }

    @Override
    public Optional<CodigoCanjeado> codigoVigente(UUID institucionId, String codigoHash, Instant ahora) {
        return buscarVigente(institucionId, codigoHash, ahora).map(c -> new CodigoCanjeado(c.usuario(), c.tipo()));
    }

    private Optional<Codigo> buscarVigente(UUID institucionId, String codigoHash, Instant ahora) {
        return codigos.stream()
                .filter(c -> c.institucion().equals(institucionId) && c.hash().equals(codigoHash) && vigente(c, ahora)
                        && porId(institucionId, c.usuario()).map(Usuario::activo).orElse(false))
                .max(Comparator.comparingLong(Codigo::orden));
    }

    @Override
    public Optional<CodigoCanjeado> canjearCodigo(UUID institucionId, String codigoHash, Instant ahora) {
        Optional<Codigo> vigente = buscarVigente(institucionId, codigoHash, ahora);
        vigente.ifPresent(c -> codigos.set(codigos.indexOf(c),
                new Codigo(c.institucion(), c.usuario(), c.tipo(), c.hash(), c.caduca(), Optional.of(ahora), c.orden())));
        return vigente.map(c -> new CodigoCanjeado(c.usuario(), c.tipo()));
    }

    @Override
    public Optional<TipoCodigo> codigoPendiente(UUID institucionId, UUID usuarioId, Instant ahora) {
        return codigos.stream().filter(c -> c.institucion().equals(institucionId) && c.usuario().equals(usuarioId) && vigente(c, ahora))
                .max(Comparator.comparingLong(Codigo::orden)).map(Codigo::tipo);
    }

    private static boolean vigente(Codigo c, Instant ahora) {
        return c.usado().isEmpty() && c.caduca().isAfter(ahora);
    }

    private static String clave(Usuario u) {
        return Usuario.claveDeNombre(u.nombre());
    }
}
