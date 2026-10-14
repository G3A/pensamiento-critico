package pensamiento.web.usuarios;

import java.util.List;
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

import pensamiento.nucleo.Usuario;
import pensamiento.nucleo.puertos.RepositorioInstituciones;
import pensamiento.nucleo.puertos.RepositorioUsuarios;
import pensamiento.web.Pagina;
import pensamiento.web.seguridad.UsuarioSesion;

/**
 * /usuarios (P21, hito 9): buscar con páginas, crear una cuenta con su código de alta, generar un código para restablecer,
 * desactivar (con confirmación) y reactivar. Solo el administrador. Un dato inválido o un nombre repetido responde 422 con
 * la página y el mensaje; el código se muestra una sola vez en una tarjeta imprimible. La transacción vive en el servicio.
 */
@Controller
public class ControladorUsuarios {

    /** Una fila de la lista: la cuenta y si tiene un código pendiente de canjear. */
    public record FilaCuenta(Usuario cuenta, Optional<RepositorioUsuarios.TipoCodigo> codigoPendiente) {
    }

    /** Lo que pinta /usuarios. */
    public record VistaUsuarios(List<FilaCuenta> filas, long total, int pagina, int paginas, String busqueda, Optional<String> error,
                                Optional<String> aviso, String nombreEscrito, UUID yo, List<String> enEspera, String quienDaCuentas) {

        public String enlacePagina(int numero) {
            return "/usuarios?pagina=" + numero + (busqueda.isEmpty() ? "" : "&q=" + java.net.URLEncoder.encode(busqueda, java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private final ServicioUsuarios servicio;
    private final Pagina.Fabrica paginas;
    private final java.time.ZoneId zona;
    private final RepositorioInstituciones instituciones;
    private final TransactionTemplate tx;

    public ControladorUsuarios(ServicioUsuarios servicio, Pagina.Fabrica paginas, pensamiento.nucleo.puertos.Reloj reloj,
                               RepositorioInstituciones instituciones, PlatformTransactionManager gestor) {
        this.servicio = servicio;
        this.paginas = paginas;
        this.zona = reloj.zona();
        this.instituciones = instituciones;
        this.tx = new TransactionTemplate(gestor);
    }

    /** El texto de "¿No tienes cuenta?" de esta institución (RF-18): a quién pedirla. */
    @PostMapping("/usuarios/quien-da-cuentas")
    public String quienDaCuentas(@RequestParam(defaultValue = "") String texto, HttpServletRequest request, HttpServletResponse response,
                                 Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        String limpio = texto.strip();
        if (limpio.isEmpty() || limpio.length() > 120) {
            response.setStatus(422);
            return pintar(request, modelo, "", 0, Optional.of("Escribe a quién pedir la cuenta, con 120 caracteres como máximo."), Optional.empty(), "");
        }
        tx.execute(e -> instituciones.cambiarQuienDaCuentas(yo.institucionId(), limpio));
        return "redirect:/usuarios?aviso=texto";
    }

    @GetMapping("/usuarios")
    public String lista(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int pagina,
                        @RequestParam(required = false) String aviso, HttpServletRequest request, Model modelo) {
        return pintar(request, modelo, q, pagina, Optional.empty(), Optional.ofNullable(aviso).map(ControladorUsuarios::textoDeAviso), "");
    }

    @PostMapping("/usuarios")
    public String crear(@RequestParam(defaultValue = "") String nombre, @RequestParam(defaultValue = "persona") String rol,
                        HttpServletRequest request, HttpServletResponse response, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        try {
            ServicioUsuarios.CodigoEmitido emitido = servicio.crear(yo.id(), yo.institucionId(), nombre, rolPedido(rol));
            response.setStatus(HttpServletResponse.SC_CREATED);
            return tarjeta(request, modelo, emitido);
        } catch (ServicioUsuarios.DatosInvalidos e) {
            response.setStatus(422);
            return pintar(request, modelo, "", 0, Optional.of(e.getMessage()), Optional.empty(), nombre);
        }
    }

    @PostMapping("/usuarios/{id}/restablecer")
    public String restablecer(@PathVariable UUID id, HttpServletRequest request, HttpServletResponse response, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        try {
            return tarjeta(request, modelo, servicio.restablecer(yo.id(), yo.institucionId(), id));
        } catch (ServicioUsuarios.DatosInvalidos e) {
            response.setStatus(422);
            return pintar(request, modelo, "", 0, Optional.of(e.getMessage()), Optional.empty(), "");
        }
    }

    @PostMapping("/usuarios/{id}/codigo-de-alta")
    public String codigoDeAlta(@PathVariable UUID id, HttpServletRequest request, HttpServletResponse response, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        try {
            return tarjeta(request, modelo, servicio.codigoDeAltaNuevo(yo.id(), yo.institucionId(), id));
        } catch (ServicioUsuarios.DatosInvalidos e) {
            response.setStatus(422);
            return pintar(request, modelo, "", 0, Optional.of(e.getMessage()), Optional.empty(), "");
        }
    }

    @PostMapping("/usuarios/{id}/desactivar")
    public String desactivar(@PathVariable UUID id, HttpServletRequest request, HttpServletResponse response, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        try {
            servicio.desactivar(yo.id(), yo.institucionId(), id);
            return "redirect:/usuarios?aviso=desactivada";
        } catch (ServicioUsuarios.DatosInvalidos e) {
            response.setStatus(422);
            return pintar(request, modelo, "", 0, Optional.of(e.getMessage()), Optional.empty(), "");
        }
    }

    @PostMapping("/usuarios/{id}/reactivar")
    public String reactivar(@PathVariable UUID id, HttpServletRequest request, HttpServletResponse response, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        try {
            servicio.reactivar(yo.id(), yo.institucionId(), id);
            return "redirect:/usuarios?aviso=reactivada";
        } catch (ServicioUsuarios.DatosInvalidos e) {
            response.setStatus(422);
            return pintar(request, modelo, "", 0, Optional.of(e.getMessage()), Optional.empty(), "");
        }
    }

    @PostMapping("/usuarios/espera")
    public String levantarEspera(@RequestParam(defaultValue = "") String nombre) {
        UsuarioSesion yo = paginas.usuarioActual();
        servicio.levantarEspera(yo.id(), yo.institucionId(), nombre);
        return "redirect:/usuarios?aviso=espera";
    }

    private String pintar(HttpServletRequest request, Model modelo, String busqueda, int pagina, Optional<String> error,
                          Optional<String> aviso, String nombreEscrito) {
        UsuarioSesion yo = paginas.usuarioActual();
        String texto = busqueda == null ? "" : busqueda.strip();
        RepositorioUsuarios.PaginaDeCuentas encontradas = servicio.buscar(yo.institucionId(), texto, pagina);
        int total = (int) Math.max(1, (encontradas.total() + ServicioUsuarios.TAMANO_PAGINA - 1) / ServicioUsuarios.TAMANO_PAGINA);
        List<FilaCuenta> filas = encontradas.cuentas().stream()
                .map(c -> new FilaCuenta(c, c.tieneClave() ? Optional.empty() : servicio.codigoPendiente(yo.institucionId(), c.id()))).toList();
        modelo.addAttribute("pagina", paginas.crear("Usuarios", request));
        modelo.addAttribute("v", new VistaUsuarios(filas, encontradas.total(), Math.max(pagina, 0), total, texto, error, aviso, nombreEscrito, yo.id(),
                servicio.enEspera(yo.institucionId()), tx.execute(e -> instituciones.quienDaCuentas(yo.institucionId()))
                        .orElse(RepositorioInstituciones.QUIEN_DA_CUENTAS)));
        return "usuarios";
    }

    private String tarjeta(HttpServletRequest request, Model modelo, ServicioUsuarios.CodigoEmitido emitido) {
        modelo.addAttribute("pagina", paginas.crear("Código para " + emitido.cuenta().nombre(), request));
        modelo.addAttribute("emitido", emitido);
        modelo.addAttribute("ruta", paginas.usuarioActual().ruta());
        modelo.addAttribute("zona", zona);
        return "usuarios-tarjeta";
    }

    private static Usuario.RolGlobal rolPedido(String rol) {
        return switch (rol) {
            case "administrador" -> Usuario.RolGlobal.ADMINISTRADOR;
            case "docente" -> Usuario.RolGlobal.DOCENTE;
            case "estudiante" -> Usuario.RolGlobal.ESTUDIANTE;
            default -> Usuario.RolGlobal.PERSONA;
        };
    }

    private static String textoDeAviso(String motivo) {
        return switch (motivo) {
            case "desactivada" -> "La cuenta quedó desactivada y sus sesiones se cerraron.";
            case "reactivada" -> "La cuenta quedó activa otra vez.";
            case "espera" -> "La persona ya puede volver a intentar entrar.";
            case "texto" -> "Listo: «¿No tienes cuenta?» ya dice a quién pedirla.";
            default -> "";
        };
    }
}
