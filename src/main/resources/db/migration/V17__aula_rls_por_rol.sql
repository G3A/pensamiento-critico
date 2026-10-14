-- Hito 10 · Aula, entregables 1 y 2: RLS por rol, además de por institución.
-- El rol se lee de la base (usuario.rol_global de la cuenta activa de la transacción), nunca de lo que diga la app.
--   administrador: todas las cuentas, grupos, miembros y clases de su institución.
--   docente: su propia cuenta, los estudiantes de sus grupos (solo la fila de la cuenta: nombre y estado), sus grupos, sus
--            miembros y sus clases. Nunca el texto de un estudiante: esas tablas siguen siendo solo de su dueño.
--   estudiante y persona: su propia cuenta; el estudiante, además, los grupos donde es miembro, su propia fila de miembro
--            y las clases de esos grupos (para saber si su clase está abierta). Nunca la lista de sus compañeros.
--   sin usuario fijado (entrar, canjear un código: antes de saber quién es): por institución, como hasta el hito 9.
-- Lo que cruza personas (crear un estudiante en un grupo, la entrada con el código de clase, los agregados del panel) va por
-- funciones SECURITY DEFINER con search_path fijo, acotadas a la institución y al grupo, que devuelven solo conteos y nombres.

-- ---------------------------------------------------------------------------------------------
-- Funciones de apoyo de las políticas (SECURITY DEFINER: leen sin RLS y así las políticas no se llaman en círculo)
-- ---------------------------------------------------------------------------------------------
CREATE FUNCTION app_rol_actual() RETURNS text
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$ SELECT u.rol_global FROM public.usuario u
        WHERE u.id = public.app_usuario_actual() AND u.institucion_id = public.app_institucion_actual() AND u.activo $$;

CREATE FUNCTION app_rol_de(p_usuario uuid) RETURNS text
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$ SELECT u.rol_global FROM public.usuario u WHERE u.id = p_usuario AND u.institucion_id = public.app_institucion_actual() $$;

CREATE FUNCTION app_es_docente_de(p_grupo uuid) RETURNS boolean
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$ SELECT EXISTS (SELECT 1 FROM public.grupo g
                       WHERE g.id = p_grupo AND g.institucion_id = public.app_institucion_actual()
                         AND g.docente_id = public.app_usuario_actual())
               AND public.app_rol_actual() = 'docente' $$;

CREATE FUNCTION app_administra_grupo(p_grupo uuid) RETURNS boolean
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$ SELECT public.app_es_docente_de(p_grupo)
               OR (public.app_rol_actual() = 'administrador'
                   AND EXISTS (SELECT 1 FROM public.grupo g WHERE g.id = p_grupo AND g.institucion_id = public.app_institucion_actual())) $$;

CREATE FUNCTION app_es_miembro_de(p_grupo uuid) RETURNS boolean
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$ SELECT EXISTS (SELECT 1 FROM public.miembro_grupo m WHERE m.grupo_id = p_grupo AND m.usuario_id = public.app_usuario_actual()) $$;

-- ¿Puede quien consulta ver la fila de esta cuenta?
CREATE FUNCTION app_puede_ver_usuario(p_usuario uuid) RETURNS boolean
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$ SELECT public.app_usuario_actual() IS NULL
               OR p_usuario = public.app_usuario_actual()
               OR public.app_rol_actual() = 'administrador'
               OR (public.app_rol_actual() = 'docente'
                   AND EXISTS (SELECT 1 FROM public.miembro_grupo m JOIN public.grupo g ON g.id = m.grupo_id
                               WHERE m.usuario_id = p_usuario AND g.docente_id = public.app_usuario_actual()
                                 AND g.institucion_id = public.app_institucion_actual())) $$;

DO $$
DECLARE f text;
BEGIN
  FOREACH f IN ARRAY ARRAY['app_rol_actual()', 'app_rol_de(uuid)', 'app_es_docente_de(uuid)', 'app_administra_grupo(uuid)',
                           'app_es_miembro_de(uuid)', 'app_puede_ver_usuario(uuid)'] LOOP
    EXECUTE format('REVOKE ALL ON FUNCTION %s FROM PUBLIC', f);
    EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO ${rol_app}', f);
  END LOOP;
END $$;

-- ---------------------------------------------------------------------------------------------
-- Cuentas
-- ---------------------------------------------------------------------------------------------
DROP POLICY usuario_por_institucion ON usuario;
CREATE POLICY usuario_por_rol ON usuario
  USING (institucion_id = app_institucion_actual() AND app_puede_ver_usuario(id))
  WITH CHECK (institucion_id = app_institucion_actual()
              AND (app_usuario_actual() IS NULL OR id = app_usuario_actual() OR (SELECT app_rol_actual()) = 'administrador'));

-- Los códigos de alta y de restablecimiento (solo hashes): los ve quien ve la cuenta (su docente los genera para su grupo).
DROP POLICY codigo_cuenta_por_institucion ON codigo_cuenta;
CREATE POLICY codigo_cuenta_por_rol ON codigo_cuenta
  USING (institucion_id = app_institucion_actual() AND app_puede_ver_usuario(usuario_id))
  WITH CHECK (institucion_id = app_institucion_actual() AND app_puede_ver_usuario(usuario_id));

-- ---------------------------------------------------------------------------------------------
-- Grupos: el tema del Dojo asignado y las dos opciones de la clase (lista de nombres y modelo), apagadas por defecto
-- ---------------------------------------------------------------------------------------------
ALTER TABLE grupo
  ADD COLUMN tema_dojo        text CHECK (tema_dojo IN ('T13', 'T14', 'T18', 'T19')),
  ADD COLUMN tema_hasta       date,
  ADD COLUMN tema_asignado_en timestamptz,
  ADD COLUMN lista_en_clase   boolean NOT NULL DEFAULT false,
  ADD COLUMN ia_en_clase      boolean NOT NULL DEFAULT false,
  ADD CONSTRAINT grupo_tema_con_fecha CHECK (tema_dojo IS NOT NULL OR tema_hasta IS NULL);
CREATE INDEX grupo_docente_idx ON grupo (docente_id);
CREATE INDEX miembro_grupo_usuario_idx ON miembro_grupo (usuario_id);

DROP POLICY grupo_por_institucion ON grupo;
CREATE POLICY grupo_lee ON grupo FOR SELECT
  USING (institucion_id = app_institucion_actual()
         AND ((SELECT app_rol_actual()) = 'administrador' OR docente_id = app_usuario_actual() OR app_es_miembro_de(id)));
CREATE POLICY grupo_crea ON grupo FOR INSERT
  WITH CHECK (institucion_id = app_institucion_actual() AND (SELECT app_rol_actual()) = 'administrador');
CREATE POLICY grupo_cambia ON grupo FOR UPDATE
  USING (institucion_id = app_institucion_actual()
         AND ((SELECT app_rol_actual()) = 'administrador' OR (docente_id = app_usuario_actual() AND (SELECT app_rol_actual()) = 'docente')))
  WITH CHECK (institucion_id = app_institucion_actual()
              AND ((SELECT app_rol_actual()) = 'administrador' OR docente_id = app_usuario_actual()));
CREATE POLICY grupo_borra ON grupo FOR DELETE
  USING (institucion_id = app_institucion_actual() AND (SELECT app_rol_actual()) = 'administrador');

DROP POLICY miembro_por_grupo ON miembro_grupo;
CREATE POLICY miembro_lee ON miembro_grupo FOR SELECT
  USING (EXISTS (SELECT 1 FROM grupo g WHERE g.id = grupo_id)
         AND (usuario_id = app_usuario_actual() OR app_administra_grupo(grupo_id)));
CREATE POLICY miembro_crea ON miembro_grupo FOR INSERT
  WITH CHECK (app_administra_grupo(grupo_id) AND app_rol_de(usuario_id) = 'estudiante');
CREATE POLICY miembro_borra ON miembro_grupo FOR DELETE
  USING (app_administra_grupo(grupo_id));

-- ---------------------------------------------------------------------------------------------
-- Clases (sesion_aula): las ven quienes ven el grupo; las abre y las termina su docente (o el administrador)
-- ---------------------------------------------------------------------------------------------
DROP POLICY sesion_aula_por_institucion ON sesion_aula;
CREATE POLICY sesion_aula_lee ON sesion_aula FOR SELECT
  USING (institucion_id = app_institucion_actual() AND EXISTS (SELECT 1 FROM grupo g WHERE g.id = grupo_id));
CREATE POLICY sesion_aula_abre ON sesion_aula FOR INSERT
  WITH CHECK (institucion_id = app_institucion_actual() AND app_administra_grupo(grupo_id));
CREATE POLICY sesion_aula_termina ON sesion_aula FOR UPDATE
  USING (institucion_id = app_institucion_actual() AND app_administra_grupo(grupo_id))
  WITH CHECK (institucion_id = app_institucion_actual() AND app_administra_grupo(grupo_id));

-- ---------------------------------------------------------------------------------------------
-- Biblioteca: el documento de un estudiante nunca se ve como compartido (su texto es solo suyo)
-- ---------------------------------------------------------------------------------------------
DROP POLICY documento_propio_o_compartido ON documento;
CREATE POLICY documento_propio_o_compartido ON documento
  USING (usuario_id = app_usuario_actual()
         OR (compartido AND institucion_id = app_institucion_actual() AND app_rol_de(usuario_id) IS DISTINCT FROM 'estudiante'))
  WITH CHECK (usuario_id = app_usuario_actual() AND institucion_id = app_institucion_actual());

-- ---------------------------------------------------------------------------------------------
-- Auditoría del aula
-- ---------------------------------------------------------------------------------------------
ALTER TABLE auditoria DROP CONSTRAINT auditoria_accion_check;
ALTER TABLE auditoria ADD CONSTRAINT auditoria_accion_check CHECK (accion IN ('crear', 'importar', 'exportar', 'compartir', 'borrar',
  'desactivar', 'sesion', 'reactivar', 'codigo_alta', 'codigo_restablecer', 'canjear_codigo', 'cambiar_clave', 'entrar',
  'fallo_entrar', 'espera_entrar', 'levantar_espera', 'registrar_llave', 'usar_recuperacion',
  'crear_grupo', 'cambiar_grupo', 'alta_en_lote', 'abrir_clase', 'terminar_clase', 'asignar_tema'));
CREATE INDEX auditoria_entradas_idx ON auditoria (institucion_id, fecha) WHERE accion = 'entrar';

-- ---------------------------------------------------------------------------------------------
-- Lo que cruza personas, por funciones acotadas
-- ---------------------------------------------------------------------------------------------

-- Crea la cuenta de un estudiante (sin clave) y la suma al grupo, si quien llama administra ese grupo. Nada si el nombre ya
-- existe en la institución (no lanza: la transacción del lote sigue sana) o si no administra el grupo.
CREATE FUNCTION aula_crear_estudiante(p_grupo uuid, p_id uuid, p_nombre text) RETURNS uuid
  LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$
DECLARE
  creado uuid;
BEGIN
  IF NOT public.app_administra_grupo(p_grupo) THEN
    RETURN NULL;
  END IF;
  INSERT INTO public.usuario (id, institucion_id, nombre, rol_global)
    VALUES (p_id, public.app_institucion_actual(), p_nombre, 'estudiante')
    ON CONFLICT DO NOTHING RETURNING id INTO creado;
  IF creado IS NULL THEN
    RETURN NULL;
  END IF;
  INSERT INTO public.miembro_grupo (grupo_id, usuario_id, rol) VALUES (p_grupo, creado, 'estudiante');
  RETURN creado;
END $$;

-- La clase abierta que corresponde a un código (antes de entrar, desde el QR): de qué grupo es y si muestra la lista.
CREATE FUNCTION aula_clase_por_codigo(p_institucion uuid, p_codigo_hash text, p_ahora timestamptz)
  RETURNS TABLE (sesion_id uuid, grupo_id uuid, grupo text, lista boolean, cierra_en timestamptz)
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$ SELECT s.id, g.id, g.nombre, g.lista_en_clase, s.cierra_en
        FROM public.sesion_aula s JOIN public.grupo g ON g.id = s.grupo_id JOIN public.institucion i ON i.id = s.institucion_id
        WHERE s.institucion_id = p_institucion AND s.codigo_hash = p_codigo_hash AND s.terminada_en IS NULL
          AND s.cierra_en > p_ahora AND g.activo AND i.estado = 'activa'
        ORDER BY s.abierta_en DESC LIMIT 1 $$;

-- Los nombres del grupo de esa clase: solo mientras está abierta y solo si su docente activó la lista para ese grupo.
CREATE FUNCTION aula_lista_de_clase(p_institucion uuid, p_codigo_hash text, p_ahora timestamptz) RETURNS TABLE (nombre text)
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$ SELECT u.nombre FROM public.aula_clase_por_codigo(p_institucion, p_codigo_hash, p_ahora) c
        JOIN public.miembro_grupo m ON m.grupo_id = c.grupo_id
        JOIN public.usuario u ON u.id = m.usuario_id
        WHERE c.lista AND u.activo AND u.rol_global = 'estudiante'
        ORDER BY u.nombre_clave $$;

-- Panel del grupo: solo números (y los nombres de quien lleva días sin practicar), y solo para su docente o el administrador.
CREATE FUNCTION aula_panel_resumen(p_grupo uuid, p_desde_hoy timestamptz, p_desde_clase timestamptz, p_tema text, p_hoy date)
  RETURNS TABLE (miembros integer, entraron_hoy integer, entraron_en_clase integer, retos_hoy integer, respondieron_hoy integer,
                 retos_del_tema integer)
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$ WITH m AS (SELECT mg.usuario_id FROM public.miembro_grupo mg JOIN public.usuario u ON u.id = mg.usuario_id
                   WHERE mg.grupo_id = p_grupo AND u.activo AND public.app_administra_grupo(p_grupo))
        SELECT (SELECT count(*) FROM m)::int,
               (SELECT count(DISTINCT a.usuario_id) FROM public.auditoria a JOIN m ON m.usuario_id = a.usuario_id
                 WHERE a.accion = 'entrar' AND a.fecha >= p_desde_hoy)::int,
               (SELECT count(DISTINCT a.usuario_id) FROM public.auditoria a JOIN m ON m.usuario_id = a.usuario_id
                 WHERE a.accion = 'entrar' AND p_desde_clase IS NOT NULL AND a.fecha >= p_desde_clase)::int,
               (SELECT count(*) FROM public.intento_dojo i JOIN m ON m.usuario_id = i.usuario_id WHERE i.dia = p_hoy)::int,
               (SELECT count(DISTINCT i.usuario_id) FROM public.intento_dojo i JOIN m ON m.usuario_id = i.usuario_id WHERE i.dia = p_hoy)::int,
               (SELECT count(*) FROM public.intento_dojo i JOIN m ON m.usuario_id = i.usuario_id WHERE i.tecnica_id = p_tema)::int
        WHERE public.app_administra_grupo(p_grupo) $$;

-- Aciertos por concepto del tema: los niveles identificar, analizar y evaluar por un lado y "crear" (rúbrica) por otro.
CREATE FUNCTION aula_panel_conceptos(p_grupo uuid, p_tema text)
  RETURNS TABLE (concepto text, crear boolean, intentos integer, aciertos integer, personas integer)
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$ SELECT i.concepto, i.nivel = 'crear', count(*)::int, count(*) FILTER (WHERE i.acierto)::int, count(DISTINCT i.usuario_id)::int
        FROM public.intento_dojo i JOIN public.miembro_grupo m ON m.usuario_id = i.usuario_id AND m.grupo_id = p_grupo
        WHERE i.tecnica_id = p_tema AND public.app_administra_grupo(p_grupo)
        GROUP BY i.concepto, i.nivel = 'crear' ORDER BY i.concepto, i.nivel = 'crear' $$;

-- Nivel de Bloom alcanzado en el tema: cuántas personas en cada nivel ('sin empezar' si nunca practicaron el tema).
CREATE FUNCTION aula_panel_bloom(p_grupo uuid, p_tema text) RETURNS TABLE (nivel text, personas integer)
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$ SELECT coalesce(c.nivel_bloom, 'sin empezar'), count(*)::int
        FROM public.miembro_grupo m JOIN public.usuario u ON u.id = m.usuario_id AND u.activo
        LEFT JOIN public.competencia c ON c.usuario_id = m.usuario_id AND c.tecnica_id = p_tema
        WHERE m.grupo_id = p_grupo AND public.app_administra_grupo(p_grupo)
        GROUP BY 1 ORDER BY 1 $$;

-- Quién lleva p_dias o más sin practicar en el Dojo (dias nulo: nunca practicó). Nombre y conteo, nunca respuestas.
CREATE FUNCTION aula_panel_sin_practicar(p_grupo uuid, p_hoy date, p_dias integer) RETURNS TABLE (nombre text, dias integer)
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$ SELECT u.nombre, (p_hoy - max(i.dia))::int
        FROM public.miembro_grupo m JOIN public.usuario u ON u.id = m.usuario_id AND u.activo
        LEFT JOIN public.intento_dojo i ON i.usuario_id = m.usuario_id
        WHERE m.grupo_id = p_grupo AND public.app_administra_grupo(p_grupo)
        GROUP BY u.id, u.nombre, u.nombre_clave
        HAVING max(i.dia) IS NULL OR p_hoy - max(i.dia) >= p_dias
        ORDER BY max(i.dia) NULLS FIRST, u.nombre_clave $$;

DO $$
DECLARE f text;
BEGIN
  FOREACH f IN ARRAY ARRAY['aula_crear_estudiante(uuid, uuid, text)', 'aula_clase_por_codigo(uuid, text, timestamptz)',
                           'aula_lista_de_clase(uuid, text, timestamptz)',
                           'aula_panel_resumen(uuid, timestamptz, timestamptz, text, date)', 'aula_panel_conceptos(uuid, text)',
                           'aula_panel_bloom(uuid, text)', 'aula_panel_sin_practicar(uuid, date, integer)'] LOOP
    EXECUTE format('REVOKE ALL ON FUNCTION %s FROM PUBLIC', f);
    EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO ${rol_app}', f);
  END LOOP;
END $$;
