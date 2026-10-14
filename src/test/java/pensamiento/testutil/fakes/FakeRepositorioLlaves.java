package pensamiento.testutil.fakes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import pensamiento.nucleo.LlaveDeAcceso;
import pensamiento.nucleo.Titular;
import pensamiento.nucleo.puertos.RepositorioLlaves;

/**
 * Llaves y códigos de recuperación en memoria. Como el real, solo acepta titulares que "existen" (los que se registran con
 * {@link #conTitular}) y una cuenta solo con su institución. Certificado por FakeRepositorioLlavesContractTest.
 */
public final class FakeRepositorioLlaves implements RepositorioLlaves {

    private record Guardada(Titular titular, LlaveDeAcceso llave) {
    }

    private record Codigo(Titular titular, UUID id, String hash, Optional<Instant> usado) {
    }

    private final Map<String, Guardada> llaves = new LinkedHashMap<>();
    private final List<Codigo> codigos = new ArrayList<>();
    private final List<Titular> titulares = new ArrayList<>();
    private Predicate<Titular> valido = titulares::contains;

    /** Da por existente al titular (lo que en la base es la fila de usuario u operador). */
    public FakeRepositorioLlaves conTitular(Titular titular) {
        titulares.add(titular);
        return this;
    }

    /** Para collaboration tests: cualquier titular es válido. */
    public FakeRepositorioLlaves aceptarCualquierTitular() {
        valido = t -> true;
        return this;
    }

    @Override
    public boolean guardar(Titular titular, LlaveDeAcceso llave) {
        if (!valido.test(titular)) {
            return false;
        }
        String clave = llave.credencialHex();
        Guardada previa = llaves.get(clave);
        if (previa != null && !previa.titular().equals(titular)) {
            return false;
        }
        LlaveDeAcceso guardar = previa == null ? llave : new LlaveDeAcceso(previa.llave().credencialId(), previa.llave().entidadUsuario(),
                previa.llave().clavePublicaCose(), llave.firmas(), llave.uvInicializada(), previa.llave().transportes(), previa.llave().respaldable(),
                llave.respaldada(), previa.llave().atestacion(), previa.llave().datosCliente(), llave.etiqueta(), previa.llave().creada(), llave.usada());
        llaves.put(clave, new Guardada(titular, guardar));
        return true;
    }

    @Override
    public Optional<LlaveDeAcceso> porCredencial(Titular titular, byte[] credencialId) {
        return de(titular).stream().filter(l -> Arrays.equals(l.credencialId(), credencialId)).findFirst();
    }

    @Override
    public List<LlaveDeAcceso> de(Titular titular) {
        if (!valido.test(titular)) {
            return List.of();
        }
        return llaves.values().stream().filter(g -> g.titular().equals(titular)).map(Guardada::llave)
                .sorted(Comparator.comparing(LlaveDeAcceso::creada).thenComparing(LlaveDeAcceso::credencialHex)).toList();
    }

    @Override
    public boolean borrar(Titular titular, byte[] credencialId) {
        return valido.test(titular) && porCredencial(titular, credencialId).map(l -> llaves.remove(l.credencialHex())).isPresent();
    }

    @Override
    public boolean reemplazarRecuperacion(Titular titular, List<String> hashes) {
        if (!valido.test(titular)) {
            return false;
        }
        codigos.removeIf(c -> c.titular().equals(titular) && c.usado().isEmpty());
        hashes.forEach(h -> codigos.add(new Codigo(titular, UUID.randomUUID(), h, Optional.empty())));
        return true;
    }

    @Override
    public List<CodigoDeRecuperacion> recuperacionVigente(Titular titular) {
        if (!valido.test(titular)) {
            return List.of();
        }
        return codigos.stream().filter(c -> c.titular().equals(titular) && c.usado().isEmpty())
                .map(c -> new CodigoDeRecuperacion(c.id(), c.hash())).toList();
    }

    @Override
    public boolean usarRecuperacion(Titular titular, UUID id, Instant ahora) {
        if (!valido.test(titular)) {
            return false;
        }
        for (int i = 0; i < codigos.size(); i++) {
            Codigo c = codigos.get(i);
            if (c.id().equals(id) && c.titular().equals(titular) && c.usado().isEmpty()) {
                codigos.set(i, new Codigo(c.titular(), c.id(), c.hash(), Optional.of(ahora)));
                return true;
            }
        }
        return false;
    }
}
