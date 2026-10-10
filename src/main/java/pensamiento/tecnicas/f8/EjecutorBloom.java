package pensamiento.tecnicas.f8;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecutor;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.NivelBloom;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.Validacion;
import pensamiento.tecnicas.comun.Textos;

/**
 * T48 · Taxonomía de Bloom (Bloom 1956; Anderson y Krathwohl 2001). Con los intentos de un tema del Dojo dice qué niveles
 * (identificar, analizar, evaluar, crear) están dominados, cuál está en curso y cuáles bloqueados, con la regla de
 * EscaleraBloom. No es una técnica de pensamiento crítico: organiza la práctica. No usa IA (la rúbrica de "crear" es de
 * reglas). Las reglas están en docs/ejemplos/T48.md.
 */
@Component
public class EjecutorBloom implements Ejecutor<EjecutorBloom.Config, EjecutorBloom.Entrada, ResultadoBloom> {

    public static final IdTecnica ID = IdTecnica.de("T48");
    public static final int VERSION_ESQUEMA = 1;
    public static final int TOPE_INTENTOS = 60;

    /** Configuración de T48, versión de esquema 1. */
    public record Config(List<NivelBloom> niveles, boolean avanceAutomatico, int aciertosParaDominar) {
        public Config {
            niveles = niveles == null ? List.of() : List.copyOf(niveles);
        }

        public EscaleraBloom.Parametros parametros() {
            return new EscaleraBloom.Parametros(niveles, avanceAutomatico, aciertosParaDominar);
        }
    }

    public enum ResultadoIntento {
        ACIERTO, ERROR;

        @Override
        public String toString() {
            return name().toLowerCase();
        }
    }

    public record Intento(NivelBloom nivel, ResultadoIntento resultado) {
    }

    public record Entrada(TemaDojo tema, List<Intento> intentos) {
        public Entrada {
            intentos = intentos == null ? List.of() : List.copyOf(intentos);
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
    public Tipos<Config, Entrada, ResultadoBloom> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoBloom.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.niveles().isEmpty()) {
            errores.add(new Validacion.Error("config.niveles", "Deja al menos un nivel activo."));
        }
        if (config.aciertosParaDominar() < 2 || config.aciertosParaDominar() > 30) {
            errores.add(new Validacion.Error("config.aciertosParaDominar", "Los aciertos para dominar un nivel van de 2 a 30."));
        }
        if (entrada.tema() == null) {
            errores.add(new Validacion.Error("tema", "Elige el tema que practicas."));
        }
        if (entrada.intentos().size() > TOPE_INTENTOS) {
            errores.add(new Validacion.Error("intentos", "Caben como máximo " + TOPE_INTENTOS + " intentos."));
        }
        for (int i = 0; i < entrada.intentos().size(); i++) {
            Intento x = entrada.intentos().get(i);
            if (x.nivel() == null) {
                errores.add(new Validacion.Error("intentos[" + i + "].nivel", "Elige el nivel del intento " + (i + 1) + "."));
            }
            if (x.resultado() == null) {
                errores.add(new Validacion.Error("intentos[" + i + "].resultado", "Marca si el intento " + (i + 1) + " fue acierto o error."));
            }
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoBloom> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        List<EscaleraBloom.Intento> intentos = entrada.intentos().stream()
                .map(i -> new EscaleraBloom.Intento(i.nivel(), i.resultado() == ResultadoIntento.ACIERTO)).toList();
        ResultadoBloom valor = describir(entrada.tema(), config, EscaleraBloom.calcular(config.parametros(), intentos));
        return new Resultado<>(VERSION_ESQUEMA, valor, List.of(), List.of(), valor.resumen());
    }

    /** El progreso de un tema en palabras: mensaje, avisos y resumen. También lo usa el Dojo. */
    public static ResultadoBloom describir(TemaDojo tema, Config config, EscaleraBloom.Progreso p) {
        int activos = config.niveles().size();
        String mensaje;
        if (p.todosDominados()) {
            mensaje = activos == 1 ? "Dominaste el único nivel activo." : "Dominaste los " + activos + " niveles activos.";
        } else if (!config.avanceAutomatico()) {
            mensaje = "Avance manual: eliges el nivel en el Dojo y ningún nivel se bloquea.";
        } else {
            mensaje = p.siguienteBloqueado().map(b -> "El nivel «" + b + "» se abre al dominar «" + p.actual() + "».")
                    .orElse("Practica «" + p.actual() + "» hasta dominarlo.");
        }
        List<String> avisos = new ArrayList<>();
        if (p.fueraDeNivel() > 0) {
            avisos.add(Textos.contar(p.fueraDeNivel(), "intento", "intentos") + " en un nivel todavía bloqueado no "
                    + (p.fueraDeNivel() == 1 ? "cuenta." : "cuentan."));
        }
        if (p.apagados() > 0) {
            avisos.add(Textos.contar(p.apagados(), "intento", "intentos") + " en niveles apagados no " + (p.apagados() == 1 ? "cuenta." : "cuentan."));
        }
        String resumen;
        if (p.todosDominados()) {
            resumen = tema.cita() + ": " + (activos == 1 ? "dominaste el único nivel activo." : "dominaste los " + activos + " niveles activos.");
        } else if (!config.avanceAutomatico()) {
            resumen = tema.cita() + ": " + p.dominados() + " de " + activos + " niveles dominados · avance manual.";
        } else {
            resumen = tema.cita() + ": nivel " + p.actual() + " · " + p.de(p.actual()).aciertos() + " de " + config.aciertosParaDominar() + " aciertos.";
        }
        List<ResultadoBloom.Nivel> niveles = p.niveles().stream()
                .map(n -> new ResultadoBloom.Nivel(n.nivel(), n.estado(), n.aciertos(), n.intentos())).toList();
        return new ResultadoBloom(tema, niveles, p.actual(), config.aciertosParaDominar(), mensaje, avisos, resumen);
    }

    @Override
    public ResultadoBloom migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }
}
