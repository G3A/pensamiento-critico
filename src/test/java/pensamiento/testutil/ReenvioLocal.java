package pensamiento.testutil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;

/**
 * Reenvía 127.0.0.1:8080 del contenedor de pruebas a app-prueba:8080 (hito 9). Las llaves de acceso (WebAuthn) solo
 * funcionan en un contexto seguro, y http://localhost lo es sin certificado: así el navegador de las pruebas entra por
 * http://localhost:8080, el mismo origen que la app de uso en el equipo local. Se arranca una sola vez por corrida.
 */
public final class ReenvioLocal {

    public static final String BASE = "http://localhost:8080";
    private static boolean arrancado;

    private ReenvioLocal() {
    }

    public static synchronized String arrancar() {
        if (arrancado) {
            return BASE;
        }
        URI destino = URI.create(Entorno.urlApp());
        try {
            ServerSocket servidor = new ServerSocket(8080, 50, InetAddress.getLoopbackAddress());
            Thread.ofVirtual().start(() -> {
                while (!servidor.isClosed()) {
                    try {
                        Socket cliente = servidor.accept();
                        Socket app = new Socket(destino.getHost(), destino.getPort());
                        copiar(cliente, app);
                        copiar(app, cliente);
                    } catch (IOException e) {
                        // una conexión que se cae no detiene el reenvío
                    }
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo reenviar localhost:8080 a " + destino, e);
        }
        arrancado = true;
        return BASE;
    }

    private static void copiar(Socket desde, Socket hacia) {
        Thread.ofVirtual().start(() -> {
            try (InputStream entrada = desde.getInputStream(); OutputStream salida = hacia.getOutputStream()) {
                entrada.transferTo(salida);
            } catch (IOException e) {
                // el otro extremo cerró
            } finally {
                try {
                    hacia.shutdownOutput();
                } catch (IOException e) {
                    // ya estaba cerrado
                }
            }
        });
    }
}
