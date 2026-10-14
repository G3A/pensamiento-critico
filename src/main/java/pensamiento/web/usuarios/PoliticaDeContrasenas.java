package pensamiento.web.usuarios;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import pensamiento.nucleo.Usuario;

/**
 * Contraseñas del hito 9 (ronda 4 de la sección 11): 10 caracteres o más para persona y estudiante, 12 o más para
 * administrador y docente (y el operador); frases permitidas, sin reglas de composición; rechazo de las contraseñas
 * comunes de la lista vendorizada (seguridad/contrasenas-comunes.txt, con su origen y licencia en la cabecera) y del
 * propio nombre.
 */
public final class PoliticaDeContrasenas {

    public static final int LARGO_MAXIMO = 128;

    private final Set<String> comunes;

    public PoliticaDeContrasenas(Set<String> comunes) {
        this.comunes = Set.copyOf(comunes);
    }

    /** Con la lista vendorizada del classpath. */
    public static PoliticaDeContrasenas conListaVendorizada() {
        Set<String> comunes = new HashSet<>();
        try (InputStream entrada = PoliticaDeContrasenas.class.getResourceAsStream("/seguridad/contrasenas-comunes.txt")) {
            if (entrada == null) {
                throw new IllegalStateException("Falta seguridad/contrasenas-comunes.txt en el classpath");
            }
            BufferedReader lector = new BufferedReader(new InputStreamReader(entrada, StandardCharsets.UTF_8));
            for (String linea = lector.readLine(); linea != null; linea = lector.readLine()) {
                if (!linea.isBlank() && !linea.startsWith("#")) {
                    comunes.add(linea.strip());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new PoliticaDeContrasenas(comunes);
    }

    /** Vacío si la contraseña sirve para ese rol; si no, el motivo para mostrarle a la persona. */
    public java.util.Optional<String> problema(String contrasena, int largoMinimo, String nombre) {
        if (contrasena == null || contrasena.codePointCount(0, contrasena.length()) < largoMinimo) {
            return java.util.Optional.of("La contraseña tiene " + largoMinimo + " caracteres o más. Una frase corta sirve, por ejemplo "
                    + "«mi perro come mango».");
        }
        if (contrasena.length() > LARGO_MAXIMO) {
            return java.util.Optional.of("La contraseña tiene como máximo " + LARGO_MAXIMO + " caracteres.");
        }
        String minusculas = contrasena.strip().toLowerCase(Locale.ROOT);
        if (comunes.contains(minusculas) || caracteresDistintos(minusculas) < 3) {
            return java.util.Optional.of("Esa contraseña es de las más usadas y se adivina fácil. Elige otra; una frase propia sirve.");
        }
        if (nombre != null && Usuario.claveDeNombre(contrasena).replace(" ", "").equals(Usuario.claveDeNombre(nombre).replace(" ", ""))) {
            return java.util.Optional.of("La contraseña no puede ser tu nombre.");
        }
        return java.util.Optional.empty();
    }

    public java.util.Optional<String> problema(String contrasena, Usuario.RolGlobal rol, String nombre) {
        return problema(contrasena, rol.largoMinimoDeContrasena(), nombre);
    }

    private static long caracteresDistintos(String texto) {
        return texto.codePoints().distinct().count();
    }
}
