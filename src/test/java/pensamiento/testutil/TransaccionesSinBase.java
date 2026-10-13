package pensamiento.testutil;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/** Gestor de transacciones que no hace nada: para collaboration tests de clases que abren transacciones sobre Fakes. */
public final class TransaccionesSinBase extends AbstractPlatformTransactionManager {

    @Override
    protected Object doGetTransaction() {
        return new Object();
    }

    @Override
    protected void doBegin(Object transaccion, TransactionDefinition definicion) {
        // sin base: nada que abrir
    }

    @Override
    protected void doCommit(DefaultTransactionStatus estado) {
        // sin base: nada que confirmar
    }

    @Override
    protected void doRollback(DefaultTransactionStatus estado) {
        // sin base: nada que deshacer
    }
}
