package pensamiento.tecnicas.f5;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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
import pensamiento.nucleo.PrediccionDeclarada;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.RolAfirmacion;
import pensamiento.nucleo.SentidoAfirmacion;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.TipoPendiente;
import pensamiento.nucleo.Validacion;
import pensamiento.nucleo.reglas.R05Calibracion;
import pensamiento.tecnicas.comun.Textos;

/**
 * T32 · Diario de decisiones (Kahneman 2011; Parrish 2014). Registra la decisión con contexto, alternativas, una predicción
 * con la confianza que la persona declara (R05) y qué la haría cambiar de opinión, con fecha de revisión. Ningún campo se
 * puede saltar; una fecha de revisión demasiado cercana bloquea el guardado. Al guardar, la predicción va a la tabla
 * prediccion y un pendiente de revisión vence en la fecha. No usa IA. Las reglas están en docs/ejemplos/T32.md.
 */
@Component
public class EjecutorDiarioDecisiones implements Ejecutor<EjecutorDiarioDecisiones.Config, EjecutorDiarioDecisiones.Entrada, ResultadoDiario> {

    public static final IdTecnica ID = IdTecnica.de("T32");
    public static final int VERSION_ESQUEMA = 1;

    /** Configuración de T32, versión de esquema 1. Los campos obligatorios no se configuran: nunca se pueden saltar. */
    public record Config(int diasMinimosRevision) {
    }

    /** @param fechaRevision AAAA-MM-DD */
    public record Entrada(String decision, String contexto, String alternativas, String prediccion, Integer confianza, String cambiarOpinion,
                          String fechaRevision) {
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
    public Tipos<Config, Entrada, ResultadoDiario> tipos() {
        return new Tipos<>(Config.class, Entrada.class, ResultadoDiario.class);
    }

    @Override
    public Validacion validar(Config config, Entrada entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.diasMinimosRevision() < 1 || config.diasMinimosRevision() > 365) {
            errores.add(new Validacion.Error("config.diasMinimosRevision", "Los días mínimos hasta la revisión van de 1 a 365."));
        }
        obligatorio(errores, "decision", entrada.decision(), "Escribe la decisión.");
        obligatorio(errores, "contexto", entrada.contexto(), "Escribe el contexto: qué sabías al decidir.");
        obligatorio(errores, "alternativas", entrada.alternativas(), "Escribe las alternativas que descartaste.");
        obligatorio(errores, "prediccion", entrada.prediccion(), "Escribe qué predices que va a pasar.");
        obligatorio(errores, "cambiarOpinion", entrada.cambiarOpinion(), "Escribe qué te haría cambiar de opinión.");
        if (entrada.confianza() == null || entrada.confianza() < 0 || entrada.confianza() > 100) {
            errores.add(new Validacion.Error("confianza", "Escribe tu confianza en la predicción, de 0 a 100."));
        }
        if (Textos.vacio(entrada.fechaRevision())) {
            errores.add(new Validacion.Error("fechaRevision", "Elige la fecha de revisión: sin ella no hay calibración."));
        } else {
            try {
                LocalDate.parse(entrada.fechaRevision().strip());
            } catch (DateTimeParseException e) {
                errores.add(new Validacion.Error("fechaRevision", "La fecha va como AAAA-MM-DD."));
            }
        }
        return new Validacion(errores);
    }

    private static void obligatorio(List<Validacion.Error> errores, String campo, String texto, String mensaje) {
        if (Textos.vacio(texto)) {
            errores.add(new Validacion.Error(campo, mensaje));
        }
    }

    @Override
    public Resultado<ResultadoDiario> ejecutar(Config config, Entrada entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        LocalDate hoy = ctx.reloj().hoy();
        LocalDate revision = LocalDate.parse(entrada.fechaRevision().strip());
        LocalDate desde = hoy.plusDays(config.diasMinimosRevision());
        String bloqueo = revision.isBefore(desde) ? "Guardado bloqueado: la fecha de revisión tiene que ser al menos " + config.diasMinimosRevision()
                + (config.diasMinimosRevision() == 1 ? " día" : " días") + " después de hoy (desde el " + Textos.fecha(desde) + ")." : null;
        int confianza = entrada.confianza();
        List<String> avisos = new ArrayList<>();
        if (confianza >= 90) {
            avisos.add("Con " + confianza + "%, si no se cumple, esta predicción suma " + R05Calibracion.decimal(R05Calibracion.costoSiFalla(confianza))
                    + " al Brier (el peor es 1).");
        } else if (confianza <= 10) {
            avisos.add("Con " + confianza + "%, si se cumple, esta predicción suma " + R05Calibracion.decimal(R05Calibracion.costoSiFalla(confianza))
                    + " al Brier (el peor es 1).");
        }
        String decision = entrada.decision().strip();
        UUID decisionId = ctx.nuevoId().get();
        UUID prediccionAfirmacion = ctx.nuevoId().get();
        UUID condicion = ctx.nuevoId().get();
        UUID prediccionId = ctx.nuevoId().get();
        List<AfirmacionConRol> afirmaciones = List.of(
                new AfirmacionConRol(decisionId, decision, TipoAfirmacion.JUICIO_DE_VALOR, RolAfirmacion.OPCION, SentidoAfirmacion.PRODUCIDA,
                        OrigenAfirmacion.USUARIO),
                new AfirmacionConRol(prediccionAfirmacion, entrada.prediccion().strip(), TipoAfirmacion.PREDICCION, RolAfirmacion.PREDICCION,
                        SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO),
                new AfirmacionConRol(condicion, entrada.cambiarOpinion().strip(), TipoAfirmacion.HECHO, RolAfirmacion.CONDICION_FALSACION,
                        SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO));
        List<Pendiente> pendientes = List.of(new Pendiente(TipoPendiente.REVISION, Optional.of(prediccionAfirmacion), Optional.of(revision),
                "Revisar la decisión: " + decision));
        List<ResultadoDiario.Hito> linea = List.of(new ResultadoDiario.Hito(hoy.toString(), Textos.fecha(hoy), "registrada"),
                new ResultadoDiario.Hito(revision.toString(), Textos.fecha(revision), "revisión programada"));
        String resumen = Textos.comoClausula(decision).isEmpty() ? "" : Textos.mayusculaInicial(Textos.comoClausula(decision)) + " · predicción "
                + confianza + "% · revisar el " + Textos.fecha(revision) + ".";
        ResultadoDiario valor = new ResultadoDiario(decision, entrada.contexto().strip(), entrada.alternativas().strip(), entrada.prediccion().strip(),
                confianza, entrada.cambiarOpinion().strip(), revision.toString(), Textos.fecha(revision), prediccionId, linea, "pendiente de revisión",
                null, bloqueo, avisos, resumen);
        return new Resultado<>(VERSION_ESQUEMA, valor, afirmaciones, pendientes, resumen, List.of(), Optional.empty(), Optional.ofNullable(bloqueo),
                List.of(new PrediccionDeclarada(prediccionId, prediccionAfirmacion, confianza, revision)));
    }

    @Override
    public ResultadoDiario migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }
}
