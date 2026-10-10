package pensamiento.web.consejero;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;

import pensamiento.catalogo.BancoSocratico;
import pensamiento.flujos.Consejero;
import pensamiento.nucleo.Expediente;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.SesionConsejero;
import pensamiento.nucleo.TurnoConsejero;
import pensamiento.tecnicas.f2.EstrategiaSocratica;
import pensamiento.tecnicas.f2.ResultadoEscalera;
import pensamiento.tecnicas.f2.ResultadoPreguntasSocraticas;
import pensamiento.tecnicas.f6.ResultadoEquipoRojo;
import pensamiento.tecnicas.f6.ResultadoSeisSombreros;
import pensamiento.tecnicas.f8.EjecutorReflexion;
import pensamiento.web.patrones.Modo;
import pensamiento.web.patrones.RenderizadorEquipoRojo;
import pensamiento.web.patrones.RenderizadorPreguntasSocraticas;
import pensamiento.web.patrones.V09;

/** Lo que pintan las plantillas del Consejero socrático (P15 y P16). */
public final class VistaConsejero {

    private VistaConsejero() {
    }

    /** P15 al entrar: el historial de sesiones y el formulario de una sesión nueva. */
    public record Inicio(List<Resumen> sesiones, List<Expediente> expedientes, boolean modeloDisponible, String error, Valores valores) {
    }

    /** Lo que la persona ya escribió en el formulario, para no perderlo si hay un error. */
    public record Valores(String modo, String postura, List<String> razones, List<String> apoyos, String confianza, String expediente, boolean usaModelo) {
        public static Valores vacios() {
            return new Valores("decision", "", List.of("", "", ""), List.of("no_se", "no_se", "no_se"), "", "", true);
        }

        public String razon(int i) {
            return i < razones.size() ? razones.get(i) : "";
        }

        public String apoyo(int i) {
            return i < apoyos.size() ? apoyos.get(i) : "no_se";
        }
    }

    public record Resumen(UUID id, String postura, String modo, String estado, String fecha) {
    }

    /**
     * Una burbuja del diálogo.
     *
     * @param rol        "persona", "consejero" o "equipo-rojo"
     * @param redactando si el modelo todavía la está redactando (se pinta con SSE)
     * @param cola       cuántos pedidos de otras personas esperan antes que este (solo mientras redacta)
     */
    public record Burbuja(UUID sesionId, UUID turnoId, int numero, String rol, String autor, String texto, List<String> chips, String detalle, boolean redactando,
                          int cola) {
        public Burbuja {
            chips = List.copyOf(chips);
        }

        public String id() {
            return "turno-" + turnoId;
        }
    }

    /** Una propuesta de elemento del segundo paso que la persona puede adoptar. */
    public record PropuestaElemento(UUID turnoId, String elemento, String texto, String porque, boolean adoptada) {
    }

    /** El panel lateral: según el modo, elementos y estándares, peldaños, sombreros o debilidades. */
    public record Panel(String titulo, List<V09.SeccionPanel> secciones, List<PropuestaElemento> propuestas) {
    }

    /**
     * La sesión.
     *
     * @param estado     "esperando" (toca responder), "redactando", "lista" (falta cerrar) o "cerrada"
     * @param resultado  la ejecución guardada al cerrar, pintada en modo lectura
     * @param debate     en el modo debate, las técnicas que siguen al equipo rojo
     */
    public record Sesion(SesionConsejero sesion, String titulo, List<Burbuja> burbujas, Panel panel, String estado, boolean modeloDisponible,
                         Optional<Content> resultado, List<String> comprobables, List<Expediente> expedientes, String error,
                         Optional<Expediente> expediente, Reflexion reflexion) {
        public boolean cerrada() {
            return "cerrada".equals(estado);
        }

        public boolean lista() {
            return "lista".equals(estado);
        }

        public boolean redactando() {
            return "redactando".equals(estado);
        }

        public boolean debate() {
            return sesion.modo() == SesionConsejero.Modo.DEBATE;
        }

        public String base() {
            return "/consejero/sesiones/" + sesion.id();
        }
    }

    /** Las preguntas de T47 · Reflexión estructurada que el cierre muestra además de "qué cambió", y si es obligatoria. */
    public record Reflexion(List<EjecutorReflexion.Pregunta> preguntas, boolean obligatoria) {
        /** "r_aprendi", el nombre del campo de cada pregunta en el formulario del cierre. */
        public static String campo(EjecutorReflexion.Pregunta p) {
            return "r_" + p.toString();
        }
    }

    /** El estado de la sesión según sus turnos. */
    public static String estado(SesionConsejero s, List<TurnoConsejero> turnos) {
        if (s.cerrada()) {
            return "cerrada";
        }
        if (Consejero.listaParaCerrar(turnos)) {
            return "lista";
        }
        if (!turnos.isEmpty() && turnos.getLast().delConsejero() && turnos.getLast().estado() == TurnoConsejero.Estado.REDACTANDO) {
            return "redactando";
        }
        return "esperando";
    }

    /** Las burbujas: la postura y cada turno, con quién habla, de dónde salió y por qué. */
    public static List<Burbuja> burbujas(SesionConsejero s, List<TurnoConsejero> turnos, java.util.function.Predicate<TurnoConsejero> enVivo, int cola) {
        List<Burbuja> burbujas = new ArrayList<>();
        burbujas.add(new Burbuja(s.id(), s.id(), 0, "persona", "tú · postura", s.postura(), List.of(), null, false, 0));
        for (TurnoConsejero t : turnos) {
            burbujas.add(burbuja(s, t, enVivo.test(t), cola));
        }
        return burbujas;
    }

    public static Burbuja burbuja(SesionConsejero s, TurnoConsejero t, boolean enVivo, int cola) {
        if (!t.delConsejero()) {
            return new Burbuja(s.id(), t.id(), t.numero(), "persona", "tú", t.texto(), List.of(), null, false, 0);
        }
        List<String> chips = new ArrayList<>();
        if (t.estado() == TurnoConsejero.Estado.REDACTANDO && enVivo) {
            chips.add("redactando");
        } else {
            chips.add(t.origen() == TurnoConsejero.Origen.MODELO ? "redactada por el modelo · validada" : "del banco");
        }
        boolean debate = t.paso().matches("A\\d+");
        return new Burbuja(s.id(), t.id(), t.numero(), debate ? "equipo-rojo" : "consejero", autor(s, t), t.texto(), chips, t.porquePropuesto().orElse(null),
                t.estado() == TurnoConsejero.Estado.REDACTANDO && enVivo, cola);
    }

    /** Quién habla en un turno del Consejero, a partir de su paso. */
    static String autor(SesionConsejero s, TurnoConsejero t) {
        BancoSocratico banco = BancoSocratico.delCatalogo();
        String paso = t.paso();
        if (TurnoConsejero.CIERRE.equals(paso)) {
            return "consejero · cierre";
        }
        if (TurnoConsejero.SINTESIS.equals(paso)) {
            return "consejero · síntesis";
        }
        if (paso.matches("A\\d+")) {
            return "equipo rojo · ataque " + paso.substring(1);
        }
        if (paso.contains("/")) {
            String tipo = paso.substring(0, paso.indexOf('/'));
            String elemento = paso.substring(paso.indexOf('/') + 1);
            return "consejero · " + banco.tipo(tipo).nombre() + " · " + banco.elemento(elemento).nombre().toLowerCase();
        }
        if (s.modo() == SesionConsejero.Modo.ESCALERA) {
            return "consejero · peldaño · " + banco.peldano(paso).nombre().toLowerCase();
        }
        if (s.modo() == SesionConsejero.Modo.SOMBREROS) {
            return "consejero · sombrero " + banco.sombrero(paso).nombre().toLowerCase();
        }
        return "consejero";
    }

    /** El panel del modo, armado con el resultado de la técnica sobre lo que va de la sesión. */
    public static Panel panel(SesionConsejero s, List<TurnoConsejero> turnos, Resultado<?> r) {
        List<PropuestaElemento> propuestas = turnos.stream().filter(t -> !t.delConsejero() && t.elementoPropuesto().isPresent())
                .map(t -> new PropuestaElemento(t.id(), t.elementoPropuesto().get(), t.texto(), t.porquePropuesto().orElse(""), t.propuestaAdoptada()))
                .toList();
        return switch (r.valor()) {
            case ResultadoPreguntasSocraticas p -> new Panel("Elementos del razonamiento (Paul-Elder)",
                    RenderizadorPreguntasSocraticas.vista(Optional.empty(), "panel", p, Modo.COMPLETO).panel(), propuestas);
            case ResultadoEquipoRojo e -> new Panel("Debilidades que identificó el código",
                    RenderizadorEquipoRojo.vista(Optional.empty(), "panel", e, Modo.COMPLETO).panel(), List.of());
            case ResultadoEscalera e -> new Panel("Escalera de inferencia", List.of(new V09.SeccionPanel(null, e.peldanos().stream()
                    .map(p -> new V09.ItemPanel(p.id(), p.numero() + " · " + p.nombre(), p.estado(),
                            "peldaño débil".equals(p.estado()) ? "chip chip-aviso" : "pendiente".equals(p.estado()) ? "chip chip-pendiente" : "chip chip-ok",
                            p.texto(), p.motivo())).toList())), List.of());
            case ResultadoSeisSombreros r6 -> new Panel("Seis Sombreros", List.of(new V09.SeccionPanel(null, r6.celdas().stream()
                    .map(c -> new V09.ItemPanel(c.id(), c.nombre() + " · " + c.mira(), c.estado() == null ? "con notas" : c.estado(),
                            c.estado() == null ? "chip chip-ok" : "chip chip-pendiente", c.lineas().isEmpty() ? null : String.join(" · ", c.lineas()), null))
                    .toList())), List.of());
            default -> new Panel("Panel", List.of(), List.of());
        };
    }

    /** "decisión", "ensayo"… */
    public static String modo(SesionConsejero.Modo m) {
        return m.nombre();
    }

    static EstrategiaSocratica.ModoSesion modoSesion(SesionConsejero.Modo m) {
        return m == SesionConsejero.Modo.ENSAYO ? EstrategiaSocratica.ModoSesion.ENSAYO : EstrategiaSocratica.ModoSesion.DECISION;
    }
}
