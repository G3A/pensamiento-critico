package pensamiento.flujos;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import pensamiento.catalogo.CatalogoJson;
import pensamiento.catalogo.MapeadorJson;
import pensamiento.expediente.GuardadoDeEjecuciones;
import pensamiento.nucleo.Afirmacion;
import pensamiento.nucleo.Argumento;
import pensamiento.nucleo.CambioOpinion;
import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.EstadoAfirmacion;
import pensamiento.nucleo.EstandarPrueba;
import pensamiento.nucleo.Evidencia;
import pensamiento.nucleo.EvidenciaGuardada;
import pensamiento.nucleo.FichaFuente;
import pensamiento.nucleo.Fuente;
import pensamiento.nucleo.PendienteGuardado;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.TipoAfirmacion;
import pensamiento.nucleo.TipoPendiente;
import pensamiento.nucleo.Uuid7;
import pensamiento.nucleo.Verificacion;
import pensamiento.nucleo.puertos.Biblioteca;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.nucleo.puertos.RepositorioArgumentos;
import pensamiento.nucleo.puertos.RepositorioEjecucion;
import pensamiento.nucleo.puertos.RepositorioEsquemas;
import pensamiento.nucleo.puertos.RepositorioEvidencias;
import pensamiento.nucleo.puertos.RepositorioVerificaciones;
import pensamiento.nucleo.reglas.R01FuerzaEvidencia;
import pensamiento.nucleo.reglas.R04Aceptabilidad;
import pensamiento.tecnicas.f4.EjecutorCraap;
import pensamiento.tecnicas.f4.EjecutorTriangulacion;
import pensamiento.tecnicas.f4.ResultadoTriangulacion;
import pensamiento.tecnicas.f7.EjecutorArbolMece;

/**
 * La ficha de verificación (A+, P10, P11 y P12): tipificar la afirmación (T17), marcar preguntas críticas, registrar fuentes
 * con SIFT y CRAAP (T19 a T21) como evidencias, y el veredicto con R01, R02 y R03 de T22 · Triangulación y R04 sobre los
 * argumentos donde la afirmación es premisa. La app nunca dice "verdadero": firma "verificada por ti con N fuentes". Las
 * reglas están en docs/verificacion.md. Lo que escribe corre en la transacción de quien llama.
 */
@Service
public class FichaDeVerificacion {

    /** Hasta seis fuentes por afirmación, como T22 · Triangulación. */
    public static final int FUENTES_MAXIMAS = 6;

    /** La configuración de la persona que la ficha usa: el mínimo de grupos de T22 y los pesos de CRAAP de T21. */
    public record Configuracion(EjecutorTriangulacion.Config t22, EjecutorCraap.Config t21) {
    }

    public record TipoInfo(TipoAfirmacion tipo, String nombre, boolean verificable, String probaria) {
    }

    public record Pregunta(String texto, boolean respondida) {
    }

    /** Un chequeo por reglas: su nombre, su estado con texto ("aviso", "sin aviso", "no aplica", "no revisada") y qué dice. */
    public record Chequeo(String nombre, String estado, String texto) {
    }

    /**
     * El cálculo de la ficha con las evidencias de ahora (R01, R02, R03), el mismo que guardaría T22.
     *
     * @param fuerzas la fuerza de cada evidencia por su identificador, con el tipo de la afirmación y la fecha de hoy
     * @param firma   cómo lo dice la app: nunca "verdadero"
     */
    public record Calculo(EstadoAfirmacion estado, int neta, String magnitud, String motivo, Map<UUID, Integer> fuerzas, int cuentan, int grupos,
                          String firma) {
    }

    /**
     * R04 recalculado para un argumento donde la afirmación es premisa: si es aplicable y si su conclusión es aceptable bajo
     * cada estándar, con el estado guardado de cada premisa.
     */
    public record R04Recalculado(UUID argumentoId, UUID ejecucionId, String conclusion, EstandarPrueba estandar, boolean aplicable,
                                 Map<EstandarPrueba, Boolean> aceptable, String frase) {
    }

    public record Ficha(Afirmacion afirmacion, TipoInfo tipo, List<TipoInfo> tipos, List<Pregunta> preguntas, Optional<String> esquema,
                        List<EvidenciaGuardada> evidencias, Calculo calculo, List<Chequeo> chequeos, List<R04Recalculado> argumentos,
                        Optional<Verificacion.Origen> origen, List<PendienteGuardado> pendientes) {

        public int preguntasSinResponder() {
            return (int) preguntas.stream().filter(p -> !p.respondida()).count();
        }

        public boolean admiteMasFuentes() {
            return evidencias.size() < FUENTES_MAXIMAS;
        }
    }

    /** Lo que la persona escribe en la ficha de fuente (P12). Los criterios de CRAAP van todos o ninguno. */
    public record BorradorFuente(String titulo, String autor, String fecha, String tipoFuente, String diseno, String grupo, boolean independiente,
                                 boolean original, Integer actualidad, Integer relevancia, Integer autoridad, Integer exactitud, Integer proposito,
                                 String siftFuente, String siftCobertura, String siftContexto, String postura, String pasaje, String fragmentoId,
                                 String etiquetadaPor, String propuesta) {
    }

    /** Antes de guardar, qué aporta la fuente. */
    public record Previa(Optional<Integer> fuerza, Optional<Integer> puntajeCraap, String texto) {
    }

    /** Lo que vuelve de registrar una fuente: los errores por campo, o la evidencia guardada. */
    public record Registro(Map<String, String> errores, Optional<EvidenciaGuardada> evidencia) {
    }

    /**
     * Lo que vuelve de guardar el veredicto.
     *
     * @param error      por qué no se guardó; vacío si se guardó
     * @param ejecucion  la ejecución de T22 en el expediente, si hubo evidencias
     * @param cerrados   cuántos pendientes de verificación y de revisión se cerraron sobre la afirmación
     * @param cambio     el cambio de opinión, si la confianza cambió
     */
    public record VeredictoGuardado(Optional<String> error, Optional<Ejecucion> ejecucion, int cerrados, Optional<CambioOpinion.Declarado> cambio) {
    }

    private static final Pattern TENDENCIA = Pattern.compile("\\b(mas|menos|mejor|peor|subio|bajo|aumento|disminuyo|crecio)\\b");
    private static final Pattern NUMERO = Pattern.compile("\\d");
    private static final Pattern ESPACIOS = Pattern.compile("\\s+");

    private final RepositorioVerificaciones verificaciones;
    private final RepositorioEvidencias evidencias;
    private final RepositorioArgumentos argumentos;
    private final RepositorioEjecucion ejecuciones;
    private final RepositorioEsquemas esquemas;
    private final Biblioteca biblioteca;
    private final GuardadoDeEjecuciones guardado;
    private final Reloj reloj;
    private final EjecutorTriangulacion t22 = new EjecutorTriangulacion();
    private final Map<TipoAfirmacion, CatalogoJson.TipoVerificable> tipos = new EnumMap<>(TipoAfirmacion.class);

    public FichaDeVerificacion(RepositorioVerificaciones verificaciones, RepositorioEvidencias evidencias, RepositorioArgumentos argumentos,
                               RepositorioEjecucion ejecuciones, RepositorioEsquemas esquemas, Biblioteca biblioteca, GuardadoDeEjecuciones guardado,
                               Reloj reloj) {
        this.verificaciones = verificaciones;
        this.evidencias = evidencias;
        this.argumentos = argumentos;
        this.ejecuciones = ejecuciones;
        this.esquemas = esquemas;
        this.biblioteca = biblioteca;
        this.guardado = guardado;
        this.reloj = reloj;
        for (CatalogoJson.TipoVerificable t : new CatalogoJson().tiposVerificables()) {
            tipos.put(TipoAfirmacion.valueOf(t.tipo().toUpperCase()), t);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Leer la ficha
    // ---------------------------------------------------------------------------------------------

    /** La ficha de una afirmación de la persona; vacía si no existe o si es de otra persona. */
    public Optional<Ficha> abrir(UUID usuarioId, UUID afirmacionId, Configuracion config) {
        return verificaciones.afirmacion(usuarioId, afirmacionId).map(a -> armar(usuarioId, a, config));
    }

    private Ficha armar(UUID usuarioId, Afirmacion a, Configuracion config) {
        List<EvidenciaGuardada> lista = evidencias.deAfirmacion(usuarioId, a.id());
        List<RepositorioArgumentos.ArgumentoGuardado> conPremisa = argumentos.conPremisa(usuarioId, a.id());
        Optional<String> esquemaId = conPremisa.stream().map(g -> g.argumento().esquemaId()).flatMap(Optional::stream).findFirst();
        Optional<pensamiento.nucleo.Esquema> esquema = esquemaId.flatMap(esquemas::porId);
        Verificacion v = verificaciones.verificacion(usuarioId, a.id());
        List<String> textos = esquema.map(e -> e.preguntas().stream().map(pensamiento.nucleo.Esquema.PreguntaCritica::texto).toList())
                .orElseGet(() -> tipos.get(a.tipo()).preguntas());
        List<Pregunta> preguntas = textos.stream().map(t -> new Pregunta(t, v.preguntasRespondidas().contains(t))).toList();
        List<PendienteGuardado> pendientes = ejecuciones.pendientes(usuarioId).stream()
                .filter(p -> p.pendiente().objetoId().equals(Optional.of(a.id()))).toList();
        return new Ficha(a, info(a.tipo()), tiposEnOrden(), preguntas, esquema.map(pensamiento.nucleo.Esquema::nombre), lista,
                calcular(a, lista, config), chequeos(usuarioId, a, conPremisa), r04(usuarioId, conPremisa), verificaciones.origen(usuarioId, a.id()),
                pendientes);
    }

    private List<TipoInfo> tiposEnOrden() {
        return tipos.keySet().stream().map(this::info).toList();
    }

    private TipoInfo info(TipoAfirmacion t) {
        CatalogoJson.TipoVerificable v = tipos.get(t);
        return new TipoInfo(t, v.nombre(), v.verificable(), v.probaria());
    }

    // ---------------------------------------------------------------------------------------------
    // Paso 1 y paso 2
    // ---------------------------------------------------------------------------------------------

    /** Cambia el tipo (paso 1, T17). Falso si la afirmación no es de la persona. */
    public boolean cambiarTipo(UUID usuarioId, UUID afirmacionId, TipoAfirmacion tipo) {
        return verificaciones.cambiarTipo(usuarioId, afirmacionId, tipo);
    }

    /** Marca las preguntas respondidas (paso 2); solo cuentan las de la ficha. Falso si la afirmación no es de la persona. */
    public boolean marcarPreguntas(UUID usuarioId, UUID institucionId, UUID afirmacionId, List<String> respondidas, Configuracion config) {
        Optional<Ficha> ficha = abrir(usuarioId, afirmacionId, config);
        if (ficha.isEmpty()) {
            return false;
        }
        Set<String> validas = new LinkedHashSet<>(ficha.get().preguntas().stream().map(Pregunta::texto).toList());
        List<String> marcadas = respondidas.stream().filter(validas::contains).distinct().toList();
        verificaciones.marcarPreguntas(usuarioId, institucionId, afirmacionId, marcadas, reloj.ahora());
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Paso 3: fuentes y evidencias (P12)
    // ---------------------------------------------------------------------------------------------

    /** Qué aporta la fuente antes de guardarla: su fuerza (R01), su CRAAP y el estado al que pasaría la afirmación (R03). */
    public Optional<Previa> previsualizar(UUID usuarioId, UUID afirmacionId, BorradorFuente borrador, Configuracion config) {
        return verificaciones.afirmacion(usuarioId, afirmacionId).map(a -> {
            Map<String, String> errores = new LinkedHashMap<>();
            Optional<EvidenciaGuardada> nueva = construir(usuarioId, a, borrador, config, errores);
            if (nueva.isEmpty()) {
                return new Previa(Optional.empty(), Optional.empty(),
                        "Completa título, tipo de fuente, pasaje y postura para ver qué aporta. " + String.join(" ", errores.values()));
            }
            List<EvidenciaGuardada> antes = evidencias.deAfirmacion(usuarioId, a.id());
            List<EvidenciaGuardada> despues = new ArrayList<>(antes);
            despues.add(nueva.get());
            EstadoAfirmacion estadoAntes = calcular(a, antes, config).estado();
            Calculo calculoDespues = calcular(a, despues, config);
            EvidenciaGuardada e = nueva.get();
            String sentido = switch (e.postura()) {
                case APOYA -> "a favor";
                case CONTRADICE -> "en contra";
                case MATIZA -> "que matiza (no suma en la fuerza neta)";
            };
            StringBuilder texto = new StringBuilder("Aporta fuerza " + e.fuerza() + " " + sentido + " (R01). Con esta fuente, la afirmación ");
            texto.append(estadoAntes == calculoDespues.estado() ? "sigue «" + texto(estadoAntes) + "»."
                    : "pasa de «" + texto(estadoAntes) + "» a «" + texto(calculoDespues.estado()) + "».");
            if (calculoDespues.estado() == EstadoAfirmacion.EN_VERIFICACION && Math.abs(calculoDespues.neta()) >= 6) {
                texto.append(calculoDespues.neta() > 0 ? " Para «verificada» hace falta una fuente de otro grupo de origen."
                        : " Para «refutada» hace falta una fuente de otro grupo de origen.");
            }
            return new Previa(Optional.of(e.fuerza()), e.fuente().puntajeCraap(), texto.toString());
        });
    }

    /** Guarda la fuente como evidencia de la afirmación. Vacío si la afirmación no es de la persona. */
    public Optional<Registro> registrarFuente(UUID usuarioId, UUID institucionId, UUID afirmacionId, BorradorFuente borrador, Configuracion config) {
        Optional<Afirmacion> a = verificaciones.afirmacion(usuarioId, afirmacionId);
        if (a.isEmpty()) {
            return Optional.empty();
        }
        Map<String, String> errores = new LinkedHashMap<>();
        if (evidencias.deAfirmacion(usuarioId, afirmacionId).size() >= FUENTES_MAXIMAS) {
            errores.put("titulo", "La ficha admite hasta seis fuentes por afirmación, como T22 · Triangulación.");
            return Optional.of(new Registro(errores, Optional.empty()));
        }
        Optional<EvidenciaGuardada> nueva = construir(usuarioId, a.get(), borrador, config, errores);
        if (nueva.isEmpty()) {
            return Optional.of(new Registro(errores, Optional.empty()));
        }
        evidencias.guardar(usuarioId, institucionId, nueva.get());
        return Optional.of(new Registro(Map.of(), nueva));
    }

    /** Quita una evidencia de la afirmación. Falso si no existe o no es de la persona. */
    public boolean quitarEvidencia(UUID usuarioId, UUID afirmacionId, UUID evidenciaId) {
        boolean deEsta = evidencias.porId(usuarioId, evidenciaId).filter(e -> e.afirmacionId().equals(afirmacionId)).isPresent();
        return deEsta && evidencias.quitar(usuarioId, evidenciaId);
    }

    private Optional<EvidenciaGuardada> construir(UUID usuarioId, Afirmacion a, BorradorFuente b, Configuracion config, Map<String, String> errores) {
        if (vacio(b.titulo())) {
            errores.put("titulo", "Escribe el título de la fuente.");
        }
        Optional<Fuente.TipoFuente> tipoFuente = enumeracion(Fuente.TipoFuente.class, b.tipoFuente());
        if (tipoFuente.isEmpty()) {
            errores.put("tipoFuente", "Elige si es primaria, secundaria o terciaria.");
        }
        Optional<Evidencia.Postura> postura = enumeracion(Evidencia.Postura.class, b.postura());
        if (postura.isEmpty()) {
            errores.put("postura", "Elige si el pasaje apoya, contradice o matiza la afirmación.");
        }
        Optional<LocalDate> fecha = Optional.empty();
        if (!vacio(b.fecha())) {
            try {
                fecha = Optional.of(LocalDate.parse(b.fecha().strip()));
            } catch (DateTimeParseException e) {
                errores.put("fecha", "La fecha va como AAAA-MM-DD.");
            }
        }
        List<Integer> criterios = java.util.Arrays.asList(b.actualidad(), b.relevancia(), b.autoridad(), b.exactitud(), b.proposito());
        long puntuados = criterios.stream().filter(java.util.Objects::nonNull).count();
        if (puntuados != 0 && puntuados != 5) {
            errores.put("actualidad", "Puntúa los cinco criterios de CRAAP o ninguno.");
        } else if (criterios.stream().filter(java.util.Objects::nonNull).anyMatch(v -> v < 0 || v > 5)) {
            errores.put("actualidad", "Cada criterio de CRAAP va de 0 a 5.");
        }
        Optional<Biblioteca.Cita> cita = Optional.empty();
        if (!vacio(b.fragmentoId())) {
            cita = uuid(b.fragmentoId()).flatMap(f -> biblioteca.cita(usuarioId, f));
            if (cita.isEmpty()) {
                errores.put("pasaje", "El pasaje ya no está en tu biblioteca: búscalo de nuevo.");
            }
        }
        String pasaje = vacio(b.pasaje()) ? cita.map(Biblioteca.Cita::texto).orElse("") : b.pasaje().strip();
        if (pasaje.isEmpty()) {
            errores.put("pasaje", "Copia el pasaje tal cual.");
        } else if (cita.isPresent() && !normalizado(cita.get().texto()).contains(normalizado(pasaje))) {
            errores.put("pasaje", "El pasaje tiene que estar copiado tal cual del documento.");
        }
        if (!errores.isEmpty()) {
            return Optional.empty();
        }
        Optional<Integer> puntaje = puntuados == 5 ? Optional.of(EjecutorCraap.puntaje(criterios, pesos(config))) : Optional.empty();
        Optional<FichaFuente.Craap> craap = puntuados == 5
                ? Optional.of(new FichaFuente.Craap(b.actualidad(), b.relevancia(), b.autoridad(), b.exactitud(), b.proposito())) : Optional.empty();
        FichaFuente ficha = new FichaFuente(Uuid7.en(reloj.ahora()), b.titulo().strip(), opcional(b.autor()), fecha, tipoFuente.orElseThrow(),
                enumeracion(Fuente.DisenoEstudio.class, b.diseno()), opcional(b.grupo()).map(g -> g.toLowerCase(java.util.Locale.ROOT)), b.independiente(),
                b.original(), puntaje, craap, new FichaFuente.Sift(texto(b.siftFuente()), texto(b.siftCobertura()), texto(b.siftContexto())),
                cita.map(Biblioteca.Cita::documentoId), cita.map(Biblioteca.Cita::documento), cita.flatMap(Biblioteca.Cita::pagina));
        int fuerza = R01FuerzaEvidencia.fuerza(a.tipo(), ficha.comoFuente(), reloj.hoy(), R01FuerzaEvidencia.Parametros.v1());
        // La etiqueta es del modelo solo si la persona adoptó su propuesta y no la cambió.
        boolean delModelo = "modelo".equals(b.etiquetadaPor()) && b.postura() != null && b.postura().equals(b.propuesta());
        return Optional.of(new EvidenciaGuardada(Uuid7.en(reloj.ahora()), a.id(), ficha, cita.map(Biblioteca.Cita::fragmentoId), pasaje,
                postura.orElseThrow(), fuerza, delModelo ? Evidencia.EtiquetadaPor.MODELO : Evidencia.EtiquetadaPor.USUARIO, true));
    }

    private static List<Integer> pesos(Configuracion config) {
        List<Integer> pesos = config.t21().pesos();
        return pesos.stream().mapToInt(Integer::intValue).sum() == 0 ? EjecutorCraap.Config.porDefecto().pesos() : pesos;
    }

    // ---------------------------------------------------------------------------------------------
    // Paso 4: el veredicto
    // ---------------------------------------------------------------------------------------------

    /**
     * Guarda el veredicto: escribe el estado en la afirmación, cierra sus pendientes de verificación y de revisión, guarda la
     * ejecución de T22 con las evidencias (que consume la afirmación y proyecta sus propios pendientes) y, si la confianza
     * cambió, el cambio de opinión con causa "evidencia". Vacío si la afirmación no es de la persona.
     */
    public Optional<VeredictoGuardado> guardarVeredicto(UUID usuarioId, UUID institucionId, UUID afirmacionId, Optional<Integer> confianza, String clave,
                                                        Configuracion config) {
        Optional<Afirmacion> leida = verificaciones.afirmacion(usuarioId, afirmacionId);
        if (leida.isEmpty()) {
            return Optional.empty();
        }
        Afirmacion a = leida.get();
        if (confianza.isPresent() && (confianza.get() < 0 || confianza.get() > 100)) {
            return Optional.of(new VeredictoGuardado(Optional.of("La confianza va de 0 a 100."), Optional.empty(), 0, Optional.empty()));
        }
        List<EvidenciaGuardada> lista = evidencias.deAfirmacion(usuarioId, afirmacionId);
        if (lista.isEmpty() && a.esVerificable()) {
            return Optional.of(new VeredictoGuardado(Optional.of("Registra al menos una fuente antes del veredicto."), Optional.empty(), 0, Optional.empty()));
        }
        Calculo calculo = calcular(a, lista, config);
        verificaciones.guardarVeredicto(usuarioId, afirmacionId, new Verificacion.Veredicto(a.tipo(), calculo.estado(), calculo.neta(), confianza));
        int cerrados = ejecuciones.cerrarPendientes(usuarioId, TipoPendiente.VERIFICACION, afirmacionId)
                + ejecuciones.cerrarPendientes(usuarioId, TipoPendiente.REVISION, afirmacionId);
        if (lista.isEmpty()) {
            return Optional.of(new VeredictoGuardado(Optional.empty(), Optional.empty(), cerrados, Optional.empty()));
        }
        Optional<Verificacion.Origen> origen = verificaciones.origen(usuarioId, afirmacionId);
        Optional<UUID> expediente = origen.flatMap(Verificacion.Origen::expedienteId);
        EjecutorTriangulacion.Entrada entrada = entradaT22(a, lista);
        Contexto ctx = new Contexto(usuarioId, institucionId, expediente, reloj, Optional.empty(), () -> Uuid7.en(reloj.ahora()));
        Resultado<ResultadoTriangulacion> r = t22.ejecutar(config.t22(), entrada, ctx);
        Optional<CambioOpinion.Declarado> cambio = Optional.empty();
        if (a.confianza().isPresent() && confianza.isPresent() && !a.confianza().equals(confianza)) {
            cambio = Optional.of(new CambioOpinion.Declarado(Uuid7.en(reloj.ahora()), afirmacionId, a.confianza().get(), confianza.get(),
                    CambioOpinion.Causa.EVIDENCIA));
            r = r.conCambios(List.of(cambio.get()));
        }
        Ejecucion nueva = new Ejecucion(Uuid7.en(reloj.ahora()), usuarioId, institucionId, EjecutorTriangulacion.ID, r.versionEsquema(), expediente,
                MapeadorJson.escribir(config.t22()), MapeadorJson.escribir(entrada), MapeadorJson.escribir(r.valor()), r.resumen(), r.modelo(), clave,
                reloj.ahora());
        Ejecucion guardada = guardado.guardar(nueva, r);
        return Optional.of(new VeredictoGuardado(Optional.empty(), Optional.of(guardada), cerrados, cambio));
    }

    /** R01, R02 y R03 con las evidencias de ahora: lo mismo que calcula T22 · Triangulación sobre la afirmación. */
    public Calculo calcular(Afirmacion a, List<EvidenciaGuardada> lista, Configuracion config) {
        if (lista.isEmpty()) {
            EstadoAfirmacion estado = a.esVerificable() ? EstadoAfirmacion.SIN_VERIFICAR : EstadoAfirmacion.NO_VERIFICABLE;
            String motivo = a.esVerificable() ? "Ninguna evidencia cuenta todavía." : "Es un juicio de valor o una definición: no se verifica con fuentes.";
            return new Calculo(estado, 0, "débil", motivo, Map.of(), 0, 0, firma(estado, 0, 0, motivo));
        }
        Contexto ctx = new Contexto(a.usuarioId(), a.institucionId(), Optional.empty(), reloj, Optional.empty(), () -> Uuid7.en(reloj.ahora()));
        ResultadoTriangulacion r = t22.ejecutar(config.t22(), entradaT22(a, lista), ctx).valor();
        Map<UUID, Integer> fuerzas = new LinkedHashMap<>();
        for (int i = 0; i < lista.size(); i++) {
            fuerzas.put(lista.get(i).id(), r.evidencias().get(i).fuerza());
        }
        EstadoAfirmacion estado = EstadoAfirmacion.valueOf(r.estado().toUpperCase());
        return new Calculo(estado, r.neta(), r.magnitud(), r.motivo(), fuerzas, r.cuentan(), r.grupos(), firma(estado, r.cuentan(), r.grupos(), r.motivo()));
    }

    /** Las evidencias de la ficha como entrada de T22: cada fuente con su identificador; lo del modelo sin adoptar, sin etiquetar. */
    private static EjecutorTriangulacion.Entrada entradaT22(Afirmacion a, List<EvidenciaGuardada> lista) {
        List<EjecutorTriangulacion.FuenteRegistrada> fuentes = new ArrayList<>();
        for (EvidenciaGuardada e : lista) {
            FichaFuente f = e.fuente();
            fuentes.add(new EjecutorTriangulacion.FuenteRegistrada(f.titulo(), f.tipo().name().toLowerCase(),
                    f.disenoEstudio().map(d -> d.name().toLowerCase()).orElse("no_aplica"), f.fecha().map(LocalDate::toString).orElse(null),
                    f.grupoOrigen().orElse(null), f.independiente(), f.accesoOriginal(), f.puntajeCraap().orElse(null), e.pasaje(),
                    e.adoptada() ? e.postura().name().toLowerCase() : EjecutorTriangulacion.SIN_ETIQUETAR,
                    e.etiquetadaPor() == Evidencia.EtiquetadaPor.MODELO ? "modelo" : null, f.id().toString()));
        }
        return new EjecutorTriangulacion.Entrada(a.texto(), a.tipo().enBaseDeDatos(), fuentes, List.of(), a.id().toString());
    }

    /** La firma del veredicto (docs/verificacion.md, regla 9). */
    static String firma(EstadoAfirmacion estado, int cuentan, int grupos, String motivo) {
        String fuentes = cuentan == 1 ? "1 fuente" : cuentan + " fuentes";
        String deGrupos = grupos == 1 ? "1 grupo" : grupos + " grupos distintos";
        return switch (estado) {
            case VERIFICADA -> "Verificada por ti con " + fuentes + " de " + deGrupos + ", bajo R03.";
            case REFUTADA -> "Refutada por ti con " + fuentes + " de " + deGrupos + ", bajo R03.";
            case DISPUTADA -> "Disputada: hay evidencia fuerte a favor y en contra.";
            case EN_VERIFICACION -> "En verificación: " + motivo;
            case SIN_VERIFICAR -> "Sin verificar: ninguna evidencia cuenta todavía.";
            case NO_VERIFICABLE -> "No verificable: es un juicio de valor o una definición.";
        };
    }

    /** "verificada", "en verificación"… */
    public static String texto(EstadoAfirmacion estado) {
        return EjecutorTriangulacion.texto(estado);
    }

    // ---------------------------------------------------------------------------------------------
    // El modelo etiqueta un pasaje de la biblioteca (RF-14): propone, nunca califica
    // ---------------------------------------------------------------------------------------------

    /** Lo que el modelo necesita para etiquetar un pasaje: se lee en la petición, bajo RLS, antes de abrir el turno. */
    public record PasajeParaEtiquetar(EjecutorTriangulacion.Entrada entrada, Biblioteca.Cita cita) {
    }

    public Optional<PasajeParaEtiquetar> pasajeParaEtiquetar(UUID usuarioId, UUID afirmacionId, UUID fragmentoId) {
        Optional<Afirmacion> a = verificaciones.afirmacion(usuarioId, afirmacionId);
        Optional<Biblioteca.Cita> cita = biblioteca.cita(usuarioId, fragmentoId);
        if (a.isEmpty() || cita.isEmpty()) {
            return Optional.empty();
        }
        EjecutorTriangulacion.FuenteRegistrada fila = new EjecutorTriangulacion.FuenteRegistrada(cita.get().documento(), "primaria", "no_aplica", null,
                null, false, false, null, cita.get().texto(), EjecutorTriangulacion.SIN_ETIQUETAR, null);
        return Optional.of(new PasajeParaEtiquetar(new EjecutorTriangulacion.Entrada(a.get().texto(), a.get().tipo().enBaseDeDatos(), List.of(fila),
                List.of(), a.get().id().toString()), cita.get()));
    }

    /**
     * La propuesta del modelo para el pasaje (prompt de T22, el pasaje entre marcas como material citado). Corre fuera de toda
     * transacción; nunca lanza por el modelo: si no hay o no responde, devuelve la caída.
     */
    public pensamiento.nucleo.ConModelo.Propuestas etiquetar(PasajeParaEtiquetar p, Contexto ctxConIa, java.util.function.Consumer<String> provisional) {
        return t22.proponer(new EjecutorTriangulacion.Config(2, EjecutorTriangulacion.Modo.PLANTILLAS_Y_MODELO), p.entrada(), ctxConIa, provisional, 1);
    }

    // ---------------------------------------------------------------------------------------------
    // Chequeos por reglas y R04
    // ---------------------------------------------------------------------------------------------

    private List<Chequeo> chequeos(UUID usuarioId, Afirmacion a, List<RepositorioArgumentos.ArgumentoGuardado> conPremisa) {
        List<Chequeo> lista = new ArrayList<>();
        String plegado = pensamiento.tecnicas.comun.Textos.plegar(a.texto());
        boolean conNumero = NUMERO.matcher(a.texto()).find();
        if (a.tipo() == TipoAfirmacion.DATO_ESTADISTICO && !conNumero) {
            lista.add(new Chequeo("Cifra y fecha", "aviso", "Afirma un dato sin dar cifra ni fecha."));
        } else if (!conNumero && TENDENCIA.matcher(plegado).find()) {
            lista.add(new Chequeo("Cifra y fecha", "aviso", "Afirma una comparación o tendencia sin cifra ni fecha."));
        } else {
            lista.add(new Chequeo("Cifra y fecha", "sin aviso", "Trae una cifra o no la necesita."));
        }
        if (conPremisa.isEmpty()) {
            lista.add(new Chequeo("Circularidad", "no aplica", "No es premisa de ningún argumento guardado."));
        } else {
            boolean circular = conPremisa.stream().map(g -> g.argumento().argumento().conclusionId())
                    .map(c -> verificaciones.afirmacion(usuarioId, c).map(Afirmacion::texto).orElse(""))
                    .anyMatch(c -> EjecutorArbolMece.seSolapan(a.texto(), c));
            lista.add(circular ? new Chequeo("Circularidad", "aviso", "Dice casi lo mismo que la conclusión: puede ser circular.")
                    : new Chequeo("Circularidad", "sin aviso", "No dice lo mismo que la conclusión (por reglas)."));
        }
        lista.add(new Chequeo("Contradicción entre premisas", "no revisada",
                "Queda para cuando el modelo tenga banco y umbrales: revísala tú."));
        return lista;
    }

    /** R04 para cada argumento de la persona donde la afirmación es premisa, con el estado guardado de cada premisa. */
    private List<R04Recalculado> r04(UUID usuarioId, List<RepositorioArgumentos.ArgumentoGuardado> conPremisa) {
        List<R04Recalculado> lista = new ArrayList<>();
        Map<UUID, List<RepositorioArgumentos.ArgumentoGuardado>> grafos = new LinkedHashMap<>();
        for (RepositorioArgumentos.ArgumentoGuardado g : conPremisa) {
            List<RepositorioArgumentos.ArgumentoGuardado> grafo = grafos.computeIfAbsent(g.ejecucionId(), e -> argumentos.deEjecucion(usuarioId, e));
            lista.add(recalcular(usuarioId, g, grafo));
        }
        return lista;
    }

    /** R04 de un argumento guardado y su conclusión, con lo verificado hasta ahora (la vista del argumento lo muestra). */
    public Optional<R04Recalculado> r04DeArgumento(UUID usuarioId, UUID argumentoId) {
        return argumentos.porId(usuarioId, argumentoId).map(g -> recalcular(usuarioId, g, argumentos.deEjecucion(usuarioId, g.ejecucionId())));
    }

    private R04Recalculado recalcular(UUID usuarioId, RepositorioArgumentos.ArgumentoGuardado g, List<RepositorioArgumentos.ArgumentoGuardado> grafo) {
        List<Argumento> todos = grafo.stream().map(x -> x.argumento().argumento()).toList();
        Map<UUID, R04Aceptabilidad.EstadoPremisa> estados = new LinkedHashMap<>();
        for (Argumento arg : todos) {
            for (Argumento.Premisa p : arg.premisas()) {
                EstadoAfirmacion estado = verificaciones.afirmacion(usuarioId, p.afirmacionId()).map(Afirmacion::estado).orElse(EstadoAfirmacion.SIN_VERIFICAR);
                boolean atacada = todos.stream().anyMatch(x -> x.sentido() == Argumento.Sentido.CONTRA && x.conclusionId().equals(p.afirmacionId()));
                estados.put(p.afirmacionId(), new R04Aceptabilidad.EstadoPremisa(estado, false, atacada));
            }
        }
        Argumento a = g.argumento().argumento();
        EstandarPrueba estandar = g.argumento().estandar();
        Map<EstandarPrueba, Boolean> aceptable = new EnumMap<>(EstandarPrueba.class);
        for (EstandarPrueba e : EstandarPrueba.values()) {
            aceptable.put(e, R04Aceptabilidad.aceptable(a.conclusionId(), e, todos, estados, R04Aceptabilidad.Parametros.v1()));
        }
        boolean aplicable = R04Aceptabilidad.aplicable(a, estados, estandar) && !R04Aceptabilidad.esCiclico(a, todos);
        String conclusion = verificaciones.afirmacion(usuarioId, a.conclusionId()).map(Afirmacion::texto).orElse("");
        boolean algunProAplicable = todos.stream().anyMatch(x -> x.conclusionId().equals(a.conclusionId()) && x.sentido() == Argumento.Sentido.PRO
                && R04Aceptabilidad.aplicable(x, estados, estandar) && !R04Aceptabilidad.esCiclico(x, todos));
        String frase;
        if (aceptable.get(estandar)) {
            frase = "Aceptable bajo " + estandar.nombre() + ".";
        } else if (!algunProAplicable) {
            frase = "No aceptable bajo " + estandar.nombre() + " todavía: ningún argumento a favor es aplicable.";
        } else {
            frase = "No aceptable bajo " + estandar.nombre() + ": los argumentos a favor aplicables no alcanzan el estándar.";
        }
        return new R04Recalculado(a.id(), g.ejecucionId(), conclusion, estandar, aplicable, aceptable, frase);
    }

    // ---------------------------------------------------------------------------------------------

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    private static String texto(String texto) {
        return texto == null ? "" : texto.strip();
    }

    private static Optional<String> opcional(String texto) {
        return vacio(texto) ? Optional.empty() : Optional.of(texto.strip());
    }

    private static String normalizado(String texto) {
        return ESPACIOS.matcher(texto).replaceAll(" ").strip();
    }

    private static Optional<UUID> uuid(String texto) {
        try {
            return Optional.of(UUID.fromString(texto.strip()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static <E extends Enum<E>> Optional<E> enumeracion(Class<E> tipo, String valor) {
        if (vacio(valor)) {
            return Optional.empty();
        }
        for (E e : tipo.getEnumConstants()) {
            if (e.name().equalsIgnoreCase(valor.strip())) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }
}
