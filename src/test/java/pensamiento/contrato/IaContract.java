package pensamiento.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pensamiento.nucleo.puertos.Clasificacion;
import pensamiento.nucleo.puertos.Ia;
import pensamiento.nucleo.puertos.IaNoDisponible;
import pensamiento.nucleo.puertos.IaRespuestaInvalida;
import pensamiento.nucleo.puertos.IaTiempoAgotado;
import pensamiento.nucleo.puertos.Mensaje;
import pensamiento.nucleo.puertos.PeticionChat;
import pensamiento.nucleo.puertos.PeticionClasificacion;
import pensamiento.nucleo.puertos.PeticionEmbeddings;
import pensamiento.nucleo.puertos.RespuestaChat;

/**
 * Contrato del puerto Ia (sección 9: no disponible, tiempo de espera, JSON inválido, respuesta sin pregunta).
 * Lo cumplen FakeIa (en cada PR) y el adaptador Spring AI contra Ollama real (nocturno, CONTRACT_REAL=true).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class IaContract {

    protected static final Duration TIEMPO_NORMAL = Duration.ofSeconds(90);
    protected static final List<String> ETIQUETAS = List.of("apoya", "contradice", "irrelevante");

    /** Implementación disponible y con los modelos listos. */
    protected abstract Ia disponible();

    /** Implementación que apunta a un Ollama que no responde. */
    protected abstract Ia noDisponible();

    /** Implementación que tarda más que cualquier tiempo máximo razonable. */
    protected abstract Ia lenta();

    /** Implementación cuyo modelo responde con algo que no es JSON válido en la clasificación. */
    protected abstract Ia conJsonInvalido();

    @Test
    void cuando_esta_disponible_lo_dice_y_lista_los_dos_modelos() {
        var estado = disponible().estado();
        assertThat(estado.disponible()).isTrue();
        assertThat(estado.modelos()).anyMatch(m -> m.startsWith("qwen3")).anyMatch(m -> m.startsWith("bge-m3"));
    }

    @Test
    void cuando_no_esta_disponible_el_estado_no_lanza_y_lo_dice() {
        var estado = noDisponible().estado();
        assertThat(estado.disponible()).isFalse();
        assertThat(estado.detalle()).isNotBlank();
    }

    @Test
    void el_chat_entrega_tokens_provisionales_y_la_respuesta_completa_al_final() {
        List<String> tokens = new ArrayList<>();
        RespuestaChat respuesta = disponible().chat(PeticionChat.simple("Responde en una sola palabra en español: ¿de qué color es el cielo despejado?", TIEMPO_NORMAL), tokens::add);
        assertThat(respuesta.texto()).isNotBlank();
        assertThat(tokens).isNotEmpty();
        assertThat(String.join("", tokens).trim()).isEqualTo(respuesta.texto());
        assertThat(respuesta.modelo()).isNotBlank();
        assertThat(respuesta.intentos()).isEqualTo(1);
    }

    @Test
    void una_respuesta_que_no_pasa_el_validador_se_reintenta_y_luego_lanza_respuesta_invalida() {
        Predicate<String> nuncaPasa = texto -> false;
        PeticionChat peticion = new PeticionChat(List.of(Mensaje.usuario("Di hola.")), TIEMPO_NORMAL, Optional.of(nuncaPasa), 1);
        assertThatThrownBy(() -> disponible().chat(peticion, t -> { }))
                .isInstanceOf(IaRespuestaInvalida.class)
                .hasMessageContaining("2 intentos");
    }

    @Test
    void sin_ollama_el_chat_lanza_no_disponible() {
        assertThatThrownBy(() -> noDisponible().chat(PeticionChat.simple("hola", TIEMPO_NORMAL), t -> { }))
                .isInstanceOf(IaNoDisponible.class);
    }

    @Test
    void si_se_pasa_del_tiempo_maximo_lanza_tiempo_agotado() {
        assertThatThrownBy(() -> lenta().chat(PeticionChat.simple("Escribe un cuento largo sobre una panadería.", Duration.ofMillis(1)), t -> { }))
                .isInstanceOf(IaTiempoAgotado.class);
    }

    @Test
    void la_clasificacion_devuelve_una_etiqueta_del_enum_y_un_por_que() {
        Clasificacion c = disponible().clasificar(new PeticionClasificacion(
                "Clasifica si el pasaje apoya, contradice o es irrelevante para la afirmación: 'el centro tiene más tráfico peatonal que el barrio'.",
                "El conteo municipal de 2025 registró el triple de peatones por hora en el centro que en el barrio.",
                ETIQUETAS, TIEMPO_NORMAL));
        assertThat(ETIQUETAS).contains(c.etiqueta());
        assertThat(c.porQue()).isNotNull();
    }

    @Test
    void si_el_modelo_devuelve_json_invalido_lanza_respuesta_invalida() {
        assertThatThrownBy(() -> conJsonInvalido().clasificar(new PeticionClasificacion("Clasifica.", "texto", ETIQUETAS, TIEMPO_NORMAL)))
                .isInstanceOf(IaRespuestaInvalida.class);
    }

    @Test
    void los_embeddings_tienen_una_dimension_fija_de_1024_y_uno_por_texto() {
        List<float[]> vectores = disponible().incrustar(new PeticionEmbeddings(List.of("pan de masa madre", "junta de vecinos"), TIEMPO_NORMAL));
        assertThat(vectores).hasSize(2);
        assertThat(vectores.get(0)).hasSize(1024);
        assertThat(vectores.get(1)).hasSize(1024);
    }

    @Test
    void sin_ollama_los_embeddings_lanzan_no_disponible() {
        assertThatThrownBy(() -> noDisponible().incrustar(new PeticionEmbeddings(List.of("hola"), TIEMPO_NORMAL)))
                .isInstanceOf(IaNoDisponible.class);
    }
}
