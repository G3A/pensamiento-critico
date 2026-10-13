package pensamiento.web.operador;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pensamiento.nucleo.Institucion;
import pensamiento.nucleo.puertos.RepositorioOperador;

/** Todo por las funciones SECURITY DEFINER de V12: el rol de la app no tiene permisos sobre operador ni invitacion. */
@Repository
@Transactional
public class RepositorioOperadorJdbc implements RepositorioOperador {

    private static final String OPERADOR_DESCONOCIDO = "42501";

    private final JdbcClient jdbc;

    public RepositorioOperadorJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Operador> porNombre(String nombre) {
        return jdbc.sql("SELECT id, clave_hash FROM operador_por_nombre(:n)").param("n", nombre)
                .query((rs, i) -> new Operador(rs.getObject("id", UUID.class), rs.getString("clave_hash"))).optional();
    }

    @Override
    public boolean crearSiNoHay(String nombre, String claveHash) {
        return Boolean.TRUE.equals(jdbc.sql("SELECT operador_crear_si_no_hay(:n, :h)").param("n", nombre).param("h", claveHash)
                .query(Boolean.class).single());
    }

    @Override
    public List<InstitucionDelOperador> instituciones() {
        return jdbc.sql("SELECT id, nombre, ruta, estado, creada_en, tiene_cuentas FROM operador_instituciones()")
                .query((rs, i) -> new InstitucionDelOperador(
                        new Institucion(rs.getObject("id", UUID.class), rs.getString("nombre"), rs.getString("ruta"),
                                Institucion.Estado.valueOf(rs.getString("estado").toUpperCase())),
                        rs.getTimestamp("creada_en").toInstant(), rs.getBoolean("tiene_cuentas")))
                .list();
    }

    @Override
    public UUID crearInstitucion(UUID operador, String nombre, String ruta) {
        try {
            return comoOperador(() -> jdbc.sql("SELECT operador_crear_institucion(:o, :n, :r)")
                    .param("o", operador).param("n", nombre).param("r", ruta).query(UUID.class).single());
        } catch (DuplicateKeyException e) {
            throw new RutaRepetida(ruta);
        }
    }

    @Override
    public boolean cambiarEstado(UUID operador, UUID institucion, Institucion.Estado estado) {
        return comoOperador(() -> Boolean.TRUE.equals(jdbc.sql("SELECT operador_cambiar_estado(:o, :i, :e)")
                .param("o", operador).param("i", institucion).param("e", estado.enBaseDeDatos()).query(Boolean.class).single()));
    }

    @Override
    public UUID invitar(UUID operador, UUID institucion, String codigoHash, Instant caducaEn) {
        return comoOperador(() -> jdbc.sql("SELECT operador_invitar(:o, :i, :h, :c)")
                .param("o", operador).param("i", institucion).param("h", codigoHash).param("c", Timestamp.from(caducaEn))
                .query(UUID.class).single());
    }

    @Override
    public List<InvitacionVigente> vigentes(UUID institucion) {
        return jdbc.sql("SELECT id, codigo_hash FROM invitaciones_vigentes(:i)").param("i", institucion)
                .query((rs, i) -> new InvitacionVigente(rs.getObject("id", UUID.class), rs.getString("codigo_hash"))).list();
    }

    @Override
    public boolean canjear(UUID invitacion) {
        return Boolean.TRUE.equals(jdbc.sql("SELECT canjear_invitacion(:i)").param("i", invitacion).query(Boolean.class).single());
    }

    @Override
    public List<Evento> auditoria(int limite) {
        return jdbc.sql("SELECT fecha, accion, ruta, operador_id, institucion_id FROM operador_auditoria(:l)").param("l", limite)
                .query((rs, i) -> new Evento(rs.getTimestamp("fecha").toInstant(), Accion.valueOf(rs.getString("accion").toUpperCase()),
                        rs.getString("ruta"), Optional.ofNullable(rs.getObject("operador_id", UUID.class)),
                        rs.getObject("institucion_id", UUID.class)))
                .list();
    }

    private static <T> T comoOperador(Supplier<T> accion) {
        try {
            return accion.get();
        } catch (DataAccessException e) {
            if (e.getMostSpecificCause() instanceof SQLException sql && OPERADOR_DESCONOCIDO.equals(sql.getSQLState())) {
                throw new OperadorDesconocido();
            }
            throw e;
        }
    }
}
