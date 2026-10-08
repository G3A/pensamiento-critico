package pensamiento.catalogo;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pensamiento.nucleo.Ejemplo;
import pensamiento.nucleo.Familia;
import pensamiento.nucleo.RelacionTecnica;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Json;
import pensamiento.nucleo.Tecnica;
import pensamiento.nucleo.puertos.RepositorioTecnica;

/** Catálogo leído de PostgreSQL. Es compartido: sin filtro por usuario y sin RLS. */
@Repository
@Transactional(readOnly = true)
public class RepositorioTecnicaJdbc implements RepositorioTecnica {

    private static final String COLUMNAS = """
            id, familia_codigo, nombre, nombre_llano, usala_cuando, definicion, tipo, operacion, objeto, modalidad,
            patron, origen, requiere_ia, version_esquema, esquema_config::text AS esquema_config,
            esquema_entrada::text AS esquema_entrada, config_default::text AS config_default, estado, ia_experimental
            """;

    private final JdbcClient jdbc;

    public RepositorioTecnicaJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Ejemplo> ejemplos(IdTecnica tecnica) {
        return jdbc.sql("SELECT " + COLUMNAS_EJEMPLO + " FROM ejemplo WHERE tecnica_id = :t ORDER BY orden, titulo")
                .param("t", tecnica.valor()).query(RepositorioTecnicaJdbc::ejemplo).list();
    }

    @Override
    public Optional<Ejemplo> ejemplo(UUID id) {
        return jdbc.sql("SELECT " + COLUMNAS_EJEMPLO + " FROM ejemplo WHERE id = :id")
                .param("id", id).query(RepositorioTecnicaJdbc::ejemplo).optional();
    }

    @Override
    public List<RelacionTecnica> relaciones(IdTecnica tecnica) {
        return jdbc.sql("SELECT origen_id, destino_id, tipo FROM relacion_tecnica WHERE origen_id = :t OR destino_id = :t ORDER BY tipo, origen_id, destino_id")
                .param("t", tecnica.valor())
                .query((rs, i) -> new RelacionTecnica(IdTecnica.de(rs.getString("origen_id")), IdTecnica.de(rs.getString("destino_id")),
                        RelacionTecnica.Tipo.valueOf(rs.getString("tipo").toUpperCase())))
                .list();
    }

    private static final String COLUMNAS_EJEMPLO = """
            id, tecnica_id, orden, version_esquema, ambito, titulo, config::text AS config, datos::text AS datos,
            resultado::text AS resultado, nota
            """;

    static Ejemplo ejemplo(ResultSet rs, int i) throws SQLException {
        return new Ejemplo(rs.getObject("id", UUID.class), IdTecnica.de(rs.getString("tecnica_id")), rs.getInt("orden"),
                rs.getInt("version_esquema"), Ejemplo.Ambito.valueOf(rs.getString("ambito").toUpperCase()), rs.getString("titulo"),
                new Json(rs.getString("config")), new Json(rs.getString("datos")), new Json(rs.getString("resultado")), rs.getString("nota"));
    }

    @Override
    public List<Familia> familias() {
        return jdbc.sql("SELECT codigo, nombre, orden FROM familia ORDER BY orden")
                .query((rs, i) -> new Familia(rs.getString("codigo"), rs.getString("nombre"), rs.getInt("orden")))
                .list();
    }

    @Override
    public List<Tecnica> todas() {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM tecnica ORDER BY id").query(RepositorioTecnicaJdbc::fila).list();
    }

    @Override
    public List<Tecnica> porFamilia(String codigoFamilia) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM tecnica WHERE familia_codigo = :familia ORDER BY id")
                .param("familia", codigoFamilia)
                .query(RepositorioTecnicaJdbc::fila)
                .list();
    }

    @Override
    public Optional<Tecnica> porId(IdTecnica id) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM tecnica WHERE id = :id")
                .param("id", id.valor())
                .query(RepositorioTecnicaJdbc::fila)
                .optional();
    }

    @Override
    public long contar() {
        return jdbc.sql("SELECT count(*) FROM tecnica").query(Long.class).single();
    }

    static Tecnica fila(ResultSet rs, int i) throws SQLException {
        return new Tecnica(
                IdTecnica.de(rs.getString("id")),
                rs.getString("familia_codigo"),
                rs.getString("nombre"),
                rs.getString("nombre_llano"),
                rs.getString("usala_cuando"),
                rs.getString("definicion"),
                Tecnica.Tipo.valueOf(rs.getString("tipo").toUpperCase()),
                Tecnica.Operacion.valueOf(rs.getString("operacion").toUpperCase()),
                Tecnica.Objeto.valueOf(rs.getString("objeto").toUpperCase()),
                Tecnica.Modalidad.valueOf(rs.getString("modalidad").toUpperCase()),
                rs.getString("patron"),
                rs.getString("origen"),
                Tecnica.RequiereIa.valueOf(rs.getString("requiere_ia").toUpperCase()),
                rs.getInt("version_esquema"),
                new Json(rs.getString("esquema_config")),
                new Json(rs.getString("esquema_entrada")),
                new Json(rs.getString("config_default")),
                Tecnica.Estado.valueOf(rs.getString("estado").toUpperCase()),
                rs.getBoolean("ia_experimental"));
    }
}
