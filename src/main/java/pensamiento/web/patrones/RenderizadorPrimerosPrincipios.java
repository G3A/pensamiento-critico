package pensamiento.web.patrones;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import org.springframework.stereotype.Component;

import pensamiento.tecnicas.f7.ResultadoPrimerosPrincipios;

/**
 * V04 para T41 · Primeros principios: "Sé con certeza" contra "Asumo". Cada supuesto lleva "?" y su forma de verificarlo;
 * el pendiente de verificación es el enlace a la ficha de verificación del hito 6.
 */
@Component
public class RenderizadorPrimerosPrincipios implements RenderizadorResultado<ResultadoPrimerosPrincipios> {

    private final TemplateEngine plantillas;

    public RenderizadorPrimerosPrincipios(TemplateEngine plantillas) {
        this.plantillas = plantillas;
    }

    @Override
    public String patron() {
        return V04.PATRON;
    }

    @Override
    public Class<ResultadoPrimerosPrincipios> tipo() {
        return ResultadoPrimerosPrincipios.class;
    }

    @Override
    public Content render(Optional<UUID> idEjecucion, String sufijo, ResultadoPrimerosPrincipios r, Modo modo) {
        List<V04.Item> certezas = r.certezas().stream().map(c -> new V04.Item(c.texto(), "certeza", "chip chip-ok",
                c.como() == null ? null : "Cómo lo sé: " + c.como())).toList();
        List<V04.Item> supuestos = r.supuestos().stream().map(s -> new V04.Item(s.texto(), s.faltaComo() ? "? · falta cómo verificarlo" : "? · por verificar",
                s.faltaComo() ? "chip chip-aviso" : "chip chip-pendiente", s.como() == null ? null : "Cómo lo verificaría: " + s.como())).toList();
        V04 v = new V04(idEjecucion, sufijo, modo, "Primeros principios", "Problema: " + r.problema(),
                new V04.Columna("Sé con certeza", certezas, "Todavía no hay certezas."),
                new V04.Columna("Asumo", supuestos, "Todavía no hay supuestos."),
                null, "Lo que falta", r.avisos(), List.of(), r.resumen(),
                "Cada supuesto queda como pendiente de verificación: cuando lo verifiques, pasa a la columna de las certezas.");
        return salida -> plantillas.render("tag/v/v04.jte", Map.of("v", v), salida);
    }
}
