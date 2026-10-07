package pensamiento.web.seguridad;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.TransactionDefinition;

/**
 * Al abrir cada transacción ejecuta SET LOCAL app.usuario y app.institucion con el contexto de la
 * sesión. Las políticas RLS de PostgreSQL (FORCE ROW LEVEL SECURITY) leen esos valores; sin ellos, el
 * rol de aplicación no ve ninguna fila de usuario.
 */
public class GestorTransaccionesRls extends JdbcTransactionManager {

    public GestorTransaccionesRls(DataSource dataSource) {
        super(dataSource);
    }

    @Override
    protected void prepareTransactionalConnection(Connection con, TransactionDefinition definition) throws SQLException {
        super.prepareTransactionalConnection(con, definition);
        ContextoRls.Contexto contexto = ContextoRls.actual();
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT set_config('app.usuario', ?, true), set_config('app.institucion', ?, true)")) {
            ps.setString(1, contexto.usuarioId().map(Object::toString).orElse(""));
            ps.setString(2, contexto.institucionId().map(Object::toString).orElse(""));
            ps.execute();
        }
    }
}
