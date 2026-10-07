package pensamiento.tecnicas.f5;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

/**
 * T28 · Análisis de hipótesis en competencia (ACH), Heuer 1999. Cruza hipótesis con evidencias, suma las
 * inconsistencias ponderadas de cada hipótesis y señala la menos refutada. ACH elimina, no confirma: la
 * evidencia a favor no resta inconsistencias. Las reglas de cálculo están en docs/ejemplos/T28.md.
 */
@Component
public class EjecutorAch implements Ejecutor<ConfigAch, EntradaAch, ResultadoAch> {

    public static final IdTecnica ID = IdTecnica.de("T28");
    public static final int VERSION_ESQUEMA = 1;

    private static final int LARGO_PREGUNTA = 300;
    private static final int LARGO_TEXTO = 200;
    private static final Set<String> CIN = Set.of("C", "I", "N");
    private static final Set<String> NUMERICA = Set.of("-2", "-1", "0", "1", "2", "+1", "+2");

    @Override
    public IdTecnica id() {
        return ID;
    }

    @Override
    public int versionEsquema() {
        return VERSION_ESQUEMA;
    }

    @Override
    public Tipos<ConfigAch, EntradaAch, ResultadoAch> tipos() {
        return new Tipos<>(ConfigAch.class, EntradaAch.class, ResultadoAch.class);
    }

    @Override
    public Validacion validar(ConfigAch config, EntradaAch entrada) {
        List<Validacion.Error> errores = new ArrayList<>();
        if (config.maxHipotesis() < ConfigAch.MIN_HIPOTESIS || config.maxHipotesis() > ConfigAch.TOPE_HIPOTESIS) {
            errores.add(new Validacion.Error("maxHipotesis", "El máximo de hipótesis va de 2 a 8."));
        }
        if (config.escala() == null) {
            errores.add(new Validacion.Error("escala", "Elige la escala: C, I, N o numérica."));
        }
        textoObligatorio(errores, "pregunta", entrada.pregunta(), LARGO_PREGUNTA, "Escribe la pregunta que quieres responder.");
        int n = entrada.hipotesis().size();
        if (n < ConfigAch.MIN_HIPOTESIS) {
            errores.add(new Validacion.Error("hipotesis", "Escribe al menos dos hipótesis: ACH compara explicaciones en competencia."));
        } else if (n > config.maxHipotesis()) {
            errores.add(new Validacion.Error("hipotesis", "Tu configuración admite como máximo " + config.maxHipotesis() + " hipótesis."));
        }
        for (int i = 0; i < n; i++) {
            textoObligatorio(errores, "hipotesis[" + i + "].texto", entrada.hipotesis().get(i).texto(), LARGO_TEXTO,
                    "Escribe la hipótesis H" + (i + 1) + ".");
        }
        if (entrada.evidencias().isEmpty()) {
            errores.add(new Validacion.Error("evidencias", "Escribe al menos una evidencia."));
        } else if (entrada.evidencias().size() > ConfigAch.TOPE_EVIDENCIAS) {
            errores.add(new Validacion.Error("evidencias", "Caben como máximo " + ConfigAch.TOPE_EVIDENCIAS + " evidencias."));
        }
        for (int e = 0; e < entrada.evidencias().size(); e++) {
            EntradaAch.Evidencia ev = entrada.evidencias().get(e);
            String campo = "evidencias[" + e + "]";
            textoObligatorio(errores, campo + ".texto", ev.texto(), LARGO_TEXTO, "Escribe la evidencia E" + (e + 1) + ".");
            if (config.pesosActivos() && ev.peso() == null) {
                errores.add(new Validacion.Error(campo + ".peso", "Elige el peso de E" + (e + 1) + ": alto, medio o bajo."));
            }
            List<String> celdas = ev.celdas() == null ? List.of() : ev.celdas();
            for (int h = 0; h < n; h++) {
                String celda = h < celdas.size() ? celdas.get(h) : null;
                if (config.escala() != null && !celdaValida(config.escala(), celda)) {
                    errores.add(new Validacion.Error(campo + ".celdas[" + h + "]", config.escala() == ConfigAch.Escala.CIN
                            ? "Marca E" + (e + 1) + " frente a H" + (h + 1) + " como C, I o N."
                            : "Marca E" + (e + 1) + " frente a H" + (h + 1) + " con un número de -2 a 2."));
                }
            }
        }
        return new Validacion(errores);
    }

    @Override
    public Resultado<ResultadoAch> ejecutar(ConfigAch config, EntradaAch entrada, Contexto ctx) {
        Validacion validacion = validar(config, entrada);
        if (!validacion.esValida()) {
            throw new IllegalArgumentException("Entrada inválida para " + ID + ": " + validacion.errores());
        }
        int n = entrada.hipotesis().size();
        List<ResultadoAch.EvidenciaEvaluada> evidencias = new ArrayList<>();
        int[] inconsistencias = new int[n];
        List<List<String>> enContra = new ArrayList<>();
        for (int h = 0; h < n; h++) {
            enContra.add(new ArrayList<>());
        }
        for (int e = 0; e < entrada.evidencias().size(); e++) {
            EntradaAch.Evidencia ev = entrada.evidencias().get(e);
            String codigo = "E" + (e + 1);
            int peso = config.pesosActivos() ? ev.peso().valor() : 1;
            List<String> celdas = new ArrayList<>();
            for (int h = 0; h < n; h++) {
                String celda = normalizar(ev.celdas().get(h));
                celdas.add(celda);
                int inconsistencia = inconsistencia(celda);
                if (inconsistencia > 0) {
                    inconsistencias[h] += peso * inconsistencia;
                    enContra.get(h).add(codigo);
                }
            }
            evidencias.add(new ResultadoAch.EvidenciaEvaluada(codigo, ev.texto().trim(),
                    config.pesosActivos() ? ev.peso() : null, peso, celdas));
        }

        List<ResultadoAch.HipotesisEvaluada> hipotesis = new ArrayList<>();
        List<AfirmacionConRol> afirmaciones = new ArrayList<>();
        int minimo = Integer.MAX_VALUE;
        int maximo = Integer.MIN_VALUE;
        for (int h = 0; h < n; h++) {
            String texto = entrada.hipotesis().get(h).texto().trim();
            UUID id = ctx.nuevoId().get();
            hipotesis.add(new ResultadoAch.HipotesisEvaluada("H" + (h + 1), id, texto, inconsistencias[h], enContra.get(h)));
            afirmaciones.add(new AfirmacionConRol(id, texto, TipoAfirmacion.CAUSAL, RolAfirmacion.HIPOTESIS,
                    SentidoAfirmacion.PRODUCIDA, OrigenAfirmacion.USUARIO));
            minimo = Math.min(minimo, inconsistencias[h]);
            maximo = Math.max(maximo, inconsistencias[h]);
        }
        final int min = minimo;
        final int max = maximo;
        List<String> menosRefutadas = hipotesis.stream().filter(h -> h.inconsistencias() == min)
                .map(ResultadoAch.HipotesisEvaluada::codigo).toList();
        List<String> masRefutadas = min == max ? List.of()
                : hipotesis.stream().filter(h -> h.inconsistencias() == max).map(ResultadoAch.HipotesisEvaluada::codigo).toList();

        List<ResultadoAch.Verificacion> verificar = new ArrayList<>();
        List<Pendiente> pendientes = new ArrayList<>();
        for (String codigo : menosRefutadas) {
            ResultadoAch.HipotesisEvaluada hip = hipotesis.get(Integer.parseInt(codigo.substring(1)) - 1);
            Optional<ResultadoAch.EvidenciaEvaluada> masApoyo = evidenciaQueMasApoya(evidencias, hipotesis.indexOf(hip));
            verificar.add(new ResultadoAch.Verificacion(codigo, masApoyo.map(ResultadoAch.EvidenciaEvaluada::codigo).orElse(null)));
            String descripcion = masApoyo
                    .map(ev -> "Verificar " + ev.codigo() + ": " + ev.texto())
                    .orElse("Buscar una evidencia que distinga " + codigo + " de las demás");
            pendientes.add(new Pendiente(TipoPendiente.VERIFICACION, Optional.of(hip.afirmacionId()), Optional.empty(),
                    descripcion + " (hipótesis " + codigo + ": " + hip.texto() + ")"));
        }

        String resumen = resumen(hipotesis, menosRefutadas);
        ResultadoAch valor = new ResultadoAch(entrada.pregunta().trim(), config.escala(), config.pesosActivos(), hipotesis, evidencias,
                menosRefutadas, menosRefutadas.size() > 1, masRefutadas, verificar, resumen);
        return new Resultado<>(VERSION_ESQUEMA, valor, afirmaciones, pendientes, resumen);
    }

    @Override
    public ResultadoAch migrar(Json datosViejos, int desdeVersion) {
        throw new IllegalArgumentException(ID + " no tiene versiones anteriores a la " + VERSION_ESQUEMA
                + "; se pidió migrar desde la " + desdeVersion);
    }

    private static String resumen(List<ResultadoAch.HipotesisEvaluada> hipotesis, List<String> menosRefutadas) {
        ResultadoAch.HipotesisEvaluada primera = hipotesis.stream()
                .filter(x -> x.codigo().equals(menosRefutadas.getFirst())).findFirst().orElseThrow();
        String unidad = primera.inconsistencias() == 1 ? "inconsistencia ponderada" : "inconsistencias ponderadas";
        if (menosRefutadas.size() == 1) {
            return "Menos refutada: " + primera.codigo() + ", " + primera.texto() + " (" + primera.inconsistencias() + " " + unidad + ").";
        }
        return "Empate entre " + enumerar(menosRefutadas) + " (" + primera.inconsistencias() + " " + unidad + " cada una).";
    }

    /** "H1 y H2", "H1, H2 y H3". */
    public static String enumerar(List<String> codigos) {
        if (codigos.size() == 1) {
            return codigos.getFirst();
        }
        return String.join(", ", codigos.subList(0, codigos.size() - 1)) + " y " + codigos.getLast();
    }

    /** Peso por consistencia; en empate gana la primera escrita; vacía si ninguna la apoya. */
    private static Optional<ResultadoAch.EvidenciaEvaluada> evidenciaQueMasApoya(List<ResultadoAch.EvidenciaEvaluada> evidencias, int h) {
        ResultadoAch.EvidenciaEvaluada mejor = null;
        int mejorApoyo = 0;
        for (ResultadoAch.EvidenciaEvaluada ev : evidencias) {
            int apoyo = ev.pesoValor() * consistencia(ev.celdas().get(h));
            if (apoyo > mejorApoyo) {
                mejor = ev;
                mejorApoyo = apoyo;
            }
        }
        return Optional.ofNullable(mejor);
    }

    private static int inconsistencia(String celda) {
        return switch (celda) {
            case "I" -> 1;
            case "C", "N" -> 0;
            default -> Math.max(0, -Integer.parseInt(celda));
        };
    }

    private static int consistencia(String celda) {
        return switch (celda) {
            case "C" -> 1;
            case "I", "N" -> 0;
            default -> Math.max(0, Integer.parseInt(celda));
        };
    }

    /** "+1" se guarda como "1"; "c" como "C". */
    private static String normalizar(String celda) {
        String limpia = celda.trim().toUpperCase();
        return limpia.startsWith("+") ? limpia.substring(1) : limpia;
    }

    private static boolean celdaValida(ConfigAch.Escala escala, String celda) {
        if (celda == null) {
            return false;
        }
        String limpia = celda.trim().toUpperCase();
        return escala == ConfigAch.Escala.CIN ? CIN.contains(limpia) : NUMERICA.contains(limpia);
    }

    private static void textoObligatorio(List<Validacion.Error> errores, String campo, String texto, int largo, String mensaje) {
        if (texto == null || texto.isBlank()) {
            errores.add(new Validacion.Error(campo, mensaje));
        } else if (texto.trim().length() > largo) {
            errores.add(new Validacion.Error(campo, "Como máximo " + largo + " caracteres."));
        }
    }
}
