package pensamiento.contrato;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import pensamiento.nucleo.Evidencia;
import pensamiento.nucleo.EvidenciaGuardada;
import pensamiento.nucleo.FichaFuente;
import pensamiento.nucleo.Fuente;
import pensamiento.nucleo.puertos.RepositorioEvidencias;

/**
 * Contrato de las evidencias con la ficha de su fuente: lo guardado se lee igual (CRAAP, SIFT, tildes y ñ); el orden es
 * el de registro; otra persona no ve nada ni puede registrar sobre una afirmación ajena; insistir no duplica; una fuente
 * compartida por dos evidencias se actualiza una vez; quitar borra; la etiqueta del modelo sin adoptar se guarda tal cual;
 * si el documento de la biblioteca se borra, la ficha queda como "documento retirado".
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class RepositorioEvidenciasContract {

    /** Dos usuarios distintos de la misma institución, ya existentes en el backend. */
    public record Personas(UUID institucion, UUID usuarioA, UUID usuarioB) {
    }

    protected abstract Personas personas();

    protected abstract RepositorioEvidencias comoUsuario(UUID usuarioId);

    /** Una afirmación del usuario, producida por una ejecución ya guardada. */
    protected abstract UUID dadaUnaAfirmacion(UUID usuarioId, String texto);

    /** Un documento del usuario en su biblioteca. */
    protected abstract UUID dadoUnDocumento(UUID usuarioId, String nombre);

    /** Borra el documento de la biblioteca, como lo haría la persona. */
    protected abstract void borrarDocumento(UUID usuarioId, UUID documentoId);

    protected static FichaFuente fuente(String titulo, String grupo) {
        return new FichaFuente(UUID.randomUUID(), titulo, Optional.of("Oficina de movilidad"), Optional.of(LocalDate.of(2025, 3, 15)),
                Fuente.TipoFuente.PRIMARIA, Optional.empty(), Optional.of(grupo), true, true, Optional.of(20), Optional.of(new FichaFuente.Craap(4, 5, 4, 4, 3)),
                new FichaFuente.Sift("La oficina de movilidad del municipio.", "La cámara de comercio cita el mismo conteo.", "Conteo de marzo, días hábiles."),
                Optional.empty(), Optional.empty(), Optional.empty());
    }

    protected static EvidenciaGuardada evidencia(UUID afirmacion, FichaFuente fuente, String pasaje, Evidencia.Postura postura, int fuerza) {
        return new EvidenciaGuardada(UUID.randomUUID(), afirmacion, fuente, Optional.empty(), pasaje, postura, fuerza, Evidencia.EtiquetadaPor.USUARIO, true);
    }

    @Test
    void lo_guardado_se_lee_igual_con_su_ficha_de_fuente() {
        Personas p = personas();
        UUID afirmacion = dadaUnaAfirmacion(p.usuarioA(), "El centro tiene más tráfico peatonal que el barrio.");
        EvidenciaGuardada e = evidencia(afirmacion, fuente("Conteo peatonal del municipio, año de la señalización", "municipio"),
                "En el centro pasan en promedio 1.200 personas por hora; en el barrio, 300.", Evidencia.Postura.APOYA, 6);

        comoUsuario(p.usuarioA()).guardar(p.usuarioA(), p.institucion(), e);

        assertThat(comoUsuario(p.usuarioA()).deAfirmacion(p.usuarioA(), afirmacion)).containsExactly(e);
        assertThat(comoUsuario(p.usuarioA()).porId(p.usuarioA(), e.id())).contains(e);
        assertThat(comoUsuario(p.usuarioA()).deUsuario(p.usuarioA())).contains(e);
    }

    @Test
    void una_fuente_con_solo_el_puntaje_craap_y_sin_fecha_ni_grupo_tambien_se_lee_igual() {
        Personas p = personas();
        UUID afirmacion = dadaUnaAfirmacion(p.usuarioA(), "Los robos en la cuadra subieron este año.");
        FichaFuente minima = new FichaFuente(UUID.randomUUID(), "Resumen del presidente de la junta", Optional.empty(), Optional.empty(),
                Fuente.TipoFuente.SECUNDARIA, Optional.of(Fuente.DisenoEstudio.TESTIMONIO), Optional.empty(), false, false, Optional.of(12), Optional.empty(),
                FichaFuente.Sift.VACIA, Optional.empty(), Optional.empty(), Optional.empty());
        EvidenciaGuardada e = evidencia(afirmacion, minima, "Este año ya van el doble de robos que el anterior.", Evidencia.Postura.MATIZA, 0);

        comoUsuario(p.usuarioA()).guardar(p.usuarioA(), p.institucion(), e);

        assertThat(comoUsuario(p.usuarioA()).porId(p.usuarioA(), e.id())).contains(e);
    }

    @Test
    void una_afirmacion_sin_evidencias_o_que_no_existe_devuelve_vacio() {
        Personas p = personas();
        assertThat(comoUsuario(p.usuarioA()).deAfirmacion(p.usuarioA(), dadaUnaAfirmacion(p.usuarioA(), "Sin evidencias"))).isEmpty();
        assertThat(comoUsuario(p.usuarioA()).deAfirmacion(p.usuarioA(), UUID.randomUUID())).isEmpty();
        assertThat(comoUsuario(p.usuarioA()).porId(p.usuarioA(), UUID.randomUUID())).isEmpty();
    }

    @Test
    void las_evidencias_de_una_afirmacion_vienen_en_el_orden_en_que_se_registraron() {
        Personas p = personas();
        UUID afirmacion = dadaUnaAfirmacion(p.usuarioA(), "El colegio nuevo tiene mejor nivel en matemáticas que el actual.");
        EvidenciaGuardada primera = evidencia(afirmacion, fuente("Prueba nacional", "ministerio de educación"), "El colegio nuevo obtuvo 72.",
                Evidencia.Postura.APOYA, 6);
        EvidenciaGuardada segunda = evidencia(afirmacion, fuente("Estudio de la universidad regional", "universidad regional"),
                "Al comparar estudiantes con el mismo nivel de entrada, no hay diferencia.", Evidencia.Postura.CONTRADICE, 6);
        RepositorioEvidencias repo = comoUsuario(p.usuarioA());

        repo.guardar(p.usuarioA(), p.institucion(), primera);
        repo.guardar(p.usuarioA(), p.institucion(), segunda);

        assertThat(repo.deAfirmacion(p.usuarioA(), afirmacion)).containsExactly(primera, segunda);
    }

    @Test
    void otra_persona_no_ve_las_evidencias_ni_puede_quitarlas() {
        Personas p = personas();
        UUID afirmacion = dadaUnaAfirmacion(p.usuarioA(), "Las cámaras bajan los robos.");
        EvidenciaGuardada e = evidencia(afirmacion, fuente("Boletín de la policía", "policía"), "Los robos bajaron 12%.", Evidencia.Postura.APOYA, 5);
        comoUsuario(p.usuarioA()).guardar(p.usuarioA(), p.institucion(), e);
        RepositorioEvidencias comoB = comoUsuario(p.usuarioB());

        assertThat(comoB.deAfirmacion(p.usuarioB(), afirmacion)).isEmpty();
        assertThat(comoB.porId(p.usuarioB(), e.id())).isEmpty();
        assertThat(comoB.deUsuario(p.usuarioB())).extracting(EvidenciaGuardada::id).doesNotContain(e.id());
        assertThat(comoB.quitar(p.usuarioB(), e.id())).isFalse();
        assertThat(comoUsuario(p.usuarioA()).porId(p.usuarioA(), e.id())).contains(e);
    }

    @Test
    void nadie_registra_evidencias_sobre_la_afirmacion_de_otra_persona() {
        Personas p = personas();
        UUID deA = dadaUnaAfirmacion(p.usuarioA(), "Mamá estaría mejor con nosotros.");
        EvidenciaGuardada intrusa = evidencia(deA, fuente("Nota ajena", "otro grupo"), "Un pasaje.", Evidencia.Postura.CONTRADICE, 2);

        assertThatThrownBy(() -> comoUsuario(p.usuarioB()).guardar(p.usuarioB(), p.institucion(), intrusa)).isInstanceOf(IllegalArgumentException.class);
        assertThat(comoUsuario(p.usuarioA()).deAfirmacion(p.usuarioA(), deA)).isEmpty();
    }

    @Test
    void guardar_dos_veces_la_misma_evidencia_no_la_duplica_ni_la_cambia() {
        Personas p = personas();
        UUID afirmacion = dadaUnaAfirmacion(p.usuarioA(), "La harina nueva rinde más.");
        EvidenciaGuardada e = evidencia(afirmacion, fuente("Prueba en el horno", "panadería"), "Salieron 410 panes.", Evidencia.Postura.APOYA, 4);
        RepositorioEvidencias repo = comoUsuario(p.usuarioA());

        repo.guardar(p.usuarioA(), p.institucion(), e);
        repo.guardar(p.usuarioA(), p.institucion(), new EvidenciaGuardada(e.id(), afirmacion, e.fuente(), Optional.empty(), "Otro pasaje.",
                Evidencia.Postura.CONTRADICE, 1, Evidencia.EtiquetadaPor.USUARIO, true));

        assertThat(repo.deAfirmacion(p.usuarioA(), afirmacion)).containsExactly(e);
    }

    @Test
    void una_fuente_que_respalda_dos_evidencias_se_actualiza_para_las_dos() {
        Personas p = personas();
        UUID afirmacion = dadaUnaAfirmacion(p.usuarioA(), "El pan integral se agota antes del mediodía.");
        FichaFuente f = fuente("Encuesta a clientes", "panadería");
        EvidenciaGuardada e1 = evidencia(afirmacion, f, "Se agota antes del mediodía.", Evidencia.Postura.APOYA, 4);
        FichaFuente corregida = new FichaFuente(f.id(), "Encuesta a 60 clientes, julio", f.autor(), f.fecha(), f.tipo(), f.disenoEstudio(),
                f.grupoOrigen(), f.independiente(), f.accesoOriginal(), Optional.of(20), Optional.of(new FichaFuente.Craap(5, 5, 3, 3, 4)), f.sift(),
                Optional.empty(), Optional.empty(), Optional.empty());
        EvidenciaGuardada e2 = evidencia(afirmacion, corregida, "Hoy solo se hornea los martes y los viernes.", Evidencia.Postura.MATIZA, 4);
        RepositorioEvidencias repo = comoUsuario(p.usuarioA());

        repo.guardar(p.usuarioA(), p.institucion(), e1);
        repo.guardar(p.usuarioA(), p.institucion(), e2);

        assertThat(repo.deAfirmacion(p.usuarioA(), afirmacion)).extracting(EvidenciaGuardada::fuente).containsExactly(corregida, corregida);
    }

    @Test
    void quitar_una_evidencia_la_borra_y_una_segunda_vez_dice_que_no_estaba() {
        Personas p = personas();
        UUID afirmacion = dadaUnaAfirmacion(p.usuarioA(), "El precio de la harina va a subir.");
        EvidenciaGuardada e = evidencia(afirmacion, fuente("Audio del grupo", "proveedores"), "Va a subir 40%.", Evidencia.Postura.APOYA, 1);
        RepositorioEvidencias repo = comoUsuario(p.usuarioA());
        repo.guardar(p.usuarioA(), p.institucion(), e);

        assertThat(repo.quitar(p.usuarioA(), e.id())).isTrue();
        assertThat(repo.quitar(p.usuarioA(), e.id())).isFalse();
        assertThat(repo.deAfirmacion(p.usuarioA(), afirmacion)).isEmpty();
    }

    @Test
    void la_etiqueta_del_modelo_sin_adoptar_se_guarda_tal_cual() {
        Personas p = personas();
        UUID afirmacion = dadaUnaAfirmacion(p.usuarioA(), "Los robos en la cuadra subieron este año.");
        EvidenciaGuardada propuesta = new EvidenciaGuardada(UUID.randomUUID(), afirmacion, fuente("Boletín de la estación de policía", "policía"),
                Optional.empty(), "En el sector se denunciaron 12 robos.", Evidencia.Postura.APOYA, 6, Evidencia.EtiquetadaPor.MODELO, false);

        comoUsuario(p.usuarioA()).guardar(p.usuarioA(), p.institucion(), propuesta);

        assertThat(comoUsuario(p.usuarioA()).porId(p.usuarioA(), propuesta.id())).contains(propuesta)
                .hasValueSatisfying(e -> assertThat(e.comoEvidencia().cuenta()).isFalse());
    }

    @Test
    void si_el_documento_de_la_biblioteca_se_borra_la_ficha_queda_como_documento_retirado() {
        Personas p = personas();
        UUID afirmacion = dadaUnaAfirmacion(p.usuarioA(), "El centro tiene más tráfico.");
        UUID documento = dadoUnDocumento(p.usuarioA(), "conteo-peatonal-municipio-2025.pdf");
        FichaFuente f = fuente("Conteo peatonal del municipio", "municipio");
        FichaFuente deLaBiblioteca = new FichaFuente(f.id(), f.titulo(), f.autor(), f.fecha(), f.tipo(), f.disenoEstudio(), f.grupoOrigen(),
                f.independiente(), f.accesoOriginal(), f.puntajeCraap(), f.craap(), f.sift(), Optional.of(documento), Optional.of("conteo-peatonal-municipio-2025.pdf"),
                Optional.of(2));
        EvidenciaGuardada e = evidencia(afirmacion, deLaBiblioteca, "En el centro pasan 1.200 personas por hora.", Evidencia.Postura.APOYA, 6);
        RepositorioEvidencias repo = comoUsuario(p.usuarioA());
        repo.guardar(p.usuarioA(), p.institucion(), e);
        assertThat(repo.porId(p.usuarioA(), e.id())).hasValueSatisfying(x -> assertThat(x.fuente().documentoRetirado()).isFalse());

        borrarDocumento(p.usuarioA(), documento);

        assertThat(repo.porId(p.usuarioA(), e.id())).hasValueSatisfying(x -> {
            assertThat(x.fuente().documentoId()).isEmpty();
            assertThat(x.fuente().documentoNombre()).contains("conteo-peatonal-municipio-2025.pdf");
            assertThat(x.fuente().pagina()).contains(2);
            assertThat(x.fuente().documentoRetirado()).isTrue();
        });
    }
}
