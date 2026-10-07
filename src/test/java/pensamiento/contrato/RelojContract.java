package pensamiento.contrato;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pensamiento.nucleo.puertos.Reloj;

/** Contrato del puerto Reloj: el tiempo no retrocede y "hoy" es "ahora" en la zona del reloj. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class RelojContract {

    protected abstract Reloj crearSut();

    @Test
    void el_tiempo_nunca_retrocede_entre_dos_lecturas() {
        Reloj reloj = crearSut();
        Instant primera = reloj.ahora();
        Instant segunda = reloj.ahora();
        assertThat(segunda).isAfterOrEqualTo(primera);
    }

    @Test
    void hoy_es_la_fecha_de_ahora_en_la_zona_del_reloj() {
        Reloj reloj = crearSut();
        LocalDate hoy = reloj.hoy();
        LocalDate esperada = reloj.ahora().atZone(reloj.zona()).toLocalDate();
        assertThat(hoy).isBetween(esperada.minusDays(1), esperada.plusDays(1));
    }

    @Test
    void la_zona_esta_definida() {
        assertThat(crearSut().zona()).isNotNull();
    }
}
