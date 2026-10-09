package pensamiento.tecnicas.f4;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
 * T19 · SIFT (Caulfield 2019): detente, investiga la fuente, encuentra mejor cobertura y rastrea el contexto original. Arma
 * la ficha con lo que la persona encontró en cada paso activo y da una señal sobre si conviene compartir. No usa IA. Las
 * reglas están en docs/ejemplos/T19.md.
 */
@Component
public class EjecutorSift implements Ejecutor<EjecutorSift.Config, EjecutorSift.Entrada, ResultadoSift> {

    public static final IdTecnica ID = IdTecnica.de("T19");
    public static final int VERSION_ESQUEMA = 1;

    /** Los cuatro movimientos, en orden. */
    public enum Paso {
        DETENTE("Detente"), INVESTIGA("Investiga la fuente"), COBERTURA("Encuentra mejor cobertura"), ORIGEN("Rastrea el origen");

        private final String nombre;

        Paso(String nombre) {
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

    public enum Interes {
        SIN_REVISAR(null), INDEPENDIENTE("independiente de lo que afirma"), INTERESADA("fuente interesada"), DESCONOCIDA("no se sabe quién es");

        private final String etiqueta;

        Interes(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    public enum Cobertura {
        SIN_BUSCAR(null), CONFIRMA("otras fuentes lo confirman"), CONTRADICE("otras fuentes lo contradicen"), NINGUNA("nadie más lo reporta");

        private final String etiqueta;

        Cobertura(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    public enum Origen {
        SIN_RASTREAR(null), COINCIDE("el original dice lo mismo"), DISTINTO("el original dice otra cosa"), NO_ENCONTRADO("no encontré el original");

        private final String etiqueta;

        Origen(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /** Configuración de T19, versión de esquema 1: pasos activos, recordatorio de minutos por paso y notas obligatorias. */
    public record Config(List<Paso> pasos, int minutosPorPaso, boolean exigirNotas) {
        public Config {
            pasos = pasos == null ? List.of() : List.copyOf(pasos);
        }
    }

    public record Entrada(String afirmacion, String fuente, boolean detente, Interes interes, String notaFuente, Cobertura cobertura,
                          String notaCobertura, Origen origen, String notaOrigen) {
        public Entrada {
            interes = interes == null ? Interes.SIN_REVISAR : interes;
            cobertura = cobertura == null ? Cobertura.SIN_BUSCAR : cobertura;
            origen = origen == null ? Origen.SIN_RASTREAR : origen;
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
    public Tipos<Config, Entrada, ResultadoSift> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoSift.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.pasos().isEmpty()) {
            errores.add(new Validacion.Error("config.pasos", "Activa al menos un paso."));
        }
        if (config.minutosPorPaso() < 1 || config.minutosPorPaso() > 15) {
            errores.add(new Validacion.Error("config.minutosPorPaso", "Los minutos por paso van de 1 a 15."));
        }
        if (Textos.vacio(entrada.afirmacion())) {
            errores.add(new Validacion.Error("afirmacion", "Escribe el titular o el mensaje que vas a revisar."));
        }
        if (Textos.vacio(entrada.fuente())) {
            errores.add(new Validacion.Error("fuente", "Escribe quién lo publica o de dónde te llegó."));
        }
        if (config.exigirNotas()) {
            for (Paso paso : List.of(Paso.INVESTIGA, Paso.COBERTURA, Paso.ORIGEN)) {
                if (config.pasos().contains(paso) && hecho(paso, entrada) && Textos.vacio(nota(paso, entrada))) {
                    errores.add(new Validacion.Error(campoNota(paso), "Escribe lo que encontraste en «" + paso.nombre() + "»."));
                }
            }
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoSift> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        UUID afirmacionId = ctx.nuevoId().get();
        String afirmacion = entrada.afirmacion().strip();
        List<ResultadoSift.PasoEvaluado> pasos = new ArrayList<>();
        List<Pendiente> pendientes = new ArrayList<>();
        List<String> faltan = new ArrayList<>();
        for (Paso paso : Paso.values()) {
            if (!config.pasos().contains(paso)) {
                continue;
            }
            boolean hecho = hecho(paso, entrada);
            String hallazgo = hecho ? hallazgo(paso, entrada) : "pendiente";
            pasos.add(new ResultadoSift.PasoEvaluado(paso, paso.nombre(), hecho, hallazgo, paso.nombre() + " · " + hallazgo));
            if (!hecho) {
                faltan.add(paso.nombre());
                pendiente(paso, afirmacion).ifPresent(d -> pendientes.add(
                        new Pendiente(TipoPendiente.VERIFICACION, Optional.of(afirmacionId), Optional.empty(), d)));
            }
        }
        boolean investigaActiva = config.pasos().contains(Paso.INVESTIGA);
        boolean coberturaActiva = config.pasos().contains(Paso.COBERTURA);
        boolean origenActivo = config.pasos().contains(Paso.ORIGEN);
        String senal;
        String motivo;
        if (origenActivo && entrada.origen() == Origen.DISTINTO) {
            senal = "contexto alterado";
            motivo = "El original dice otra cosa: no lo compartas así.";
        } else if (coberturaActiva && entrada.cobertura() == Cobertura.CONTRADICE) {
            senal = "cobertura en contra";
            motivo = "Otras fuentes lo contradicen: no lo compartas sin aclararlo.";
        } else if (investigaActiva && entrada.interes() == Interes.INTERESADA) {
            senal = "fuente interesada";
            motivo = "Quien lo publica gana algo si le crees: busca quién más lo dice.";
        } else if (coberturaActiva && entrada.cobertura() == Cobertura.NINGUNA) {
            senal = "sin cobertura";
            motivo = "Nadie independiente lo reporta todavía: espera antes de compartirlo.";
        } else if (origenActivo && entrada.origen() == Origen.NO_ENCONTRADO) {
            senal = "origen desconocido";
            motivo = "No encontraste el original: no sabes de dónde salió el dato.";
        } else if (!faltan.isEmpty()) {
            senal = "incompleta";
            motivo = "Faltan pasos: " + Textos.enumerar(faltan) + ".";
        } else {
            senal = "sin alertas";
            motivo = pasos.size() == 1 ? "El paso activo no encontró alertas: si lo compartes, enlaza el original."
                    : "Los " + pasos.size() + " pasos activos no encontraron alertas: si lo compartes, enlaza el original.";
        }
        int hechos = (int) pasos.stream().filter(ResultadoSift.PasoEvaluado::hecho).count();
        String resumen = Textos.mayusculaInicial(senal) + " · " + hechos + " de " + pasos.size() + " pasos hechos.";
        List<AfirmacionConRol> afirmaciones = List.of(new AfirmacionConRol(afirmacionId, afirmacion, TipoAfirmacion.HECHO, RolAfirmacion.HIPOTESIS,
                SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO));
        ResultadoSift valor = new ResultadoSift(afirmacion, entrada.fuente().strip(), pasos, senal, motivo, hechos, pasos.size(),
                config.minutosPorPaso(), resumen);
        return new Resultado<>(VERSION_ESQUEMA, valor, afirmaciones, pendientes, resumen);
    }

    private static boolean hecho(Paso paso, Entrada e) {
        return switch (paso) {
            case DETENTE -> e.detente();
            case INVESTIGA -> e.interes() != Interes.SIN_REVISAR;
            case COBERTURA -> e.cobertura() != Cobertura.SIN_BUSCAR;
            case ORIGEN -> e.origen() != Origen.SIN_RASTREAR;
        };
    }

    private static String nota(Paso paso, Entrada e) {
        return switch (paso) {
            case DETENTE -> null;
            case INVESTIGA -> e.notaFuente();
            case COBERTURA -> e.notaCobertura();
            case ORIGEN -> e.notaOrigen();
        };
    }

    private static String campoNota(Paso paso) {
        return switch (paso) {
            case DETENTE -> "detente";
            case INVESTIGA -> "notaFuente";
            case COBERTURA -> "notaCobertura";
            case ORIGEN -> "notaOrigen";
        };
    }

    /** La nota si la hay; si no, la etiqueta de lo elegido. Detente hecho dice "no reenviar aún". */
    private static String hallazgo(Paso paso, Entrada e) {
        if (paso == Paso.DETENTE) {
            return "no reenviar aún";
        }
        String nota = nota(paso, e);
        if (!Textos.vacio(nota)) {
            return nota.strip();
        }
        return switch (paso) {
            case INVESTIGA -> e.interes().etiqueta;
            case COBERTURA -> e.cobertura().etiqueta;
            case ORIGEN -> e.origen().etiqueta;
            case DETENTE -> "no reenviar aún";
        };
    }

    private static Optional<String> pendiente(Paso paso, String afirmacion) {
        return switch (paso) {
            case DETENTE -> Optional.empty();
            case INVESTIGA -> Optional.of("Investigar quién publica: " + afirmacion);
            case COBERTURA -> Optional.of("Buscar mejor cobertura de: " + afirmacion);
            case ORIGEN -> Optional.of("Rastrear el origen de: " + afirmacion);
        };
    }

    @Override
    public ResultadoSift migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }
}
