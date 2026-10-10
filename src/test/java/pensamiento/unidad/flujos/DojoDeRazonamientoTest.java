package pensamiento.unidad.flujos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pensamiento.catalogo.CatalogoJson;
import pensamiento.flujos.DojoDeRazonamiento;
import pensamiento.nucleo.BancoDojo;
import pensamiento.nucleo.Competencia;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.NivelBloom;
import pensamiento.tecnicas.f8.EjecutorBloom;
import pensamiento.tecnicas.f8.EjecutorRepeticion;
import pensamiento.tecnicas.f8.ResultadoRepeticion;
import pensamiento.tecnicas.f8.TemaDojo;
import pensamiento.testutil.builders.Contextos;
import pensamiento.testutil.fakes.FakeReloj;
import pensamiento.testutil.fakes.FakeRepositorioDojo;

/**
 * El Dojo de punta a punta con el ejemplo de docs/dojo.md: tres retos por día, dos aciertos para dominar cada nivel, tema
 * falacias, del miércoles 7 al lunes 12 de octubre de 2026 con el reloj adelantado. Cada valor esperado está escrito a mano en
 * ese documento; el banco es el real del catálogo.
 */
class DojoDeRazonamientoTest {

    private static final BancoDojo BANCO = new CatalogoJson().bancoDojo();
    private static final UUID YO = Contextos.DUENA_DE_LA_PANADERIA;
    private static final UUID INSTITUCION = Contextos.INSTITUCION;
    private static final DojoDeRazonamiento.Configuracion CONFIG = new DojoDeRazonamiento.Configuracion(
            new EjecutorBloom.Config(List.of(NivelBloom.values()), true, 2), new EjecutorRepeticion.Config(3, "2.5"));
    private static final Optional<TemaDojo> FALACIAS = Optional.of(TemaDojo.FALACIAS);

    private final FakeRepositorioDojo repositorio = new FakeRepositorioDojo();
    private final FakeReloj reloj = new FakeReloj();
    private final DojoDeRazonamiento dojo = new DojoDeRazonamiento(repositorio, BANCO, reloj);

    private DojoDeRazonamiento.RetoElegido siguiente() {
        DojoDeRazonamiento.Pantalla p = dojo.pantalla(YO, FALACIAS, Optional.empty(), CONFIG);
        assertThat(p.mensaje()).as("debería haber reto").isEmpty();
        return p.reto().orElseThrow();
    }

    /** Responde el reto que sale: bien (la opción correcta o la respuesta modelo) o mal (otra opción o el texto original). */
    private DojoDeRazonamiento.Respuesta responder(String retoEsperado, boolean bien) {
        DojoDeRazonamiento.RetoElegido elegido = siguiente();
        assertThat(elegido.reto().id()).isEqualTo(retoEsperado);
        return dojo.responder(YO, INSTITUCION, elegido.reto().id(), respuesta(elegido.reto(), bien), UUID.randomUUID().toString(), CONFIG);
    }

    private static String respuesta(BancoDojo.Reto reto, boolean bien) {
        if (reto.esDeEscribir()) {
            return bien ? reto.respuestaModelo() : reto.texto();
        }
        return bien ? reto.correcta() : reto.opciones().stream().map(BancoDojo.Opcion::id).filter(id -> !id.equals(reto.correcta())).findFirst().orElseThrow();
    }

    private void manana() {
        reloj.avanzar(Duration.ofDays(1));
    }

    @Test
    void una_semana_de_dojo_programa_cada_repaso_y_sube_de_nivel_como_dice_el_documento() {
        // Miércoles 7: tres conceptos nuevos en identificar.
        DojoDeRazonamiento.Pantalla primera = dojo.pantalla(YO, FALACIAS, Optional.empty(), CONFIG);
        assertThat(primera.nivel()).isEqualTo(NivelBloom.IDENTIFICAR);
        assertThat(primera.reto().orElseThrow().repaso()).isFalse();
        DojoDeRazonamiento.Respuesta r1 = responder("generalizacion-i1", true);
        assertThat(r1.acierto()).isTrue();
        assertThat(r1.siguienteRepaso()).isEqualTo("Siguiente repaso: mañana.");
        assertThat(r1.progreso()).isEqualTo("T13 · Falacias como esquemas fallidos: nivel identificar · 1 de 2 aciertos.");
        DojoDeRazonamiento.Respuesta r2 = responder("ad_hominem-i1", false);
        assertThat(r2.acierto()).isFalse();
        assertThat(r2.siguienteRepaso()).isEqualTo("Siguiente repaso: mañana.");
        DojoDeRazonamiento.Respuesta r3 = responder("falso_dilema-i1", true);
        assertThat(r3.dominaste()).contains("Dominaste «identificar»: se abre «analizar».");
        DojoDeRazonamiento.Pantalla fin = dojo.pantalla(YO, FALACIAS, Optional.empty(), CONFIG);
        assertThat(fin.mensaje()).contains("Por hoy terminaste: hiciste 3 retos.");
        assertThat(fin.racha()).isEqualTo(1);

        // Jueves 8: los tres tocan, en el orden del banco; nivel analizar.
        manana();
        assertThat(siguiente().repaso()).isTrue();
        assertThat(responder("generalizacion-a1", true).siguienteRepaso()).isEqualTo("Siguiente repaso: en 6 días.");
        DojoDeRazonamiento.Respuesta jueves2 = responder("ad_hominem-a1", true);
        assertThat(jueves2.siguienteRepaso()).isEqualTo("Siguiente repaso: mañana.");
        assertThat(jueves2.dominaste()).contains("Dominaste «analizar»: se abre «evaluar».");
        assertThat(responder("falso_dilema-e1", false).siguienteRepaso()).isEqualTo("Siguiente repaso: mañana.");
        assertThat(dojo.pantalla(YO, FALACIAS, Optional.empty(), CONFIG).racha()).isEqualTo(2);

        // Viernes 9: ataque a la persona y falso dilema en evaluar; después, un concepto nuevo ya en crear.
        manana();
        assertThat(responder("ad_hominem-e1", true).siguienteRepaso()).isEqualTo("Siguiente repaso: en 6 días.");
        DojoDeRazonamiento.Respuesta viernes2 = responder("falso_dilema-e1", true);
        assertThat(viernes2.siguienteRepaso()).isEqualTo("Siguiente repaso: mañana.");
        assertThat(viernes2.dominaste()).contains("Dominaste «evaluar»: se abre «crear».");
        DojoDeRazonamiento.Respuesta viernes3 = responder("pendiente_resbaladiza-c1", true);
        assertThat(viernes3.acierto()).isTrue();
        assertThat(viernes3.chequeos()).isNotEmpty().allSatisfy(c -> assertThat(c.cumple()).isTrue());
        assertThat(viernes3.siguienteRepaso()).isEqualTo("Siguiente repaso: mañana.");

        // Sábado 10: los dos repasos de crear y un concepto nuevo respondido con la falacia sin quitar.
        manana();
        DojoDeRazonamiento.Respuesta sabado1 = responder("falso_dilema-c1", true);
        assertThat(sabado1.siguienteRepaso()).isEqualTo("Siguiente repaso: en 6 días.");
        assertThat(sabado1.dominaste()).contains("Dominaste los 4 niveles activos.");
        assertThat(responder("pendiente_resbaladiza-c1", true).siguienteRepaso()).isEqualTo("Siguiente repaso: en 6 días.");
        DojoDeRazonamiento.Respuesta sabado3 = responder("autoridad-c1", false);
        assertThat(sabado3.acierto()).isFalse();
        assertThat(sabado3.chequeos()).extracting(DojoDeRazonamiento.ChequeoRevisado::cumple).contains(false);
        assertThat(sabado3.siguienteRepaso()).isEqualTo("Siguiente repaso: mañana.");
        assertThat(sabado3.racha()).isEqualTo(4);

        // Domingo 11 no entra. Lunes 12: la racha se cortó, toca autoridad y el calendario de la semana.
        manana();
        manana();
        assertThat(dojo.retosParaHoy(YO, CONFIG)).isEqualTo(3);
        DojoDeRazonamiento.Pantalla lunes = dojo.pantalla(YO, FALACIAS, Optional.empty(), CONFIG);
        assertThat(lunes.racha()).isZero();
        assertThat(lunes.reto().orElseThrow().reto().id()).isEqualTo("autoridad-c1");
        assertThat(lunes.reto().orElseThrow().repaso()).isTrue();
        ResultadoRepeticion calendario = dojo.progreso(YO, CONFIG).calendario();
        assertThat(calendario.calendario()).extracting(ResultadoRepeticion.Dia::nombre)
                .containsExactly("lun 12", "mar 13", "mié 14", "jue 15", "vie 16", "sáb 17", "dom 18");
        assertThat(calendario.calendario()).extracting(ResultadoRepeticion.Dia::repasos).containsExactly(1, 0, 1, 1, 2, 0, 0);

        // La competencia del tema quedó proyectada con el último intento.
        assertThat(repositorio.competencias(YO)).containsExactly(new Competencia(IdTecnica.de("T13"), NivelBloom.CREAR, 12, 9,
                java.time.Instant.parse("2026-10-10T15:00:00Z")));
    }

    @Test
    void responder_dos_veces_el_mismo_formulario_guarda_un_solo_intento_y_da_la_misma_respuesta() {
        String clave = UUID.randomUUID().toString();
        DojoDeRazonamiento.Respuesta primera = dojo.responder(YO, INSTITUCION, "generalizacion-i1", "b", clave, CONFIG);
        DojoDeRazonamiento.Respuesta segunda = dojo.responder(YO, INSTITUCION, "generalizacion-i1", "a", clave, CONFIG);

        assertThat(repositorio.intentos(YO)).hasSize(1);
        assertThat(segunda.respuesta()).isEqualTo(primera.respuesta());
        assertThat(segunda.acierto()).isEqualTo(primera.acierto());
        assertThat(segunda.siguienteRepaso()).isEqualTo(primera.siguienteRepaso());
    }

    @Test
    void sin_opcion_o_sin_texto_no_se_califica() {
        assertThatThrownBy(() -> dojo.responder(YO, INSTITUCION, "generalizacion-i1", " ", "k1", CONFIG))
                .isInstanceOf(DojoDeRazonamiento.NoPermitido.class).hasMessage("Elige una opción.");
        assertThatThrownBy(() -> dojo.responder(YO, INSTITUCION, "generalizacion-i1", "z", "k2", CONFIG))
                .isInstanceOf(DojoDeRazonamiento.NoPermitido.class).hasMessage("Elige una opción.");
        assertThatThrownBy(() -> dojo.responder(YO, INSTITUCION, "generalizacion-c1", "", "k3", CONFIG))
                .isInstanceOf(DojoDeRazonamiento.NoPermitido.class).hasMessage("Escribe tu versión antes de responder.");
        assertThatThrownBy(() -> dojo.responder(YO, INSTITUCION, "no-existe", "a", "k4", CONFIG)).isInstanceOf(DojoDeRazonamiento.NoEncontrado.class);
        assertThat(repositorio.intentos(YO)).isEmpty();
    }

    @Test
    void con_avance_manual_sale_el_nivel_que_la_persona_elige() {
        var manual = new DojoDeRazonamiento.Configuracion(new EjecutorBloom.Config(List.of(NivelBloom.values()), false, 10),
                new EjecutorRepeticion.Config(10, "2.5"));

        DojoDeRazonamiento.Pantalla p = dojo.pantalla(YO, Optional.of(TemaDojo.SESGOS), Optional.of(NivelBloom.EVALUAR), manual);

        assertThat(p.avanceManual()).isTrue();
        assertThat(p.reto().orElseThrow().reto().id()).isEqualTo("anclaje-e1");
    }
}
