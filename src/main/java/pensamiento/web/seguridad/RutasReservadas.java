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
            "administracion", "argumentos", "biblioteca", "bloqueo", "cambios-de-opinion", "catalogo", "consejero", "diario",
            "dojo", "ejecuciones", "expedientes", "ia", "ir", "manual", "mis-datos", "operador", "taller", "tecnicas", "usuarios",
            "verificar",
            // Spring Security y Spring Boot
            "sesion", "salir", "error", "actuator",
            // carpetas de archivos estáticos
            "ext");

    private RutasReservadas() {
    }

    /** La ruta, si tiene forma de ruta de institución y no es una ruta de la app. */
    public static Optional<String> deInstitucion(String ruta) {
        return Institucion.rutaValida(ruta).filter(r -> !PRIMER_SEGMENTO.contains(r));
    }
}
