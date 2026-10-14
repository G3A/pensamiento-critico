package pensamiento.web.seguridad;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import pensamiento.nucleo.Usuario;
import pensamiento.nucleo.puertos.RegistroIntentos;
import pensamiento.nucleo.puertos.RegistroIntentos.Estado;
import pensamiento.nucleo.puertos.Reloj;

/**
 * Retardo progresivo al entrar (innegociable 3, RF-15), sin bloqueo duro. Tres contadores:
 * <ul>
 *   <li>Por cuenta (la clave del nombre, exista o no la cuenta, para que no se distinga): tras 5 fallos seguidos espera
 *       30 s; cada fallo después de esperar sube a 1 min, 5 min y un tope de 15 min. Entrar bien lo pone en cero.</li>
 *   <li>Por IP e institución, con un umbral alto que solo corta ráfagas (un colegio entero sale por una sola IP).</li>
 *   <li>Por IP sin institución, contra quien prueba en muchas instituciones.</li>
 * </ul>
 * Mientras alguno espera, el intento se rechaza sin mirar la contraseña (429), aunque sea la correcta.
 */
public final class LimiteDeIntentos {

    public static final int FALLOS_ANTES_DE_ESPERAR = 5;
    public static final List<Duration> ESCALONES = List.of(Duration.ofSeconds(30), Duration.ofMinutes(1), Duration.ofMinutes(5), Duration.ofMinutes(15));

    /** Un tope por IP: cuántos fallos caben en la ventana antes de esperar a que termine. */
    public record Tope(int umbral, Duration ventana) {
    }

    private final RegistroIntentos registro;
    private final Reloj reloj;
    private final Tope porIpEInstitucion;
    private final Tope porIpGlobal;

    public LimiteDeIntentos(RegistroIntentos registro, Reloj reloj, Tope porIpEInstitucion, Tope porIpGlobal) {
        this.registro = registro;
        this.reloj = reloj;
        this.porIpEInstitucion = porIpEInstitucion;
        this.porIpGlobal = porIpGlobal;
    }

    public static String claveDeCuenta(String nombre) {
        return "cuenta:" + Usuario.claveDeNombre(nombre == null ? "" : nombre);
    }

    /** Cuánto falta para poder intentar de nuevo con ese nombre desde esa IP; vacío si puede intentar ya. */
    public Optional<Duration> espera(UUID institucion, String nombre, String ip) {
        Instant ahora = reloj.ahora();
        return Stream.of(
                        registro.leer(Optional.of(institucion), claveDeCuenta(nombre)),
                        registro.leer(Optional.of(institucion), "ip:" + ip),
                        registro.leer(Optional.empty(), "ip:" + ip))
                .flatMap(Optional::stream)
                .flatMap(e -> e.esperaHasta().stream())
                .filter(hasta -> hasta.isAfter(ahora))
                .max(Instant::compareTo)
                .map(hasta -> Duration.between(ahora, hasta));
    }

    /** Un intento fallido: suma en los tres contadores y fija la espera que corresponda. */
    public void fallo(UUID institucion, String nombre, String ip) {
        Instant ahora = reloj.ahora();
        String cuenta = claveDeCuenta(nombre);
        int fallos = registro.leer(Optional.of(institucion), cuenta).map(Estado::fallos).orElse(0) + 1;
        Optional<Instant> espera = fallos < FALLOS_ANTES_DE_ESPERAR ? Optional.empty()
                : Optional.of(ahora.plus(ESCALONES.get(Math.min(fallos - FALLOS_ANTES_DE_ESPERAR, ESCALONES.size() - 1))));
        registro.guardar(Optional.of(institucion), cuenta, new Estado(fallos, ahora, espera));
        contarEnVentana(Optional.of(institucion), "ip:" + ip, porIpEInstitucion, ahora);
        contarEnVentana(Optional.empty(), "ip:" + ip, porIpGlobal, ahora);
    }

    /** Entró bien: el contador de su cuenta vuelve a cero (los de la IP siguen su ventana). */
    public void exito(UUID institucion, String nombre) {
        registro.borrar(Optional.of(institucion), claveDeCuenta(nombre));
    }

    /** El administrador levanta la espera de una persona. */
    public void levantar(UUID institucion, String nombre) {
        registro.borrar(Optional.of(institucion), claveDeCuenta(nombre));
    }

    /**
     * Canjear códigos de alta o de restablecimiento: un tope por IP e institución con el mismo umbral alto (un salón entero
     * canjea desde la misma IP), sin contador por cuenta, porque el código se escribe sin nombre.
     */
    public Optional<Duration> esperaDeCodigo(UUID institucion, String ip) {
        Instant ahora = reloj.ahora();
        return registro.leer(Optional.of(institucion), "codigo:" + ip).flatMap(Estado::esperaHasta)
                .filter(hasta -> hasta.isAfter(ahora)).map(hasta -> Duration.between(ahora, hasta));
    }

    public void falloDeCodigo(UUID institucion, String ip) {
        contarEnVentana(Optional.of(institucion), "codigo:" + ip, porIpEInstitucion, reloj.ahora());
    }

    /** Las claves de nombre (sin el prefijo) de las cuentas que esperan ahora en esa institución. */
    public List<String> nombresEnEspera(UUID institucion) {
        return registro.esperas(institucion, reloj.ahora()).stream().map(e -> e.clave().substring("cuenta:".length())).toList();
    }

    private void contarEnVentana(Optional<UUID> institucion, String clave, Tope tope, Instant ahora) {
        Optional<Estado> previo = registro.leer(institucion, clave).filter(e -> e.ventanaDesde().plus(tope.ventana()).isAfter(ahora));
        Instant desde = previo.map(Estado::ventanaDesde).orElse(ahora);
        int fallos = previo.map(Estado::fallos).orElse(0) + 1;
        registro.guardar(institucion, clave, new Estado(fallos, desde,
                fallos >= tope.umbral() ? Optional.of(desde.plus(tope.ventana())) : Optional.empty()));
    }
}
