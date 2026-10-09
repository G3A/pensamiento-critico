package pensamiento.testutil;

import java.util.UUID;
import java.util.function.Supplier;

import javax.sql.DataSource;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import pensamiento.web.seguridad.ContextoRls;
import pensamiento.web.seguridad.GestorTransaccionesRls;

/**
 * Acceso al PostgreSQL del compose para los *IT: una conexión como rol de aplicación (bajo RLS, con el
 * gestor de transacciones real) y otra como administrador (para sembrar y limpiar datos de prueba).
 */
public final class BaseDatosDePrueba {

    private final DataSource app;
    private final DataSource admin;
    private final TransactionTemplate transaccion;

    public BaseDatosDePrueba() {
        this.app = fuente(Entorno.usuarioApp());
        this.admin = fuente(Entorno.usuarioAdministradorDb());
        this.transaccion = new TransactionTemplate(new GestorTransaccionesRls(app));
    }

    private static DataSource fuente(String usuario) {
        DriverManagerDataSource ds = new DriverManagerDataSource(Entorno.urlBaseDeDatos(), usuario, Entorno.claveDb());
        ds.setDriverClassName("org.postgresql.Driver");
        return ds;
    }

    public JdbcClient jdbcApp() {
        return JdbcClient.create(app);
    }

    public JdbcClient jdbcAdmin() {
        return JdbcClient.create(admin);
    }

    public DataSource dataSourceApp() {
        return app;
    }

    /** Transacción como el usuario dado: fija app.usuario y app.institucion igual que la aplicación. */
    public <T> T comoUsuario(UUID usuarioId, UUID institucionId, Supplier<T> accion) {
        return ContextoRls.conUsuario(usuarioId, institucionId, () -> transaccion.execute(e -> accion.get()));
    }

    public <T> T comoInstitucion(UUID institucionId, Supplier<T> accion) {
        return ContextoRls.conInstitucion(institucionId, () -> transaccion.execute(e -> accion.get()));
    }

    /** Institución y usuario de prueba creados como administrador (sin RLS). */
    public UUID crearInstitucion(String nombre) {
        return jdbcAdmin().sql("INSERT INTO institucion (nombre) VALUES (:n) RETURNING id").param("n", nombre).query(UUID.class).single();
    }

    public UUID crearUsuario(UUID institucion, String nombre) {
        return jdbcAdmin().sql("INSERT INTO usuario (institucion_id, nombre, pin_hash, rol_global) VALUES (:i, :n, 'x', 'persona') RETURNING id")
                .param("i", institucion).param("n", nombre).query(UUID.class).single();
    }

    /** Expediente de prueba creado como administrador (sin RLS). */
    public UUID crearExpediente(UUID institucion, UUID usuario, String nombre) {
        return jdbcAdmin().sql("INSERT INTO expediente (usuario_id, institucion_id, nombre) VALUES (:u, :i, :n) RETURNING id")
                .param("u", usuario).param("i", institucion).param("n", nombre).query(UUID.class).single();
    }

    /** Borra en cascada todo lo de la institución de prueba. */
    public void borrarInstitucion(UUID institucion) {
        JdbcClient jdbc = jdbcAdmin();
        jdbc.sql("DELETE FROM auditoria WHERE institucion_id = :i").param("i", institucion).update();
        jdbc.sql("DELETE FROM pendiente WHERE institucion_id = :i").param("i", institucion).update();
        jdbc.sql("DELETE FROM prediccion WHERE institucion_id = :i").param("i", institucion).update();
        jdbc.sql("DELETE FROM argumento WHERE institucion_id = :i").param("i", institucion).update();
        jdbc.sql("DELETE FROM ejecucion WHERE institucion_id = :i").param("i", institucion).update();
        jdbc.sql("DELETE FROM expediente WHERE institucion_id = :i").param("i", institucion).update();
        jdbc.sql("DELETE FROM verificacion WHERE institucion_id = :i").param("i", institucion).update();
        jdbc.sql("DELETE FROM evidencia WHERE afirmacion_id IN (SELECT id FROM afirmacion WHERE institucion_id = :i)").param("i", institucion).update();
        jdbc.sql("DELETE FROM fuente WHERE institucion_id = :i").param("i", institucion).update();
        jdbc.sql("DELETE FROM documento WHERE institucion_id = :i").param("i", institucion).update();
        jdbc.sql("DELETE FROM afirmacion WHERE institucion_id = :i").param("i", institucion).update();
        jdbc.sql("DELETE FROM configuracion_usuario WHERE institucion_id = :i").param("i", institucion).update();
        jdbc.sql("DELETE FROM usuario WHERE institucion_id = :i").param("i", institucion).update();
        jdbc.sql("DELETE FROM institucion WHERE id = :i").param("i", institucion).update();
    }
}
