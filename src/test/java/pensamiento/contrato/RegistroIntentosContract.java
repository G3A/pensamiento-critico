package pensamiento.contrato;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pensamiento.nucleo.puertos.RegistroIntentos;
import pensamiento.nucleo.puertos.RegistroIntentos.Espera;
import pensamiento.nucleo.puertos.RegistroIntentos.Estado;

/** Contrato de los contadores de intentos: guardar y leer por institución y clave, el tope global sin institución, esperas. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class RegistroIntentosContract {

    protected static final Instant AHORA = Instant.parse("2026-10-14T15:00:00Z");

    protected abstract RegistroIntentos crearSut();

    protected abstract UUID institucion();

    protected abstract UUID otraInstitucion();

    /** Una clave que ninguna otra corrida usa (el real comparte la tabla del tope global). */
    protected String unica(String prefijo) {
        return prefijo + UUID.randomUUID();
    }

    @Test
    void una_clave_sin_fallos_no_tiene_estado() {
        assertThat(crearSut().leer(Optional.of(institucion()), unica("cuenta:"))).isEmpty();
    }

    @Test
    void lo_guardado_se_lee_y_se_reemplaza() {
        RegistroIntentos sut = crearSut();
        String clave = unica("cuenta:");
        sut.guardar(Optional.of(institucion()), clave, new Estado(1, AHORA, Optional.empty()));
        sut.guardar(Optional.of(institucion()), clave, new Estado(5, AHORA, Optional.of(AHORA.plusSeconds(30))));
        assertThat(sut.leer(Optional.of(institucion()), clave)).contains(new Estado(5, AHORA, Optional.of(AHORA.plusSeconds(30))));
    }

    @Test
    void la_misma_clave_en_otra_institucion_o_global_es_otro_contador() {
        RegistroIntentos sut = crearSut();
        String clave = unica("ip:");
        sut.guardar(Optional.of(institucion()), clave, new Estado(3, AHORA, Optional.empty()));
        sut.guardar(Optional.empty(), clave, new Estado(7, AHORA, Optional.empty()));
        assertThat(sut.leer(Optional.of(otraInstitucion()), clave)).isEmpty();
        assertThat(sut.leer(Optional.empty(), clave)).map(Estado::fallos).contains(7);
        assertThat(sut.leer(Optional.of(institucion()), clave)).map(Estado::fallos).contains(3);
        sut.borrar(Optional.empty(), clave);
        assertThat(sut.leer(Optional.empty(), clave)).isEmpty();
        assertThat(sut.leer(Optional.of(institucion()), clave)).isPresent();
    }

    @Test
    void borrar_deja_la_clave_sin_estado() {
        RegistroIntentos sut = crearSut();
        String clave = unica("cuenta:");
        sut.guardar(Optional.of(institucion()), clave, new Estado(6, AHORA, Optional.of(AHORA.plusSeconds(60))));
        sut.borrar(Optional.of(institucion()), clave);
        assertThat(sut.leer(Optional.of(institucion()), clave)).isEmpty();
    }

    @Test
    void esperas_trae_solo_las_cuentas_que_esperan_todavia_en_esa_institucion() {
        RegistroIntentos sut = crearSut();
        sut.guardar(Optional.of(institucion()), "cuenta:beto", new Estado(5, AHORA, Optional.of(AHORA.plusSeconds(30))));
        sut.guardar(Optional.of(institucion()), "cuenta:ana", new Estado(8, AHORA, Optional.of(AHORA.plusSeconds(900))));
        sut.guardar(Optional.of(institucion()), "cuenta:caro", new Estado(5, AHORA, Optional.of(AHORA.minusSeconds(1))));
        sut.guardar(Optional.of(institucion()), "cuenta:dani", new Estado(2, AHORA, Optional.empty()));
        sut.guardar(Optional.of(institucion()), "ip:10.0.0.1", new Estado(300, AHORA, Optional.of(AHORA.plusSeconds(600))));
        sut.guardar(Optional.of(otraInstitucion()), "cuenta:eva", new Estado(5, AHORA, Optional.of(AHORA.plusSeconds(30))));
        assertThat(sut.esperas(institucion(), AHORA)).containsExactly(
                new Espera("cuenta:ana", AHORA.plusSeconds(900)), new Espera("cuenta:beto", AHORA.plusSeconds(30)));
    }
}
