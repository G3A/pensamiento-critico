package pensamiento.web.seguridad;

import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import pensamiento.nucleo.puertos.TransaccionComoUsuario;

/**
 * Transacción como una persona fuera de una petición web (los trabajos largos de la biblioteca): fija el contexto RLS con
 * el usuario y la institución y después abre la transacción, que hace SET LOCAL de los dos (GestorTransaccionesRls).
 */
@Component
public class TransaccionComoUsuarioRls implements TransaccionComoUsuario {

    private final TransactionTemplate transaccion;

    public TransaccionComoUsuarioRls(PlatformTransactionManager gestor) {
        this.transaccion = new TransactionTemplate(gestor);
    }

    @Override
    public <T> T ejecutar(UUID usuarioId, UUID institucionId, Supplier<T> accion) {
        return ContextoRls.conUsuario(usuarioId, institucionId, () -> transaccion.execute(e -> accion.get()));
    }
}
