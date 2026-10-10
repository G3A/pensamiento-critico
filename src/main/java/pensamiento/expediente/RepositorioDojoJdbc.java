package pensamiento.expediente;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pensamiento.nucleo.Competencia;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.IntentoDojo;
import pensamiento.nucleo.NivelBloom;
import pensamiento.nucleo.puertos.RepositorioDojo;

/**
 * Intentos del Dojo y competencia en PostgreSQL (intento_dojo de V10 y competencia de V1). Filtra por usuario; RLS por
 * debajo. El rol de aplicación solo puede insertar y leer intentos; la competencia se reescribe con cada intento.
 */
@Repository
@Transactional
public class RepositorioDojoJdbc implements RepositorioDojo {

    private static final String COLUMNAS = "id, clave, reto_id, tecnica_id, concepto, nivel, respuesta, acierto, dia, creado_en";

    private final JdbcClient jdbc;

    public RepositorioDojoJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean guardar(UUID usuarioId, UUID institucionId, IntentoDojo intento, Competencia competencia) {
        if (!intento.tecnica().equals(competencia.tecnica())) {
            throw new IllegalArgumentException("La competencia tiene que ser del tema del intento");
        }
        if (insertar(usuarioId, institucionId, intento) == 0) {
            return false;
        }
        restaurar(usuarioId, institucionId, competencia);
        return true;
    }

    private int insertar(UUID usuarioId, UUID institucionId, IntentoDojo i) {
        return jdbc.sql("""
                INSERT INTO intento_dojo (id, usuario_id, institucion_id, clave, reto_id, tecnica_id, concepto, nivel, respuesta, acierto, dia, creado_en)
                VALUES (:id, :usuario, :institucion, :clave, :reto, :tecnica, :concepto, :nivel, :respuesta, :acierto, :dia, :creado)
                ON CONFLICT DO NOTHING
                """)
                .param("id", i.id()).param("usuario", usuarioId).param("institucion", institucionId).param("clave", i.clave())
                .param("reto", i.retoId()).param("tecnica", i.tecnica().valor()).param("concepto", i.concepto())
                .param("nivel", i.nivel().toString()).param("respuesta", i.respuesta()).param("acierto", i.acierto())
                .param("dia", Date.valueOf(i.dia())).param("creado", Timestamp.from(i.creadoEn()))
                .update();
    }

    @Override
    @Transactional(readOnly = true)
    public List<IntentoDojo> intentos(UUID usuarioId) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM intento_dojo WHERE usuario_id = :usuario ORDER BY creado_en, id")
                .param("usuario", usuarioId).query(RepositorioDojoJdbc::intento).list();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Competencia> competencias(UUID usuarioId) {
        return jdbc.sql("""
                SELECT tecnica_id, nivel_bloom, intentos, aciertos, ultima_practica FROM competencia
                WHERE usuario_id = :usuario ORDER BY tecnica_id
                """)
                .param("usuario", usuarioId)
                .query((rs, n) -> new Competencia(IdTecnica.de(rs.getString("tecnica_id")), NivelBloom.de(rs.getString("nivel_bloom")),
                        rs.getInt("intentos"), rs.getInt("aciertos"), rs.getTimestamp("ultima_practica").toInstant()))
                .list();
    }

    @Override
    public void restaurar(UUID usuarioId, UUID institucionId, IntentoDojo intento) {
        insertar(usuarioId, institucionId, intento);
    }

    @Override
    public void restaurar(UUID usuarioId, UUID institucionId, Competencia c) {
        jdbc.sql("""
                INSERT INTO competencia (usuario_id, institucion_id, tecnica_id, nivel_bloom, intentos, aciertos, ultima_practica)
                VALUES (:usuario, :institucion, :tecnica, :nivel, :intentos, :aciertos, :ultima)
                ON CONFLICT (usuario_id, tecnica_id) DO UPDATE SET nivel_bloom = EXCLUDED.nivel_bloom, intentos = EXCLUDED.intentos,
                  aciertos = EXCLUDED.aciertos, ultima_practica = EXCLUDED.ultima_practica
                """)
                .param("usuario", usuarioId).param("institucion", institucionId).param("tecnica", c.tecnica().valor())
                .param("nivel", c.nivel().toString()).param("intentos", c.intentos()).param("aciertos", c.aciertos())
                .param("ultima", Timestamp.from(c.ultimaPractica()))
                .update();
    }

    private static IntentoDojo intento(ResultSet rs, int n) throws SQLException {
        return new IntentoDojo(rs.getObject("id", UUID.class), rs.getString("clave"), rs.getString("reto_id"), IdTecnica.de(rs.getString("tecnica_id")),
                rs.getString("concepto"), NivelBloom.de(rs.getString("nivel")), rs.getString("respuesta"), rs.getBoolean("acierto"),
                rs.getDate("dia").toLocalDate(), rs.getTimestamp("creado_en").toInstant());
    }
}
