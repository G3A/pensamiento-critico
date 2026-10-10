package pensamiento.flujos;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import pensamiento.nucleo.BancoDojo;
import pensamiento.nucleo.Competencia;
import pensamiento.nucleo.IntentoDojo;
import pensamiento.nucleo.NivelBloom;
import pensamiento.nucleo.Uuid7;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.nucleo.puertos.RepositorioDojo;
import pensamiento.tecnicas.f8.EjecutorBloom;
import pensamiento.tecnicas.f8.EjecutorRepeticion;
import pensamiento.tecnicas.f8.EscaleraBloom;
import pensamiento.tecnicas.f8.ResultadoBloom;
import pensamiento.tecnicas.f8.ResultadoRepeticion;
import pensamiento.tecnicas.f8.Sm2;
import pensamiento.tecnicas.f8.TemaDojo;

/**
 * Flujo B · Dojo de razonamiento (P19). Elige el siguiente reto del banco por reglas (límite del día, repasos que tocan con
 * SM-2, conceptos nuevos en el orden del banco y el nivel de Bloom del tema), lo califica por reglas (la opción correcta o la
 * rúbrica de "crear") y guarda el intento con la proyección de la competencia. No usa el modelo. El estado del Dojo no se
 * guarda: se calcula sobre los intentos con las reglas de T48 y T49. La transacción la abre quien llama. Reglas en
 * docs/dojo.md.
 */
@Service
public class DojoDeRazonamiento {

    /** La configuración de la persona en T48 · Taxonomía de Bloom y T49 · Repetición espaciada. */
    public record Configuracion(EjecutorBloom.Config bloom, EjecutorRepeticion.Config repeticion) {
    }

    /** El reto que sale, con su concepto y si es un repaso que tocaba o un concepto nuevo. */
    public record RetoElegido(BancoDojo.Reto reto, BancoDojo.Concepto concepto, TemaDojo tema, boolean repaso) {
    }

    /**
     * Lo que muestra la pantalla del Dojo.
     *
     * @param mensaje vacío si hay reto; si no, por qué no hay ("Por hoy terminaste…")
     * @param nivel   el nivel del tema del reto (o del primer tema del filtro si no hay reto)
     */
    public record Pantalla(Optional<TemaDojo> filtro, int hechosHoy, int limite, int racha, Optional<RetoElegido> reto, Optional<String> mensaje,
                           NivelBloom nivel, String progreso, boolean avanceManual, List<NivelBloom> nivelesActivos) {
    }

    /** Un chequeo de la rúbrica de "crear" con su resultado. */
    public record ChequeoRevisado(String texto, boolean cumple) {
    }

    /** Lo que vuelve al responder: la calificación por reglas, la explicación y lo que cambió en el repaso y en el nivel. */
    public record Respuesta(BancoDojo.Reto reto, BancoDojo.Concepto concepto, TemaDojo tema, String respuesta, boolean acierto,
                            Optional<BancoDojo.Opcion> elegida, Optional<BancoDojo.Opcion> correcta, List<ChequeoRevisado> chequeos,
                            String siguienteRepaso, String progreso, Optional<String> dominaste, int hechosHoy, int limite, int racha) {
    }

    /** El progreso de cada tema (T48) y el calendario de repasos (T49). */
    public record Progreso(List<ResultadoBloom> temas, ResultadoRepeticion calendario, int racha) {
    }

    public static class NoEncontrado extends RuntimeException {
        public NoEncontrado() {
            super("No hay un reto con ese identificador.");
        }
    }

    public static class NoPermitido extends RuntimeException {
        public NoPermitido(String motivo) {
            super(motivo);
        }
    }

    static final int LARGO_MAXIMO = 1000;

    private final RepositorioDojo repositorio;
    private final BancoDojo banco;
    private final Reloj reloj;

    public DojoDeRazonamiento(RepositorioDojo repositorio, BancoDojo banco, Reloj reloj) {
        this.repositorio = repositorio;
        this.banco = banco;
        this.reloj = reloj;
    }

    public BancoDojo banco() {
        return banco;
    }

    /** Los intentos de la persona, del más viejo al más nuevo (para "Usar mis datos" en T48 y T49). */
    public List<IntentoDojo> intentos(UUID usuarioId) {
        return repositorio.intentos(usuarioId);
    }

    // ---------------------------------------------------------------------------------------------
    // Qué reto sale
    // ---------------------------------------------------------------------------------------------

    public Pantalla pantalla(UUID usuarioId, Optional<TemaDojo> filtro, Optional<NivelBloom> nivelElegido, Configuracion c) {
        LocalDate hoy = reloj.hoy();
        List<IntentoDojo> intentos = repositorio.intentos(usuarioId);
        int hechosHoy = hechosEl(intentos, hoy);
        int limite = c.repeticion().retosPorDia();
        int racha = racha(intentos, hoy);
        boolean manual = !c.bloom().avanceAutomatico();
        TemaDojo temaDelFiltro = filtro.orElse(TemaDojo.FALACIAS);
        if (hechosHoy >= limite) {
            return pantallaSinReto(filtro, hechosHoy, limite, racha, "Por hoy terminaste: hiciste " + hechosHoy + (hechosHoy == 1 ? " reto." : " retos."),
                    temaDelFiltro, intentos, nivelElegido, c);
        }
        Map<String, Sm2.Estado> estados = estados(intentos, c);
        List<BancoDojo.Concepto> candidatos = banco.conceptos().stream()
                .filter(x -> filtro.isEmpty() || TemaDojo.de(x.idTecnica()) == filtro.get()).toList();
        Optional<BancoDojo.Concepto> repaso = candidatos.stream().filter(x -> estados.containsKey(x.id()) && !estados.get(x.id()).proximo().isAfter(hoy))
                .min(Comparator.comparing((BancoDojo.Concepto x) -> estados.get(x.id()).proximo()).thenComparingInt(x -> banco.orden(x.id())));
        Optional<BancoDojo.Concepto> nuevo = candidatos.stream().filter(x -> !estados.containsKey(x.id())).findFirst();
        Optional<BancoDojo.Concepto> elegido = repaso.or(() -> nuevo);
        if (elegido.isEmpty()) {
            Optional<LocalDate> proximo = candidatos.stream().map(x -> estados.get(x.id()).proximo()).min(Comparator.naturalOrder());
            String mensaje = "Hoy no te toca ningún reto" + (filtro.isPresent() ? " de este tema" : "")
                    + proximo.map(p -> "; el próximo repaso es " + EjecutorRepeticion.cuando(p, hoy) + ".").orElse(".");
            return pantallaSinReto(filtro, hechosHoy, limite, racha, mensaje, temaDelFiltro, intentos, nivelElegido, c);
        }
        BancoDojo.Concepto concepto = elegido.get();
        TemaDojo tema = TemaDojo.de(concepto.idTecnica());
        EscaleraBloom.Progreso progreso = bloom(tema, intentos, c);
        NivelBloom nivel = nivelDe(progreso, nivelElegido, c);
        BancoDojo.Reto reto = elegirReto(concepto, nivel, intentos);
        return new Pantalla(filtro, hechosHoy, limite, racha, Optional.of(new RetoElegido(reto, concepto, tema, repaso.isPresent())), Optional.empty(),
                nivel, EjecutorBloom.describir(tema, c.bloom(), progreso).resumen(), manual, activos(c));
    }

    private Pantalla pantallaSinReto(Optional<TemaDojo> filtro, int hechos, int limite, int racha, String mensaje, TemaDojo tema,
                                     List<IntentoDojo> intentos, Optional<NivelBloom> nivelElegido, Configuracion c) {
        EscaleraBloom.Progreso p = bloom(tema, intentos, c);
        return new Pantalla(filtro, hechos, limite, racha, Optional.empty(), Optional.of(mensaje), nivelDe(p, nivelElegido, c),
                EjecutorBloom.describir(tema, c.bloom(), p).resumen(), !c.bloom().avanceAutomatico(), activos(c));
    }

    /** Con avance automático, el nivel actual del tema; con manual, el elegido si está activo, o el actual. */
    private static NivelBloom nivelDe(EscaleraBloom.Progreso p, Optional<NivelBloom> elegido, Configuracion c) {
        if (c.bloom().avanceAutomatico()) {
            return p.actual();
        }
        return elegido.filter(c.bloom().niveles()::contains).orElse(p.actual());
    }

    private static List<NivelBloom> activos(Configuracion c) {
        return java.util.Arrays.stream(NivelBloom.values()).filter(c.bloom().niveles()::contains).toList();
    }

    /**
     * Entre los retos del concepto en ese nivel, el que la persona respondió menos veces (y el primero del banco si empatan).
     * Si el concepto no tiene retos en ese nivel, el nivel más cercano por debajo y, si no hay, por encima.
     */
    BancoDojo.Reto elegirReto(BancoDojo.Concepto concepto, NivelBloom nivel, List<IntentoDojo> intentos) {
        List<NivelBloom> orden = new ArrayList<>();
        orden.add(nivel);
        for (int i = nivel.ordinal() - 1; i >= 0; i--) {
            orden.add(NivelBloom.values()[i]);
        }
        for (int i = nivel.ordinal() + 1; i < NivelBloom.values().length; i++) {
            orden.add(NivelBloom.values()[i]);
        }
        Map<String, Long> veces = new HashMap<>();
        intentos.forEach(i -> veces.merge(i.retoId(), 1L, Long::sum));
        for (NivelBloom n : orden) {
            Optional<BancoDojo.Reto> reto = banco.retos().stream().filter(r -> r.concepto().equals(concepto.id()) && r.nivel() == n)
                    .min(Comparator.comparingLong(r -> veces.getOrDefault(r.id(), 0L)));
            if (reto.isPresent()) {
                return reto.get();
            }
        }
        throw new IllegalStateException("El concepto " + concepto.id() + " no tiene retos en el banco");
    }

    // ---------------------------------------------------------------------------------------------
    // Responder
    // ---------------------------------------------------------------------------------------------

    public Respuesta responder(UUID usuarioId, UUID institucionId, String retoId, String respuesta, String clave, Configuracion c) {
        BancoDojo.Reto reto = banco.reto(retoId).orElseThrow(NoEncontrado::new);
        String texto = respuesta == null ? "" : respuesta.strip();
        if (texto.isEmpty() || !reto.esDeEscribir() && reto.opcion(texto).isEmpty()) {
            throw new NoPermitido(reto.esDeEscribir() ? "Escribe tu versión antes de responder." : "Elige una opción.");
        }
        if (texto.length() > LARGO_MAXIMO) {
            throw new NoPermitido("Tu versión pasa de " + LARGO_MAXIMO + " caracteres: acórtala.");
        }
        if (clave == null || clave.isBlank() || clave.length() > 64) {
            throw new NoPermitido("Vuelve a abrir el reto: el formulario no trae su clave.");
        }
        BancoDojo.Concepto concepto = banco.concepto(reto.concepto()).orElseThrow();
        TemaDojo tema = TemaDojo.de(concepto.idTecnica());
        LocalDate hoy = reloj.hoy();
        Instant ahora = reloj.ahora();
        List<IntentoDojo> intentos = new ArrayList<>(repositorio.intentos(usuarioId));
        Optional<IntentoDojo> previo = intentos.stream().filter(i -> i.clave().equals(clave)).findFirst();
        IntentoDojo intento;
        List<IntentoDojo> antes;
        if (previo.isPresent()) {
            // Doble clic: el intento ya está guardado; se responde lo mismo que la primera vez.
            intento = previo.get();
            antes = intentos.stream().filter(i -> !i.id().equals(intento.id())).toList();
        } else {
            antes = List.copyOf(intentos);
            intento = new IntentoDojo(Uuid7.en(ahora), clave, reto.id(), tema.tecnica(), concepto.id(), reto.nivel(), texto, reto.califica(texto), hoy,
                    ahora);
            intentos.add(intento);
            EscaleraBloom.Progreso tras = bloom(tema, intentos, c);
            List<IntentoDojo> delTema = intentos.stream().filter(i -> i.tecnica().equals(tema.tecnica())).toList();
            Competencia competencia = new Competencia(tema.tecnica(), tras.actual(), delTema.size(),
                    (int) delTema.stream().filter(IntentoDojo::acierto).count(), ahora);
            repositorio.guardar(usuarioId, institucionId, intento, competencia);
        }
        EscaleraBloom.Progreso progresoAntes = bloom(tema, antes, c);
        EscaleraBloom.Progreso progresoDespues = bloom(tema, intentos, c);
        Sm2.Estado estado = estados(intentos, c).get(concepto.id());
        List<ChequeoRevisado> chequeos = reto.rubrica().stream().map(ch -> new ChequeoRevisado(ch.texto(), ch.cumple(intento.respuesta()))).toList();
        return new Respuesta(reto, concepto, tema, intento.respuesta(), intento.acierto(),
                reto.esDeEscribir() ? Optional.empty() : reto.opcion(intento.respuesta()), reto.opcion(reto.correcta()), chequeos,
                "Siguiente repaso: " + EjecutorRepeticion.cuando(estado.proximo(), hoy) + ".",
                EjecutorBloom.describir(tema, c.bloom(), progresoDespues).resumen(), dominaste(reto.nivel(), progresoAntes, progresoDespues, c),
                hechosEl(intentos, hoy), c.repeticion().retosPorDia(), racha(intentos, hoy));
    }

    private static Optional<String> dominaste(NivelBloom nivel, EscaleraBloom.Progreso antes, EscaleraBloom.Progreso despues, Configuracion c) {
        boolean ahora = despues.de(nivel).estado() == EscaleraBloom.Estado.DOMINADO;
        if (!ahora || antes.de(nivel).estado() == EscaleraBloom.Estado.DOMINADO) {
            return Optional.empty();
        }
        if (despues.todosDominados()) {
            int n = c.bloom().niveles().size();
            return Optional.of(n == 1 ? "Dominaste el único nivel activo." : "Dominaste los " + n + " niveles activos.");
        }
        if (c.bloom().avanceAutomatico() && despues.actual() != nivel) {
            return Optional.of("Dominaste «" + nivel + "»: se abre «" + despues.actual() + "».");
        }
        return Optional.of("Dominaste «" + nivel + "».");
    }

    // ---------------------------------------------------------------------------------------------
    // Progreso, Inicio y reglas comunes
    // ---------------------------------------------------------------------------------------------

    public Progreso progreso(UUID usuarioId, Configuracion c) {
        List<IntentoDojo> intentos = repositorio.intentos(usuarioId);
        List<ResultadoBloom> temas = java.util.Arrays.stream(TemaDojo.values())
                .map(t -> EjecutorBloom.describir(t, c.bloom(), bloom(t, intentos, c))).toList();
        List<EjecutorRepeticion.Hecho> hechos = intentos.stream().map(i -> new EjecutorRepeticion.Hecho(TemaDojo.de(i.tecnica()),
                banco.concepto(i.concepto()).map(BancoDojo.Concepto::nombre).orElse(i.concepto()), i.dia(), i.acierto())).toList();
        return new Progreso(temas, EjecutorRepeticion.calcular(c.repeticion(), hechos, reloj.hoy()), racha(intentos, reloj.hoy()));
    }

    /**
     * Para Inicio: el menor entre lo que falta del límite del día y los repasos que tocan más los conceptos nuevos; 0 si la
     * persona nunca practicó (Inicio no la invita a un módulo que no abrió).
     */
    public int retosParaHoy(UUID usuarioId, Configuracion c) {
        LocalDate hoy = reloj.hoy();
        List<IntentoDojo> intentos = repositorio.intentos(usuarioId);
        if (intentos.isEmpty()) {
            return 0;
        }
        Map<String, Sm2.Estado> estados = estados(intentos, c);
        long tocan = estados.values().stream().filter(e -> !e.proximo().isAfter(hoy)).count();
        long nuevos = banco.conceptos().stream().filter(x -> !estados.containsKey(x.id())).count();
        int faltan = Math.max(0, c.repeticion().retosPorDia() - hechosEl(intentos, hoy));
        return (int) Math.min(faltan, tocan + nuevos);
    }

    /** Si la persona respondió algún reto alguna vez. */
    public boolean practico(UUID usuarioId) {
        return !repositorio.intentos(usuarioId).isEmpty();
    }

    public int racha(UUID usuarioId) {
        return racha(repositorio.intentos(usuarioId), reloj.hoy());
    }

    private static int hechosEl(List<IntentoDojo> intentos, LocalDate dia) {
        return (int) intentos.stream().filter(i -> i.dia().equals(dia)).count();
    }

    private static int racha(List<IntentoDojo> intentos, LocalDate hoy) {
        Set<LocalDate> dias = new HashSet<>();
        intentos.forEach(i -> dias.add(i.dia()));
        return EjecutorRepeticion.racha(dias, hoy);
    }

    /** SM-2 de cada concepto del banco sobre todos los intentos, por identificador de concepto. */
    private static Map<String, Sm2.Estado> estados(List<IntentoDojo> intentos, Configuracion c) {
        List<EjecutorRepeticion.Hecho> hechos = intentos.stream()
                .map(i -> new EjecutorRepeticion.Hecho(TemaDojo.de(i.tecnica()), i.concepto(), i.dia(), i.acierto())).toList();
        Map<String, Sm2.Estado> estados = new HashMap<>();
        for (EjecutorRepeticion.ConceptoEnCurso cc : EjecutorRepeticion.estados(c.repeticion().facilidad(), hechos)) {
            estados.put(cc.concepto(), cc.estado());
        }
        return estados;
    }

    /** El progreso de Bloom de un tema con los intentos de ese tema, en orden. */
    private static EscaleraBloom.Progreso bloom(TemaDojo tema, List<IntentoDojo> intentos, Configuracion c) {
        List<EscaleraBloom.Intento> delTema = intentos.stream().filter(i -> i.tecnica().equals(tema.tecnica()))
                .map(i -> new EscaleraBloom.Intento(i.nivel(), i.acierto())).toList();
        return EscaleraBloom.calcular(c.bloom().parametros(), delTema);
    }
}
