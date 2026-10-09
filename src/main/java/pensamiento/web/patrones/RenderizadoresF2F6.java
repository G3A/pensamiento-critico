package pensamiento.web.patrones;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import org.springframework.stereotype.Component;

import pensamiento.tecnicas.f2.ResultadoEscalera;
import pensamiento.tecnicas.f2.ResultadoFalsacion;
import pensamiento.tecnicas.f2.ResultadoTerminos;
import pensamiento.tecnicas.f6.ResultadoDoubleCrux;
import pensamiento.tecnicas.f6.ResultadoEtico;
import pensamiento.tecnicas.f6.ResultadoSeisSombreros;
import pensamiento.tecnicas.f6.ResultadoTuring;

/**
 * Los renderizadores de las técnicas del hito 5 que reúsan patrones existentes: T10 en V02, T11 en V13a, T12 en V05, T35
 * en V03c, T37 en V10, T38 en V04 y T39 en V03b. Cada uno traduce el resultado al record de su patrón; ninguno dice
 * "correcto" ni "bien hecho" (corrección 13).
 */
public final class RenderizadoresF2F6 {

    private RenderizadoresF2F6() {
    }

    /** V02 para T10 · Escalera de inferencia: los peldaños en el orden del sentido, el débil con su motivo. */
    @Component
    public static class Escalera implements RenderizadorResultado<ResultadoEscalera> {
        private final TemplateEngine plantillas;

        public Escalera(TemplateEngine plantillas) {
            this.plantillas = plantillas;
        }

        @Override
        public String patron() {
            return V02.PATRON;
        }

        @Override
        public Class<ResultadoEscalera> tipo() {
            return ResultadoEscalera.class;
        }

        @Override
        public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoEscalera r, Modo modo) {
            List<V02.Item> items = r.peldanos().stream().map(p -> new V02.Item(p.id(), p.numero() + " · " + p.nombre(), p.estado(),
                    switch (p.estado()) {
                        case "peldaño débil" -> "chip chip-aviso";
                        case "pendiente" -> "chip chip-pendiente";
                        default -> "chip chip-ok";
                    }, "peldano-" + p.id(), p.texto(), p.comprobado() ? "lo comprobaste" : "", p.motivo())).toList();
            List<V02.Aviso> avisos = r.nota() == null ? List.of() : List.of(new V02.Aviso(r.nota(), "nota"));
            V02 v = new V02(idEjecucion, sufijo, modo, "Escalera de inferencia · " + ("bajar".equals(r.sentido()) ? "bajando desde la acción" : "subiendo desde los datos"),
                    "Revisas: " + r.revisa(), List.of(new V02.Medidor("peldanos", "Peldaños llenos " + r.llenos() + " de " + r.peldanos().size(), r.llenos(),
                    r.peldanos().size())), List.of(new V02.Seccion(null, items)), avisos, List.of(), r.resumen(),
                    "La regla mira palabras y casillas: un peldaño marcado débil es por donde empezar a revisar, no un error demostrado.");
            return salida -> plantillas.render("tag/v/v02.jte", Map.of("v", v), salida);
        }
    }

    /** V13a para T11 · Falsación: las condiciones con su estado y, debajo, lo que haría cambiar de opinión. */
    @Component
    public static class Falsacion implements RenderizadorResultado<ResultadoFalsacion> {
        private final TemplateEngine plantillas;

        public Falsacion(TemplateEngine plantillas) {
            this.plantillas = plantillas;
        }

        @Override
        public String patron() {
            return V13a.PATRON;
        }

        @Override
        public Class<ResultadoFalsacion> tipo() {
            return ResultadoFalsacion.class;
        }

        @Override
        public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoFalsacion r, Modo modo) {
            List<V13a.Item> items = r.condiciones().stream().map(c -> new V13a.Item(c.codigo(), c.texto(), c.estado(),
                    "verificable".equals(c.estado()) ? "chip chip-ok" : "no verificable".equals(c.estado()) ? "chip chip-aviso" : "chip chip-pendiente",
                    c.como() == null ? "Sin cómo verificarla." : "Cómo: " + c.como())).toList();
            List<String> acciones = new ArrayList<>(r.acciones());
            acciones.addAll(r.avisos());
            V13a v = new V13a(idEjecucion, sufijo, modo, "¿Qué tendría que ser cierto?", "Postura: " + r.postura(), items, "Qué sigue", acciones,
                    r.resumen(), "Una postura que no dice qué la haría falsa no se puede poner a prueba.");
            return salida -> plantillas.render("tag/v/v13a.jte", Map.of("v", v), salida);
        }
    }

    /** V05 para T12 · Definición de términos: el texto con los términos marcados y una nota por término. */
    @Component
    public static class Terminos implements RenderizadorResultado<ResultadoTerminos> {
        private final TemplateEngine plantillas;

        public Terminos(TemplateEngine plantillas) {
            this.plantillas = plantillas;
        }

        @Override
        public String patron() {
            return V05.PATRON;
        }

        @Override
        public Class<ResultadoTerminos> tipo() {
            return ResultadoTerminos.class;
        }

        @Override
        public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoTerminos r, Modo modo) {
            List<V05.Marca> marcas = r.terminos().stream().map(t -> new V05.Marca(t.codigo(), t.inicio(), t.fin(),
                    "definido".equals(t.estado()) ? "marca-texto" : "marca-texto propuesta")).toList();
            List<V05.Nota> notas = new ArrayList<>();
            for (ResultadoTerminos.Termino t : r.terminos()) {
                notas.add(nota(t.codigo(), t));
            }
            for (ResultadoTerminos.Termino t : r.sueltos()) {
                notas.add(nota("—", t));
            }
            V05 v = new V05(idEjecucion, sufijo, modo, "Términos y ambigüedad", r.texto(), marcas, "lista-terminos", notas, List.of(), r.resumen(),
                    String.join(" ", r.avisos()).isEmpty() ? "Dos personas pueden usar la misma palabra con sentidos distintos: definirla es ponerse de acuerdo antes de discutir."
                            : String.join(" ", r.avisos()));
            return salida -> plantillas.render("tag/v/v05.jte", Map.of("v", v), salida);
        }

        private static V05.Nota nota(String codigo, ResultadoTerminos.Termino t) {
            List<V05.Linea> lineas = new ArrayList<>();
            lineas.add(new V05.Linea("linea-definicion", "Definición:", t.definicion() == null ? "sin definir" : t.definicion()));
            if (t.ejemplo() != null) {
                lineas.add(new V05.Linea("linea-ejemplo", "Ejemplo:", t.ejemplo()));
            }
            if (t.contraejemplo() != null) {
                lineas.add(new V05.Linea("linea-contraejemplo", "Contraejemplo:", t.contraejemplo()));
            }
            lineas.add(new V05.Linea("linea-origen", null, "lista".equals(t.origen()) ? "Lo marcó la lista de términos difusos." : "Lo escribiste tú."));
            return new V05.Nota(codigo, "termino", t.estado(), "definido".equals(t.estado()) ? "chip chip-ok" : "chip chip-pendiente", t.termino(), lineas);
        }
    }

    /** V03c para T35 · Seis Sombreros: una celda por sombrero en su orden y la síntesis. */
    @Component
    public static class SeisSombreros implements RenderizadorResultado<ResultadoSeisSombreros> {
        private final TemplateEngine plantillas;

        public SeisSombreros(TemplateEngine plantillas) {
            this.plantillas = plantillas;
        }

        @Override
        public String patron() {
            return V03c.PATRON;
        }

        @Override
        public Class<ResultadoSeisSombreros> tipo() {
            return ResultadoSeisSombreros.class;
        }

        @Override
        public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoSeisSombreros r, Modo modo) {
            List<V03c.Celda> celdas = r.celdas().stream().map(c -> new V03c.Celda(c.id(), c.nombre(), c.nombre() + " · " + c.mira(),
                    c.lineas().stream().map(l -> new V03c.Texto(l, false)).toList(), c.estado())).toList();
            List<String> avisos = new ArrayList<>(r.avisos());
            V03c v = new V03c(idEjecucion, sufijo, modo, "Seis Sombreros", "Tema: " + r.tema(), celdas, String.join(" ", r.notas()), "Síntesis",
                    r.sintesis() == null ? List.of() : List.of(r.sintesis()), avisos, r.resumen(),
                    "Cada sombrero es una mirada a la vez: la síntesis es tuya, no un promedio de los sombreros.");
            return salida -> plantillas.render("tag/v/v03c.jte", Map.of("v", v), salida);
        }
    }

    /** V10 para T37 · Test de Turing ideológico: el puntaje de la rúbrica, sus tres partes y las señales. */
    @Component
    public static class Turing implements RenderizadorResultado<ResultadoTuring> {
        private final TemplateEngine plantillas;

        public Turing(TemplateEngine plantillas) {
            this.plantillas = plantillas;
        }

        @Override
        public String patron() {
            return V10.PATRON;
        }

        @Override
        public Class<ResultadoTuring> tipo() {
            return ResultadoTuring.class;
        }

        @Override
        public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoTuring r, Modo modo) {
            List<V10.Barra> barras = new ArrayList<>();
            barras.add(new V10.Barra("puntaje", "Rúbrica " + r.puntaje() + " de 100 (umbral " + r.umbral() + ")", 0, 100, r.puntaje()));
            barras.add(new V10.Barra("caricatura", "Caricatura " + r.caricatura() + " de " + r.pesoCaricatura(), 0, Math.max(1, r.pesoCaricatura()), r.caricatura()));
            if (r.omision() != null) {
                barras.add(new V10.Barra("omision", "Omisión " + r.omision() + " de " + r.pesoOmision(), 0, Math.max(1, r.pesoOmision()), r.omision()));
            }
            barras.add(new V10.Barra("tono", "Tono " + r.tono() + " de " + r.pesoTono(), 0, Math.max(1, r.pesoTono()), r.tono()));
            List<V10.Item> items = new ArrayList<>();
            items.add(r.caricaturas().isEmpty() ? new V10.Item("caricatura", "Caricatura", "sin marcas de caricatura", "chip", null, null)
                    : new V10.Item("caricatura", "Caricatura", "posible caricatura", "chip chip-aviso", "«" + String.join("», «", r.caricaturas()) + "»", null));
            items.add(r.burlas().isEmpty() ? new V10.Item("tono", "Tono", "sin tono burlón", "chip", null, null)
                    : new V10.Item("tono", "Tono", "tono burlón", "chip chip-aviso", "«" + String.join("», «", r.burlas()) + "»", null));
            for (int i = 0; i < r.argumentos().size(); i++) {
                ResultadoTuring.Argumento a = r.argumentos().get(i);
                items.add(new V10.Item("a" + (i + 1), a.texto(), a.cubierto() ? "cubierto" : "omitido", a.cubierto() ? "chip" : "chip chip-pendiente",
                        a.cubierto() ? "Clave encontrada: «" + a.clave() + "»" : null, null));
            }
            List<String> lineas = new ArrayList<>();
            if (r.omision() == null) {
                lineas.add("Sin argumentos de referencia: la omisión no se mide y el puntaje sale de caricatura y tono.");
            }
            if (r.steelman() != null) {
                lineas.add("Steelman de referencia: " + r.steelman());
            }
            V10 v = new V10(idEjecucion, sufijo, modo, "Test de Turing ideológico", "Imitas a: " + r.postura(), barras, "Señales de la rúbrica", items,
                    new V10.Veredicto("Rúbrica", r.aprueba() ? "aprueba la rúbrica" : "no aprueba la rúbrica", r.aprueba() ? "chip chip-pendiente" : "chip chip-aviso",
                            r.motivo()), lineas, List.of(), r.resumen(), "El puntaje sale de una rúbrica en código; quien sostiene esa postura es quien puede decir si la reconoce.");
            return salida -> plantillas.render("tag/v/v10.jte", Map.of("v", v), salida);
        }
    }

    /** V04 para T38 · Double crux: los dos lados con sus hechos y, debajo, los cruxes, las sugerencias y los avisos. */
    @Component
    public static class DoubleCrux implements RenderizadorResultado<ResultadoDoubleCrux> {
        private final TemplateEngine plantillas;

        public DoubleCrux(TemplateEngine plantillas) {
            this.plantillas = plantillas;
        }

        @Override
        public String patron() {
            return V04.PATRON;
        }

        @Override
        public Class<ResultadoDoubleCrux> tipo() {
            return ResultadoDoubleCrux.class;
        }

        @Override
        public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoDoubleCrux r, Modo modo) {
            List<String> preguntas = new ArrayList<>();
            for (ResultadoDoubleCrux.Crux c : r.cruxes()) {
                preguntas.add((c.comun() ? "Crux común: «" : "Crux: «") + c.hecho() + "»" + (c.comun() ? " · si es cierto, los dos cambian de opinión" : "")
                        + (c.comun() && c.verificable() ? " · a verificación" : "") + (c.como() == null ? "" : " · cómo: " + c.como()));
            }
            preguntas.addAll(r.sugerencias());
            preguntas.addAll(r.avisos());
            V04 v = new V04(idEjecucion, sufijo, modo, "Double crux", null, lado(r.a()), lado(r.b()),
                    new V04.Estado("Estado", r.estado(), r.comunes() > 0 ? "chip chip-pendiente" : "chip chip-aviso",
                            r.comunes() > 0 ? "El hecho común queda como pendiente de verificación." : "Busca el hecho que haría cambiar a los dos."),
                    "Cruxes, sugerencias y avisos", preguntas, List.of(), r.resumen(),
                    "Un double crux no dice quién tiene razón: dice qué comprobar para que los dos puedan cambiar de opinión.");
            return salida -> plantillas.render("tag/v/v04.jte", Map.of("v", v), salida);
        }

        private static V04.Columna lado(ResultadoDoubleCrux.Lado l) {
            List<V04.Item> items = new ArrayList<>();
            items.add(new V04.Item(l.postura(), "sostiene", "chip", null));
            l.depende().forEach(h -> items.add(new V04.Item(h, "depende de", "chip", null)));
            return new V04.Columna(l.quien(), items, "");
        }
    }

    /** V03b para T39 · Razonamiento ético: marcos por partes afectadas, el balance de cada marco y los conflictos. */
    @Component
    public static class Etico implements RenderizadorResultado<ResultadoEtico> {
        private final TemplateEngine plantillas;

        public Etico(TemplateEngine plantillas) {
            this.plantillas = plantillas;
        }

        @Override
        public String patron() {
            return V03b.PATRON;
        }

        @Override
        public Class<ResultadoEtico> tipo() {
            return ResultadoEtico.class;
        }

        @Override
        public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoEtico r, Modo modo) {
            List<V03b.Columna> columnas = r.partes().stream().map(p -> new V03b.Columna(p, null)).toList();
            List<V03b.Fila> filas = r.marcos().stream().map(m -> new V03b.Fila(null, m.nombre(), m.valoraciones(), null,
                    (m.balance() > 0 ? "+" : m.balance() < 0 ? "−" : "") + Math.abs(m.balance()), false)).toList();
            List<String> avisos = new ArrayList<>(r.avisos());
            for (ResultadoEtico.Objecion o : r.objeciones()) {
                avisos.add("Objeción desde " + o.nombre() + ": " + (o.texto() == null ? "falta." : o.texto()));
            }
            V03b v = new V03b(idEjecucion, sufijo, modo, "Razonamiento ético", "Decisión: " + r.decision(), "Marco", columnas, filas, null, "Balance", null,
                    null, r.conflictos(), null, avisos, r.resumen(),
                    "Los marcos no se suman entre sí: un conflicto señala dónde tienes que decidir qué pesa más, y eso es tuyo.",
                    "Valoración por parte afectada: 1 beneficia o cumple, 0 neutral, -1 perjudica o incumple", "Conflictos entre marcos");
            return salida -> plantillas.render("tag/v/v03b.jte", Map.of("v", v), salida);
        }
    }
}
