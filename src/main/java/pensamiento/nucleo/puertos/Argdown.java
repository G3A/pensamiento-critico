package pensamiento.nucleo.puertos;

import pensamiento.nucleo.argdown.DocumentoArgdown;
import pensamiento.nucleo.argdown.ErrorSintaxisArgdown;

/**
 * Puerto del subconjunto Argdown (RF-09). Leer y escribir son inversas: escribir lo leído devuelve el texto en
 * forma canónica y leer lo escrito devuelve el mismo árbol.
 */
public interface Argdown {

    /** @throws ErrorSintaxisArgdown con línea, columna y explicación si el texto no está en el subconjunto */
    DocumentoArgdown leer(String texto);

    /** Forma canónica: dos espacios por nivel, una línea en blanco entre conclusiones, sin espacios sobrantes. */
    String escribir(DocumentoArgdown documento);
}
