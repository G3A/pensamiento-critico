package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import pensamiento.testutil.Entorno;

/**
 * RF-11 con respaldos cifrados (hito 8): docker/respaldo/respaldo.sh, el mismo script del servicio backup, deja un
 * pg_dump -Fc cifrado con GnuPG (AES-256) y la clave de respaldo; el archivo cifrado no se puede leer sin ella, y el mismo
 * script lo restaura en una base vacía con 49 técnicas, 8 familias, las cuentas y la RLS forzada.
 */
class RF11RespaldoYRestauracionIT {

    private static final Path SCRIPT = Path.of("docker/respaldo/respaldo.sh");

    @Test
    void el_respaldo_cifrado_se_restaura_en_una_base_vacia_y_cuenta_49_tecnicas_y_las_cuentas() throws Exception {
        URI url = URI.create(Entorno.urlBaseDeDatos().substring("jdbc:".length()));
        String host = url.getHost();
        String puerto = String.valueOf(url.getPort() == -1 ? 5432 : url.getPort());
        String admin = Entorno.usuarioAdministradorDb();
        String clave = Entorno.claveDb();
        Path destino = Files.createTempDirectory("respaldos-");
        String baseNueva = "restauracion_" + Long.toHexString(System.nanoTime());
        Map<String, String> entorno = Map.of("PGHOST", host, "PGPORT", puerto, "PGUSER", admin, "PGDATABASE", "pensamiento",
                "CLAVE_DB", "/run/secrets/db_password", "CLAVE_RESPALDO", "/run/secrets/respaldo_clave", "DESTINO", destino.toString());

        ejecutar(entorno, 0, "bash", SCRIPT.toString());
        Path cifrado;
        try (Stream<Path> archivos = Files.list(destino)) {
            cifrado = archivos.filter(p -> p.getFileName().toString().endsWith(".dump.gpg")).findFirst().orElseThrow();
        }
        assertThat(Files.size(cifrado)).as("el respaldo no está vacío").isGreaterThan(1000);
        assertThat(ejecutar(Map.of(), -1, "pg_restore", "--list", cifrado.toString())).as("sin descifrar no es un dump legible")
                .contains("does not appear to be a valid");

        JdbcClient cluster = jdbc("jdbc:postgresql://" + host + ":" + puerto + "/postgres", admin, clave);
        cluster.sql("CREATE DATABASE " + baseNueva).update();
        try {
            Path claveFalsa = Files.writeString(destino.resolve("clave-falsa"), "no-es-la-clave-de-respaldo");
            ejecutar(Map.of("PGHOST", host, "PGPORT", puerto, "PGUSER", admin, "PGDATABASE", baseNueva, "CLAVE_DB", "/run/secrets/db_password",
                    "CLAVE_RESPALDO", claveFalsa.toString(), "DESTINO", destino.toString()), -1, "bash", SCRIPT.toString(), "restaurar", cifrado.toString());
            assertThat(jdbc("jdbc:postgresql://" + host + ":" + puerto + "/" + baseNueva, admin, clave)
                    .sql("SELECT count(*) FROM pg_tables WHERE schemaname = 'public'").query(Long.class).single())
                    .as("con otra clave no se restaura nada").isZero();
            ejecutar(Map.of("PGHOST", host, "PGPORT", puerto, "PGUSER", admin, "PGDATABASE", baseNueva, "CLAVE_DB", "/run/secrets/db_password",
                    "CLAVE_RESPALDO", "/run/secrets/respaldo_clave", "DESTINO", destino.toString()), 0, "bash", SCRIPT.toString(), "restaurar", cifrado.toString());
            JdbcClient restaurada = jdbc("jdbc:postgresql://" + host + ":" + puerto + "/" + baseNueva, admin, clave);
            Long tecnicas = restaurada.sql("SELECT count(*) FROM tecnica").query(Long.class).single();
            Long familias = restaurada.sql("SELECT count(*) FROM familia").query(Long.class).single();
            Long usuarios = restaurada.sql("SELECT count(*) FROM usuario").query(Long.class).single();
            Boolean rlsForzado = restaurada.sql("SELECT relforcerowsecurity FROM pg_class WHERE relname = 'expediente'").query(Boolean.class).single();
            assertThat(tecnicas).isEqualTo(49L);
            assertThat(familias).isEqualTo(8L);
            assertThat(usuarios).as("los datos de usuario viajan en el respaldo").isGreaterThanOrEqualTo(1L);
            assertThat(rlsForzado).as("RLS forzada sobrevive a la restauración").isTrue();
        } finally {
            cluster.sql("DROP DATABASE IF EXISTS " + baseNueva + " WITH (FORCE)").update();
            try (Stream<Path> archivos = Files.list(destino)) {
                archivos.forEach(p -> p.toFile().delete());
            }
            Files.deleteIfExists(destino);
        }
    }

    private static JdbcClient jdbc(String url, String usuario, String clave) {
        DriverManagerDataSource ds = new DriverManagerDataSource(url, usuario, clave);
        ds.setDriverClassName("org.postgresql.Driver");
        return JdbcClient.create(ds);
    }

    /** Corre el comando y devuelve su salida; {@code esperado} 0 exige éxito, -1 exige que falle. */
    static String ejecutar(Map<String, String> entorno, int esperado, String... comando) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(List.of(comando)).redirectErrorStream(true);
        pb.environment().putAll(entorno);
        pb.environment().put("PGPASSWORD", Entorno.claveDb());
        Process p = pb.start();
        String salida = new String(p.getInputStream().readAllBytes());
        assertThat(p.waitFor(180, TimeUnit.SECONDS)).as(comando[0] + " termina a tiempo").isTrue();
        if (esperado == 0) {
            assertThat(p.exitValue()).as(String.join(" ", comando) + " salió bien: " + salida).isZero();
        } else {
            assertThat(p.exitValue()).as(String.join(" ", comando) + " debía fallar: " + salida).isNotZero();
        }
        return salida;
    }
}
