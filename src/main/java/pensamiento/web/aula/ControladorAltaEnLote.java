package pensamiento.web.aula;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import pensamiento.nucleo.Grupo;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.web.Pagina;
import pensamiento.web.seguridad.UsuarioSesion;
import pensamiento.web.usuarios.ServicioUsuarios;
import pensamiento.web.usuarios.ServicioUsuarios.CodigoEmitido;

/**
 * Alta en lote y tarjetas (hito 10, entregable 3). El docente del grupo (o el administrador) pega una lista o sube un CSV de
 * hasta 500 filas; la vista previa marca las repetidas, las inválidas y las que ya existen antes de crear nada; al confirmar
 * se crean en una transacción y la respuesta es una hoja de tarjetas imprimibles (8 por hoja, A4 o carta) con el código de
 * alta de cada estudiante, que no se vuelve a mostrar. También genera códigos nuevos para el grupo, de a uno o en lote.
 */
@Controller
public class ControladorAltaEnLote {

    /** Un CSV de 500 filas con nombres de 60 caracteres cabe de sobra en 100 KB. */
    static final long TAMANO_MAXIMO_CSV = 100_000;

    /** Lo que pinta la página del alta en lote: el formulario y, si ya hay, la vista previa. */
    public record VistaLote(Grupo grupo, String texto, Optional<ServicioAula.VistaPrevia> previa, Optional<String> error) {
    }

    /** La hoja de tarjetas: de qué grupo, para qué ruta de entrada, y qué filas no se crearon. */
    public record HojaDeTarjetas(Grupo grupo, String ruta, List<CodigoEmitido> codigos, List<ListaDeEstudiantes.Fila> sinCrear, java.time.ZoneId zona,
                                 boolean alta) {
    }

    private final ServicioAula servicio;
    private final Pagina.Fabrica paginas;
    private final java.time.ZoneId zona;

    public ControladorAltaEnLote(ServicioAula servicio, Pagina.Fabrica paginas, Reloj reloj) {
        this.servicio = servicio;
        this.paginas = paginas;
        this.zona = reloj.zona();
    }

    @GetMapping("/grupos/{id}/lote")
    public String formulario(@PathVariable UUID id, HttpServletRequest request, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        Grupo g = servicio.grupoQueAdministro(yo.actor(), id);
        return pintar(request, modelo, new VistaLote(g, "", Optional.empty(), Optional.empty()));
    }

    @PostMapping("/grupos/{id}/lote/vista-previa")
    public String vistaPrevia(@PathVariable UUID id, @RequestParam(defaultValue = "") String texto,
                              @RequestParam(required = false) MultipartFile archivo, HttpServletRequest request, HttpServletResponse response,
                              Model modelo) throws IOException {
        UsuarioSesion yo = paginas.usuarioActual();
        Grupo g = servicio.grupoQueAdministro(yo.actor(), id);
        try {
            String lista = texto(texto, archivo);
            return pintar(request, modelo, new VistaLote(g, lista, Optional.of(servicio.vistaPrevia(yo.actor(), id, lista)), Optional.empty()));
        } catch (ServicioUsuarios.DatosInvalidos e) {
            response.setStatus(422);
            return pintar(request, modelo, new VistaLote(g, texto, Optional.empty(), Optional.of(e.getMessage())));
        }
    }

    @PostMapping("/grupos/{id}/lote")
    public String crear(@PathVariable UUID id, @RequestParam(defaultValue = "") String texto, HttpServletRequest request,
                        HttpServletResponse response, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        Grupo g = servicio.grupoQueAdministro(yo.actor(), id);
        try {
            ServicioAula.AltaEnLote alta = servicio.altaEnLote(yo.actor(), id, texto);
            response.setStatus(HttpServletResponse.SC_CREATED);
            return hoja(request, modelo, new HojaDeTarjetas(alta.grupo(), yo.ruta(), alta.creadas(), alta.sinCrear(), zona, true));
        } catch (ServicioUsuarios.DatosInvalidos e) {
            response.setStatus(422);
            return pintar(request, modelo, new VistaLote(g, texto, Optional.empty(), Optional.of(e.getMessage())));
        }
    }

    /** Códigos nuevos para los estudiantes marcados (o uno solo): cierran sus sesiones abiertas. */
    @PostMapping("/grupos/{id}/codigos")
    public String codigos(@PathVariable UUID id, @RequestParam(name = "estudiante", required = false) List<UUID> estudiantes,
                          HttpServletRequest request, Model modelo) {
        UsuarioSesion yo = paginas.usuarioActual();
        try {
            List<CodigoEmitido> emitidos = servicio.codigosNuevos(yo.actor(), id, estudiantes == null ? List.of() : estudiantes);
            Grupo g = servicio.grupo(yo.actor(), id);
            return hoja(request, modelo, new HojaDeTarjetas(g, yo.ruta(), emitidos, List.of(), zona, false));
        } catch (ServicioUsuarios.DatosInvalidos e) {
            return "redirect:/grupos/" + id + "?aviso=elige";
        }
    }

    private String pintar(HttpServletRequest request, Model modelo, VistaLote v) {
        modelo.addAttribute("pagina", paginas.crear("Alta en lote · " + v.grupo().nombre(), request));
        modelo.addAttribute("v", v);
        return "grupo-lote";
    }

    private String hoja(HttpServletRequest request, Model modelo, HojaDeTarjetas h) {
        modelo.addAttribute("pagina", paginas.crear("Tarjetas · " + h.grupo().nombre(), request));
        modelo.addAttribute("h", h);
        return "tarjetas";
    }

    /** El texto pegado o, si subieron un archivo, su contenido (UTF-8, hasta 100 KB). */
    private static String texto(String pegado, MultipartFile archivo) throws IOException {
        if (archivo == null || archivo.isEmpty()) {
            return pegado;
        }
        if (archivo.getSize() > TAMANO_MAXIMO_CSV) {
            throw new ServicioUsuarios.DatosInvalidos("El archivo pasa de 100 KB: sube un CSV con una persona por fila, hasta 500 filas.");
        }
        return new String(archivo.getBytes(), StandardCharsets.UTF_8);
    }
}
