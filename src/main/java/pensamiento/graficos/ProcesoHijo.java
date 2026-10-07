package pensamiento.graficos;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Corre un comando como proceso hijo con tiempo máximo. Si se pasa del tiempo, lo mata (con su árbol)
 * y lanza TiempoAgotado. La salida se lee en un hilo aparte para que el hijo no se bloquee por el buffer.
 */
public final class ProcesoHijo {

    public record Salida(int codigo, String stdout, String stderr) {
        public boolean exitoso() {
            return codigo == 0;
        }
    }

    public static class TiempoAgotado extends RuntimeException {
        public TiempoAgotado(String mensaje) {
            super(mensaje);
        }
    }

    public static class NoSePudoEjecutar extends RuntimeException {
        public NoSePudoEjecutar(String mensaje, Throwable causa) {
            super(mensaje, causa);
        }
    }

    private ProcesoHijo() {
    }

    public static Salida ejecutar(List<String> comando, String entrada, Duration tiempoMaximo) {
        Process proceso;
        try {
            proceso = new ProcessBuilder(comando).redirectErrorStream(false).start();
        } catch (IOException e) {
            throw new NoSePudoEjecutar("No se pudo lanzar " + comando.getFirst(), e);
        }
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        Thread lectorOut = Thread.ofVirtual().start(() -> copiar(proceso.getInputStream(), stdout));
        Thread lectorErr = Thread.ofVirtual().start(() -> copiar(proceso.getErrorStream(), stderr));
        try (OutputStream in = proceso.getOutputStream()) {
            if (entrada != null) {
                in.write(entrada.getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            // El hijo pudo cerrar la entrada antes: se decide por el código de salida.
        }
        try {
            if (!proceso.waitFor(tiempoMaximo.toMillis(), TimeUnit.MILLISECONDS)) {
                proceso.descendants().forEach(ProcessHandle::destroyForcibly);
                proceso.destroyForcibly();
                throw new TiempoAgotado(comando.getFirst() + " superó el tiempo máximo de " + tiempoMaximo.toSeconds() + " s");
            }
            lectorOut.join();
            lectorErr.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            proceso.destroyForcibly();
            throw new NoSePudoEjecutar("Interrumpido esperando a " + comando.getFirst(), e);
        }
        return new Salida(proceso.exitValue(), stdout.toString(StandardCharsets.UTF_8), stderr.toString(StandardCharsets.UTF_8));
    }

    private static void copiar(InputStream origen, OutputStream destino) {
        try (origen) {
            origen.transferTo(destino);
        } catch (IOException e) {
            // Lectura interrumpida por el fin del proceso: lo que se leyó es lo que hay.
        }
    }
}
