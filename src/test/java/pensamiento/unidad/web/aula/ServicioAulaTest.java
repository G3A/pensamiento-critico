package pensamiento.unidad.web.aula;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import java.util.List;

import pensamiento.nucleo.ClaseDeAula;
import pensamiento.nucleo.Grupo;
import pensamiento.nucleo.Usuario;
import pensamiento.nucleo.puertos.RegistroAuditoria;
import pensamiento.nucleo.puertos.RepositorioAula;
import pensamiento.nucleo.puertos.RepositorioUsuarios;
import pensamiento.nucleo.puertos.RepositorioAula.Actor;
import pensamiento.testutil.TransaccionesSinBase;
import pensamiento.testutil.fakes.FakeRegistroAuditoria;
import pensamiento.testutil.fakes.FakeReloj;
import pensamiento.testutil.fakes.FakeRepositorioAula;
import pensamiento.testutil.fakes.FakeRepositorioDojo;
import pensamiento.testutil.fakes.FakeRepositorioUsuarios;
import pensamiento.web.ObjetoNoEncontrado;
import pensamiento.web.aula.ListaDeEstudiantes;
import pensamiento.web.aula.ServicioAula;
import pensamiento.web.usuarios.CodigoDeUnUso;
import pensamiento.web.usuarios.ServicioUsuarios;

/**
 * Collaboration test de ServicioAula, grupos (hito 10, entregable 2): el administrador crea grupos con nombre único y les
 * asigna un docente activo; el docente ve solo los suyos (varios); un grupo ajeno es 404; todo queda en la auditoría.
 */
class ServicioAulaTest {

    private final UUID colegio = UUID.fromString("0192f000-0000-7000-8000-00000000c01e");
    private final FakeRepositorioUsuarios usuarios = new FakeRepositorioUsuarios();
    private final FakeRegistroAuditoria auditoria = new FakeRegistroAuditoria();
    private final FakeRepositorioAula aula = new FakeRepositorioAula(usuarios, new FakeRepositorioDojo(), auditoria);
    private final FakeReloj reloj = new FakeReloj();
    private final java.util.List<UUID> sesionesCerradas = new java.util.ArrayList<>();
    /** Azar fijo para los códigos: el primero sale "789ABCDE", el segundo "FGHJKMNP" (como en ServicioUsuariosTest). */
    private final java.util.concurrent.atomic.AtomicInteger siguiente = new java.util.concurrent.atomic.AtomicInteger(7);
    private final ServicioAula servicio = new ServicioAula(aula, usuarios, auditoria, reloj, new TransaccionesSinBase(),
            new CodigoDeUnUso(n -> siguiente.getAndIncrement() % n), sesionesCerradas::add);

    private Actor cuenta(String nombre, Usuario.RolGlobal rol) {
        Usuario u = usuarios.crear(Usuario.nueva(UUID.randomUUID(), colegio, nombre, rol)).orElseThrow();
        return new Actor(u.id(), colegio, rol);
    }

    @Test
    void el_administrador_crea_un_grupo_con_su_docente_y_queda_en_la_auditoria() {
        Actor rectora = cuenta("Rectora", Usuario.RolGlobal.ADMINISTRADOR);
        Actor profe = cuenta("Profe Ramírez", Usuario.RolGlobal.DOCENTE);

        Grupo g = servicio.crearGrupo(rectora, "  10.º A · Filosofía  ", Optional.of(profe.id()));

        assertThat(g.nombre()).isEqualTo("10.º A · Filosofía");
        assertThat(g.docenteId()).contains(profe.id());
        assertThat(auditoria.todos()).extracting(RegistroAuditoria.Evento::accion).containsExactly(RegistroAuditoria.Accion.CREAR_GRUPO);
    }

    @Test
    void un_docente_tiene_varios_grupos_y_ve_solo_los_suyos() {
        Actor rectora = cuenta("Rectora", Usuario.RolGlobal.ADMINISTRADOR);
        Actor profe = cuenta("Profe Ramírez", Usuario.RolGlobal.DOCENTE);
        Actor otra = cuenta("Profe Díaz", Usuario.RolGlobal.DOCENTE);
        for (String nombre : new String[] {"10.º A", "10.º B", "11.º A", "11.º B"}) {
            servicio.crearGrupo(rectora, nombre, Optional.of(profe.id()));
        }
        Grupo ajeno = servicio.crearGrupo(rectora, "9.º C", Optional.of(otra.id()));

        assertThat(servicio.grupos(profe)).extracting(Grupo::nombre).containsExactly("10.º A", "10.º B", "11.º A", "11.º B");
        assertThat(servicio.grupos(rectora)).hasSize(5);
        assertThatThrownBy(() -> servicio.grupo(profe, ajeno.id())).isInstanceOf(ObjetoNoEncontrado.class);
        assertThatThrownBy(() -> servicio.miembros(profe, ajeno.id())).isInstanceOf(ObjetoNoEncontrado.class);
    }

    @Test
    void un_nombre_repetido_o_vacio_y_un_docente_que_no_es_docente_se_rechazan_con_su_mensaje() {
        Actor rectora = cuenta("Rectora", Usuario.RolGlobal.ADMINISTRADOR);
        Actor persona = cuenta("Ana", Usuario.RolGlobal.PERSONA);
        servicio.crearGrupo(rectora, "10.º A", Optional.empty());

        assertThatThrownBy(() -> servicio.crearGrupo(rectora, "10.º a", Optional.empty())).isInstanceOf(ServicioUsuarios.DatosInvalidos.class)
                .hasMessageContaining("Ya hay un grupo llamado «10.º a»");
        assertThatThrownBy(() -> servicio.crearGrupo(rectora, "  ", Optional.empty())).isInstanceOf(ServicioUsuarios.DatosInvalidos.class)
                .hasMessageContaining("obligatorio");
        assertThatThrownBy(() -> servicio.crearGrupo(rectora, "11.º B", Optional.of(persona.id()))).isInstanceOf(ServicioUsuarios.DatosInvalidos.class)
                .hasMessage("«Ana» no es una cuenta de docente activa.");
        assertThat(servicio.grupos(rectora)).extracting(Grupo::nombre).containsExactly("10.º A");
    }

    @Test
    void solo_el_administrador_crea_grupos_y_cambia_su_docente() {
        Actor rectora = cuenta("Rectora", Usuario.RolGlobal.ADMINISTRADOR);
        Actor profe = cuenta("Profe Ramírez", Usuario.RolGlobal.DOCENTE);
        Actor otra = cuenta("Profe Díaz", Usuario.RolGlobal.DOCENTE);
        Grupo g = servicio.crearGrupo(rectora, "10.º A", Optional.of(profe.id()));

        assertThatThrownBy(() -> servicio.crearGrupo(profe, "Mío", Optional.of(profe.id()))).isInstanceOf(ObjetoNoEncontrado.class);
        assertThatThrownBy(() -> servicio.asignarDocente(profe, g.id(), Optional.of(otra.id()))).isInstanceOf(ObjetoNoEncontrado.class);

        servicio.asignarDocente(rectora, g.id(), Optional.of(otra.id()));

        assertThat(servicio.grupos(otra)).extracting(Grupo::id).containsExactly(g.id());
        assertThat(servicio.grupos(profe)).isEmpty();
        assertThat(auditoria.todos()).extracting(RegistroAuditoria.Evento::accion)
                .containsExactly(RegistroAuditoria.Accion.CREAR_GRUPO, RegistroAuditoria.Accion.CAMBIAR_GRUPO);
    }

    @Test
    void la_vista_previa_marca_repetidas_invalidas_y_las_que_ya_existen_sin_crear_nada() {
        Actor rectora = cuenta("Rectora", Usuario.RolGlobal.ADMINISTRADOR);
        Actor profe = cuenta("Profe Ramírez", Usuario.RolGlobal.DOCENTE);
        cuenta("Luis Gómez", Usuario.RolGlobal.PERSONA);
        Grupo g = servicio.crearGrupo(rectora, "10.º A", Optional.of(profe.id()));

        ServicioAula.VistaPrevia previa = servicio.vistaPrevia(profe, g.id(), "Ana Pérez\nana perez\nLuis Gómez\n" + "x".repeat(61) + "\nSofía Ruiz");

        assertThat(previa.filas()).extracting(ListaDeEstudiantes.Fila::estado).containsExactly(ListaDeEstudiantes.Estado.NUEVA,
                ListaDeEstudiantes.Estado.REPETIDA_EN_LA_LISTA, ListaDeEstudiantes.Estado.YA_EXISTE, ListaDeEstudiantes.Estado.INVALIDA,
                ListaDeEstudiantes.Estado.NUEVA);
        assertThat(previa.nuevas()).isEqualTo(2);
        assertThat(servicio.miembros(profe, g.id())).isEmpty();
    }

    @Test
    void el_alta_en_lote_crea_las_nuevas_en_el_grupo_con_su_codigo_guardado_como_hash() {
        Actor rectora = cuenta("Rectora", Usuario.RolGlobal.ADMINISTRADOR);
        Actor profe = cuenta("Profe Ramírez", Usuario.RolGlobal.DOCENTE);
        cuenta("Luis Gómez", Usuario.RolGlobal.PERSONA);
        Grupo g = servicio.crearGrupo(rectora, "10.º A", Optional.of(profe.id()));

        ServicioAula.AltaEnLote alta = servicio.altaEnLote(profe, g.id(), "Ana Pérez\nana perez\nLuis Gómez\nSofía Ruiz");

        assertThat(alta.creadas()).extracting(c -> c.cuenta().nombre()).containsExactly("Ana Pérez", "Sofía Ruiz");
        assertThat(alta.creadas()).extracting(ServicioUsuarios.CodigoEmitido::legible).containsExactly("789A-BCDE", "FGHJ-KMNP");
        assertThat(alta.sinCrear()).extracting(ListaDeEstudiantes.Fila::nombre).containsExactly("ana perez", "Luis Gómez");
        assertThat(servicio.miembros(profe, g.id())).extracting(RepositorioAula.Miembro::nombre).containsExactly("Ana Pérez", "Sofía Ruiz");
        assertThat(usuarios.porNombre(colegio, "Ana Pérez")).map(Usuario::rol).contains(Usuario.RolGlobal.ESTUDIANTE);
        assertThat(usuarios.codigoVigente(colegio, CodigoDeUnUso.hash("789ABCDE"), reloj.ahora()))
                .map(RepositorioUsuarios.CodigoCanjeado::tipo).contains(RepositorioUsuarios.TipoCodigo.ALTA);
        assertThat(usuarios.codigoVigente(colegio, "789ABCDE", reloj.ahora())).as("nunca en claro").isEmpty();
        assertThat(auditoria.todos()).filteredOn(e -> e.accion() == RegistroAuditoria.Accion.ALTA_EN_LOTE)
                .extracting(e -> e.detalle().orElse("")).containsExactly("2 creadas, 2 sin crear");
    }

    @Test
    void un_docente_no_da_de_alta_en_un_grupo_ajeno_y_una_lista_vacia_o_de_501_se_rechaza() {
        Actor rectora = cuenta("Rectora", Usuario.RolGlobal.ADMINISTRADOR);
        Actor profe = cuenta("Profe Ramírez", Usuario.RolGlobal.DOCENTE);
        Actor otra = cuenta("Profe Díaz", Usuario.RolGlobal.DOCENTE);
        Grupo g = servicio.crearGrupo(rectora, "10.º A", Optional.of(profe.id()));
        String quinientasUna = java.util.stream.IntStream.rangeClosed(1, 501).mapToObj(i -> "Estudiante " + i).collect(java.util.stream.Collectors.joining("\n"));

        assertThatThrownBy(() -> servicio.altaEnLote(otra, g.id(), "Ana")).isInstanceOf(ObjetoNoEncontrado.class);
        assertThatThrownBy(() -> servicio.altaEnLote(profe, g.id(), " \n ")).isInstanceOf(ServicioUsuarios.DatosInvalidos.class)
                .hasMessageContaining("vacía");
        assertThatThrownBy(() -> servicio.altaEnLote(profe, g.id(), quinientasUna)).isInstanceOf(ServicioUsuarios.DatosInvalidos.class)
                .hasMessageContaining("501 filas");
        assertThat(servicio.miembros(profe, g.id())).isEmpty();
    }

    @Test
    void los_codigos_nuevos_restablecen_a_quien_tiene_clave_dan_alta_a_quien_no_y_cierran_sus_sesiones() {
        Actor rectora = cuenta("Rectora", Usuario.RolGlobal.ADMINISTRADOR);
        Actor profe = cuenta("Profe Ramírez", Usuario.RolGlobal.DOCENTE);
        Grupo g = servicio.crearGrupo(rectora, "10.º A", Optional.of(profe.id()));
        ServicioAula.AltaEnLote alta = servicio.altaEnLote(profe, g.id(), "Ana Pérez\nSofía Ruiz\nLuis Gómez");
        UUID ana = alta.creadas().get(0).cuenta().id();
        UUID sofia = alta.creadas().get(1).cuenta().id();
        UUID luis = alta.creadas().get(2).cuenta().id();
        usuarios.cambiarClave(colegio, ana, "hash-de-ana", Usuario.TipoClave.CONTRASENA);
        usuarios.cambiarActivo(colegio, luis, false);
        UUID ajena = cuenta("Persona ajena", Usuario.RolGlobal.ESTUDIANTE).id();

        var emitidos = servicio.codigosNuevos(profe, g.id(), java.util.List.of(ana, sofia, luis, ajena));

        assertThat(emitidos).extracting(e -> e.cuenta().nombre() + ":" + e.tipo())
                .containsExactly("Ana Pérez:RESTABLECER", "Sofía Ruiz:ALTA");
        assertThat(sesionesCerradas).containsExactly(ana, sofia);
        assertThatThrownBy(() -> servicio.codigosNuevos(profe, g.id(), java.util.List.of(ajena))).isInstanceOf(ServicioUsuarios.DatosInvalidos.class);
    }

    @Test
    void abrir_la_clase_da_un_codigo_corto_que_lleva_al_grupo_y_la_lista_solo_si_se_activo() {
        Actor rectora = cuenta("Rectora", Usuario.RolGlobal.ADMINISTRADOR);
        Actor profe = cuenta("Profe Ramírez", Usuario.RolGlobal.DOCENTE);
        Grupo g = servicio.crearGrupo(rectora, "10.º A", Optional.of(profe.id()));
        servicio.altaEnLote(profe, g.id(), "Beto Ruiz\nAna Pérez");

        ServicioAula.ClaseAbierta abierta = servicio.abrirClase(profe, g.id(), 50);

        assertThat(abierta.codigo()).hasSize(6);
        assertThat(java.time.Duration.between(abierta.clase().abiertaEn(), abierta.clase().cierraEn())).isEqualTo(java.time.Duration.ofMinutes(50));
        assertThat(servicio.clasePorCodigo(colegio, abierta.codigo().toLowerCase())).map(RepositorioAula.ClasePorCodigo::grupo).contains("10.º A");
        assertThat(servicio.listaDeClase(colegio, abierta.codigo())).as("apagada por defecto").isEmpty();
        servicio.cambiarOpciones(profe, g.id(), true, false);
        assertThat(servicio.listaDeClase(colegio, abierta.codigo())).containsExactly("Ana Pérez", "Beto Ruiz");
        assertThat(servicio.clasePorCodigo(colegio, "no-es")).isEmpty();
        assertThat(auditoria.todos()).extracting(RegistroAuditoria.Evento::accion).contains(RegistroAuditoria.Accion.ABRIR_CLASE);
    }

    @Test
    void la_clase_dura_entre_10_y_60_minutos() {
        Actor rectora = cuenta("Rectora", Usuario.RolGlobal.ADMINISTRADOR);
        Actor profe = cuenta("Profe Ramírez", Usuario.RolGlobal.DOCENTE);
        Grupo g = servicio.crearGrupo(rectora, "10.º A", Optional.of(profe.id()));

        ClaseDeAula larga = servicio.abrirClase(profe, g.id(), 600).clase();
        ClaseDeAula corta = servicio.abrirClase(profe, g.id(), 1).clase();

        assertThat(java.time.Duration.between(larga.abiertaEn(), larga.cierraEn())).isEqualTo(java.time.Duration.ofMinutes(60));
        assertThat(java.time.Duration.between(corta.abiertaEn(), corta.cierraEn())).isEqualTo(java.time.Duration.ofMinutes(10));
    }

    @Test
    void la_sesion_del_estudiante_dura_lo_que_queda_de_su_clase_y_si_no_hay_clase_10_minutos() {
        Actor rectora = cuenta("Rectora", Usuario.RolGlobal.ADMINISTRADOR);
        Actor profe = cuenta("Profe Ramírez", Usuario.RolGlobal.DOCENTE);
        Grupo g = servicio.crearGrupo(rectora, "10.º A", Optional.of(profe.id()));
        UUID ana = servicio.altaEnLote(profe, g.id(), "Ana Pérez").creadas().get(0).cuenta().id();
        Actor estudiante = new Actor(ana, colegio, Usuario.RolGlobal.ESTUDIANTE);
        assertThat(servicio.duracionDeSesion(estudiante)).isEqualTo(java.time.Duration.ofMinutes(10));

        servicio.abrirClase(profe, g.id(), 50);
        reloj.avanzar(java.time.Duration.ofMinutes(5));
        assertThat(servicio.duracionDeSesion(estudiante)).isEqualTo(java.time.Duration.ofMinutes(45));
        assertThat(servicio.duracionDeSesion(profe)).as("solo los estudiantes").isEqualTo(java.time.Duration.ofMinutes(10));
        reloj.avanzar(java.time.Duration.ofMinutes(42));
        assertThat(servicio.duracionDeSesion(estudiante)).as("nunca menos de 10").isEqualTo(java.time.Duration.ofMinutes(10));

        servicio.terminarClase(profe, g.id());
        assertThat(servicio.duracionDeSesion(estudiante)).isEqualTo(java.time.Duration.ofMinutes(10));
    }

    @Test
    void terminar_la_clase_cierra_las_sesiones_de_todo_el_grupo_y_su_codigo_deja_de_servir() {
        Actor rectora = cuenta("Rectora", Usuario.RolGlobal.ADMINISTRADOR);
        Actor profe = cuenta("Profe Ramírez", Usuario.RolGlobal.DOCENTE);
        Actor otra = cuenta("Profe Díaz", Usuario.RolGlobal.DOCENTE);
        Grupo g = servicio.crearGrupo(rectora, "10.º A", Optional.of(profe.id()));
        List<UUID> ids = servicio.altaEnLote(profe, g.id(), "Ana Pérez\nBeto Ruiz").creadas().stream().map(c -> c.cuenta().id()).toList();
        String codigo = servicio.abrirClase(profe, g.id(), 50).codigo();

        assertThatThrownBy(() -> servicio.terminarClase(otra, g.id())).isInstanceOf(ObjetoNoEncontrado.class);
        assertThat(servicio.terminarClase(profe, g.id())).isEqualTo(2);

        assertThat(sesionesCerradas).containsExactlyInAnyOrderElementsOf(ids);
        assertThat(servicio.clasePorCodigo(colegio, codigo)).isEmpty();
        assertThat(servicio.claseAbierta(profe, g.id())).isEmpty();
        assertThat(auditoria.todos()).extracting(RegistroAuditoria.Evento::accion).contains(RegistroAuditoria.Accion.TERMINAR_CLASE);
    }

    @Test
    void los_docentes_para_elegir_son_solo_las_cuentas_activas_de_docente() {
        Actor rectora = cuenta("Rectora", Usuario.RolGlobal.ADMINISTRADOR);
        cuenta("Profe Ramírez", Usuario.RolGlobal.DOCENTE);
        Actor retirada = cuenta("Profe Díaz", Usuario.RolGlobal.DOCENTE);
        cuenta("Ana", Usuario.RolGlobal.ESTUDIANTE);
        usuarios.cambiarActivo(colegio, retirada.id(), false);

        assertThat(servicio.docentes(rectora)).extracting(Usuario::nombre).containsExactly("Profe Ramírez");
    }
}
