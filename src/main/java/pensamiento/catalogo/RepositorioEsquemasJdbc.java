package pensamiento.catalogo;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;

import pensamiento.nucleo.Esquema;
import pensamiento.nucleo.puertos.RepositorioEsquemas;

/** Esquemas de Walton leídos de PostgreSQL. Catálogo compartido: sin filtro por usuario y sin RLS. */
@Repository
@Transactional(readOnly = true)
public class RepositorioEsquemasJdbc implements RepositorioEsquemas {

    private static final String COLUMNAS = "id, nombre, descripcion, preguntas_criticas::text AS preguntas, origen";

    private final JdbcClient jdbc;

    public RepositorioEsquemasJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Esquema> todos() {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM esquema_walton ORDER BY id").query(RepositorioEsquemasJdbc::esquema).list();
    }

    @Override
    public Optional<Esquema> porId(String id) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM esquema_walton WHERE id = :id").param("id", id)
                .query(RepositorioEsquemasJdbc::esquema).optional();
    }

    private static Esquema esquema(ResultSet rs, int fila) throws SQLException {
        List<Esquema.PreguntaCritica> preguntas = MapeadorJson.mapper()
                .readValue(rs.getString("preguntas"), new TypeReference<List<Esquema.PreguntaCritica>>() { });
        return new Esquema(rs.getString("id"), rs.getString("nombre"), rs.getString("descripcion"), preguntas, rs.getString("origen"));
    }
}
