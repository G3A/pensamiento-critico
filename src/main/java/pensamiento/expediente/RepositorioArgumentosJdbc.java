package pensamiento.expediente;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pensamiento.nucleo.Argumento;
import pensamiento.nucleo.ArgumentoProducido;
import pensamiento.nucleo.EstandarPrueba;
import pensamiento.nucleo.puertos.RepositorioArgumentos;

/**
 * Argumentos y sus premisas en PostgreSQL. Filtra por usuario; RLS por debajo (premisa_argumento la hereda del
 * argumento). Idempotente por identificador: ON CONFLICT DO NOTHING en ambas tablas.
 */
@Repository
@Transactional
public class RepositorioArgumentosJdbc implements RepositorioArgumentos {

    private static final String COLUMNAS = """
            a.id, a.ejecucion_id, a.orden, a.conclusion_id, a.esquema_id, a.peso, a.sentido, a.estandar, a.texto_argdown
            """;

    private final JdbcClient jdbc;

    public RepositorioArgumentosJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void guardar(UUID usuarioId, UUID institucionId, UUID ejecucionId, List<ArgumentoProducido> argumentos) {
        for (int i = 0; i < argumentos.size(); i++) {
            ArgumentoProducido p = argumentos.get(i);
            Argumento a = p.argumento();
            jdbc.sql("""
                    INSERT INTO argumento (id, usuario_id, institucion_id, conclusion_id, esquema_id, peso, sentido, estandar,
                                           texto_argdown, ejecucion_id, orden)
                    VALUES (:id, :usuario, :institucion, :conclusion, :esquema, :peso, :sentido, :estandar, :texto, :ejecucion, :orden)
                    ON CONFLICT (id) DO NOTHING
                    """)
                    .param("id", a.id()).param("usuario", usuarioId).param("institucion", institucionId)
                    .param("conclusion", a.conclusionId()).param("esquema", p.esquemaId().orElse(null)).param("peso", a.peso())
                    .param("sentido", a.sentido().name().toLowerCase()).param("estandar", p.estandar().enBaseDeDatos())
                    .param("texto", p.textoArgdown().orElse(null)).param("ejecucion", ejecucionId).param("orden", i + 1)
                    .update();
            for (Argumento.Premisa premisa : a.premisas()) {
                jdbc.sql("""
                        INSERT INTO premisa_argumento (argumento_id, afirmacion_id, orden, asumible) VALUES (:a, :af, :orden, :asumible)
                        ON CONFLICT (argumento_id, afirmacion_id) DO NOTHING
                        """)
                        .param("a", a.id()).param("af", premisa.afirmacionId()).param("orden", premisa.orden()).param("asumible", premisa.asumible())
                        .update();
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArgumentoGuardado> deEjecucion(UUID usuarioId, UUID ejecucionId) {
        return leer(jdbc.sql("SELECT " + COLUMNAS + " FROM argumento a WHERE a.usuario_id = :usuario AND a.ejecucion_id = :ejecucion ORDER BY a.orden, a.id")
                .param("usuario", usuarioId).param("ejecucion", ejecucionId).query(RepositorioArgumentosJdbc::fila).list());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ArgumentoGuardado> porId(UUID usuarioId, UUID argumentoId) {
        return leer(jdbc.sql("SELECT " + COLUMNAS + " FROM argumento a WHERE a.usuario_id = :usuario AND a.id = :id")
                .param("usuario", usuarioId).param("id", argumentoId).query(RepositorioArgumentosJdbc::fila).list()).stream().findFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ArgumentoGuardado> conPremisa(UUID usuarioId, UUID afirmacionId) {
        return leer(jdbc.sql("SELECT " + COLUMNAS + """
                 FROM argumento a WHERE a.usuario_id = :usuario
                  AND EXISTS (SELECT 1 FROM premisa_argumento p WHERE p.argumento_id = a.id AND p.afirmacion_id = :afirmacion)
                ORDER BY a.ejecucion_id, a.orden, a.id
                """).param("usuario", usuarioId).param("afirmacion", afirmacionId).query(RepositorioArgumentosJdbc::fila).list());
    }

    /** Cabecera del argumento sin premisas todavía. */
    private record Fila(UUID id, UUID ejecucionId, int orden, UUID conclusionId, String esquemaId, int peso, String sentido, String estandar,
                        String texto) {
    }

    private static Fila fila(ResultSet rs, int i) throws SQLException {
        return new Fila(rs.getObject("id", UUID.class), rs.getObject("ejecucion_id", UUID.class), rs.getInt("orden"),
                rs.getObject("conclusion_id", UUID.class), rs.getString("esquema_id"), rs.getInt("peso"), rs.getString("sentido"),
                rs.getString("estandar"), rs.getString("texto_argdown"));
    }

    private List<ArgumentoGuardado> leer(List<Fila> filas) {
        if (filas.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<Argumento.Premisa>> premisas = new LinkedHashMap<>();
        filas.forEach(f -> premisas.put(f.id(), new ArrayList<>()));
        jdbc.sql("SELECT argumento_id, afirmacion_id, orden, asumible FROM premisa_argumento WHERE argumento_id = ANY (:ids) ORDER BY orden")
                .param("ids", filas.stream().map(Fila::id).toArray(UUID[]::new))
                .query(rs -> {
                    premisas.get(rs.getObject("argumento_id", UUID.class)).add(new Argumento.Premisa(rs.getObject("afirmacion_id", UUID.class),
                            rs.getInt("orden"), rs.getBoolean("asumible")));
                });
        return filas.stream().map(f -> new ArgumentoGuardado(f.ejecucionId(), f.orden(), new ArgumentoProducido(
                new Argumento(f.id(), f.conclusionId(), premisas.get(f.id()), f.peso(), Argumento.Sentido.valueOf(f.sentido().toUpperCase())),
                EstandarPrueba.valueOf(f.estandar().toUpperCase()), Optional.ofNullable(f.esquemaId()), Optional.ofNullable(f.texto())))).toList();
    }
}
