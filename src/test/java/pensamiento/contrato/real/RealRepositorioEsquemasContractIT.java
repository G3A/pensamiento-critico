package pensamiento.contrato.real;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.transaction.support.TransactionTemplate;

import pensamiento.catalogo.RepositorioEsquemasJdbc;
import pensamiento.contrato.RepositorioEsquemasContract;
import pensamiento.nucleo.Esquema;
import pensamiento.nucleo.puertos.RepositorioEsquemas;
import pensamiento.testutil.BaseDatosDePrueba;
import pensamiento.web.seguridad.GestorTransaccionesRls;

/** Contra la tabla esquema_walton del compose, sembrada por la migración repeatable; lee como rol de aplicación. */
@EnabledIfEnvironmentVariable(named = "CONTRACT_REAL", matches = "true")
class RealRepositorioEsquemasContractIT extends RepositorioEsquemasContract {

    private final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private final TransactionTemplate tx = new TransactionTemplate(new GestorTransaccionesRls(bd.dataSourceApp()));

    @Override
    protected RepositorioEsquemas crearSut() {
        RepositorioEsquemasJdbc real = new RepositorioEsquemasJdbc(bd.jdbcApp());
        return new RepositorioEsquemas() {
            @Override
            public java.util.List<Esquema> todos() {
                return tx.execute(e -> real.todos());
            }

            @Override
            public java.util.Optional<Esquema> porId(String id) {
                return tx.execute(e -> real.porId(id));
            }
        };
    }
}
