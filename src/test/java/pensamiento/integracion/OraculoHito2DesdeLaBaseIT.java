package pensamiento.integracion;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import pensamiento.argdown.ParserArgdown;
import pensamiento.catalogo.MapeadorJson;
import pensamiento.catalogo.RepositorioEsquemasJdbc;
import pensamiento.catalogo.RepositorioTecnicaJdbc;
import pensamiento.nucleo.Ejemplo;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Pendiente;
import pensamiento.nucleo.Resultado;
import pensamiento.tecnicas.f1.EjecutorMapa;
import pensamiento.tecnicas.f1.EjecutorPremisasOcultas;
import pensamiento.tecnicas.f1.EjecutorToulmin;
import pensamiento.tecnicas.f3.ConfigFalacias;
import pensamiento.tecnicas.f3.EjecutorFalacias;
import pensamiento.tecnicas.f3.EntradaFalacias;
import pensamiento.testutil.BaseDatosDePrueba;
import pensamiento.testutil.builders.Contextos;
import pensamiento.web.seguridad.GestorTransaccionesRls;

/**
 * El oráculo de T01, T02, T06 y T13 contra lo que la semilla repeatable dejó en las tablas ejemplo y esquema_walton
 * del PostgreSQL del compose: cada ejemplo leído de la base produce el resumen y los pendientes escritos a mano.
 */
class OraculoHito2DesdeLaBaseIT {

    /** Lo común de los cuatro oráculos: resumen y pendientes. */
    record Esperado(String resumen, List<String> pendientes) {
    }

    private final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private final TransactionTemplate tx = new TransactionTemplate(new GestorTransaccionesRls(bd.dataSourceApp()));
    private final ParserArgdown parser = new ParserArgdown();

    private List<Ejemplo> ejemplos(IdTecnica tecnica) {
        RepositorioTecnicaJdbc repo = new RepositorioTecnicaJdbc(bd.jdbcApp());
        return tx.execute(t -> repo.ejemplos(tecnica));
    }

    private static void comparar(Ejemplo e, Resultado<?> r) {
        Esperado esperado = MapeadorJson.leer(e.resultado(), Esperado.class);
        assertThat(r.resumen()).as(e.titulo()).isEqualTo(esperado.resumen());
        assertThat(r.pendientes()).extracting(Pendiente::descripcion).as(e.titulo()).containsExactlyElementsOf(esperado.pendientes());
    }

    @Test
    void t01_mapeo_de_argumentos_desde_la_tabla_ejemplo() {
        List<Ejemplo> ejemplos = ejemplos(EjecutorMapa.ID);
        assertThat(ejemplos).extracting(Ejemplo::titulo).containsExactly("El carro usado", "La segunda sucursal", "Las cámaras del barrio");
        EjecutorMapa t01 = new EjecutorMapa(parser);
        ejemplos.forEach(e -> comparar(e, t01.ejecutar(MapeadorJson.leer(e.config(), EjecutorMapa.Config.class),
                MapeadorJson.leer(e.datos(), EjecutorMapa.Entrada.class), Contextos.sinIa())));
    }

    @Test
    void t02_modelo_de_toulmin_desde_la_tabla_ejemplo() {
        List<Ejemplo> ejemplos = ejemplos(EjecutorToulmin.ID);
        assertThat(ejemplos).extracting(Ejemplo::titulo)
                .containsExactly("Las vacaciones con los abuelos", "El reductor frente al parque", "La segunda sucursal");
        EjecutorToulmin t02 = new EjecutorToulmin();
        ejemplos.forEach(e -> comparar(e, t02.ejecutar(MapeadorJson.leer(e.config(), EjecutorToulmin.Config.class),
                MapeadorJson.leer(e.datos(), EjecutorToulmin.Entrada.class), Contextos.sinIa())));
    }

    @Test
    void t06_premisas_ocultas_desde_la_tabla_ejemplo() {
        List<Ejemplo> ejemplos = ejemplos(EjecutorPremisasOcultas.ID);
        assertThat(ejemplos).extracting(Ejemplo::titulo)
                .containsExactly("Venderá más en el centro", "El cambio de colegio", "La calle cerrada los domingos");
        EjecutorPremisasOcultas t06 = new EjecutorPremisasOcultas(parser);
        ejemplos.forEach(e -> comparar(e, t06.ejecutar(MapeadorJson.leer(e.config(), EjecutorPremisasOcultas.Config.class),
                MapeadorJson.leer(e.datos(), EjecutorPremisasOcultas.Entrada.class), Contextos.sinIa())));
    }

    @Test
    void t13_falacias_desde_la_tabla_ejemplo_con_los_esquemas_de_la_base() {
        List<Ejemplo> ejemplos = ejemplos(EjecutorFalacias.ID);
        assertThat(ejemplos).extracting(Ejemplo::titulo)
                .containsExactly("Los que se oponen a las cámaras", "La harina del proveedor", "El mercado del mes");
        RepositorioEsquemasJdbc esquemas = new RepositorioEsquemasJdbc(bd.jdbcApp());
        tx.executeWithoutResult(t -> {
            EjecutorFalacias t13 = new EjecutorFalacias(esquemas);
            ejemplos.forEach(e -> comparar(e, t13.ejecutar(MapeadorJson.leer(e.config(), ConfigFalacias.class),
                    MapeadorJson.leer(e.datos(), EntradaFalacias.class), Contextos.sinIa())));
        });
    }
}
