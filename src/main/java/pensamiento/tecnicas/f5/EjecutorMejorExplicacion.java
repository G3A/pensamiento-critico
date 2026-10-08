package pensamiento.tecnicas.f5;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import pensamiento.nucleo.AfirmacionConRol;
import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.OrigenAfirmacion;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.SentidoAfirmacion;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.TipoPendiente;
import pensamiento.nucleo.Validacion;
import pensamiento.tecnicas.comun.Textos;

/**
 * T33 · Inferencia a la mejor explicación (Harman 1965; Lipton 1991). Puntúa cada explicación de 1 a 3 en los criterios
 * activos (alcance, simplicidad, coherencia, fecundidad) con sus pesos, elige la mejor si nadie la iguala y dice qué
 * comprobar. Comparte el cálculo y la sensibilidad con T31 (patrón V03b). No usa IA. Las reglas están en
 * docs/ejemplos/T33.md.
 */
@Component
public class EjecutorMejorExplicacion implements Ejecutor<EjecutorMejorExplicacion.Config, EjecutorMejorExplicacion.Entrada, ResultadoMatriz> {

    public static final IdTecnica ID = IdTecnica.de("T33");
    public static final int VERSION_ESQUEMA = 1;
    static final int TOPE_EXPLICACIONES = 6;

    /** Los cuatro criterios, en orden y en llano. */
    public enum Criterio {
        ALCANCE("Alcance", "cuántos de los hechos explica"),
        SIMPLICIDAD("Simplicidad", "cuántas cosas hay que suponer"),
        COHERENCIA("Coherencia", "encaja con lo que ya se sabe"),
        FECUNDIDAD("Fecundidad", "predice algo nuevo que se puede comprobar");

        private final String nombre;
        private final String llano;

        Criterio(String nombre, String llano) {
            this.nombre = nombre;
            this.llano = llano;
        }

        public String nombre() {
            return nombre;
        }

        public String llano() {
            return llano;
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /** Configuración de T33, versión de esquema 1. */
    public record Config(List<Criterio> criterios, int pesoAlcance, int pesoSimplicidad, int pesoCoherencia, int pesoFecundidad) {
        public Config {
            criterios = criterios == null ? List.of() : List.copyOf(criterios);
        }

        int peso(Criterio c) {
            return switch (c) {
                case ALCANCE -> pesoAlcance;
                case SIMPLICIDAD -> pesoSimplicidad;
                case COHERENCIA -> pesoCoherencia;
                case FECUNDIDAD -> pesoFecundidad;
            };
        }
    }

    /** @param tambienExplica otro hecho que explicaría y se puede comprobar; opcional */
    public record Explicacion(String texto, Integer alcance, Integer simplicidad, Integer coherencia, Integer fecundidad, String tambienExplica) {
        Integer puntaje(Criterio c) {
            return switch (c) {
                case ALCANCE -> alcance;
                case SIMPLICIDAD -> simplicidad;
                case COHERENCIA -> coherencia;
                case FECUNDIDAD -> fecundidad;
            };
        }
    }

    public record Entrada(String hecho, List<Explicacion> explicaciones) {
        public Entrada {
            explicaciones = explicaciones == null ? List.of() : List.copyOf(explicaciones);
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
    public Tipos<Config, Entrada, ResultadoMatriz> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoMatriz.class);
    }

    private static List<Criterio> activos(Config config) {
        return java.util.Arrays.stream(Criterio.values()).filter(config.criterios()::contains).toList();
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.criterios().isEmpty()) {
            errores.add(new Validacion.Error("config.criterios", "Activa al menos un criterio."));
        }
        for (Criterio c : Criterio.values()) {
            if (config.peso(c) < 1 || config.peso(c) > 3) {
                errores.add(new Validacion.Error("config.peso" + c.nombre().replace("í", "i"), "Los pesos van de 1 a 3."));
            }
        }
        if (Textos.vacio(entrada.hecho())) {
            errores.add(new Validacion.Error("hecho", "Escribe el hecho que quieres explicar."));
        }
        if (entrada.explicaciones().size() < 2 || entrada.explicaciones().size() > TOPE_EXPLICACIONES) {
            errores.add(new Validacion.Error("explicaciones", "Escribe de 2 a " + TOPE_EXPLICACIONES + " explicaciones."));
        }
        for (int i = 0; i < entrada.explicaciones().size(); i++) {
            Explicacion e = entrada.explicaciones().get(i);
            if (Textos.vacio(e.texto())) {
                errores.add(new Validacion.Error("explicaciones[" + i + "].texto", "Escribe la explicación X" + (i + 1) + "."));
            }
            for (Criterio c : activos(config)) {
                Integer p = e.puntaje(c);
                if (p == null || p < 1 || p > 3) {
                    errores.add(new Validacion.Error("explicaciones[" + i + "]." + c, "Puntúa X" + (i + 1) + " en " + c.nombre().toLowerCase() + " de 1 a 3."));
                }
            }
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoMatriz> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        List<Criterio> activos = activos(config);
        List<Integer> pesos = activos.stream().map(config::peso).toList();
        List<List<Integer>> puntajes = entrada.explicaciones().stream().map(e -> activos.stream().map(e::puntaje).toList()).toList();
        List<Integer> unos = entrada.explicaciones().stream().map(e -> 1).toList();
        MatrizPonderada.Calculo calculo = MatrizPonderada.calcular(pesos, puntajes, unos);
        List<String> nombres = activos.stream().map(Criterio::nombre).toList();
        List<String> textos = entrada.explicaciones().stream().map(e -> e.texto().strip()).toList();
        ResultadoMatriz valor = ResultadoMatriz.armar("Inferencia a la mejor explicación", entrada.hecho().strip(), nombres, pesos, textos, puntajes,
                calculo, null, String::valueOf, "gana", "ningún peso entre 0 y 10 cambia la mejor");
        int maximo = 3 * pesos.stream().mapToInt(Integer::intValue).sum();
        List<Pendiente> pendientes = new ArrayList<>();
        String resumen;
        String justificacion = null;
        if (calculo.ganador().isPresent()) {
            int g = calculo.ganador().get();
            Explicacion mejor = entrada.explicaciones().get(g);
            long total = calculo.totales().get(g);
            String tambien = Textos.vacio(mejor.tambienExplica()) ? null : mejor.tambienExplica().strip();
            justificacion = "Explica más con menos: " + total + " de " + maximo + "." + (tambien == null ? "" : " También explicaría: " + tambien
                    + (tambien.endsWith(".") ? "" : "."));
            pendientes.add(new Pendiente(TipoPendiente.VERIFICACION, Optional.empty(), Optional.empty(), tambien == null
                    ? "Comprobar la mejor explicación: " + textos.get(g) : "Comprobar lo que predice la mejor explicación: " + tambien));
            resumen = "Mejor explicación: " + textos.get(g) + " (" + total + " de " + maximo + ") · sensibilidad " + calculo.nivel() + ".";
        } else {
            List<String> empatadas = valor.filas().stream().filter(f -> f.puesto() == 1).map(ResultadoMatriz.FilaOpcion::opcion).toList();
            pendientes.add(new Pendiente(TipoPendiente.REVISION, Optional.empty(), Optional.empty(), "Buscar un hecho que distinga: "
                    + Textos.enumerar(empatadas)));
            resumen = "Empate: " + Textos.enumerar(empatadas) + " (" + valor.filas().getFirst().total() + " de " + maximo + ").";
        }
        List<AfirmacionConRol> afirmaciones = new ArrayList<>();
        for (String t : textos) {
            afirmaciones.add(new AfirmacionConRol(ctx.nuevoId().get(), t, TipoAfirmacion.CAUSAL, RolAfirmacion.HIPOTESIS, SentidoAfirmacion.PRODUCIDA,
                    OrigenAfirmacion.USUARIO));
        }
        ResultadoMatriz completo = valor.conJustificacionYAvisos(justificacion, List.of()).conResumen(resumen);
        return new Resultado<>(VERSION_ESQUEMA, completo, afirmaciones, pendientes, resumen);
    }

    @Override
    public ResultadoMatriz migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }
}
