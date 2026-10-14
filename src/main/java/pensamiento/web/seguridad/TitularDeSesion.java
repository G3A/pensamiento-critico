package pensamiento.web.seguridad;

import org.springframework.security.core.userdetails.UserDetails;

import pensamiento.nucleo.Titular;

/**
 * Una sesión que puede tener llave de acceso (hito 9): la de una persona de una institución o la del operador del servicio.
 * La llave se registra o se confirma en {@link #baseDeLlave()}; al terminar, la sesión pasa a {@link #lista(boolean)}.
 */
public interface TitularDeSesion extends UserDetails {

    Titular titular();

    /** Lo que el autenticador muestra al registrar la llave (el nombre de la persona, u "operador"). */
    String nombreVisible();

    /** Dónde se registra o confirma la llave: /llave o /operador/llave. */
    String baseDeLlave();

    /** Adónde ir cuando ya entró del todo. */
    String inicio();

    /** ¿Le falta registrar su llave (true) o confirmarla (false)? Solo tiene sentido mientras no esté lista. */
    boolean debeRegistrarLlave();

    boolean lista();

    /** La misma sesión ya dentro: con la llave confirmada o registrada, o (sin llave) con un código de recuperación. */
    TitularDeSesion lista(boolean conLlave);
}
