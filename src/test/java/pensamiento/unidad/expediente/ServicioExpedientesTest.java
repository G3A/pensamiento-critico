package pensamiento.unidad.expediente;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pensamiento.catalogo.CatalogoJson;
import pensamiento.catalogo.MapeadorJson;
import pensamiento.expediente.ServicioExpedientes;
import pensamiento.nucleo.Ejecucion;
import pensamiento.nucleo.Ejemplo;
import pensamiento.nucleo.Expediente;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.RelacionTecnica;
import pensamiento.nucleo.Resultado;
import pensamiento.nucleo.Uuid7;
import pensamiento.nucleo.puertos.RegistroAuditoria;
import pensamiento.tecnicas.f5.ConfigAch;
import pensamiento.tecnicas.f5.EjecutorAch;
import pensamiento.tecnicas.f5.EntradaAch;
import pensamiento.tecnicas.f5.ResultadoAch;
import pensamiento.testutil.builders.Contextos;
import pensamiento.testutil.fakes.FakeRegistroAuditoria;
import pensamiento.testutil.fakes.FakeReloj;
import pensamiento.testutil.fakes.FakeRepositorioEjecucion;
import pensamiento.testutil.fakes.FakeRepositorioExpediente;
import pensamiento.testutil.fakes.FakeRepositorioTecnica;

/** Collaboration test del Expediente (RF-07): asociar, línea de tiempo, resumen por familia, qué falta y borrado lógico. */
class ServicioExpedientesTest {

    private static final UUID INSTITUCION = Contextos.INSTITUCION;
    private static final UUID DUENA = Contextos.DUENA_DE_LA_PANADERIA;
    private static final UUID OTRA_PERSONA = UUID.fromString("00000000-0000-7000-8000-0000000000c3");

    private final FakeRepositorioExpediente expedientes = new FakeRepositorioExpediente();
    private final FakeRepositorioEjecucion ejecuciones = new FakeRepositorioEjecucion();
    private final FakeRepositorioTecnica tecnicas = new FakeRepositorioTecnica();
    private final FakeRegistroAuditoria auditoria = new FakeRegistroAuditoria();
    private final FakeReloj reloj = new FakeReloj();
    private final ServicioExpedientes servicio = new ServicioExpedientes(expedientes, ejecuciones, tecnicas, auditoria, reloj);

    @BeforeEach
    void catalogoReal() {
        CatalogoJson catalogo = new CatalogoJson();
        catalogo.familias().forEach(tecnicas::agregarFamilia);
        catalogo.tecnicas().forEach(tecnicas::agregar);
        catalogo.relaciones().forEach(r -> tecnicas.agregarRelacion(new RelacionTecnica(IdTecnica.de(r.origen()), IdTecnica.de(r.destino()),
                RelacionTecnica.Tipo.valueOf(r.tipo().toUpperCase()))));
    }

    private Ejecucion guardarLasVentasDeLosSabados(UUID usuario, String clave) {
        Ejemplo ventas = new CatalogoJson().ejemplosDe(EjecutorAch.ID).getFirst();
        Resultado<ResultadoAch> r = new EjecutorAch().ejecutar(MapeadorJson.leer(ventas.config(), ConfigAch.class),
                MapeadorJson.leer(ventas.datos(), EntradaAch.class), Contextos.sinIa(usuario));
        Ejecucion e = new Ejecucion(Uuid7.en(reloj.ahora()), usuario, INSTITUCION, EjecutorAch.ID, 1, Optional.empty(), ventas.config(),
                ventas.datos(), MapeadorJson.escribir(r.valor()), r.resumen(), Optional.empty(), clave, reloj.ahora());
        return ejecuciones.guardar(e, r.afirmaciones(), r.pendientes());
    }

    @Test
    void una_ejecucion_asociada_aparece_en_la_linea_de_tiempo_con_su_pendiente_en_que_falta_para_cerrar() {
        Expediente sucursal = servicio.crear(DUENA, INSTITUCION, "La segunda sucursal de la panadería");
        Ejecucion ventas = guardarLasVentasDeLosSabados(DUENA, "ventas");

        assertThat(servicio.asociar(DUENA, INSTITUCION, ventas.id(), Optional.of(sucursal.id()), Optional.empty())).contains(Optional.of(sucursal));

        ServicioExpedientes.Vista vista = servicio.vista(DUENA, sucursal.id()).orElseThrow();
        assertThat(vista.lineaDeTiempo()).extracting(Ejecucion::id).containsExactly(ventas.id());
        assertThat(vista.faltaParaCerrar()).extracting(p -> p.pendiente().descripcion())
                .containsExactly("Verificar E1: La baja es solo los sábados (hipótesis H1: Abrió una feria a dos cuadras los sábados)");
        assertThat(vista.familiasConEjecuciones()).isEqualTo(1);
    }

    @Test
    void el_resumen_por_familia_cubre_las_ocho_y_sugiere_por_relacion_tipada() {
        Expediente sucursal = servicio.crear(DUENA, INSTITUCION, "La segunda sucursal de la panadería");
        Ejecucion ventas = guardarLasVentasDeLosSabados(DUENA, "ventas");
        servicio.asociar(DUENA, INSTITUCION, ventas.id(), Optional.of(sucursal.id()), Optional.empty());

        List<ServicioExpedientes.PorFamilia> porFamilia = servicio.vista(DUENA, sucursal.id()).orElseThrow().porFamilia();

        assertThat(porFamilia).extracting(pf -> pf.familia().codigo()).containsExactly("F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8");
        ServicioExpedientes.PorFamilia f5 = porFamilia.get(4);
        assertThat(f5.ejecuciones()).hasSize(1);
        assertThat(f5.pendientes()).isEqualTo(1);
        assertThat(f5.sugerida()).isEmpty();
        // F1 no tiene ejecuciones: sugiere su primera técnica, T01 · Mapeo de argumentos.
        assertThat(porFamilia.getFirst().sugerida()).map(t -> t.id().valor()).contains("T01");
    }

    @Test
    void asociar_a_un_expediente_nuevo_lo_crea_y_queda_en_auditoria() {
        Ejecucion ventas = guardarLasVentasDeLosSabados(DUENA, "ventas");

        Optional<Expediente> creado = servicio.asociar(DUENA, INSTITUCION, ventas.id(), Optional.empty(), Optional.of("Precios de la panadería"))
                .orElseThrow();

        assertThat(creado).map(Expediente::nombre).contains("Precios de la panadería");
        assertThat(ejecuciones.porId(DUENA, ventas.id()).orElseThrow().expedienteId()).isEqualTo(creado.map(Expediente::id));
        assertThat(auditoria.deUsuario(DUENA)).extracting(RegistroAuditoria.Evento::accion).containsExactly(RegistroAuditoria.Accion.CREAR);
    }

    @Test
    void nadie_asocia_ejecuciones_ni_expedientes_ajenos() {
        Expediente deLaDuena = servicio.crear(DUENA, INSTITUCION, "La segunda sucursal de la panadería");
        Ejecucion ventas = guardarLasVentasDeLosSabados(DUENA, "ventas");
        Ejecucion deOtra = guardarLasVentasDeLosSabados(OTRA_PERSONA, "otra");

        assertThat(servicio.asociar(OTRA_PERSONA, INSTITUCION, ventas.id(), Optional.empty(), Optional.empty())).isEmpty();
        assertThat(servicio.asociar(OTRA_PERSONA, INSTITUCION, deOtra.id(), Optional.of(deLaDuena.id()), Optional.empty())).isEmpty();
        assertThat(ejecuciones.porId(OTRA_PERSONA, deOtra.id()).orElseThrow().expedienteId()).isEmpty();
    }

    @Test
    void borrar_un_expediente_desasocia_sus_ejecuciones_que_siguen_en_el_historial() {
        Expediente sucursal = servicio.crear(DUENA, INSTITUCION, "La segunda sucursal de la panadería");
        Ejecucion ventas = guardarLasVentasDeLosSabados(DUENA, "ventas");
        servicio.asociar(DUENA, INSTITUCION, ventas.id(), Optional.of(sucursal.id()), Optional.empty());

        assertThat(servicio.borrar(DUENA, INSTITUCION, sucursal.id())).isTrue();

        assertThat(servicio.vista(DUENA, sucursal.id())).isEmpty();
        assertThat(ejecuciones.porId(DUENA, ventas.id()).orElseThrow().expedienteId()).isEmpty();
        assertThat(ejecuciones.porTecnica(DUENA, EjecutorAch.ID)).extracting(Ejecucion::id).containsExactly(ventas.id());
        assertThat(auditoria.deUsuario(DUENA)).extracting(RegistroAuditoria.Evento::accion)
                .containsExactlyInAnyOrder(RegistroAuditoria.Accion.CREAR, RegistroAuditoria.Accion.BORRAR);
        assertThat(servicio.borrar(DUENA, INSTITUCION, sucursal.id())).isFalse();
    }

    @Test
    void un_nombre_vacio_o_demasiado_largo_no_crea_expediente() {
        assertThatThrownBy(() -> servicio.crear(DUENA, INSTITUCION, "   ")).isInstanceOf(ServicioExpedientes.NombreInvalido.class);
        assertThatThrownBy(() -> servicio.crear(DUENA, INSTITUCION, "x".repeat(121))).isInstanceOf(ServicioExpedientes.NombreInvalido.class);
        assertThat(expedientes.deUsuario(DUENA)).isEmpty();
    }
}
