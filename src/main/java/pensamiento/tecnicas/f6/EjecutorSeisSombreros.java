package pensamiento.tecnicas.f6;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import pensamiento.catalogo.BancoSocratico;
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
import pensamiento.tecnicas.f2.Marcas;

/**
 * T35 · Seis Sombreros (de Bono 1985). Notas por sombrero en el orden que elige la persona y una síntesis; el sombrero
 * blanco avisa si una línea suena a opinión. No usa el modelo; en el Consejero, el modo sombreros hace una ronda por
 * sombrero ({@link #siguiente}). Las reglas están en docs/ejemplos/T35.md.
 */
@Component
public class EjecutorSeisSombreros implements Ejecutor<EjecutorSeisSombreros.Config, EjecutorSeisSombreros.Entrada, ResultadoSeisSombreros> {

    public static final IdTecnica ID = IdTecnica.de("T35");
    public static final int VERSION_ESQUEMA = 1;

    public enum Sombrero {
        BLANCO, ROJO, NEGRO, AMARILLO, VERDE, AZUL;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    public enum Modalidad {
        INDIVIDUAL, EQUIPO;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /**
     * Configuración de T35, versión de esquema 1. El lenguaje de campos separa los activos (casillas) del orden (lista con
     * subir y bajar); los sombreros que falten en el orden van al final, en el orden por defecto.
     */
    public record Config(List<Sombrero> activos, List<Sombrero> orden, int minutosPorSombrero, Modalidad modalidad) {
        public Config {
            activos = activos == null ? List.of() : List.copyOf(activos);
            orden = orden == null ? List.of() : List.copyOf(orden);
        }

        /** Los sombreros activos en el orden de la configuración. */
        public List<Sombrero> sombreros() {
            List<Sombrero> todos = new ArrayList<>(new java.util.LinkedHashSet<>(orden));
            for (Sombrero s : Sombrero.values()) {
                if (!todos.contains(s)) {
                    todos.add(s);
                }
            }
            return todos.stream().filter(activos::contains).toList();
        }
    }

    public record Entrada(String tema, String blanco, String rojo, String negro, String amarillo, String verde, String azul, String sintesis) {
        public String notas(Sombrero s) {
            return switch (s) {
                case BLANCO -> blanco;
                case ROJO -> rojo;
                case NEGRO -> negro;
                case AMARILLO -> amarillo;
                case VERDE -> verde;
                case AZUL -> azul;
            };
        }
    }

    /** Qué toca en el modo sombreros del Consejero: un sombrero por ronda, después la síntesis, después el cierre. */
    public sealed interface Ronda permits RondaSombrero, RondaSintesis, RondaCierre {
    }

    public record RondaSombrero(Sombrero sombrero) implements Ronda {
    }

    public record RondaSintesis() implements Ronda {
    }

    public record RondaCierre() implements Ronda {
    }

    private final BancoSocratico banco;

    public EjecutorSeisSombreros() {
        this(BancoSocratico.delCatalogo());
    }

    public EjecutorSeisSombreros(BancoSocratico banco) {
        this.banco = banco;
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
    public Tipos<Config, Entrada, ResultadoSeisSombreros> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoSeisSombreros.class);
    }

    /** La ronda que toca con n respuestas dadas: los sombreros activos en orden, la síntesis y el cierre. */
    public static Ronda siguiente(Config config, int respuestas) {
        if (respuestas < config.sombreros().size()) {
            return new RondaSombrero(config.sombreros().get(respuestas));
        }
        return respuestas == config.sombreros().size() ? new RondaSintesis() : new RondaCierre();
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.sombreros().size() < 2) {
            errores.add(new Validacion.Error("config.activos", "Activa al menos dos sombreros."));
        } else if (new HashSet<>(config.orden()).size() != config.orden().size()) {
            errores.add(new Validacion.Error("config.orden", "Cada sombrero va una sola vez en el orden."));
        }
        if (config.minutosPorSombrero() < 1 || config.minutosPorSombrero() > 10) {
            errores.add(new Validacion.Error("config.minutosPorSombrero", "El tiempo por sombrero va de 1 a 10 minutos."));
        }
        if (config.modalidad() == null) {
            errores.add(new Validacion.Error("config.modalidad", "Elige individual o en equipo."));
        }
        if (Textos.vacio(entrada.tema())) {
            errores.add(new Validacion.Error("tema", "Escribe el tema."));
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoSeisSombreros> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        String tema = entrada.tema().strip();
        List<ResultadoSeisSombreros.Celda> celdas = new ArrayList<>();
        List<AfirmacionConRol> afirmaciones = new ArrayList<>();
        List<String> avisos = new ArrayList<>();
        int conNotas = 0;
        for (Sombrero s : config.sombreros()) {
            BancoSocratico.Sombrero b = banco.sombrero(s.toString());
            List<String> lineas = lineas(entrada.notas(s));
            String estado = null;
            if (lineas.isEmpty()) {
                estado = "sin notas";
            } else {
                conNotas++;
            }
            if (s == Sombrero.BLANCO) {
                for (String l : lineas) {
                    afirmaciones.add(new AfirmacionConRol(ctx.nuevoId().get(), l, TipoAfirmacion.HECHO, RolAfirmacion.PREMISA, SentidoAfirmacion.PRODUCIDA,
                            OrigenAfirmacion.USUARIO));
                }
                Optional<String> opinion = lineas.stream().map(l -> Marcas.primera(l, banco.marcas("opinion"))).flatMap(Optional::stream).findFirst();
                if (opinion.isPresent()) {
                    estado = "¿hecho u opinión?";
                    avisos.add("En el sombrero blanco van hechos: «" + opinion.get() + "» suena a opinión.");
                }
            }
            celdas.add(new ResultadoSeisSombreros.Celda(s.toString(), b.nombre(), b.mira(), lineas, estado));
        }
        String sintesis = Textos.vacio(entrada.sintesis()) ? null : entrada.sintesis().strip();
        List<Pendiente> pendientes = new ArrayList<>();
        if (sintesis == null) {
            avisos.add("Falta la síntesis: ¿qué decides o qué sigue después de mirar los sombreros?");
            pendientes.add(new Pendiente(TipoPendiente.REVISION, Optional.empty(), Optional.empty(), "Escribir la síntesis de: " + tema));
        } else {
            afirmaciones.add(new AfirmacionConRol(ctx.nuevoId().get(), sintesis, TipoAfirmacion.JUICIO_DE_VALOR, RolAfirmacion.CONCLUSION,
                    SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO));
        }
        List<String> notas = List.of("Tiempo sugerido: " + config.minutosPorSombrero() + " min por sombrero.",
                config.modalidad() == Modalidad.EQUIPO ? "En equipo: todos usan el mismo sombrero a la vez; una línea por aporte." : "Individual.");
        String resumen = conNotas + " de " + config.sombreros().size() + " sombreros con notas · " + (sintesis == null ? "sin síntesis." : "con síntesis.");
        ResultadoSeisSombreros valor = new ResultadoSeisSombreros(tema, celdas, conNotas, sintesis, notas, avisos, resumen);
        return new Resultado<>(VERSION_ESQUEMA, valor, afirmaciones, pendientes, resumen);
    }

    /** Las líneas no vacías, sin espacios en los bordes. */
    public static List<String> lineas(String notas) {
        if (Textos.vacio(notas)) {
            return List.of();
        }
        return Arrays.stream(notas.split("\\R")).map(String::strip).filter(l -> !l.isEmpty()).toList();
    }

    @Override
    public ResultadoSeisSombreros migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA + "; se pidió migrar desde la " + desdeVersion);
    }
}
