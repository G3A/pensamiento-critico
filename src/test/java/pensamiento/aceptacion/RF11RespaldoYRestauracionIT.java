package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import pensamiento.testutil.Entorno;

/**
 * RF-11: el respaldo (pg_dump -Fc, el mismo comando del servicio backup) se restaura en una base vacía
 * con pg_restore y deja 49 técnicas y los datos del usuario. Corre dentro del servicio tests del compose.
 */
class RF11RespaldoYRestauracionIT {

    @Test
    void el_dump_se_restaura_en_una_base_vacia_y_cuenta_49_tecnicas_y_las_cuentas() throws Exception {
        URI url = URI.create(Entorno.urlBaseDeDatos().substring("jdbc:".length()));
        String host = url.getHost();
        String puerto = String.valueOf(url.getPort() == -1 ? 5432 : url.getPort());
        String admin = Entorno.usuarioAdministradorDb();
        String clave = Entorno.claveDb();
        Path dump = Files.createTempFile("pensamiento-", ".dump");
        String baseNueva = "restauracion_" + Long.toHexString(System.nanoTime());

        ejecutar(clave, "pg_dump", "-Fc", "-h", host, "-p", puerto, "-U", admin, "-f", dump.toString(), "pensamiento");
        assertThat(Files.size(dump)).as("el dump no está vacío").isGreaterThan(1000);

        JdbcClient cluster = jdbc("jdbc:postgresql://" + host + ":" + puerto + "/postgres", admin, clave);
        cluster.sql("CREATE DATABASE " + baseNueva).update();
        try {
            ejecutar(clave, "pg_restore", "-h", host, "-p", puerto, "-U", admin, "-d", baseNueva, "--no-owner", dump.toString());
            JdbcClient restaurada = jdbc("jdbc:postgresql://" + host + ":" + puerto + "/" + baseNueva, admin, clave);
            Long tecnicas = restaurada.sql("SELECT count(*) FROM tecnica").query(Long.class).single();
            Long familias = restaurada.sql("SELECT count(*) FROM familia").query(Long.class).single();
            Long usuarios = restaurada.sql("SELECT count(*) FROM usuario").query(Long.class).single();
            Boolean rlsForzado = restaurada.sql("SELECT relforcerowsecurity FROM pg_class WHERE relname = 'expediente'").query(Boolean.class).single();
            assertThat(tecnicas).isEqualTo(49L);
            assertThat(familias).isEqualTo(8L);
            assertThat(usuarios).as("los datos de usuario viajan en el dump").isGreaterThanOrEqualTo(1L);
            assertThat(rlsForzado).as("RLS forzada sobrevive a la restauración").isTrue();
        } finally {
            cluster.sql("DROP DATABASE IF EXISTS " + baseNueva + " WITH (FORCE)").update();
            Files.deleteIfExists(dump);
        }
    }

    private static JdbcClient jdbc(String url, String usuario, String clave) {
        DriverManagerDataSource ds = new DriverManagerDataSource(url, usuario, clave);
        ds.setDriverClassName("org.postgresql.Driver");
        return JdbcClient.create(ds);
    }

    private static void ejecutar(String clave, String... comando) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(List.of(comando)).redirectErrorStream(true);
        pb.environment().put("PGPASSWORD", clave);
        Process p = pb.start();
        String salida = new String(p.getInputStream().readAllBytes());
        assertThat(p.waitFor(120, TimeUnit.SECONDS)).as(comando[0] + " termina a tiempo").isTrue();
        assertThat(p.exitValue()).as(comando[0] + " salió bien: " + salida).isZero();
    }
}
