package pensamiento.tecnicas.f5;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.Validacion;
import pensamiento.nucleo.reglas.R05Calibracion;
import pensamiento.tecnicas.comun.Textos;

/**
 * T25 · Calibración y puntaje Brier (Brier 1950; Tetlock y Gardner 2015). Con las predicciones que la persona escribe,
 * aplica R05 · Confianza y calibración: puntaje Brier o logarítmico, curva por tramos y avisos, provisionales si un
 * tramo tiene pocas. El tablero del Diario usa la misma regla con las predicciones guardadas. No usa IA. Las reglas
 * están en docs/ejemplos/T25.md.
 */
@Component
public class EjecutorCalibracion implements Ejecutor<EjecutorCalibracion.Config, EjecutorCalibracion.Entrada, ResultadoCalibracion> {

    public static final IdTecnica ID = IdTecnica.de("T25");
    public static final int VERSION_ESQUEMA = 1;
    static final int TOPE_PREDICCIONES = 60;

    public enum Estado {
        SE_CUMPLIO, NO_SE_CUMPLIO, SIN_RESOLVER;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    /** Configuración de T25, versión de esquema 1. */
    public record Config(int tramos, int horizonteMeses, R05Calibracion.Puntaje puntaje, int umbral) {
    }

    /** @param fecha la de resolución, AAAA-MM-DD; opcional */
    public record Prediccion(String texto, Integer confianza, Estado resultado, String fecha) {
    }

    public record Entrada(List<Prediccion> predicciones) {
        public Entrada {
            predicciones = predicciones == null ? List.of() : List.copyOf(predicciones);
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
    public Tipos<Config, Entrada, ResultadoCalibracion> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoCalibracion.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.tramos() != 10 && config.tramos() != 20) {
            errores.add(new Validacion.Error("config.tramos", "Los tramos son de 10 o de 20 puntos."));
        }
        if (config.horizonteMeses() < 0 || config.horizonteMeses() > 120) {
            errores.add(new Validacion.Error("config.horizonteMeses", "El horizonte va de 0 (todas) a 120 meses."));
        }
        if (config.puntaje() == null) {
            errores.add(new Validacion.Error("config.puntaje", "Elige el puntaje: Brier o logarítmico."));
        }
        if (config.umbral() < 1 || config.umbral() > 50) {
            errores.add(new Validacion.Error("config.umbral", "El umbral de aviso va de 1 a 50 predicciones por tramo."));
        }
        if (entrada.predicciones().isEmpty()) {
            errores.add(new Validacion.Error("predicciones", "Escribe al menos una predicción."));
        } else if (entrada.predicciones().size() > TOPE_PREDICCIONES) {
            errores.add(new Validacion.Error("predicciones", "Caben como máximo " + TOPE_PREDICCIONES + " predicciones."));
        } else if (entrada.predicciones().stream().noneMatch(p -> p.resultado() != null && p.resultado() != Estado.SIN_RESOLVER)) {
            errores.add(new Validacion.Error("predicciones", "Marca el resultado de al menos una: solo las resueltas cuentan."));
        }
        for (int i = 0; i < entrada.predicciones().size(); i++) {
            Prediccion p = entrada.predicciones().get(i);
            String campo = "predicciones[" + i + "]";
            if (Textos.vacio(p.texto())) {
                errores.add(new Validacion.Error(campo + ".texto", "Escribe la predicción P" + (i + 1) + "."));
            }
            if (p.confianza() == null || p.confianza() < 0 || p.confianza() > 100) {
                errores.add(new Validacion.Error(campo + ".confianza", "La confianza de P" + (i + 1) + " va de 0 a 100."));
            }
            if (p.resultado() == null) {
                errores.add(new Validacion.Error(campo + ".resultado", "Elige el resultado de P" + (i + 1) + "."));
            }
            if (!Textos.vacio(p.fecha())) {
                try {
                    LocalDate.parse(p.fecha().strip());
                } catch (DateTimeParseException e) {
                    errores.add(new Validacion.Error(campo + ".fecha", "La fecha va como AAAA-MM-DD."));
                }
            }
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoCalibracion> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        List<R05Calibracion.Prediccion> predicciones = entrada.predicciones().stream().map(p -> new R05Calibracion.Prediccion(p.confianza(),
                p.resultado() == Estado.SIN_RESOLVER ? Optional.empty() : Optional.of(p.resultado() == Estado.SE_CUMPLIO),
                Textos.vacio(p.fecha()) ? Optional.empty() : Optional.of(LocalDate.parse(p.fecha().strip())))).toList();
        R05Calibracion.Calibracion c = R05Calibracion.calcular(predicciones,
                new R05Calibracion.Parametros(config.tramos(), config.umbral(), config.puntaje(), config.horizonteMeses()), ctx.reloj().hoy());
        ResultadoCalibracion valor = ResultadoCalibracion.de(c, config.umbral());
        return new Resultado<>(VERSION_ESQUEMA, valor, List.of(), List.of(), valor.resumen());
    }

    @Override
    public ResultadoCalibracion migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }
}
