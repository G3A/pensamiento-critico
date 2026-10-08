package pensamiento.web.tecnicas;

import java.util.Map;
import java.util.UUID;

import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.util.HtmlUtils;

import pensamiento.nucleo.Contexto;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Tecnica;
import pensamiento.nucleo.puertos.RepositorioTecnica;
import pensamiento.web.ObjetoNoEncontrado;
import pensamiento.web.Pagina;
import pensamiento.web.formulario.LectorFormulario;
import pensamiento.web.seguridad.UsuarioSesion;

/**
 * "Pedir propuestas al modelo" en la pestaña Usar (RF-14, corrección 11). Pedir abre un turno y devuelve la burbuja de
 * espera, que se conecta por SSE: indicador, tiempo transcurrido, texto provisional y Cancelar, con el botón
 * deshabilitado mientras el modelo trabaja. El evento final trae la versión validada y el formulario con las
 * propuestas, sin adoptar. Sin Ollama, o si cae, la técnica sigue en modo plantillas y lo dice. Nada se guarda aquí.
 */
@Controller
public class ControladorPropuestas {

    private final RepositorioTecnica tecnicas;
    private final MotorTecnicas motor;
    private final TurnosIa turnos;
    private final TemplateEngine plantillas;
    private final Pagina.Fabrica paginas;

    public ControladorPropuestas(RepositorioTecnica tecnicas, MotorTecnicas motor, TurnosIa turnos, TemplateEngine plantillas,
                                 Pagina.Fabrica paginas) {
        this.tecnicas = tecnicas;
        this.motor = motor;
        this.turnos = turnos;
        this.plantillas = plantillas;
        this.paginas = paginas;
    }

    @PostMapping("/tecnicas/{id}/propuestas")
    public String pedir(@PathVariable String id, @RequestParam MultiValueMap<String, String> parametros, Model modelo) {
        Tecnica t = tecnica(id);
        UsuarioSesion yo = paginas.usuarioActual();
        Map<String, Object> config = ControladorTecnicas.configDelFormulario(motor, t, yo, parametros);
        Map<String, Object> valores = LectorFormulario.leer(motor.camposEntrada(t), parametros, "");
        String clave = ControladorTecnicas.clave(parametros);
        String origen = ControladorTecnicas.origen(parametros);
        VistaFormulario.Modelo enFormulario = new VistaFormulario.Modelo(true, motor.modeloDisponible(), t.iaExperimental());
        VistaFormulario f = VistaFormulario.de(t, motor.camposConfig(t), motor.camposEntrada(t), config, valores, Map.of(), clave, origen)
                .conModelo(enFormulario);
        modelo.addAttribute("f", f);
        if (!motor.pideModelo(t, config)) {
            modelo.addAttribute("mensaje", "Tu configuración no pide propuestas al modelo: elige el modo con el modelo local.");
            return "fragmentos/ficha/aviso-modelo";
        }
        if (!motor.modeloDisponible()) {
            modelo.addAttribute("mensaje", "El modelo no está disponible: sigues en modo plantillas. Todo lo demás funciona igual.");
            return "fragmentos/ficha/aviso-modelo";
        }
        Contexto ctx = motor.contextoConIa(yo.id(), yo.institucionId());
        TurnosIa.Turno turno = turnos.abrir(yo.id(), t.id().valor(), provisional -> fin(t, config, valores, ctx, provisional, clave, origen));
        modelo.addAttribute("turno", turno.id().toString());
        modelo.addAttribute("tecnica", t.id().valor());
        return "fragmentos/ficha/espera";
    }

    /** El trabajo del turno: corre en un hilo virtual, fuera de toda transacción, y arma el HTML del evento final. */
    private TurnosIa.Fin fin(Tecnica t, Map<String, Object> config, Map<String, Object> valores, Contexto ctx,
                             java.util.function.Consumer<String> provisional, String clave, String origen) {
        MotorTecnicas.ConPropuestas r = motor.proponer(t, config, valores, ctx, provisional);
        VistaFormulario f = VistaFormulario.de(t, motor.camposConfig(t), motor.camposEntrada(t), config, r.valores(), Map.of(), clave, origen)
                .conModelo(new VistaFormulario.Modelo(true, motor.modeloDisponible(), t.iaExperimental()));
        String mensaje;
        if (r.caida().isPresent()) {
            mensaje = "<p class=\"aviso-ia\" role=\"status\"><span class=\"chip chip-aviso\">sin modelo</span> "
                    + HtmlUtils.htmlEscape(r.caida().get()) + "</p>";
        } else if (r.nuevas() == 0) {
            mensaje = "<p class=\"aviso-ia\" role=\"status\"><span class=\"chip\">sin propuestas</span> "
                    + "El modelo no propuso nada para lo que dejaste sin llenar. Sigues con lo que tenías.</p>";
        } else {
            mensaje = "<p class=\"aviso-ia\" role=\"status\"><span class=\"chip chip-pendiente\">" + r.nuevas()
                    + (r.nuevas() == 1 ? " propuesta" : " propuestas") + " del modelo</span> Revisa cada una en el formulario: "
                    + "no cuentan hasta que las adoptes.</p>";
        }
        StringOutput salida = new StringOutput();
        plantillas.render("fragmentos/ficha/formulario.jte", Map.of("f", f, "oob", true), salida);
        return new TurnosIa.Fin(mensaje + salida);
    }

    @GetMapping(value = "/ia/turnos/{turno}/flujo", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter flujo(@PathVariable UUID turno, HttpServletResponse respuesta) {
        UsuarioSesion yo = paginas.usuarioActual();
        TurnosIa.Turno t = turnos.de(yo.id(), turno).orElseThrow(() -> new ObjetoNoEncontrado("turno"));
        respuesta.setHeader("Cache-Control", "no-store");
        respuesta.setHeader("X-Accel-Buffering", "no");
        return turnos.conectar(t);
    }

    @PostMapping("/ia/turnos/{turno}/cancelar")
    public String cancelar(@PathVariable UUID turno, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        TurnosIa.Turno t = turnos.de(yo.id(), turno).orElseThrow(() -> new ObjetoNoEncontrado("turno"));
        turnos.cancelar(yo.id(), turno);
        Tecnica tecnica = tecnica(t.tecnica());
        modelo.addAttribute("idPedir", "pedir-" + tecnica.id().valor());
        modelo.addAttribute("tecnica", tecnica.id().valor());
        return "fragmentos/ficha/cancelado";
    }

    private Tecnica tecnica(String id) {
        try {
            Tecnica t = tecnicas.porId(IdTecnica.de(id)).orElseThrow(() -> new ObjetoNoEncontrado("técnica"));
            if (t.estaPendiente() || motor.conModelo(t.id()).isEmpty()) {
                throw new ObjetoNoEncontrado("técnica");
            }
            return t;
        } catch (IllegalArgumentException e) {
            throw new ObjetoNoEncontrado("técnica");
        }
    }
}
