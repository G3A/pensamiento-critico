package pensamiento.web.usuarios;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pensamiento.nucleo.Institucion;
import pensamiento.nucleo.puertos.RepositorioInstituciones;

/**
 * La ruta se resuelve con app_institucion_por_ruta (SECURITY DEFINER, V11): el rol de la app no puede leer la tabla
 * institucion sin una institución fijada, y ni siquiera así ve las demás.
 */
@Repository
@Transactional
public class RepositorioInstitucionesJdbc implements RepositorioInstituciones {

    private final JdbcClient jdbc;

    public RepositorioInstitucionesJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UUID> activaPorRuta(String ruta) {
        return Institucion.rutaValida(ruta).flatMap(r ->
                jdbc.sql("SELECT id FROM app_institucion_por_ruta(:ruta) AS id WHERE id IS NOT NULL").param("ruta", r).query(UUID.class).optional());
    }

    @Override
    public Optional<String> quienDaCuentas(UUID institucion) {
        return jdbc.sql("SELECT t FROM institucion_quien_da_cuentas(:i) AS t WHERE t IS NOT NULL").param("i", institucion).query(String.class).optional();
    }

    @Override
    public boolean cambiarQuienDaCuentas(UUID institucion, String texto) {
        return jdbc.sql("SELECT r FROM institucion_cambiar_quien_da_cuentas(:i, :t) AS r WHERE r IS NOT NULL")
                .param("i", institucion).param("t", texto).query(Boolean.class).optional().orElse(false);
    }

    @Override
    public Optional<Institucion> porId(UUID id) {
        return jdbc.sql("SELECT id, nombre, ruta, estado FROM institucion WHERE id = :id").param("id", id)
                .query((rs, i) -> new Institucion(rs.getObject("id", UUID.class), rs.getString("nombre"), rs.getString("ruta"),
                        Institucion.Estado.valueOf(rs.getString("estado").toUpperCase())))
                .optional();
    }
}
