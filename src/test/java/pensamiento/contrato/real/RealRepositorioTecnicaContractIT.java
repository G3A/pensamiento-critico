package pensamiento.contrato.real;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import pensamiento.catalogo.RepositorioTecnicaJdbc;
import pensamiento.contrato.RepositorioTecnicaContract;
import pensamiento.nucleo.Ejemplo;
import pensamiento.nucleo.Familia;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.RelacionTecnica;
import pensamiento.nucleo.Tecnica;
import pensamiento.nucleo.puertos.RepositorioTecnica;
import pensamiento.testutil.BaseDatosDePrueba;
import pensamiento.web.seguridad.GestorTransaccionesRls;

/**
 * Contra el PostgreSQL del compose. El catálogo es compartido y ya está sembrado (49 técnicas), así que
 * "dadoQueExisten" hace upsert de las filas de prueba como administrador y "dadoQueNoExiste" retira la fila
 * guardando una copia que se restaura al terminar cada prueba. Las lecturas van como rol de aplicación.
 */
@EnabledIfEnvironmentVariable(named = "CONTRACT_REAL", matches = "true")
class RealRepositorioTecnicaContractIT extends RepositorioTecnicaContract {

    private static final String SQL_UPSERT = """
            INSERT INTO tecnica (id, familia_codigo, nombre, nombre_llano, usala_cuando, definicion, tipo, operacion, objeto,
              modalidad, patron, origen, requiere_ia, version_esquema, esquema_config, esquema_entrada, config_default, estado)
            VALUES (:id, :f, :n, :nl, :uc, :d, :tipo, :op, :obj, :mod, :pat, :ori, :ia, :v, :ec::jsonb, :ee::jsonb, :cd::jsonb, :est)
            ON CONFLICT (id) DO UPDATE SET familia_codigo = EXCLUDED.familia_codigo, nombre = EXCLUDED.nombre,
              nombre_llano = EXCLUDED.nombre_llano, usala_cuando = EXCLUDED.usala_cuando, definicion = EXCLUDED.definicion,
              tipo = EXCLUDED.tipo, operacion = EXCLUDED.operacion, objeto = EXCLUDED.objeto, modalidad = EXCLUDED.modalidad,
              patron = EXCLUDED.patron, origen = EXCLUDED.origen, requiere_ia = EXCLUDED.requiere_ia,
              version_esquema = EXCLUDED.version_esquema, esquema_config = EXCLUDED.esquema_config,
              esquema_entrada = EXCLUDED.esquema_entrada, config_default = EXCLUDED.config_default, estado = EXCLUDED.estado
            """;

    private final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private final TransactionTemplate tx = new TransactionTemplate(new GestorTransaccionesRls(bd.dataSourceApp()));
    private final List<Map<String, Object>> filasRetiradas = new ArrayList<>();
    private final List<Map<String, Object>> relacionesRetiradas = new ArrayList<>();

    @AfterEach
    void restaurarCatalogo() {
        JdbcClient admin = bd.jdbcAdmin();
        for (Map<String, Object> fila : filasRetiradas) {
            admin.sql(SQL_UPSERT)
                    .param("id", fila.get("id")).param("f", fila.get("familia_codigo")).param("n", fila.get("nombre"))
                    .param("nl", fila.get("nombre_llano")).param("uc", fila.get("usala_cuando")).param("d", fila.get("definicion"))
                    .param("tipo", fila.get("tipo")).param("op", fila.get("operacion")).param("obj", fila.get("objeto"))
                    .param("mod", fila.get("modalidad")).param("pat", fila.get("patron")).param("ori", fila.get("origen"))
                    .param("ia", fila.get("requiere_ia")).param("v", fila.get("version_esquema"))
                    .param("ec", fila.get("esquema_config")).param("ee", fila.get("esquema_entrada")).param("cd", fila.get("config_default"))
                    .param("est", fila.get("estado")).update();
        }
        for (Map<String, Object> r : relacionesRetiradas) {
            admin.sql("INSERT INTO relacion_tecnica (origen_id, destino_id, tipo) VALUES (:o, :d, :t) ON CONFLICT DO NOTHING")
                    .param("o", r.get("origen_id")).param("d", r.get("destino_id")).param("t", r.get("tipo")).update();
        }
        filasRetiradas.clear();
        relacionesRetiradas.clear();
    }

    @Override
    protected RepositorioTecnica crearSut() {
        RepositorioTecnicaJdbc real = new RepositorioTecnicaJdbc(bd.jdbcApp());
        return new RepositorioTecnica() {
            @Override public List<Familia> familias() { return tx.execute(e -> real.familias()); }
            @Override public List<Tecnica> todas() { return tx.execute(e -> real.todas()); }
            @Override public List<Tecnica> porFamilia(String codigoFamilia) { return tx.execute(e -> real.porFamilia(codigoFamilia)); }
            @Override public java.util.Optional<Tecnica> porId(IdTecnica id) { return tx.execute(e -> real.porId(id)); }
            @Override public long contar() { return tx.execute(e -> real.contar()); }
            @Override public List<Ejemplo> ejemplos(IdTecnica t) { return tx.execute(e -> real.ejemplos(t)); }
            @Override public java.util.Optional<Ejemplo> ejemplo(UUID id) { return tx.execute(e -> real.ejemplo(id)); }
            @Override public List<RelacionTecnica> relaciones(IdTecnica t) { return tx.execute(e -> real.relaciones(t)); }
        };
    }

    private final List<UUID> ejemplosInsertados = new ArrayList<>();
    private final List<RelacionTecnica> relacionesInsertadas = new ArrayList<>();

    @AfterEach
    void retirarEjemplosYRelacionesDePrueba() {
        JdbcClient admin = bd.jdbcAdmin();
        ejemplosInsertados.forEach(id -> admin.sql("DELETE FROM ejemplo WHERE id = :id").param("id", id).update());
        relacionesInsertadas.forEach(r -> admin.sql("DELETE FROM relacion_tecnica WHERE origen_id = :o AND destino_id = :d AND tipo = :t")
                .param("o", r.origen().valor()).param("d", r.destino().valor()).param("t", r.tipo().enBaseDeDatos()).update());
        ejemplosInsertados.clear();
        relacionesInsertadas.clear();
    }

    @Override
    protected void dadoQueExistenEjemplos(List<Ejemplo> ejemplos) {
        for (Ejemplo e : ejemplos) {
            int insertado = bd.jdbcAdmin().sql("""
                    INSERT INTO ejemplo (id, tecnica_id, orden, version_esquema, ambito, titulo, config, datos, resultado, nota)
                    VALUES (:id, :t, :o, :v, :a, :titulo, :c::jsonb, :d::jsonb, :r::jsonb, :n) ON CONFLICT (id) DO NOTHING
                    """)
                    .param("id", e.id()).param("t", e.tecnica().valor()).param("o", e.orden()).param("v", e.versionEsquema())
                    .param("a", e.ambito().enBaseDeDatos()).param("titulo", e.titulo()).param("c", e.config().texto())
                    .param("d", e.datos().texto()).param("r", e.resultado().texto()).param("n", e.nota()).update();
            if (insertado == 1) {
                ejemplosInsertados.add(e.id());
            }
        }
    }

    @Override
    protected void dadoQueExisteRelacion(RelacionTecnica r) {
        int insertada = bd.jdbcAdmin().sql("INSERT INTO relacion_tecnica (origen_id, destino_id, tipo) VALUES (:o, :d, :t) ON CONFLICT DO NOTHING")
                .param("o", r.origen().valor()).param("d", r.destino().valor()).param("t", r.tipo().enBaseDeDatos()).update();
        if (insertada == 1) {
            relacionesInsertadas.add(r);
        }
    }

    @Override
    protected void dadoQueExisten(List<Familia> familias, List<Tecnica> tecnicas) {
        JdbcClient admin = bd.jdbcAdmin();
        for (Familia f : familias) {
            admin.sql("INSERT INTO familia (codigo, nombre, orden) VALUES (:c, :n, :o) ON CONFLICT (codigo) DO NOTHING")
                    .param("c", f.codigo()).param("n", f.nombre()).param("o", f.orden()).update();
        }
        for (Tecnica t : tecnicas) {
            // Si la fila ya estaba en el catálogo real, se guarda su copia para restaurarla después. No se borra:
            // T28 tiene ejemplos (se irían en cascada) y ejecuciones de usuarios (la clave foránea lo impide).
            guardarCopia(t.id());
            admin.sql(SQL_UPSERT)
                    .param("id", t.id().valor()).param("f", t.familia()).param("n", t.nombre()).param("nl", t.nombreLlano())
                    .param("uc", t.usalaCuando()).param("d", t.definicion()).param("tipo", t.tipo().name().toLowerCase())
                    .param("op", t.operacion().name().toLowerCase()).param("obj", t.objeto().name().toLowerCase())
                    .param("mod", t.modalidad().name().toLowerCase()).param("pat", t.patron()).param("ori", t.origen())
                    .param("ia", t.requiereIa().name().toLowerCase()).param("v", t.versionEsquema())
                    .param("ec", t.esquemaConfig().texto()).param("ee", t.esquemaEntrada().texto()).param("cd", t.configDefault().texto())
                    .param("est", t.estado().name().toLowerCase()).update();
        }
    }

    @Override
    protected void dadoQueNoExiste(IdTecnica id) {
        JdbcClient admin = bd.jdbcAdmin();
        relacionesRetiradas.addAll(admin.sql("SELECT origen_id, destino_id, tipo FROM relacion_tecnica WHERE origen_id = :id OR destino_id = :id")
                .param("id", id.valor()).query().listOfRows());
        guardarCopia(id);
        admin.sql("DELETE FROM tecnica WHERE id = :id").param("id", id.valor()).update();
    }

    private void guardarCopia(IdTecnica id) {
        filasRetiradas.addAll(bd.jdbcAdmin().sql("""
                SELECT id, familia_codigo, nombre, nombre_llano, usala_cuando, definicion, tipo, operacion, objeto, modalidad, patron,
                       origen, requiere_ia, version_esquema, esquema_config::text AS esquema_config, esquema_entrada::text AS esquema_entrada,
                       config_default::text AS config_default, estado
                FROM tecnica WHERE id = :id
                """).param("id", id.valor()).query().listOfRows());
    }
}
