package pensamiento.contrato;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pensamiento.nucleo.Json;
import pensamiento.nucleo.Trabajo;
import pensamiento.nucleo.puertos.ColaTrabajos;

/**
 * Contrato de la cola de trabajos largos: tomar pasa a en proceso, suma un intento y respeta el payload; nunca toma lo que
 * no está disponible, lo de otro tipo ni dos veces lo mismo; el más viejo va primero; terminar, fallar y reintentar dejan
 * su estado y su motivo; los que quedaron en proceso vuelven a la cola. Cada prueba usa un tipo propio para no tomar
 * trabajos de nadie más.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class ColaTrabajosContract {

    protected static final Instant AHORA = Instant.parse("2026-10-09T20:00:00Z");

    protected abstract ColaTrabajos cola();

    private static String tipoPropio() {
        return "contrato-" + UUID.randomUUID();
    }

    @Test
    void tomar_un_trabajo_lo_pasa_a_en_proceso_con_un_intento_y_su_payload() {
        String tipo = tipoPropio();
        Json payload = new Json("{\"documento\": \"conteo-peatonal-municipio-2025.pdf\", \"página\": 2}");
        UUID id = cola().encolar(tipo, payload, AHORA);

        assertThat(cola().tomar(AHORA, Set.of(tipo))).hasValueSatisfying(t -> {
            assertThat(t.id()).isEqualTo(id);
            assertThat(t.tipo()).isEqualTo(tipo);
            assertThat(t.estado()).isEqualTo(Trabajo.Estado.EN_PROCESO);
            assertThat(t.intentos()).isEqualTo(1);
            assertThat(t.payload().texto()).contains("conteo-peatonal-municipio-2025.pdf", "página");
        });
        assertThat(cola().tomar(AHORA, Set.of(tipo))).as("no se toma dos veces").isEmpty();
    }

    @Test
    void no_toma_lo_que_todavia_no_esta_disponible_ni_lo_de_otro_tipo() {
        String tipo = tipoPropio();
        cola().encolar(tipo, Json.VACIO, AHORA.plusSeconds(60));
        cola().encolar(tipoPropio(), Json.VACIO, AHORA);

        assertThat(cola().tomar(AHORA, Set.of(tipo))).isEmpty();
        assertThat(cola().tomar(AHORA.plusSeconds(61), Set.of(tipo))).isPresent();
        assertThat(cola().tomar(AHORA, Set.of())).isEmpty();
    }

    @Test
    void el_mas_viejo_va_primero() {
        String tipo = tipoPropio();
        UUID primero = cola().encolar(tipo, Json.VACIO, AHORA);
        UUID segundo = cola().encolar(tipo, Json.VACIO, AHORA);

        assertThat(cola().tomar(AHORA, Set.of(tipo))).map(Trabajo::id).contains(primero);
        assertThat(cola().tomar(AHORA, Set.of(tipo))).map(Trabajo::id).contains(segundo);
    }

    @Test
    void terminar_y_fallar_dejan_su_estado_y_lo_fallado_no_se_vuelve_a_tomar() {
        String tipo = tipoPropio();
        UUID hecho = cola().encolar(tipo, Json.VACIO, AHORA);
        cola().tomar(AHORA, Set.of(tipo));
        cola().terminar(hecho);
        UUID fallado = cola().encolar(tipo, Json.VACIO, AHORA);
        cola().tomar(AHORA, Set.of(tipo));
        cola().fallar(fallado, "El PDF está dañado, cifrado o no es un PDF.");

        assertThat(cola().porId(hecho)).hasValueSatisfying(t -> assertThat(t.estado()).isEqualTo(Trabajo.Estado.HECHO));
        assertThat(cola().porId(fallado)).hasValueSatisfying(t -> {
            assertThat(t.estado()).isEqualTo(Trabajo.Estado.ERROR);
            assertThat(t.error()).contains("El PDF está dañado, cifrado o no es un PDF.");
        });
        assertThat(cola().tomar(AHORA.plusSeconds(3600), Set.of(tipo))).isEmpty();
    }

    @Test
    void reintentar_lo_devuelve_a_la_cola_con_espera_y_su_motivo() {
        String tipo = tipoPropio();
        UUID id = cola().encolar(tipo, Json.VACIO, AHORA);
        cola().tomar(AHORA, Set.of(tipo));

        cola().reintentar(id, AHORA.plusSeconds(30), "Ollama no responde: se reintenta en 30 s.");

        assertThat(cola().porId(id)).hasValueSatisfying(t -> {
            assertThat(t.estado()).isEqualTo(Trabajo.Estado.PENDIENTE);
            assertThat(t.error()).contains("Ollama no responde: se reintenta en 30 s.");
            assertThat(t.disponibleEn()).isEqualTo(AHORA.plusSeconds(30));
        });
        assertThat(cola().tomar(AHORA.plusSeconds(10), Set.of(tipo))).isEmpty();
        assertThat(cola().tomar(AHORA.plusSeconds(30), Set.of(tipo))).hasValueSatisfying(t -> assertThat(t.intentos()).isEqualTo(2));
    }

    @Test
    void los_que_quedaron_en_proceso_vuelven_a_la_cola() {
        String tipo = tipoPropio();
        UUID id = cola().encolar(tipo, Json.VACIO, AHORA);
        cola().tomar(AHORA, Set.of(tipo));

        assertThat(cola().reencolarEnProceso(Set.of(tipo))).isEqualTo(1);

        assertThat(cola().porId(id)).hasValueSatisfying(t -> assertThat(t.estado()).isEqualTo(Trabajo.Estado.PENDIENTE));
        assertThat(cola().tomar(AHORA, Set.of(tipo))).map(Trabajo::id).contains(id);
        assertThat(cola().porId(UUID.randomUUID())).isEmpty();
    }
}
