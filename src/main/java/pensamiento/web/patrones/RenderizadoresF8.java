package pensamiento.web.patrones;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import org.springframework.stereotype.Component;

import pensamiento.tecnicas.comun.Textos;
import pensamiento.tecnicas.f8.EscaleraBloom;
import pensamiento.tecnicas.f8.ResultadoBloom;
import pensamiento.tecnicas.f8.ResultadoCambiosOpinion;
import pensamiento.tecnicas.f8.ResultadoDiarioRazonamiento;
import pensamiento.tecnicas.f8.ResultadoReflexion;
import pensamiento.tecnicas.f8.ResultadoRepeticion;

/**
 * Los renderizadores de F8 (hito 7): T45 y T46 en V11, T47 en V02, T48 en V13b y T49 en V13c. Cada uno traduce el resultado al
 * record de su patrón con un método estático que también usan el Dojo (P19) y el registro de cambios (P20). Ninguno califica
 * a la persona: dicen lo que hay y lo que falta.
 */
public final class RenderizadoresF8 {

    private RenderizadoresF8() {
    }

    // ---------------------------------------------------------------------------------------------
    // T45 · Diario de razonamiento
    // ---------------------------------------------------------------------------------------------

    /** V11 para T45 · Diario de razonamiento: las semanas como grupos de la línea de tiempo, con su resumen. */
    @Component
    public static class DiarioRazonamiento implements RenderizadorResultado<ResultadoDiarioRazonamiento> {
        private final TemplateEngine plantillas;

        public DiarioRazonamiento(TemplateEngine plantillas) {
            this.plantillas = plantillas;
        }

        @Override
        public String patron() {
            return V11.PATRON;
        }

        @Override
        public Class<ResultadoDiarioRazonamiento> tipo() {
            return ResultadoDiarioRazonamiento.class;
        }

        @Override
        public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoDiarioRazonamiento r, Modo modo) {
            V11 v = vista(idEjecucion, sufijo, r, modo);
            return salida -> plantillas.render("tag/v/v11.jte", Map.of("v", v), salida);
        }

        public static V11 vista(Optional<UUID> idEjecucion, String sufijo, ResultadoDiarioRazonamiento r, Modo modo) {
            List<V11.Grupo> grupos = r.semanas().stream().map(s -> new V11.Grupo(s.titulo(), s.resumen(), s.registros().stream()
                    .map(x -> new V11.Hito(x.fecha(), x.fechaTexto(), CitasDeTecnicas.cita(x.tecnica()) + " — " + x.resumen()
                            + (x.expediente() == null ? "" : " · expediente «" + x.expediente() + "»")
                            + (x.cambios() == 0 ? "" : " · " + Textos.contar(x.cambios(), "cambio de opinión", "cambios de opinión"))))
                    .toList())).toList();
            List<V11.Campo> campos = new ArrayList<>();
            campos.add(new V11.Campo("Por familia", r.porFamilia().isEmpty() ? "ninguna"
                    : String.join(" · ", r.porFamilia().stream().map(f -> f.familia() + " " + f.registros()).toList())));
            campos.add(new V11.Campo("Sin usar", r.sinUsar().isEmpty() ? "ninguna" : Textos.enumerar(r.sinUsar())));
            return new V11(idEjecucion, sufijo, modo, "Diario de razonamiento", campos, null, null, List.of(), grupos, null, List.of(), null,
                    List.of(), null, r.avisos(), r.resumen(), "Mira qué familias usas y cuáles no: el diario muestra cómo pensaste, no si pensaste bien.");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // T46 · Registro de cambios de opinión
    // ---------------------------------------------------------------------------------------------

    /** V11 para T46 · Registro de cambios de opinión: la línea de tiempo, el año en barras, la lectura y las posturas sin revisar. */
    @Component
    public static class CambiosOpinion implements RenderizadorResultado<ResultadoCambiosOpinion> {
        private final TemplateEngine plantillas;

        public CambiosOpinion(TemplateEngine plantillas) {
            this.plantillas = plantillas;
        }

        @Override
        public String patron() {
            return V11.PATRON;
        }

        @Override
        public Class<ResultadoCambiosOpinion> tipo() {
            return ResultadoCambiosOpinion.class;
        }

        @Override
        public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoCambiosOpinion r, Modo modo) {
            V11 v = vista(idEjecucion, sufijo, r, modo);
            return salida -> plantillas.render("tag/v/v11.jte", Map.of("v", v), salida);
        }

        /** "«Abrir en el centro» · 80% → 45% · evidencia nueva · T22 · Triangulación". */
        public static String cambio(ResultadoCambiosOpinion.Cambio c) {
            return "«" + c.postura() + "» · " + c.antes() + "% → " + c.despues() + "% · " + c.causa().nombre() + " · " + CitasDeTecnicas.cita(c.tecnica());
        }

        /** "«El pan de masa madre no se vende en este barrio» · hace 13 meses (desde el 14 de agosto de 2025)". */
        public static String sinRevisar(ResultadoCambiosOpinion.SinRevisar s) {
            return "«" + s.postura() + "» · hace " + Textos.contar(s.meses(), "mes", "meses") + " (desde el " + s.desdeTexto() + ")";
        }

        /** "Posturas sin revisar hace más de 12 meses". */
        public static String tituloSinRevisar(ResultadoCambiosOpinion r) {
            return "Posturas sin revisar hace más de " + Textos.contar(r.mesesSinRevisar(), "mes", "meses");
        }

        public static V11 vista(Optional<UUID> idEjecucion, String sufijo, ResultadoCambiosOpinion r, Modo modo) {
            return vista(idEjecucion, sufijo, r, modo, true);
        }

        /** Con conAparte en falso, sin la lista de posturas sin revisar: P20 la pinta con su acceso al debate. */
        public static V11 vista(Optional<UUID> idEjecucion, String sufijo, ResultadoCambiosOpinion r, Modo modo, boolean conAparte) {
            List<V11.Hito> linea = r.linea().stream().map(c -> new V11.Hito(c.fecha(), c.fechaTexto(), cambio(c))).toList();
            int maximo = Math.max(1, r.porCausa().stream().mapToInt(ResultadoCambiosOpinion.PorCausa::cambios).max().orElse(1));
            List<V11.Barra> barras = r.porCausa().stream().map(p -> new V11.Barra(p.causa().toString(), Textos.mayusculaInicial(p.causa().nombre()),
                    p.cambios(), maximo)).toList();
            List<V11.Campo> campos = List.of(new V11.Campo("Este año (" + r.anio() + ")",
                    Textos.contar(r.total(), "cambio de opinión", "cambios de opinión")), new V11.Campo("Lectura", r.lectura()));
            return new V11(idEjecucion, sufijo, modo, "Registro de cambios de opinión", campos, null, null, linea, List.of(),
                    "Causas de este año", barras, conAparte ? tituloSinRevisar(r) : null,
                    conAparte ? r.sinRevisar().stream().map(CambiosOpinion::sinRevisar).toList() : List.of(), null, r.avisos(), r.resumen(),
                    "Cambiar de opinión por una razón es buena señal; el registro no juzga, muestra qué te movió.");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // T47 · Reflexión estructurada
    // ---------------------------------------------------------------------------------------------

    /** V02 para T47 · Reflexión estructurada: cada pregunta activa, respondida o pendiente. */
    @Component
    public static class Reflexion implements RenderizadorResultado<ResultadoReflexion> {
        private final TemplateEngine plantillas;

        public Reflexion(TemplateEngine plantillas) {
            this.plantillas = plantillas;
        }

        @Override
        public String patron() {
            return V02.PATRON;
        }

        @Override
        public Class<ResultadoReflexion> tipo() {
            return ResultadoReflexion.class;
        }

        @Override
        public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoReflexion r, Modo modo) {
            List<V02.Item> items = r.items().stream().map(i -> new V02.Item(i.pregunta().toString(), i.texto(),
                    i.respondida() ? "respondida" : "pendiente", i.respondida() ? "chip chip-ok" : "chip chip-pendiente",
                    i.respondida() ? "respondida" : "pendiente", i.respuesta(), "", null)).toList();
            V02 v = new V02(idEjecucion, sufijo, modo, "Reflexión estructurada", "Sobre: " + r.sobre(),
                    List.of(new V02.Medidor("respondidas", "Respondidas " + r.respondidas() + " de " + r.items().size(), r.respondidas(), r.items().size())),
                    List.of(new V02.Seccion(null, items)), List.of(new V02.Aviso(r.nota(), "nota")), List.of(), r.resumen(),
                    "Lo que escribes no se califica: queda en tu diario para volver a leerlo.");
            return salida -> plantillas.render("tag/v/v02.jte", Map.of("v", v), salida);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // T48 · Taxonomía de Bloom
    // ---------------------------------------------------------------------------------------------

    /** V13b para T48 · Taxonomía de Bloom: una barra por nivel con su estado. */
    @Component
    public static class Bloom implements RenderizadorResultado<ResultadoBloom> {
        private final TemplateEngine plantillas;

        public Bloom(TemplateEngine plantillas) {
            this.plantillas = plantillas;
        }

        @Override
        public String patron() {
            return V13b.PATRON;
        }

        @Override
        public Class<ResultadoBloom> tipo() {
            return ResultadoBloom.class;
        }

        @Override
        public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoBloom r, Modo modo) {
            V13b v = vista(idEjecucion, sufijo, r, modo);
            return salida -> plantillas.render("tag/v/v13b.jte", Map.of("v", v), salida);
        }

        public static V13b vista(Optional<UUID> idEjecucion, String sufijo, ResultadoBloom r, Modo modo) {
            List<V13b.Nivel> niveles = r.niveles().stream().map(n -> new V13b.Nivel(n.nivel().toString(), Textos.mayusculaInicial(n.nivel().toString()),
                    n.nivel().quePide(), n.estado().texto(), clase(n.estado()), n.aciertos(), r.meta(), n.intentos())).toList();
            return new V13b(idEjecucion, sufijo, modo, "Niveles de práctica · " + r.tema().nombre(), "Competencia en " + r.tema().cita() + ".",
                    niveles, r.mensaje(), r.avisos(), r.resumen(), "El nivel sale de tus aciertos, contados por reglas; ningún modelo te califica.");
        }

        static String clase(EscaleraBloom.Estado e) {
            return switch (e) {
                case DOMINADO -> "chip chip-ok";
                case EN_CURSO -> "chip chip-pendiente";
                case BLOQUEADO, APAGADO -> "chip";
            };
        }
    }

    // ---------------------------------------------------------------------------------------------
    // T49 · Repetición espaciada
    // ---------------------------------------------------------------------------------------------

    /** V13c para T49 · Repetición espaciada: el calendario de 7 días y los conceptos con su próximo repaso. */
    @Component
    public static class Repeticion implements RenderizadorResultado<ResultadoRepeticion> {
        private final TemplateEngine plantillas;

        public Repeticion(TemplateEngine plantillas) {
            this.plantillas = plantillas;
        }

        @Override
        public String patron() {
            return V13c.PATRON;
        }

        @Override
        public Class<ResultadoRepeticion> tipo() {
            return ResultadoRepeticion.class;
        }

        @Override
        public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoRepeticion r, Modo modo) {
            V13c v = vista(idEjecucion, sufijo, r, modo);
            return salida -> plantillas.render("tag/v/v13c.jte", Map.of("v", v), salida);
        }

        public static V13c vista(Optional<UUID> idEjecucion, String sufijo, ResultadoRepeticion r, Modo modo) {
            List<V13c.Dia> dias = new ArrayList<>();
            for (int i = 0; i < r.calendario().size(); i++) {
                ResultadoRepeticion.Dia d = r.calendario().get(i);
                dias.add(new V13c.Dia(d.fecha(), d.nombre(), d.repasos(), i == 0));
            }
            List<V13c.Concepto> conceptos = r.conceptos().stream().map(c -> new V13c.Concepto(c.concepto(), c.tema().nombre(),
                    Textos.contar(c.repasos(), "repaso", "repasos") + " · facilidad " + c.facilidad() + " · intervalo "
                            + Textos.contar(c.intervalo(), "día", "días"),
                    c.proximoTexto(), c.proximoTexto().equals("hoy") || c.proximoTexto().startsWith("atrasado"))).toList();
            return new V13c(idEjecucion, sufijo, modo, "Calendario de repasos", dias, conceptos, r.avisos(), r.resumen(),
                    "SM-2 separa los repasos de lo que aciertas y acerca los de lo que fallas.");
        }
    }
}
