package pensamiento.web.operador;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Primer arranque (hito 8): crea el operador del servicio con la clave de secrets/operador_clave.txt si todavía no hay
 * ninguno. Sin el secreto la app arranca igual, pero nadie puede crear instituciones; una clave de menos de 12 caracteres
 * detiene el arranque. Reemplaza a la cuenta "administrador" que se creaba con secrets/admin_pin.txt: las instituciones
 * nuevas reciben su primer administrador por invitación, y la institución existente conserva sus cuentas.
 */
@Component
public class InicializadorOperador implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(InicializadorOperador.class);

    private final ServicioOperador servicio;
    private final String clave;

    public InicializadorOperador(ServicioOperador servicio, @Value("${app.operador.clave:}") String clave) {
        this.servicio = servicio;
        this.clave = clave == null ? "" : clave.strip();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (clave.isEmpty()) {
            LOG.warn("Falta secrets/operador_clave.txt: no hay operador del servicio para crear instituciones");
            return;
        }
        if (servicio.crearOperadorSiNoHay(clave)) {
            LOG.info("Operador del servicio creado en el primer arranque");
        }
    }
}
