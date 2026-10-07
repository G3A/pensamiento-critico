package pensamiento.aceptacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/** RF-03: el perfil B recibe 404 al pedir cualquier objeto del perfil A por identificador. */
class RF03AislamientoEntreUsuariosIT {

    @Test
    void el_perfil_b_no_abre_el_expediente_del_perfil_a() {
        ClienteApp admin = Instalacion.administrador();
        String nombreA = Instalacion.personaNueva(admin, "perfil A", "1234");
        String nombreB = Instalacion.personaNueva(admin, "perfil B", "5678");

        ClienteApp a = Instalacion.entraComo(nombreA, "1234");
        UUID expediente = Instalacion.creaExpediente(a, "La segunda sucursal de la panadería");
        ClienteApp.Respuesta propio = a.get("/expedientes/" + expediente);
        assertThat(propio.estado()).isEqualTo(200);
        assertThat(propio.cuerpo()).contains("La segunda sucursal de la panadería");

        ClienteApp b = Instalacion.entraComo(nombreB, "5678");
        ClienteApp.Respuesta ajeno = b.get("/expedientes/" + expediente);
        assertThat(ajeno.estado()).isEqualTo(404);
        assertThat(ajeno.cuerpo()).doesNotContain("La segunda sucursal de la panadería");
    }

    @Test
    void un_identificador_inexistente_tambien_da_404_para_no_revelar_si_existe() {
        ClienteApp admin = Instalacion.administrador();
        String nombre = Instalacion.personaNueva(admin, "perfil C", "9999");
        ClienteApp c = Instalacion.entraComo(nombre, "9999");
        assertThat(c.get("/expedientes/" + UUID.randomUUID()).estado()).isEqualTo(404);
    }
}
