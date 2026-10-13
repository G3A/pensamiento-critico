package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import pensamiento.testutil.BaseDatosDePrueba;
import pensamiento.testutil.Entorno;
import pensamiento.testutil.SemillaDeInstitucion;

/**
 * Hito 8, entregable 7: docker/respaldo/institucion.sh exporta una institución completa (una tabla por CSV, empaquetada y
 * cifrada) con una constancia fechada, y "borrar" exporta primero y después no deja ninguna fila suya en ninguna tabla.
 * Otra institución no se toca, y el borrado queda en la auditoría del operador.
 */
class RespaldoPorInstitucionIT {

    private static final Path SCRIPT = Path.of("docker/respaldo/institucion.sh");

    private final BaseDatosDePrueba bd = new BaseDatosDePrueba();

    @Test
    void exportar_y_borrar_una_institucion_deja_su_paquete_cifrado_su_constancia_y_ninguna_fila() throws Exception {
        SemillaDeInstitucion borrada = new SemillaDeInstitucion(bd);
        SemillaDeInstitucion vecina = new SemillaDeInstitucion(bd);
        Path destino = Files.createTempDirectory("instituciones-");
        try {
            String salida = RF11RespaldoYRestauracionIT.ejecutar(entorno(destino), 0, "bash", SCRIPT.toString(), "borrar", borrada.ruta());
            assertThat(salida).contains("Constancia de borrar de la institución /" + borrada.ruta()).contains("usuario: 1").contains("evidencia: 1");

            Path paquete;
            Path constancia;
            try (Stream<Path> archivos = Files.list(destino)) {
                var lista = archivos.toList();
                paquete = lista.stream().filter(p -> p.getFileName().toString().startsWith("institucion-" + borrada.ruta())).findFirst().orElseThrow();
                constancia = lista.stream().filter(p -> p.getFileName().toString().startsWith("constancia-borrar-" + borrada.ruta())).findFirst().orElseThrow();
            }
            assertThat(new String(Files.readAllBytes(paquete), java.nio.charset.StandardCharsets.ISO_8859_1))
                    .as("el paquete está cifrado").doesNotContain(borrada.centinela());
            String textoConstancia = Files.readString(constancia);
            assertThat(textoConstancia).contains("Fecha (UTC): ").contains(borrada.institucion().toString())
                    .contains("sha256 ").contains("Borrada: sí");

            Path abierto = Files.createDirectory(destino.resolve("abierto"));
            RF11RespaldoYRestauracionIT.ejecutar(Map.of(), 0, "bash", "-c", "gpg --batch --quiet --pinentry-mode loopback "
                    + "--passphrase-file /run/secrets/respaldo_clave --decrypt '" + paquete + "' | tar -C '" + abierto + "' -xf -");
            assertThat(Files.readString(abierto.resolve(borrada.ruta()).resolve("afirmacion.csv"))).contains(borrada.centinela());
            assertThat(Files.readString(abierto.resolve(borrada.ruta()).resolve("usuario.csv"))).contains(borrada.usuario().toString());

            assertThat(borrada.contarMarcas(bd.jdbcAdmin())).as("filas de la institución borrada")
                    .allSatisfy((tabla, filas) -> assertThat(filas).as(tabla).isZero());
            assertThat(vecina.contarMarcas(bd.jdbcAdmin())).as("la otra institución sigue entera").isEqualTo(vecina.sembradas());
            assertThat(bd.jdbcAdmin().sql("SELECT count(*) FROM auditoria_operador WHERE accion = 'borrar_institucion' AND institucion_id = :i")
                    .param("i", borrada.institucion()).query(Long.class).single()).isEqualTo(1L);
        } finally {
            vecina.borrar();
            RF11RespaldoYRestauracionIT.ejecutar(Map.of(), 0, "rm", "-rf", destino.toString());
        }
    }

    @Test
    void exportar_no_borra_nada_y_una_ruta_que_no_existe_falla() throws Exception {
        SemillaDeInstitucion exportada = new SemillaDeInstitucion(bd);
        Path destino = Files.createTempDirectory("instituciones-");
        try {
            String salida = RF11RespaldoYRestauracionIT.ejecutar(entorno(destino), 0, "bash", SCRIPT.toString(), "exportar", exportada.ruta());
            assertThat(salida).contains("Constancia de exportar").doesNotContain("Borrada");
            assertThat(exportada.contarMarcas(bd.jdbcAdmin())).isEqualTo(exportada.sembradas());
            RF11RespaldoYRestauracionIT.ejecutar(entorno(destino), -1, "bash", SCRIPT.toString(), "borrar", "no-existe-" + UUID.randomUUID().toString().substring(0, 8));
        } finally {
            exportada.borrar();
            RF11RespaldoYRestauracionIT.ejecutar(Map.of(), 0, "rm", "-rf", destino.toString());
        }
    }

    private static Map<String, String> entorno(Path destino) {
        URI url = URI.create(Entorno.urlBaseDeDatos().substring("jdbc:".length()));
        return Map.of("PGHOST", url.getHost(), "PGPORT", String.valueOf(url.getPort() == -1 ? 5432 : url.getPort()),
                "PGUSER", Entorno.usuarioAdministradorDb(), "PGDATABASE", "pensamiento", "CLAVE_DB", "/run/secrets/db_password",
                "CLAVE_RESPALDO", "/run/secrets/respaldo_clave", "DESTINO", destino.toString());
    }
}
