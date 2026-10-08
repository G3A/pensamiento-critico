package pensamiento.web;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Adelantar el reloj de la aplicación, solo para pruebas de aceptación y demostraciones del Diario: así una revisión
 * programada para dentro de meses vence hoy. Solo el administrador entra (ConfiguracionSeguridad) y solo si la instalación
 * lo permite (APP_RELOJ_AJUSTABLE=true); si no, la ruta no existe (404).
 */
@RestController
public class ControladorReloj {

    private static final Logger LOG = LoggerFactory.getLogger(ControladorReloj.class);

    private final RelojDelSistema reloj;

    public ControladorReloj(RelojDelSistema reloj) {
        this.reloj = reloj;
    }

    /** Cuántos días va adelantado, en texto plano. */
    @GetMapping("/administracion/reloj")
    public ResponseEntity<String> estado() {
        if (!reloj.ajustable()) {
            throw new ObjetoNoEncontrado("reloj ajustable");
        }
        return ResponseEntity.ok(reloj.adelanto().toDays() + " días adelantado; hoy es " + reloj.hoy());
    }

    /** Fija el adelanto en días respecto del reloj real (0 lo devuelve a la hora real). */
    @PostMapping("/administracion/reloj")
    public ResponseEntity<String> adelantar(@RequestParam int dias) {
        if (!reloj.ajustable()) {
            throw new ObjetoNoEncontrado("reloj ajustable");
        }
        if (dias < 0 || dias > 3650) {
            return ResponseEntity.unprocessableEntity().body("El adelanto va de 0 a 3650 días.");
        }
        reloj.adelantar(Duration.ofDays(dias));
        LOG.warn("El administrador adelantó el reloj de la aplicación {} días; hoy es {}", dias, reloj.hoy());
        return ResponseEntity.ok(dias + " días adelantado; hoy es " + reloj.hoy());
    }
}
