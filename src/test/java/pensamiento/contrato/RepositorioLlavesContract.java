package pensamiento.contrato;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pensamiento.nucleo.LlaveDeAcceso;
import pensamiento.nucleo.Titular;
import pensamiento.nucleo.puertos.RepositorioLlaves;

/**
 * Contrato de las llaves de acceso y los códigos de recuperación: cada titular ve y toca solo lo suyo, el contador de
 * firmas se actualiza, un titular que no existe no guarda nada y un código de recuperación sirve una sola vez.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class RepositorioLlavesContract {

    protected static final Instant CREADA = Instant.parse("2026-10-14T15:00:00Z");

    protected abstract RepositorioLlaves crearSut();

    /** Una cuenta de una institución, ya sembrada. */
    protected abstract Titular cuentaA();

    /** Otra cuenta de la misma institución. */
    protected abstract Titular cuentaB();

    /** El operador del servicio, ya sembrado. */
    protected abstract Titular operador();

    private static LlaveDeAcceso llave(int n) {
        return new LlaveDeAcceso(new byte[] {1, 2, 3, (byte) n}, new byte[] {9, 9, (byte) n}, new byte[] {(byte) 0xa5, 1, 2, 3, 38},
                0, true, Set.of("internal", "hybrid"), false, false, new byte[] {7}, new byte[] {8}, "Portátil de la rectoría",
                CREADA.plusSeconds(n), CREADA.plusSeconds(n));
    }

    private byte[] unica() {
        UUID u = UUID.randomUUID();
        return java.nio.ByteBuffer.allocate(16).putLong(u.getMostSignificantBits()).putLong(u.getLeastSignificantBits()).array();
    }

    private LlaveDeAcceso llaveUnica(int n) {
        LlaveDeAcceso base = llave(n);
        return new LlaveDeAcceso(unica(), base.entidadUsuario(), base.clavePublicaCose(), 0, true, base.transportes(), false, false,
                base.atestacion(), base.datosCliente(), base.etiqueta(), base.creada(), base.usada());
    }

    @Test
    void una_llave_guardada_se_lee_igual_solo_por_su_titular() {
        RepositorioLlaves sut = crearSut();
        LlaveDeAcceso llave = llaveUnica(1);
        assertThat(sut.guardar(cuentaA(), llave)).isTrue();
        assertThat(sut.porCredencial(cuentaA(), llave.credencialId())).contains(llave);
        assertThat(sut.de(cuentaA())).containsExactly(llave);
        assertThat(sut.porCredencial(cuentaB(), llave.credencialId())).isEmpty();
        assertThat(sut.de(cuentaB())).isEmpty();
        assertThat(sut.porCredencial(operador(), llave.credencialId())).isEmpty();
    }

    @Test
    void usar_la_llave_actualiza_el_contador_y_el_ultimo_uso_sin_cambiar_lo_demas() {
        RepositorioLlaves sut = crearSut();
        LlaveDeAcceso llave = llaveUnica(2);
        sut.guardar(operador(), llave);
        assertThat(sut.guardar(operador(), llave.usada(7, CREADA.plusSeconds(3600)))).isTrue();
        LlaveDeAcceso leida = sut.porCredencial(operador(), llave.credencialId()).orElseThrow();
        assertThat(leida.firmas()).isEqualTo(7);
        assertThat(leida.usada()).isEqualTo(CREADA.plusSeconds(3600));
        assertThat(leida.creada()).isEqualTo(llave.creada());
        assertThat(leida.clavePublicaCose()).containsExactly(llave.clavePublicaCose());
    }

    @Test
    void nadie_pisa_ni_borra_la_llave_de_otro_titular() {
        RepositorioLlaves sut = crearSut();
        LlaveDeAcceso deA = llaveUnica(3);
        sut.guardar(cuentaA(), deA);
        assertThat(sut.guardar(cuentaB(), deA.usada(99, CREADA))).isFalse();
        assertThat(sut.borrar(cuentaB(), deA.credencialId())).isFalse();
        assertThat(sut.porCredencial(cuentaA(), deA.credencialId())).map(LlaveDeAcceso::firmas).contains(0L);
        assertThat(sut.borrar(cuentaA(), deA.credencialId())).isTrue();
        assertThat(sut.de(cuentaA())).isEmpty();
    }

    @Test
    void un_titular_que_no_existe_no_guarda_ni_ve_nada() {
        RepositorioLlaves sut = crearSut();
        Titular fantasma = Titular.cuenta(UUID.randomUUID(), cuentaA().institucion().orElseThrow());
        assertThat(sut.guardar(fantasma, llaveUnica(4))).isFalse();
        assertThat(sut.de(fantasma)).isEmpty();
        assertThat(sut.reemplazarRecuperacion(Titular.operador(UUID.randomUUID()), List.of("h"))).isFalse();
    }

    @Test
    void las_llaves_vienen_de_la_mas_antigua_a_la_mas_nueva() {
        RepositorioLlaves sut = crearSut();
        LlaveDeAcceso nueva = llaveUnica(9);
        LlaveDeAcceso vieja = llaveUnica(5);
        sut.guardar(cuentaB(), nueva);
        sut.guardar(cuentaB(), vieja);
        assertThat(sut.de(cuentaB())).containsExactly(vieja, nueva);
    }

    @Test
    void un_codigo_de_recuperacion_sirve_una_sola_vez_y_los_nuevos_reemplazan_a_los_vigentes() {
        RepositorioLlaves sut = crearSut();
        sut.reemplazarRecuperacion(cuentaA(), List.of("hash-1", "hash-2"));
        List<RepositorioLlaves.CodigoDeRecuperacion> vigentes = sut.recuperacionVigente(cuentaA());
        assertThat(vigentes).extracting(RepositorioLlaves.CodigoDeRecuperacion::hash).containsExactlyInAnyOrder("hash-1", "hash-2");
        assertThat(sut.recuperacionVigente(cuentaB())).isEmpty();

        UUID primero = vigentes.getFirst().id();
        assertThat(sut.usarRecuperacion(cuentaB(), primero, CREADA)).as("el código de A desde B").isFalse();
        assertThat(sut.usarRecuperacion(cuentaA(), primero, CREADA)).isTrue();
        assertThat(sut.usarRecuperacion(cuentaA(), primero, CREADA)).isFalse();
        assertThat(sut.recuperacionVigente(cuentaA())).hasSize(1);

        sut.reemplazarRecuperacion(cuentaA(), List.of("hash-3"));
        assertThat(sut.recuperacionVigente(cuentaA())).extracting(RepositorioLlaves.CodigoDeRecuperacion::hash).containsExactly("hash-3");
    }
}
