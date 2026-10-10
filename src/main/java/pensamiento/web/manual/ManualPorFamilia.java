package pensamiento.web.manual;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import gg.jte.Content;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import org.springframework.stereotype.Component;

import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.Ejemplo;
import pensamiento.nucleo.Familia;
import pensamiento.nucleo.RelacionTecnica;
import pensamiento.nucleo.Tecnica;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.nucleo.puertos.RepositorioTecnica;
import pensamiento.web.formulario.LenguajeCampos;
import pensamiento.web.patrones.Modo;
import pensamiento.web.patrones.Renderizadores;
import pensamiento.web.tecnicas.MotorTecnicas;
import pensamiento.web.tecnicas.VistaFormulario;

/**
 * El manual de usuario por familia (sección 9, "Manual de usuario por familia"): ocho capítulos generados desde las fichas
 * "Qué es" del catálogo, su configuración por defecto, sus relaciones y sus ejemplos, cada ejemplo pintado con el renderizador
 * real de su técnica en modo plantillas y con la fecha fija de los ejemplos (7 de octubre de 2026). El catálogo no cambia
 * mientras la app corre, así que cada capítulo se arma una vez. Sin red: el archivo descargable lleva el CSS adentro y los
 * diagramas como SVG.
 */
@Component
public class ManualPorFamilia {

    /** La fecha con que se calcularon a mano los ejemplos del catálogo. */
    static final Instant FECHA_DE_LOS_EJEMPLOS = Instant.parse("2026-10-07T15:00:00Z");
    static final ZoneId ZONA_DE_LOS_EJEMPLOS = ZoneId.of("America/Bogota");
    private static final UUID LECTOR = UUID.nameUUIDFromBytes("manual".getBytes(StandardCharsets.UTF_8));

    public record EjemploManual(int orden, String ambito, String titulo, String nota, String configuracion, Content resultado) {
    }

    public record TecnicaManual(Tecnica tecnica, String ia, String configuracion, List<String> relacionadas, List<EjemploManual> ejemplos) {
    }

    /** Un capítulo: la familia y sus técnicas en el orden del catálogo. */
    public record Capitulo(Familia familia, List<TecnicaManual> tecnicas) {
        public String archivo() {
            return "manual-" + familia.codigo() + ".html";
        }
    }

    /** El capítulo ya pintado: su HTML y el archivo autocontenido para descargar. */
    public record Pintado(Familia familia, String html, String descarga) {
    }

    private final RepositorioTecnica tecnicas;
    private final MotorTecnicas motor;
    private final Renderizadores renderizadores;
    private final TemplateEngine plantillas;
    private final Map<String, Pintado> cache = new ConcurrentHashMap<>();

    public ManualPorFamilia(RepositorioTecnica tecnicas, MotorTecnicas motor, Renderizadores renderizadores, TemplateEngine plantillas) {
        this.tecnicas = tecnicas;
        this.motor = motor;
        this.renderizadores = renderizadores;
        this.plantillas = plantillas;
    }

    public List<Familia> familias() {
        return tecnicas.familias();
    }

    public Optional<Pintado> pintado(String codigo) {
        Optional<Familia> familia = tecnicas.familias().stream().filter(f -> f.codigo().equals(codigo)).findFirst();
        return familia.map(f -> cache.computeIfAbsent(f.codigo(), c -> pintar(capitulo(f))));
    }

    Capitulo capitulo(Familia familia) {
        Reloj fijo = new Reloj() {
            @Override
            public Instant ahora() {
                return FECHA_DE_LOS_EJEMPLOS;
            }

            @Override
            public ZoneId zona() {
                return ZONA_DE_LOS_EJEMPLOS;
            }
        };
        Contexto ctx = new Contexto(LECTOR, LECTOR, Optional.empty(), fijo, Optional.empty(), UUID::randomUUID);
        List<TecnicaManual> lista = new ArrayList<>();
        for (Tecnica t : tecnicas.porFamilia(familia.codigo())) {
            String config = VistaFormulario.resumen(motor.camposConfig(t), LenguajeCampos.mapa(t.configDefault()));
            List<String> relacionadas = new ArrayList<>();
            for (RelacionTecnica r : tecnicas.relaciones(t.id())) {
                tecnicas.porId(r.otra(t.id())).ifPresent(o -> relacionadas.add(r.origen().equals(t.id())
                        ? "Esta técnica " + r.tipo().frase() + " " + o.cita() + "." : o.cita() + " " + r.tipo().frase() + " esta técnica."));
            }
            List<EjemploManual> ejemplos = new ArrayList<>();
            for (Ejemplo e : tecnicas.ejemplos(t.id())) {
                Map<String, Object> configEjemplo = LenguajeCampos.mapa(e.config());
                MotorTecnicas.Evaluacion ev = motor.evaluar(t, configEjemplo, LenguajeCampos.mapa(e.datos()), ctx);
                Content resultado = ev.resultado().map(r -> renderizadores.render(t, Optional.empty(), "manual-" + t.id() + "-" + e.orden(),
                        r.valor(), Modo.LECTURA)).orElse(salida -> salida.writeContent("<p class=\"detalle\">Este ejemplo no se pudo evaluar.</p>"));
                ejemplos.add(new EjemploManual(e.orden(), e.ambito().titulo(), e.titulo(), e.nota(),
                        VistaFormulario.resumen(motor.camposConfig(t), configEjemplo), resultado));
            }
            lista.add(new TecnicaManual(t, ia(t), config, relacionadas, ejemplos));
        }
        return new Capitulo(familia, lista);
    }

    private static String ia(Tecnica t) {
        return switch (t.requiereIa()) {
            case NO -> "Sin IA: todo sale de reglas.";
            case OPCIONAL -> "Ollama opcional: el modelo propone y nada cuenta hasta que lo adoptas" + (t.iaExperimental() ? " (experimental)." : ".");
            case SI -> "Necesita el modelo local.";
        };
    }

    private Pintado pintar(Capitulo c) {
        StringOutput cuerpo = new StringOutput();
        plantillas.render("fragmentos/manual/capitulo.jte", Map.of("c", c), cuerpo);
        StringOutput descarga = new StringOutput();
        plantillas.render("manual-descarga.jte", Map.of("c", c, "html", cuerpo.toString(), "css", css()), descarga);
        return new Pintado(c.familia(), cuerpo.toString(), descarga.toString());
    }

    private static String css() {
        try (InputStream in = ManualPorFamilia.class.getResourceAsStream("/static/app.css")) {
            if (in == null) {
                throw new IllegalStateException("No está /static/app.css");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
