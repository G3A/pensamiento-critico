package pensamiento.contrato.real;

import java.util.List;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import pensamiento.catalogo.RepositorioTecnicaJdbc;
import pensamiento.contrato.RepositorioTecnicaContract;
import pensamiento.nucleo.Familia;
import pensamiento.nucleo.IdTecnica;
import pensamiento.nucleo.Tecnica;
import pensamiento.nucleo.puertos.RepositorioTecnica;
import pensamiento.testutil.BaseDatosDePrueba;
import pensamiento.web.seguridad.GestorTransaccionesRls;

/**
 * Contra el PostgreSQL del compose. El catálogo es compartido y ya está sembrado (49 técnicas), así que
 * "dadoQueExisten" hace upsert de las filas de prueba como administrador y las vuelve a dejar como estaban:
 * nunca borra el catálogo real. Las lecturas van como rol de aplicación.
 */
@EnabledIfEnvironmentVariable(named = "CONTRACT_REAL", matches = "true")
class RealRepositorioTecnicaContractIT extends RepositorioTecnicaContract {

    private final BaseDatosDePrueba bd = new BaseDatosDePrueba();
    private final TransactionTemplate tx = new TransactionTemplate(new GestorTransaccionesRls(bd.dataSourceApp()));

    @Override
    protected RepositorioTecnica crearSut() {
        RepositorioTecnicaJdbc real = new RepositorioTecnicaJdbc(bd.jdbcApp());
        return new RepositorioTecnica() {
            @Override public List<Familia> familias() { return tx.execute(e -> real.familias()); }
            @Override public List<Tecnica> todas() { return tx.execute(e -> real.todas()); }
            @Override public List<Tecnica> porFamilia(String codigoFamilia) { return tx.execute(e -> real.porFamilia(codigoFamilia)); }
            @Override public java.util.Optional<Tecnica> porId(IdTecnica id) { return tx.execute(e -> real.porId(id)); }
            @Override public long contar() { return tx.execute(e -> real.contar()); }
        };
    }

    @Override
    protected void dadoQueExisten(List<Familia> familias, List<Tecnica> tecnicas) {
        JdbcClient admin = bd.jdbcAdmin();
        for (Familia f : familias) {
            admin.sql("INSERT INTO familia (codigo, nombre, orden) VALUES (:c, :n, :o) ON CONFLICT (codigo) DO NOTHING")
                    .param("c", f.codigo()).param("n", f.nombre()).param("o", f.orden()).update();
        }
        for (Tecnica t : tecnicas) {
            admin.sql("""
                    INSERT INTO tecnica (id, familia_codigo, nombre, nombre_llano, usala_cuando, definicion, tipo, operacion, objeto,
                      modalidad, patron, origen, requiere_ia, version_esquema, esquema_config, esquema_entrada, config_default, estado)
                    VALUES (:id, :f, :n, :nl, :uc, :d, :tipo, :op, :obj, :mod, :pat, :ori, :ia, :v, :ec::jsonb, :ee::jsonb, :cd::jsonb, :est)
                    ON CONFLICT (id) DO UPDATE SET familia_codigo = EXCLUDED.familia_codigo, nombre = EXCLUDED.nombre,
                      nombre_llano = EXCLUDED.nombre_llano, usala_cuando = EXCLUDED.usala_cuando, definicion = EXCLUDED.definicion,
                      tipo = EXCLUDED.tipo, operacion = EXCLUDED.operacion, objeto = EXCLUDED.objeto, modalidad = EXCLUDED.modalidad,
                      patron = EXCLUDED.patron, origen = EXCLUDED.origen, requiere_ia = EXCLUDED.requiere_ia,
                      version_esquema = EXCLUDED.version_esquema, esquema_config = EXCLUDED.esquema_config,
                      esquema_entrada = EXCLUDED.esquema_entrada, config_default = EXCLUDED.config_default, estado = EXCLUDED.estado
                    """)
                    .param("id", t.id().valor()).param("f", t.familia()).param("n", t.nombre()).param("nl", t.nombreLlano())
                    .param("uc", t.usalaCuando()).param("d", t.definicion()).param("tipo", t.tipo().name().toLowerCase())
                    .param("op", t.operacion().name().toLowerCase()).param("obj", t.objeto().name().toLowerCase())
                    .param("mod", t.modalidad().name().toLowerCase()).param("pat", t.patron()).param("ori", t.origen())
                    .param("ia", t.requiereIa().name().toLowerCase()).param("v", t.versionEsquema())
                    .param("ec", t.esquemaConfig().texto()).param("ee", t.esquemaEntrada().texto()).param("cd", t.configDefault().texto())
                    .param("est", t.estado().name().toLowerCase()).update();
        }
    }
}
