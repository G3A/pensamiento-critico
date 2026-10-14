package pensamiento.web.seguridad;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pensamiento.nucleo.puertos.RegistroIntentos;

/** Todo por las funciones SECURITY DEFINER de V14: el rol de la app no tiene permisos sobre intento_acceso. */
@Repository
@Transactional
public class RegistroIntentosJdbc implements RegistroIntentos {

    private final JdbcClient jdbc;

    public RegistroIntentosJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Estado> leer(Optional<UUID> institucion, String clave) {
        return jdbc.sql("SELECT fallos, ventana_desde, espera_hasta FROM intento_leer(:i, :c)")
                .param("i", institucion.orElse(null)).param("c", clave)
                .query((rs, n) -> new Estado(rs.getInt("fallos"), rs.getTimestamp("ventana_desde").toInstant(),
                        Optional.ofNullable(rs.getTimestamp("espera_hasta")).map(Timestamp::toInstant)))
                .optional();
    }

    @Override
    public void guardar(Optional<UUID> institucion, String clave, Estado estado) {
        jdbc.sql("SELECT intento_guardar(:i, :c, :f, :v, :e)")
                .param("i", institucion.orElse(null)).param("c", clave).param("f", estado.fallos())
                .param("v", Timestamp.from(estado.ventanaDesde())).param("e", estado.esperaHasta().map(Timestamp::from).orElse(null))
                .query((rs, n) -> 1).list();
    }

    @Override
    public void borrar(Optional<UUID> institucion, String clave) {
        jdbc.sql("SELECT intento_borrar(:i, :c)").param("i", institucion.orElse(null)).param("c", clave).query((rs, n) -> 1).list();
    }

    @Override
    public List<Espera> esperas(UUID institucion, Instant ahora) {
        return jdbc.sql("SELECT clave, espera_hasta FROM intento_esperas(:i, :a)").param("i", institucion).param("a", Timestamp.from(ahora))
                .query((rs, n) -> new Espera(rs.getString("clave"), rs.getTimestamp("espera_hasta").toInstant())).list();
    }
}
