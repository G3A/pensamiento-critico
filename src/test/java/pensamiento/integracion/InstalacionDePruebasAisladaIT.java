package pensamiento.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import pensamiento.testutil.Entorno;

/**
 * Hito 8, entregable 1: la suite corre contra la instalación de pruebas (db-prueba y app-prueba) con secretos generados
 * para ella, y no puede leer los secretos de la instalación de uso (secrets/ está tapado por un tmpfs en el servicio tests).
 */
class InstalacionDePruebasAisladaIT {

    @Test
    void la_suite_apunta_a_la_base_y_la_app_de_pruebas() {
        assertThat(Entorno.urlBaseDeDatos()).contains("//db-prueba:");
        assertThat(Entorno.urlApp()).contains("//app-prueba:");
    }

    @Test
    void los_secretos_reales_del_repositorio_no_son_visibles() {
        assertThat(Path.of("secrets", "db_password.txt")).doesNotExist();
        assertThat(Path.of("secrets", "admin_pin.txt")).doesNotExist();
        assertThat(Path.of("secrets", "operador_clave.txt")).doesNotExist();
    }

    @Test
    void los_secretos_de_la_instalacion_de_pruebas_son_propios() {
        assertThat(Path.of("/run/secrets/db_password")).exists();
        assertThat(Files.isRegularFile(Path.of("/run/secrets/db_password"))).isTrue();
        assertThat(Entorno.claveDb()).hasSize(32);
    }
}
