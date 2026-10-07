package pensamiento.web.usuarios;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import pensamiento.nucleo.puertos.RepositorioUsuarios;
import pensamiento.web.seguridad.ContextoRls;

/** Primer arranque: crea la institución única y la cuenta "administrador" con el PIN de secrets/admin_pin.txt. */
@Component
public class InicializadorAdministrador implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(InicializadorAdministrador.class);

    private final ServicioUsuarios servicio;
    private final RepositorioUsuarios usuarios;
    private final TransactionTemplate tx;
    private final String pin;

    public InicializadorAdministrador(ServicioUsuarios servicio, RepositorioUsuarios usuarios,
                                      PlatformTransactionManager gestor, @Value("${app.admin.pin:}") String pin) {
        this.servicio = servicio;
        this.usuarios = usuarios;
        this.tx = new TransactionTemplate(gestor);
        this.pin = pin == null ? "" : pin.trim();
    }

    @Override
    public void run(ApplicationArguments args) {
        UUID institucion = tx.execute(e -> usuarios.institucionUnica().orElseGet(() -> usuarios.crearInstitucion(ServicioUsuarios.NOMBRE_INSTITUCION)));
        long cuantos = ContextoRls.conInstitucion(institucion, () -> tx.execute(e -> usuarios.contar(institucion)));
        if (cuantos > 0) {
            return;
        }
        if (pin.isEmpty()) {
            throw new IllegalStateException("No hay cuentas y falta secrets/admin_pin.txt: sin PIN no se crea la cuenta administrador");
        }
        Optional<?> creado = ContextoRls.conInstitucion(institucion, () -> tx.execute(e -> servicio.inicializar(pin)));
        creado.ifPresent(u -> LOG.info("Cuenta \"{}\" creada en el primer arranque", ServicioUsuarios.NOMBRE_ADMINISTRADOR));
    }
}
