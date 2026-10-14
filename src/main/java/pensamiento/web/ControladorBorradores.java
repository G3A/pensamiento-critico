package pensamiento.web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;

import pensamiento.catalogo.MapeadorJson;
import pensamiento.nucleo.puertos.Reloj;
import pensamiento.nucleo.puertos.RepositorioBorradores;
import pensamiento.web.seguridad.UsuarioSesion;

/**
 * La copia en el servidor del borrador de un formulario (hito 10, entregable 8), que /borrador.js guarda cada 20 s y al perder
 * el foco, y borra cuando el formulario se envía bien. Solo de la persona de la sesión (y la cabecera X-Persona impide que la
 * página de otra persona escriba en su cuenta). Un borrador inválido o de más de 16 000 bytes responde 422.
 */
@Controller
public class ControladorBorradores {

    static final Pattern CLAVE = Pattern.compile("[a-z0-9:/_-]{1,120}");
    /** Bytes del borrador recibido; la base acepta hasta 20 000 de su forma jsonb, así nunca lanza por tamaño. */
    static final int TAMANO_MAXIMO = 16_000;

    private final RepositorioBorradores borradores;
    private final Reloj reloj;
    private final Pagina.Fabrica paginas;

    public ControladorBorradores(RepositorioBorradores borradores, Reloj reloj, Pagina.Fabrica paginas) {
        this.borradores = borradores;
        this.reloj = reloj;
        this.paginas = paginas;
    }

    @PostMapping("/borradores")
    @Transactional
    public ResponseEntity<Void> guardar(@RequestParam(defaultValue = "") String clave, @RequestParam(defaultValue = "") String campos) {
        UsuarioSesion yo = paginas.usuarioActual();
        if (!CLAVE.matcher(clave).matches() || campos.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > TAMANO_MAXIMO) {
            return ResponseEntity.status(422).build();
        }
        Map<String, String> leidos;
        try {
            leidos = new LinkedHashMap<>(MapeadorJson.mapper().readValue(campos, new TypeReference<Map<String, String>>() { }));
        } catch (JacksonException e) {
            return ResponseEntity.status(422).build();
        }
        borradores.guardar(yo.id(), yo.institucionId(), clave, leidos, reloj.ahora());
        return ResponseEntity.noContent().build();
    }

    /** El borrador de ese formulario, si hay: {"campo": "valor"}; si no, 204. */
    @GetMapping("/borradores")
    @Transactional(readOnly = true)
    @ResponseBody
    public ResponseEntity<Map<String, String>> leer(@RequestParam(defaultValue = "") String clave) {
        UsuarioSesion yo = paginas.usuarioActual();
        return borradores.leer(yo.id(), clave).map(b -> ResponseEntity.ok(b.campos())).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/borradores/borrar")
    @Transactional
    public ResponseEntity<Void> borrar(@RequestParam(defaultValue = "") String clave) {
        UsuarioSesion yo = paginas.usuarioActual();
        borradores.borrar(yo.id(), clave);
        return ResponseEntity.noContent().build();
    }
}
