package pensamiento.web.usuarios;

import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import pensamiento.nucleo.puertos.RepositorioInstituciones;
import pensamiento.web.ControladorSesion;
import pensamiento.web.Pagina;
import pensamiento.web.seguridad.ContextoRls;
import pensamiento.web.seguridad.LimiteDeIntentos;
import pensamiento.web.seguridad.RutasReservadas;

/**
 * "Tengo un código" (/<ruta>/codigo, hito 9, RF-16 y RF-17): la persona escribe el código de alta o de restablecimiento que
 * le dio el administrador y elige su contraseña. Un código que no sirve (mal escrito, usado, vencido) responde 410; una
 * contraseña que no sirve, 422 sin gastar el código; demasiados códigos equivocados desde la misma IP, 429.
 */
@Controller
public class ControladorCodigo {

    /** Lo que pinta el formulario: la ruta, el error y el código escrito (para no volver a escribirlo tras un 422). */
    public record VistaCodigo(String ruta, Optional<String> error, String codigoEscrito) {
    }

    private static final String RUTA = "/{ruta:[a-z0-9][a-z0-9-]{1,38}[a-z0-9]}/codigo";

    private final ServicioUsuarios servicio;
    private final RepositorioInstituciones instituciones;
    private final LimiteDeIntentos limite;
    private final Pagina.Fabrica paginas;
    private final TransactionTemplate tx;

    public ControladorCodigo(ServicioUsuarios servicio, RepositorioInstituciones instituciones, LimiteDeIntentos limite,
                             Pagina.Fabrica paginas, PlatformTransactionManager gestor) {
        this.servicio = servicio;
        this.instituciones = instituciones;
        this.limite = limite;
        this.paginas = paginas;
        this.tx = new TransactionTemplate(gestor);
    }

    @GetMapping(RUTA)
    public String formulario(@PathVariable String ruta, HttpServletRequest request, HttpServletResponse response, Model modelo) {
        if (institucion(ruta).isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        return pintar(request, modelo, new VistaCodigo(ruta, Optional.empty(), ""));
    }

    @PostMapping(RUTA)
    public String canjear(@PathVariable String ruta, @RequestParam(defaultValue = "") String codigo,
                          @RequestParam(defaultValue = "") String contrasena, @RequestParam(defaultValue = "") String repetida,
                          HttpServletRequest request, HttpServletResponse response, Model modelo) {
        Optional<UUID> institucion = institucion(ruta);
        if (institucion.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return null;
        }
        UUID id = institucion.get();
        String ip = request.getRemoteAddr();
        Optional<java.time.Duration> espera = sinContexto(() -> limite.esperaDeCodigo(id, ip));
        if (espera.isPresent()) {
            response.setStatus(429);
            return pintar(request, modelo, new VistaCodigo(ruta, Optional.of("Hubo muchos códigos equivocados desde aquí. Espera "
                    + pensamiento.web.seguridad.RespuestasSesion.duracion(espera.get()) + " y vuelve a probar."), ""));
        }
        try {
            String nombre = servicio.canjear(id, codigo, contrasena, repetida).nombre();
            request.getSession().setAttribute(ControladorSesion.ATRIBUTO_LISTO, nombre);
            return "redirect:/" + ruta;
        } catch (ServicioUsuarios.CodigoNoValido e) {
            sinContexto(() -> {
                limite.falloDeCodigo(id, ip);
                return null;
            });
            response.setStatus(HttpServletResponse.SC_GONE);
            return pintar(request, modelo, new VistaCodigo(ruta, Optional.of(e.getMessage()), ""));
        } catch (ServicioUsuarios.DatosInvalidos e) {
            response.setStatus(422);
            return pintar(request, modelo, new VistaCodigo(ruta, Optional.of(e.getMessage()), codigo));
        }
    }

    private String pintar(HttpServletRequest request, Model modelo, VistaCodigo vista) {
        modelo.addAttribute("pagina", paginas.crear("Tengo un código", request));
        modelo.addAttribute("v", vista);
        return "codigo";
    }

    private Optional<UUID> institucion(String ruta) {
        return RutasReservadas.deInstitucion(ruta).flatMap(r -> sinContexto(() -> instituciones.activaPorRuta(r)));
    }

    private <T> T sinContexto(java.util.function.Supplier<T> accion) {
        return ContextoRls.con(Optional.empty(), Optional.empty(), () -> tx.execute(e -> accion.get()));
    }
}
