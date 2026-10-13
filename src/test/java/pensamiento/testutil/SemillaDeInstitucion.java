package pensamiento.testutil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Una institución con una fila en cada tabla con datos de una institución, sembrada como administrador de la base (sin RLS).
 * Donde la tabla tiene texto libre lleva un centinela; las demás filas se reconocen por los ids de lo sembrado. Sirve para
 * probar que nada de ella se ve desde otra institución (hito 8, entregable 6) y que exportarla y borrarla no deja nada
 * (entregable 7).
 */
public final class SemillaDeInstitucion {

    private final BaseDatosDePrueba bd;
    private final String centinela = "CENTINELA-" + UUID.randomUUID();
    private final List<String> marcas = new ArrayList<>();
    private final Map<String, Integer> sembradas = new LinkedHashMap<>();
    private final UUID institucion;
    private final UUID usuario;
    private final String ruta;

    public SemillaDeInstitucion(BaseDatosDePrueba bd) {
        this.bd = bd;
        this.ruta = "semilla-" + UUID.randomUUID().toString().substring(0, 8);
        this.institucion = bd.crearInstitucion("Institución " + centinela, ruta);
        this.usuario = bd.crearUsuario(institucion, "persona " + centinela);
        marcas.add(centinela);
        marcas.add(institucion.toString());
        marcas.add(usuario.toString());
        sembradas.put("institucion", 1);
        sembradas.put("usuario", 1);
        sembrar();
    }

    public UUID institucion() {
        return institucion;
    }

    public UUID usuario() {
        return usuario;
    }

    public String ruta() {
        return ruta;
    }

    public String centinela() {
        return centinela;
    }

    /** Tabla → filas sembradas. */
    public Map<String, Integer> sembradas() {
        return Collections.unmodifiableMap(sembradas);
    }

    /** Una expresión regular que encuentra el centinela o cualquier id sembrado en el texto de una fila. */
    public String patron() {
        return "(" + String.join("|", marcas) + ")";
    }

    /** Por cada tabla sembrada, cuántas filas visibles con esa conexión traen una marca de esta institución. */
    public Map<String, Integer> contarMarcas(JdbcClient jdbc) {
        Map<String, Integer> conteo = new LinkedHashMap<>();
        for (String tabla : sembradas.keySet()) {
            conteo.put(tabla, jdbc.sql("SELECT count(*)::int FROM " + tabla + " t WHERE t::text ~ :patron").param("patron", patron())
                    .query(Integer.class).single());
        }
        return conteo;
    }

    private void sembrar() {
        UUID grupo = insertar("grupo", "INSERT INTO grupo (institucion_id, nombre) VALUES (:i, :c) RETURNING id");
        uno("miembro_grupo", "INSERT INTO miembro_grupo (grupo_id, usuario_id, rol) VALUES ('" + grupo + "', :u, 'estudiante')");
        uno("configuracion_usuario", "INSERT INTO configuracion_usuario (usuario_id, institucion_id, tecnica_id, version_esquema, valores) "
                + "VALUES (:u, :i, 'T28', 1, jsonb_build_object('nota', :c::text))");
        UUID afirmacion = insertar("afirmacion", "INSERT INTO afirmacion (usuario_id, institucion_id, texto, tipo, origen) "
                + "VALUES (:u, :i, :c, 'hecho', 'usuario') RETURNING id");
        UUID expediente = insertar("expediente", "INSERT INTO expediente (usuario_id, institucion_id, nombre) VALUES (:u, :i, :c) RETURNING id");
        UUID ejecucion = insertar("ejecucion", "INSERT INTO ejecucion (usuario_id, institucion_id, tecnica_id, version_esquema, expediente_id, "
                + "resumen, clave_idempotencia) VALUES (:u, :i, 'T28', 1, '" + expediente + "', :c, :c) RETURNING id");
        uno("ejecucion_afirmacion", "INSERT INTO ejecucion_afirmacion (ejecucion_id, afirmacion_id, rol, sentido) "
                + "VALUES ('" + ejecucion + "', '" + afirmacion + "', 'premisa', 'consumida')");
        UUID argumento = insertar("argumento", "INSERT INTO argumento (usuario_id, institucion_id, conclusion_id, sentido, texto_argdown) "
                + "VALUES (:u, :i, '" + afirmacion + "', 'pro', :c) RETURNING id");
        uno("premisa_argumento", "INSERT INTO premisa_argumento (argumento_id, afirmacion_id, orden) VALUES ('" + argumento + "', '" + afirmacion + "', 1)");
        // compartido: dentro de la institución lo ven todas sus personas; desde otra, nunca.
        UUID documento = insertar("documento", "INSERT INTO documento (usuario_id, institucion_id, nombre, tipo, hash, compartido) "
                + "VALUES (:u, :i, :c, 'texto', :c, true) RETURNING id");
        uno("fragmento", "INSERT INTO fragmento (documento_id, orden, texto) VALUES ('" + documento + "', 1, :c)");
        UUID fuente = insertar("fuente", "INSERT INTO fuente (usuario_id, institucion_id, titulo, tipo) VALUES (:u, :i, :c, 'primaria') RETURNING id");
        uno("evidencia", "INSERT INTO evidencia (afirmacion_id, fuente_id, pasaje, postura, fuerza, etiquetada_por) "
                + "VALUES ('" + afirmacion + "', '" + fuente + "', :c, 'apoya', 4, 'usuario')");
        uno("cambio_opinion", "INSERT INTO cambio_opinion (usuario_id, institucion_id, afirmacion_id, causa) VALUES (:u, :i, '" + afirmacion + "', 'manual')");
        uno("prediccion", "INSERT INTO prediccion (usuario_id, institucion_id, afirmacion_id, confianza, fecha_revision) "
                + "VALUES (:u, :i, '" + afirmacion + "', 70, current_date + 30)");
        uno("pendiente", "INSERT INTO pendiente (usuario_id, institucion_id, tipo, descripcion) VALUES (:u, :i, 'revision', :c)");
        uno("competencia", "INSERT INTO competencia (usuario_id, institucion_id, tecnica_id) VALUES (:u, :i, 'T13')");
        uno("auditoria", "INSERT INTO auditoria (usuario_id, institucion_id, accion, objeto_tipo, objeto_id) "
                + "VALUES (:u, :i, 'crear', 'expediente', '" + expediente + "')");
        UUID sesion = insertar("sesion_consejero", "INSERT INTO sesion_consejero (usuario_id, institucion_id, modo, postura, config) "
                + "VALUES (:u, :i, 'decision', :c, '{}') RETURNING id");
        uno("turno_consejero", "INSERT INTO turno_consejero (sesion_id, usuario_id, institucion_id, numero, rol, paso, texto, origen) "
                + "VALUES ('" + sesion + "', :u, :i, 1, 'persona', 'postura', :c, 'persona')");
        uno("verificacion", "INSERT INTO verificacion (afirmacion_id, usuario_id, institucion_id, preguntas) "
                + "VALUES ('" + afirmacion + "', :u, :i, jsonb_build_object('nota', :c::text))");
        uno("intento_dojo", "INSERT INTO intento_dojo (usuario_id, institucion_id, clave, reto_id, tecnica_id, concepto, nivel, respuesta, acierto, dia) "
                + "VALUES (:u, :i, :c, 'reto-i1', 'T13', 'ad-hominem', 'identificar', :c, true, current_date)");
    }

    /** Borra todo lo sembrado (y lo que la app haya agregado a esta institución), como administrador de la base. */
    public void borrar() {
        JdbcClient admin = bd.jdbcAdmin();
        admin.sql("DELETE FROM intento_dojo WHERE institucion_id = :i").param("i", institucion).update();
        admin.sql("DELETE FROM turno_consejero WHERE institucion_id = :i").param("i", institucion).update();
        admin.sql("DELETE FROM sesion_consejero WHERE institucion_id = :i").param("i", institucion).update();
        admin.sql("DELETE FROM competencia WHERE institucion_id = :i").param("i", institucion).update();
        admin.sql("DELETE FROM cambio_opinion WHERE institucion_id = :i").param("i", institucion).update();
        admin.sql("DELETE FROM miembro_grupo WHERE grupo_id IN (SELECT id FROM grupo WHERE institucion_id = :i)").param("i", institucion).update();
        admin.sql("DELETE FROM grupo WHERE institucion_id = :i").param("i", institucion).update();
        admin.sql("DELETE FROM fragmento WHERE documento_id IN (SELECT id FROM documento WHERE institucion_id = :i)").param("i", institucion).update();
        bd.borrarInstitucion(institucion);
    }

    private UUID insertar(String tabla, String sql) {
        UUID id = bd.jdbcAdmin().sql(sql).param("i", institucion).param("c", centinela).param("u", usuario).query(UUID.class).single();
        marcas.add(id.toString());
        sembradas.merge(tabla, 1, Integer::sum);
        return id;
    }

    private void uno(String tabla, String sql) {
        bd.jdbcAdmin().sql(sql).param("i", institucion).param("c", centinela).param("u", usuario).update();
        sembradas.merge(tabla, 1, Integer::sum);
    }
}
