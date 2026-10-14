package pensamiento.web.seguridad;

import java.util.Optional;
import java.util.Set;

import pensamiento.nucleo.Institucion;

/**
 * Primeros segmentos de las rutas de la app: ninguna institución puede llamarse así, porque /<ruta> es su entrada.
 * RutasReservadasTest compara esta lista con los mapeos de todos los controladores.
 */
public final class RutasReservadas {

    public static final Set<String> PRIMER_SEGMENTO = Set.of(
            // controladores
            "administracion", "argumentos", "biblioteca", "bloqueo", "cambios-de-opinion", "catalogo", "consejero", "cuenta", "diario",
            "dojo", "ejecuciones", "expedientes", "grupos", "ia", "ir", "llave", "manual", "mis-datos", "mis-trabajos", "operador", "reto", "taller", "tecnicas", "usuarios",
            "verificar",
            // Spring Security y Spring Boot
            "sesion", "salir", "error", "actuator",
            // carpetas de archivos estáticos
            "ext");

    private RutasReservadas() {
    }

    /** Lo que cuelga de la entrada de cada institución y se ve sin sesión: /<ruta>/invitacion, /<ruta>/codigo, /<ruta>/sin-cuenta. */
    public static final Set<String> SUBRUTAS_PUBLICAS = Set.of("invitacion", "codigo", "sin-cuenta");

    /** La ruta de institución de /<ruta>/<subruta pública>, si la dirección tiene esa forma. */
    public static Optional<String> subrutaPublica(String uri) {
        if (uri == null || !uri.startsWith("/")) {
            return Optional.empty();
        }
        String[] partes = uri.substring(1).split("/", -1);
        return partes.length == 2 && SUBRUTAS_PUBLICAS.contains(partes[1]) ? deInstitucion(partes[0]) : Optional.empty();
    }

    /** La ruta, si tiene forma de ruta de institución y no es una ruta de la app. */
    public static Optional<String> deInstitucion(String ruta) {
        return Institucion.rutaValida(ruta).filter(r -> !PRIMER_SEGMENTO.contains(r));
    }
}
