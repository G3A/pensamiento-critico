-- Hito 8 · Operador del servicio (sección 11 del documento).
-- El operador está fuera de toda institución: crea instituciones, las suspende o reactiva e invita a su primer
-- administrador. No lee datos de ninguna institución. Todo lo que hace pasa por funciones SECURITY DEFINER con search_path
-- fijo, que son lo único que el rol de la app puede tocar de estas tablas, y queda en auditoria_operador.

CREATE TABLE operador (
  id         uuid PRIMARY KEY DEFAULT uuidv7(),
  nombre     text NOT NULL UNIQUE,
  clave_hash text NOT NULL,
  creado_en  timestamptz NOT NULL DEFAULT now()
);

-- Invitación de un solo uso para el primer administrador: el código se guarda solo como hash (Argon2) y caduca.
CREATE TABLE invitacion (
  id             uuid PRIMARY KEY DEFAULT uuidv7(),
  institucion_id uuid NOT NULL REFERENCES institucion(id) ON DELETE CASCADE,
  codigo_hash    text NOT NULL,
  creada_por     uuid NOT NULL REFERENCES operador(id),
  creada_en      timestamptz NOT NULL DEFAULT now(),
  caduca_en      timestamptz NOT NULL,
  usada_en       timestamptz
);
CREATE INDEX invitacion_vigente_idx ON invitacion (institucion_id) WHERE usada_en IS NULL;

CREATE TABLE auditoria_operador (                     -- solo inserción
  id             uuid PRIMARY KEY DEFAULT uuidv7(),
  operador_id    uuid REFERENCES operador(id),
  accion         text NOT NULL CHECK (accion IN ('crear_institucion', 'suspender', 'reactivar', 'invitar', 'canjear_invitacion',
                                                 'borrar_institucion')),   -- borrar: docker/respaldo/institucion.sh
  institucion_id uuid,                                -- sin FK: la constancia sobrevive al borrado de la institución
  ruta           text,
  fecha          timestamptz NOT NULL DEFAULT now()
);

-- Ningún permiso para el rol de la app; RLS forzada y sin políticas por si alguien concede uno sin querer.
ALTER TABLE operador ENABLE ROW LEVEL SECURITY;
ALTER TABLE operador FORCE ROW LEVEL SECURITY;
ALTER TABLE invitacion ENABLE ROW LEVEL SECURITY;
ALTER TABLE invitacion FORCE ROW LEVEL SECURITY;
ALTER TABLE auditoria_operador ENABLE ROW LEVEL SECURITY;
ALTER TABLE auditoria_operador FORCE ROW LEVEL SECURITY;

-- ---------------------------------------------------------------------------------------------
-- Funciones del operador (dueño: el rol administrador de la base; EXECUTE solo para el rol de la app)
-- ---------------------------------------------------------------------------------------------

CREATE FUNCTION operador_por_nombre(p_nombre text) RETURNS TABLE (id uuid, clave_hash text)
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$ SELECT o.id, o.clave_hash FROM public.operador o WHERE o.nombre = p_nombre $$;

-- Primer arranque: crea el operador solo si todavía no hay ninguno. Devuelve si lo creó.
CREATE FUNCTION operador_crear_si_no_hay(p_nombre text, p_clave_hash text) RETURNS boolean
  LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$
BEGIN
  LOCK TABLE public.operador IN EXCLUSIVE MODE;
  IF EXISTS (SELECT 1 FROM public.operador) THEN
    RETURN false;
  END IF;
  INSERT INTO public.operador (nombre, clave_hash) VALUES (p_nombre, p_clave_hash);
  RETURN true;
END $$;

CREATE FUNCTION operador_verificar(p_operador uuid) RETURNS void
  LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM public.operador WHERE id = p_operador) THEN
    RAISE EXCEPTION 'operador desconocido' USING ERRCODE = '42501';
  END IF;
END $$;

-- Solo lo que el operador necesita para administrar: nada de personas ni de su trabajo, salvo si ya hay alguna cuenta.
CREATE FUNCTION operador_instituciones()
  RETURNS TABLE (id uuid, nombre text, ruta text, estado text, creada_en timestamptz, tiene_cuentas boolean)
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$ SELECT i.id, i.nombre, i.ruta, i.estado, i.creada_en, EXISTS (SELECT 1 FROM public.usuario u WHERE u.institucion_id = i.id)
        FROM public.institucion i ORDER BY i.creada_en, i.id $$;

CREATE FUNCTION operador_crear_institucion(p_operador uuid, p_nombre text, p_ruta text) RETURNS uuid
  LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$
DECLARE
  nueva uuid;
BEGIN
  PERFORM public.operador_verificar(p_operador);
  INSERT INTO public.institucion (nombre, ruta) VALUES (p_nombre, p_ruta) RETURNING id INTO nueva;
  INSERT INTO public.auditoria_operador (operador_id, accion, institucion_id, ruta) VALUES (p_operador, 'crear_institucion', nueva, p_ruta);
  RETURN nueva;
END $$;

CREATE FUNCTION operador_cambiar_estado(p_operador uuid, p_institucion uuid, p_estado text) RETURNS boolean
  LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$
DECLARE
  la_ruta text;
BEGIN
  PERFORM public.operador_verificar(p_operador);
  UPDATE public.institucion SET estado = p_estado WHERE id = p_institucion RETURNING ruta INTO la_ruta;
  IF la_ruta IS NULL THEN
    RETURN false;
  END IF;
  INSERT INTO public.auditoria_operador (operador_id, accion, institucion_id, ruta)
    VALUES (p_operador, CASE p_estado WHEN 'suspendida' THEN 'suspender' ELSE 'reactivar' END, p_institucion, la_ruta);
  RETURN true;
END $$;

-- Una invitación nueva deja sin efecto las anteriores de la misma institución que no se usaron.
CREATE FUNCTION operador_invitar(p_operador uuid, p_institucion uuid, p_codigo_hash text, p_caduca_en timestamptz) RETURNS uuid
  LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$
DECLARE
  nueva uuid;
  la_ruta text;
BEGIN
  PERFORM public.operador_verificar(p_operador);
  SELECT ruta INTO la_ruta FROM public.institucion WHERE id = p_institucion;
  IF la_ruta IS NULL THEN
    RAISE EXCEPTION 'institución desconocida' USING ERRCODE = '23503';
  END IF;
  UPDATE public.invitacion SET caduca_en = least(caduca_en, now()) WHERE institucion_id = p_institucion AND usada_en IS NULL;
  INSERT INTO public.invitacion (institucion_id, codigo_hash, creada_por, caduca_en)
    VALUES (p_institucion, p_codigo_hash, p_operador, p_caduca_en) RETURNING id INTO nueva;
  INSERT INTO public.auditoria_operador (operador_id, accion, institucion_id, ruta) VALUES (p_operador, 'invitar', p_institucion, la_ruta);
  RETURN nueva;
END $$;

-- Para canjear: las invitaciones vigentes de una institución activa (id y hash; el código en claro nunca se guarda).
CREATE FUNCTION invitaciones_vigentes(p_institucion uuid) RETURNS TABLE (id uuid, codigo_hash text)
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$ SELECT v.id, v.codigo_hash FROM public.invitacion v JOIN public.institucion i ON i.id = v.institucion_id
        WHERE v.institucion_id = p_institucion AND i.estado = 'activa' AND v.usada_en IS NULL AND v.caduca_en > now() $$;

-- Marca la invitación como usada una sola vez; devuelve si lo logró (false: ya usada, caducada o inexistente).
CREATE FUNCTION canjear_invitacion(p_invitacion uuid) RETURNS boolean
  LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$
DECLARE
  la_institucion uuid;
BEGIN
  UPDATE public.invitacion SET usada_en = now()
    WHERE id = p_invitacion AND usada_en IS NULL AND caduca_en > now() RETURNING institucion_id INTO la_institucion;
  IF la_institucion IS NULL THEN
    RETURN false;
  END IF;
  INSERT INTO public.auditoria_operador (operador_id, accion, institucion_id, ruta)
    SELECT NULL, 'canjear_invitacion', la_institucion, ruta FROM public.institucion WHERE id = la_institucion;
  RETURN true;
END $$;

-- La auditoría propia del operador, de la más reciente a la más antigua.
CREATE FUNCTION operador_auditoria(p_limite integer)
  RETURNS TABLE (fecha timestamptz, accion text, ruta text, operador_id uuid, institucion_id uuid)
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$ SELECT a.fecha, a.accion, a.ruta, a.operador_id, a.institucion_id FROM public.auditoria_operador a
        ORDER BY a.fecha DESC, a.id DESC LIMIT p_limite $$;

DO $$
DECLARE
  f text;
BEGIN
  FOREACH f IN ARRAY ARRAY['operador_por_nombre(text)', 'operador_crear_si_no_hay(text, text)', 'operador_verificar(uuid)',
      'operador_instituciones()', 'operador_crear_institucion(uuid, text, text)', 'operador_cambiar_estado(uuid, uuid, text)',
      'operador_invitar(uuid, uuid, text, timestamptz)', 'invitaciones_vigentes(uuid)', 'canjear_invitacion(uuid)',
      'operador_auditoria(integer)'] LOOP
    EXECUTE format('REVOKE ALL ON FUNCTION %s FROM PUBLIC', f);
    EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO ${rol_app}', f);
  END LOOP;
END $$;
