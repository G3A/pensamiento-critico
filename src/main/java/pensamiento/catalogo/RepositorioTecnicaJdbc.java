package pensamiento.catalogo;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pensamiento.nucleo.Familia;
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
            esquema_entrada::text AS esquema_entrada, config_default::text AS config_default, estado
            """;

    private final JdbcClient jdbc;

    public RepositorioTecnicaJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
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
                Tecnica.Estado.valueOf(rs.getString("estado").toUpperCase()));
    }
}
