package pensamiento.expediente;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import pensamiento.nucleo.puertos.RegistroIdentificadores;

/**
 * Pregunta a la función app_id_de_otro_usuario (V3, SECURITY DEFINER), que compara contra el usuario de la
 * transacción (app.usuario). El parámetro usuarioId debe coincidir con ese contexto; si no, la respuesta es la
 * del usuario de la sesión, nunca la de otro.
 */
@Repository
@Transactional(readOnly = true)
public class RegistroIdentificadoresJdbc implements RegistroIdentificadores {

    private final JdbcClient jdbc;

    public RegistroIdentificadoresJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean deOtroUsuario(UUID usuarioId, UUID id) {
        return Boolean.TRUE.equals(jdbc.sql("SELECT app_id_de_otro_usuario(:id)").param("id", id).query(Boolean.class).single());
    }
}
