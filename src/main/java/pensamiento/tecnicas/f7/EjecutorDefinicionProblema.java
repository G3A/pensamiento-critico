package pensamiento.tecnicas.f7;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.OrigenAfirmacion;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.SentidoAfirmacion;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.Validacion;
import pensamiento.tecnicas.comun.Textos;

/**
 * T40 · Definición del problema (Dewey 1910; Wedell-Wedellsborg 2017). Pide reformular el problema con plantillas, elegir
 * una reformulación y compara cuántas opciones abre frente a la original. No usa IA. En el Diario es el paso 0
 * obligatorio. Las reglas están en docs/ejemplos/T40.md.
 */
@Component
public class EjecutorDefinicionProblema
        implements Ejecutor<EjecutorDefinicionProblema.Config, EjecutorDefinicionProblema.Entrada, ResultadoDefinicionProblema> {

    public static final IdTecnica ID = IdTecnica.de("T40");
    public static final int VERSION_ESQUEMA = 1;
    static final int TOPE_REFORMULACIONES = 8;

    /** Las cinco plantillas de reformulación, en orden. */
    public enum Plantilla {
        COMO_PODRIAMOS("¿Cómo podríamos…?"),
        SIN("… sin …"),
        PARA_QUIEN("¿Para quién es un problema?"),
        CAUSA("¿Qué lo causa?"),
        META("¿Qué queremos lograr en realidad?");

        private final String nombre;

        Plantilla(String nombre) {
            this.nombre = nombre;
        }

        public String nombre() {
            return nombre;
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /** Configuración de T40, versión de esquema 1. */
    public record Config(List<Plantilla> plantillas, int minimoReformulaciones) {
        public Config {
            plantillas = plantillas == null ? List.of() : List.copyOf(plantillas);
        }
    }

    /** @param opciones cuántas opciones abre; nulo si no lo dice */
    public record Reformulacion(String texto, Plantilla plantilla, Integer opciones, boolean elegida) {
    }

    public record Entrada(String original, Integer opcionesOriginal, List<Reformulacion> reformulaciones) {
        public Entrada {
            reformulaciones = reformulaciones == null ? List.of() : List.copyOf(reformulaciones);
        }
    }

    @Override
    public IdTecnica id() {
        return ID;
    }

    @Override
    public int versionEsquema() {
        return VERSION_ESQUEMA;
    }

    @Override
    public Tipos<Config, Entrada, ResultadoDefinicionProblema> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoDefinicionProblema.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.minimoReformulaciones() < 1 || config.minimoReformulaciones() > 6) {
            errores.add(new Validacion.Error("config.minimoReformulaciones", "El mínimo de reformulaciones va de 1 a 6."));
        }
        if (Textos.vacio(entrada.original())) {
            errores.add(new Validacion.Error("original", "Escribe el problema como llegó."));
        }
        List<Reformulacion> rs = entrada.reformulaciones();
        if (rs.size() < config.minimoReformulaciones()) {
            errores.add(new Validacion.Error("reformulaciones", "Escribe al menos " + Textos.contar(config.minimoReformulaciones(),
                    "reformulación", "reformulaciones") + "."));
        } else if (rs.size() > TOPE_REFORMULACIONES) {
            errores.add(new Validacion.Error("reformulaciones", "Caben como máximo " + TOPE_REFORMULACIONES + " reformulaciones."));
        }
        for (int i = 0; i < rs.size(); i++) {
            if (Textos.vacio(rs.get(i).texto())) {
                errores.add(new Validacion.Error("reformulaciones[" + i + "].texto", "Escribe la reformulación R" + (i + 1) + "."));
            }
            if (rs.get(i).plantilla() != null && !config.plantillas().contains(rs.get(i).plantilla())) {
                errores.add(new Validacion.Error("reformulaciones[" + i + "].plantilla", "Esa plantilla no está activa en tu configuración."));
            }
        }
        long elegidas = rs.stream().filter(Reformulacion::elegida).count();
        if (!rs.isEmpty() && elegidas == 0) {
            errores.add(new Validacion.Error("reformulaciones", "Elige una reformulación: es la que vas a resolver."));
        } else if (elegidas > 1) {
            errores.add(new Validacion.Error("reformulaciones", "Elige solo una reformulación."));
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoDefinicionProblema> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        Reformulacion elegida = entrada.reformulaciones().stream().filter(Reformulacion::elegida).findFirst().orElseThrow();
        String textoElegida = elegida.texto().strip();
        List<ResultadoDefinicionProblema.Item> items = new ArrayList<>();
        items.add(item(ResultadoDefinicionProblema.Estado.ELEGIDA, textoElegida, elegida.plantilla(), elegida.opciones()));
        items.add(item(ResultadoDefinicionProblema.Estado.ORIGINAL, entrada.original().strip(), null, entrada.opcionesOriginal()));
        for (Reformulacion r : entrada.reformulaciones()) {
            if (!r.elegida()) {
                items.add(item(ResultadoDefinicionProblema.Estado.DESCARTADA, r.texto().strip(), r.plantilla(), r.opciones()));
            }
        }
        String comparacion = null;
        List<String> avisos = new ArrayList<>();
        if (entrada.opcionesOriginal() != null && elegida.opciones() != null) {
            int n = elegida.opciones();
            int m = entrada.opcionesOriginal();
            if (n > m) {
                comparacion = "La reformulación elegida abre " + Textos.contar(n, "opción", "opciones") + "; la original, " + m + ".";
            } else if (n < m) {
                comparacion = "La reformulación elegida abre menos opciones que la original (" + n + " contra " + m
                        + "): ¿no estará estrechando el problema?";
                avisos.add(comparacion);
            } else {
                comparacion = "La elegida y la original abren " + Textos.contar(n, "opción", "opciones") + ".";
            }
        }
        Set<Plantilla> usadas = new LinkedHashSet<>();
        entrada.reformulaciones().stream().map(Reformulacion::plantilla).filter(p -> p != null).forEach(usadas::add);
        String plantillas = "Probaste " + usadas.size() + " de " + config.plantillas().size() + " plantillas.";
        int descartadas = entrada.reformulaciones().size() - 1;
        String resumen = "Elegida: «" + textoElegida + "» · " + Textos.contar(descartadas, "descartada", "descartadas") + ".";
        List<AfirmacionConRol> afirmaciones = List.of(new AfirmacionConRol(ctx.nuevoId().get(), textoElegida, TipoAfirmacion.DEFINICION,
                RolAfirmacion.POSTURA, SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO));
        ResultadoDefinicionProblema valor = new ResultadoDefinicionProblema(entrada.original().strip(), textoElegida, items, comparacion, plantillas,
                avisos, resumen);
        return new Resultado<>(VERSION_ESQUEMA, valor, afirmaciones, List.of(), resumen);
    }

    private static ResultadoDefinicionProblema.Item item(ResultadoDefinicionProblema.Estado estado, String texto, Plantilla plantilla, Integer opciones) {
        return new ResultadoDefinicionProblema.Item(estado, texto, plantilla == null ? null : plantilla.nombre(), opciones);
    }

    @Override
    public ResultadoDefinicionProblema migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }
}
