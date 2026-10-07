package pensamiento.catalogo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.springframework.stereotype.Component;

import pensamiento.nucleo.Familia;
import pensamiento.nucleo.Tecnica;

/**
 * Semilla idempotente del catálogo (migración repeatable de Flyway, registrada como bean de Spring).
 * Vuelve a correr solo cuando cambia el contenido de los JSON (checksum = huella del contenido).
 * ON CONFLICT (id) DO UPDATE en familia y técnica; las versiones de regla nunca se reescriben.
 * Nunca toca tablas de usuario.
 */
@Component
public class R__Semilla_catalogo extends BaseJavaMigration {

    private final CatalogoJson catalogo = new CatalogoJson();

    @Override
    public Integer getChecksum() {
        return catalogo.huella();
    }

    @Override
    public void migrate(Context context) throws SQLException {
        Connection con = context.getConnection();
        sembrarFamilias(con, catalogo.familias());
        sembrarTecnicas(con, catalogo.tecnicas());
        sembrarRelaciones(con, catalogo.relaciones());
        sembrarReglas(con, catalogo.reglas());
    }

    private void sembrarFamilias(Connection con, List<Familia> familias) throws SQLException {
        String sql = """
                INSERT INTO familia (codigo, nombre, orden) VALUES (?, ?, ?)
                ON CONFLICT (codigo) DO UPDATE SET nombre = EXCLUDED.nombre, orden = EXCLUDED.orden
                """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (Familia f : familias) {
                ps.setString(1, f.codigo());
                ps.setString(2, f.nombre());
                ps.setInt(3, f.orden());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void sembrarTecnicas(Connection con, List<Tecnica> tecnicas) throws SQLException {
        String sql = """
                INSERT INTO tecnica (id, familia_codigo, nombre, nombre_llano, usala_cuando, definicion, tipo, operacion,
                                     objeto, modalidad, patron, origen, requiere_ia, version_esquema, esquema_config,
                                     esquema_entrada, config_default, estado)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?)
                ON CONFLICT (id) DO UPDATE SET
                  familia_codigo = EXCLUDED.familia_codigo, nombre = EXCLUDED.nombre, nombre_llano = EXCLUDED.nombre_llano,
                  usala_cuando = EXCLUDED.usala_cuando, definicion = EXCLUDED.definicion, tipo = EXCLUDED.tipo,
                  operacion = EXCLUDED.operacion, objeto = EXCLUDED.objeto, modalidad = EXCLUDED.modalidad,
                  patron = EXCLUDED.patron, origen = EXCLUDED.origen, requiere_ia = EXCLUDED.requiere_ia,
                  version_esquema = EXCLUDED.version_esquema, esquema_config = EXCLUDED.esquema_config,
                  esquema_entrada = EXCLUDED.esquema_entrada, config_default = EXCLUDED.config_default,
                  estado = EXCLUDED.estado
                """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (Tecnica t : tecnicas) {
                ps.setString(1, t.id().valor());
                ps.setString(2, t.familia());
                ps.setString(3, t.nombre());
                ps.setString(4, t.nombreLlano());
                ps.setString(5, t.usalaCuando());
                ps.setString(6, t.definicion());
                ps.setString(7, t.tipo().name().toLowerCase());
                ps.setString(8, t.operacion().name().toLowerCase());
                ps.setString(9, t.objeto().name().toLowerCase());
                ps.setString(10, t.modalidad().name().toLowerCase());
                ps.setString(11, t.patron());
                ps.setString(12, t.origen());
                ps.setString(13, t.requiereIa().name().toLowerCase());
                ps.setInt(14, t.versionEsquema());
                ps.setString(15, t.esquemaConfig().texto());
                ps.setString(16, t.esquemaEntrada().texto());
                ps.setString(17, t.configDefault().texto());
                ps.setString(18, t.estado().name().toLowerCase());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void sembrarRelaciones(Connection con, List<CatalogoJson.Relacion> relaciones) throws SQLException {
        try (PreparedStatement borrar = con.prepareStatement("DELETE FROM relacion_tecnica")) {
            borrar.executeUpdate();
        }
        String sql = "INSERT INTO relacion_tecnica (origen_id, destino_id, tipo) VALUES (?, ?, ?) ON CONFLICT DO NOTHING";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (CatalogoJson.Relacion r : relaciones) {
                ps.setString(1, r.origen());
                ps.setString(2, r.destino());
                ps.setString(3, r.tipo());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void sembrarReglas(Connection con, List<CatalogoJson.ReglaVersion> reglas) throws SQLException {
        String sql = """
                INSERT INTO regla_version (regla, version, parametros) VALUES (?, ?, ?::jsonb)
                ON CONFLICT (regla, version) DO NOTHING
                """;
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            for (CatalogoJson.ReglaVersion r : reglas) {
                ps.setString(1, r.regla());
                ps.setInt(2, r.version());
                ps.setString(3, catalogo.aJson(r.parametros()));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
