package pensamiento.flujos;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;

import pensamiento.catalogo.MapeadorJson;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.Expediente;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Prediccion;
import pensamiento.nucleo.TipoPendiente;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.nucleo.puertos.RepositorioEjecucion;
import pensamiento.nucleo.puertos.RepositorioExpediente;
import pensamiento.nucleo.puertos.RepositorioPredicciones;
import pensamiento.nucleo.reglas.R05Calibracion;
import pensamiento.tecnicas.comun.Textos;
import pensamiento.tecnicas.f5.ResultadoCalibracion;
import pensamiento.tecnicas.f5.ResultadoDiario;

/**
 * Flujo D · Diario de decisiones y calibración (P17): el tablero con las revisiones que ya vencieron, las decisiones
 * abiertas con el aviso por historial, las resueltas y la curva de calibración de R05; la revisión de una predicción,
 * que la deja inmutable y cierra su pendiente; y la creación de una decisión nueva, que es un expediente donde el
 * asistente guarda cada técnica. La fecha de hoy sale del puerto Reloj. La transacción la abre quien llama.
 */
@Service
public class DiarioDeDecisiones {

    /** Los expedientes que crea el Diario llevan este prefijo: así el tablero sabe cuáles están en preparación. */
    public static final String PREFIJO = "Decisión: ";

    /**
     * Una decisión del tablero: la predicción, el texto de la decisión, su expediente (si la registró el asistente) y, si
     * está abierta, lo que dice el historial de la persona en el tramo de su confianza.
     */
    public record Decision(Prediccion prediccion, String decision, Optional<UUID> expedienteId, String revisarEl, Optional<String> historial) {
    }

    /**
     * @param porRevisar   pendientes con la fecha de revisión ya cumplida: la lista al entrar
     * @param abiertas     pendientes que todavía no vencen
     * @param resueltas    de la más reciente a la más antigua
     * @param enPreparacion expedientes del Diario sin una decisión registrada todavía
     */
    public record Tablero(List<Decision> porRevisar, List<Decision> abiertas, List<Decision> resueltas, ResultadoCalibracion calibracion,
                          List<Expediente> enPreparacion) {
    }

    /** La predicción no existe o es de otra persona: para quien pregunta, es lo mismo. */
    public static class NoEncontrada extends RuntimeException {
        public NoEncontrada() {
            super("No encontramos esa predicción.");
        }
    }

    private final RepositorioPredicciones predicciones;
    private final RepositorioEjecucion ejecuciones;
    private final RepositorioExpediente expedientes;
    private final Reloj reloj;

    public DiarioDeDecisiones(RepositorioPredicciones predicciones, RepositorioEjecucion ejecuciones, RepositorioExpediente expedientes, Reloj reloj) {
        this.predicciones = predicciones;
        this.ejecuciones = ejecuciones;
        this.expedientes = expedientes;
        this.reloj = reloj;
    }

    public Tablero tablero(UUID usuarioId) {
        LocalDate hoy = reloj.hoy();
        List<Prediccion> todas = predicciones.deUsuario(usuarioId);
        R05Calibracion.Calibracion calibracion = R05Calibracion.calcular(todas.stream().map(DiarioDeDecisiones::paraR05).toList(),
                R05Calibracion.Parametros.diario(), hoy);
        List<Decision> porRevisar = new ArrayList<>();
        List<Decision> abiertas = new ArrayList<>();
        List<Decision> resueltas = new ArrayList<>();
        for (Prediccion p : todas) {
            Optional<Ejecucion> ejecucion = ejecuciones.porId(usuarioId, p.ejecucionId());
            String decision = ejecucion.map(DiarioDeDecisiones::decisionDe).orElse(p.texto());
            Optional<UUID> expediente = ejecucion.flatMap(Ejecucion::expedienteId);
            Optional<String> historial = p.resuelta() ? Optional.empty() : calibracion.tramoDe(p.confianza())
                    .map(t -> "Tu historial entre " + t.desde() + " y " + t.hasta() + "%: se cumple el " + t.porcentajeCumplido() + "% ("
                            + t.n() + (t.n() == 1 ? " resuelta" : " resueltas") + (t.provisional() ? ", provisional" : "") + ").");
            Decision d = new Decision(p, decision, expediente, Textos.fecha(p.fechaRevision()), historial);
            if (p.resuelta()) {
                resueltas.add(d);
            } else if (p.vencida(hoy)) {
                porRevisar.add(d);
            } else {
                abiertas.add(d);
            }
        }
        resueltas.sort(Comparator.comparing((Decision d) -> d.prediccion().resueltaEn().orElse(Instant.EPOCH)).reversed());
        List<Expediente> enPreparacion = expedientes.deUsuario(usuarioId).stream()
                .filter(x -> x.nombre().startsWith(PREFIJO))
                .filter(x -> ejecuciones.porExpediente(usuarioId, x.id()).stream().noneMatch(e -> e.tecnica().equals(Asistente.T32)))
                .toList();
        return new Tablero(porRevisar, abiertas, resueltas, ResultadoCalibracion.de(calibracion, R05Calibracion.Parametros.diario().umbral()),
                enPreparacion);
    }

    /**
     * R05: registra si la predicción se cumplió, con la fecha del reloj, y cierra su pendiente de revisión.
     *
     * @throws NoEncontrada         si no existe o es de otra persona
     * @throws Prediccion.YaResuelta si ya estaba resuelta: no se modifica nada
     */
    public Prediccion resolver(UUID usuarioId, UUID prediccionId, boolean seCumplio) {
        Prediccion resuelta = predicciones.resolver(usuarioId, prediccionId, seCumplio, reloj.ahora()).orElseThrow(NoEncontrada::new);
        ejecuciones.cerrarPendientes(usuarioId, TipoPendiente.REVISION, resuelta.afirmacionId());
        return resuelta;
    }

    /** El estado vigente de una predicción de la persona, para pintar su registro al día. */
    public Optional<Prediccion> prediccion(UUID usuarioId, UUID prediccionId) {
        return predicciones.porId(usuarioId, prediccionId);
    }

    /** El nombre de un expediente nuevo del Diario. */
    public static String nombreDeExpediente(String titulo) {
        return PREFIJO + (titulo == null ? "" : titulo.strip());
    }

    /** "Decisión: Abrir la segunda sucursal" → "Abrir la segunda sucursal". */
    public static String titulo(Expediente expediente) {
        return expediente.nombre().startsWith(PREFIJO) ? expediente.nombre().substring(PREFIJO.length()) : expediente.nombre();
    }

    private static String decisionDe(Ejecucion e) {
        if (!e.tecnica().equals(Asistente.T32)) {
            return e.resumen();
        }
        try {
            return MapeadorJson.leer(e.resultado(), ResultadoDiario.class).decision();
        } catch (RuntimeException ex) {
            return e.resumen();
        }
    }

    private static R05Calibracion.Prediccion paraR05(Prediccion p) {
        return new R05Calibracion.Prediccion(p.confianza(), p.resuelta() ? Optional.of(p.estado() == Prediccion.Estado.ACIERTO) : Optional.empty(),
                Optional.empty());
    }

    /**
     * El asistente de una decisión (P18 y los cuatro pasos de P17): qué técnicas van en cada paso, cuáles ya se guardaron en
     * el expediente y qué se puede abrir. No se avanza del paso 0 sin una reformulación elegida (T40) y sin certezas y
     * supuestos (T41); el registro (T32) pide antes la lista de T16.
     */
    public static final class Asistente {

        public static final IdTecnica T16 = IdTecnica.de("T16");
        public static final IdTecnica T24 = IdTecnica.de("T24");
        public static final IdTecnica T26 = IdTecnica.de("T26");
        public static final IdTecnica T27 = IdTecnica.de("T27");
        public static final IdTecnica T28 = IdTecnica.de("T28");
        public static final IdTecnica T29 = IdTecnica.de("T29");
        public static final IdTecnica T30 = IdTecnica.de("T30");
        public static final IdTecnica T31 = IdTecnica.de("T31");
        public static final IdTecnica T32 = IdTecnica.de("T32");
        public static final IdTecnica T33 = IdTecnica.de("T33");
        public static final IdTecnica T40 = IdTecnica.de("T40");
        public static final IdTecnica T41 = IdTecnica.de("T41");
        public static final IdTecnica T42 = IdTecnica.de("T42");
        public static final IdTecnica T43 = IdTecnica.de("T43");
        public static final IdTecnica T44 = IdTecnica.de("T44");

        /** Un paso: su número, su nombre y sus técnicas en orden; las obligatorias se marcan. */
        public record Paso(int numero, String nombre, List<IdTecnica> tecnicas, List<IdTecnica> obligatorias) {
        }

        /** Los cinco pasos, con las técnicas que la tabla de la sección 6 asigna a cada uno. */
        public static final List<Paso> PASOS = List.of(
                new Paso(0, "Problema", List.of(T40, T41, T43, T42, T44), List.of(T40, T41)),
                new Paso(1, "Contexto", List.of(T27, T26, T24), List.of()),
                new Paso(2, "Pre-mortem", List.of(T29, T30), List.of()),
                new Paso(3, "ACH", List.of(T28, T33), List.of()),
                new Paso(4, "Predicción", List.of(T31, T16, T32), List.of(T32)));

        private final Expediente expediente;
        private final Map<IdTecnica, Ejecucion> ultimas;

        /** @param ejecuciones las del expediente, de la más reciente a la más antigua */
        public Asistente(Expediente expediente, List<Ejecucion> ejecuciones) {
            this.expediente = expediente;
            this.ultimas = new LinkedHashMap<>();
            for (Ejecucion e : ejecuciones) {
                ultimas.putIfAbsent(e.tecnica(), e);
            }
        }

        public Expediente expediente() {
            return expediente;
        }

        public String titulo() {
            return DiarioDeDecisiones.titulo(expediente);
        }

        public Optional<Ejecucion> ultima(IdTecnica t) {
            return Optional.ofNullable(ultimas.get(t));
        }

        public boolean hecha(IdTecnica t) {
            return ultimas.containsKey(t);
        }

        /** El paso 0 está cumplido cuando hay reformulación elegida (T40) y certezas y supuestos (T41). */
        public boolean problemaDefinido() {
            return hecha(T40) && hecha(T41);
        }

        public boolean pasoHabilitado(int paso) {
            return paso == 0 || problemaDefinido();
        }

        /** El registro de la decisión (T32) pide antes la lista de verificación (T16). */
        public boolean tecnicaHabilitada(IdTecnica t) {
            Paso paso = PASOS.stream().filter(p -> p.tecnicas().contains(t)).findFirst().orElseThrow();
            return pasoHabilitado(paso.numero()) && (!t.equals(T32) || hecha(T16));
        }

        /** Registrada: el asistente terminó cuando hay una decisión (T32) guardada en el expediente. */
        public boolean registrada() {
            return hecha(T32);
        }

        /**
         * Lo que el formulario de la técnica trae ya escrito, a partir de lo que se guardó antes en el expediente: la
         * reformulación elegida pasa al problema de las demás, las causas de Ishikawa al árbol MECE, las ideas seleccionadas
         * de SCAMPER a las opciones, y el ganador de la matriz a la decisión. Vacío si no hay nada que encadenar.
         */
        public Map<String, Object> valoresIniciales(IdTecnica t, LocalDate hoy) {
            Map<String, Object> v = new LinkedHashMap<>();
            Optional<String> problema = resultado(T40, pensamiento.tecnicas.f7.ResultadoDefinicionProblema.class)
                    .map(pensamiento.tecnicas.f7.ResultadoDefinicionProblema::elegida);
            List<String> opciones = resultado(T44, pensamiento.tecnicas.f7.ResultadoScamper.class)
                    .map(r -> r.seleccionadas().stream().map(s -> s.replaceAll(" \\([A-Z]\\)$", "")).toList()).orElse(List.of());
            String decision = titulo();
            if (t.equals(T40)) {
                v.put("original", decision);
            } else if (t.equals(T41)) {
                v.put("problema", problema.orElse(decision));
            } else if (t.equals(T43)) {
                v.put("efecto", problema.orElse(decision));
            } else if (t.equals(T42)) {
                resultado(T43, pensamiento.tecnicas.f7.ResultadoIshikawa.class).ifPresentOrElse(i -> arbolDesdeIshikawa(i, v),
                        () -> v.put("raiz", problema.orElse(decision)));
            } else if (t.equals(T44)) {
                v.put("problema", problema.orElse(decision));
            } else if (t.equals(T27) || t.equals(T31)) {
                v.put("pregunta", decision);
                if (!opciones.isEmpty()) {
                    v.put("opciones", opciones.stream().limit(t.equals(T27) ? 6 : 8).map(o -> {
                        Map<String, Object> fila = new LinkedHashMap<>();
                        fila.put("texto", o);
                        return (Object) fila;
                    }).toList());
                }
            } else if (t.equals(T26) || t.equals(T24)) {
                v.put(t.equals(T26) ? "pregunta" : "afirmacion", "");
            } else if (t.equals(T29) || t.equals(T16)) {
                v.put("decision", decision);
            } else if (t.equals(T30)) {
                v.put("meta", decision);
            } else if (t.equals(T28)) {
                v.put("pregunta", problema.orElse(decision));
            } else if (t.equals(T33)) {
                v.put("hecho", problema.orElse(decision));
            } else if (t.equals(T32)) {
                Optional<pensamiento.tecnicas.f5.ResultadoMatriz> matriz = resultado(T31, pensamiento.tecnicas.f5.ResultadoMatriz.class);
                v.put("decision", matriz.map(pensamiento.tecnicas.f5.ResultadoMatriz::ganador).filter(g -> g != null).orElse(decision));
                matriz.ifPresent(m -> v.put("alternativas", String.join(" · ", m.filas().stream()
                        .map(pensamiento.tecnicas.f5.ResultadoMatriz.FilaOpcion::opcion).filter(o -> !o.equals(m.ganador())).toList())));
                List<String> contexto = new ArrayList<>();
                problema.ifPresent(p -> contexto.add("Problema: " + p));
                matriz.ifPresent(m -> contexto.add("Matriz ponderada: " + m.resumen()));
                if (!contexto.isEmpty()) {
                    v.put("contexto", String.join(" ", contexto));
                }
                v.put("fechaRevision", hoy.plusDays(90).toString());
            }
            v.values().removeIf(x -> x instanceof String s && s.isEmpty());
            return v;
        }

        /** "Ver como árbol MECE" (P18): cada categoría de Ishikawa es una rama y cada causa una hoja. */
        private static void arbolDesdeIshikawa(pensamiento.tecnicas.f7.ResultadoIshikawa i, Map<String, Object> v) {
            v.put("raiz", i.efecto());
            List<Object> nodos = new ArrayList<>();
            for (var c : i.categorias()) {
                nodos.add(new LinkedHashMap<>(Map.of("texto", c.nombre())));
            }
            for (int k = 0; k < i.categorias().size(); k++) {
                for (var causa : i.categorias().get(k).causas()) {
                    nodos.add(new LinkedHashMap<>(Map.of("texto", causa.texto(), "padre", "N" + (k + 1))));
                }
            }
            v.put("nodos", nodos);
        }

        private <R> Optional<R> resultado(IdTecnica t, Class<R> tipo) {
            return ultima(t).flatMap(e -> {
                try {
                    return Optional.of(MapeadorJson.leer(e.resultado(), tipo));
                } catch (RuntimeException ex) {
                    return Optional.empty();
                }
            });
        }
    }

    /** El asistente de una decisión de la persona; vacío si el expediente no existe o es de otra persona. */
    public Optional<Asistente> asistente(UUID usuarioId, UUID expedienteId) {
        return expedientes.porId(usuarioId, expedienteId).map(x -> new Asistente(x, ejecuciones.porExpediente(usuarioId, x.id())));
    }
}
