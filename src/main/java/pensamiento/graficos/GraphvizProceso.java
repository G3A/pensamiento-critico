package pensamiento.graficos;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Semaphore;

import pensamiento.nucleo.puertos.Grafico;

/**
 * Graphviz (dot) como proceso hijo con tiempo máximo (5 s por defecto), límite de iteraciones
 * (-Gnslimit, -Gnslimit1) y a lo sumo cuatro procesos a la vez. El SVG pasa por el saneador antes de salir.
 */
public class GraphvizProceso implements Grafico {

    private final String ejecutable;
    private final Duration tiempoMaximo;
    private final int nslimit;
    private final Semaphore permisos;

    public GraphvizProceso(String ejecutable, Duration tiempoMaximo, int nslimit, int procesosMaximos) {
        this.ejecutable = ejecutable;
        this.tiempoMaximo = tiempoMaximo;
        this.nslimit = nslimit;
        this.permisos = new Semaphore(procesosMaximos, true);
    }

    @Override
    public String svg(String dot) {
        if (dot == null || dot.isBlank()) {
            throw new GraficoInvalido("El DOT está vacío");
        }
        try {
            permisos.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GraficoTiempoAgotado("Interrumpido esperando a Graphviz");
        }
        ProcesoHijo.Salida salida;
        try {
            salida = ProcesoHijo.ejecutar(
                    List.of(ejecutable, "-Tsvg", "-Gnslimit=" + nslimit, "-Gnslimit1=" + nslimit, "-Gmaxiter=" + (nslimit * 10)),
                    dot, tiempoMaximo);
        } catch (ProcesoHijo.TiempoAgotado e) {
            throw new GraficoTiempoAgotado(e.getMessage());
        } catch (ProcesoHijo.NoSePudoEjecutar e) {
            throw new GraficoInvalido("Graphviz no está disponible: " + e.getMessage());
        } finally {
            permisos.release();
        }
        if (!salida.exitoso() || salida.stdout().isBlank()) {
            throw new GraficoInvalido("Graphviz rechazó el DOT: " + salida.stderr().strip());
        }
        try {
            return SaneadorSvg.sanear(salida.stdout());
        } catch (SaneadorSvg.SvgInvalido e) {
            throw new GraficoInvalido("Graphviz devolvió un SVG que no se pudo sanear");
        }
    }
}
