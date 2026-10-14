-- Hito 9 · Llave de acceso (passkey, WebAuthn) como segundo factor de los adultos (ronda 4 de la sección 11).
-- Cada llave es de una cuenta de una institución o del operador del servicio. Los códigos de recuperación (de un solo uso)
-- se guardan solo como hash. Ninguna de las dos tablas tiene permisos ni políticas para el rol de la app: todo pasa por
-- funciones SECURITY DEFINER con search_path fijo que reciben a quién pertenece; para una cuenta exigen además que su
-- institución sea la fijada en la transacción (app_institucion_actual).

CREATE TABLE llave_acceso (
  credencial_id   bytea PRIMARY KEY,
  usuario_id      uuid REFERENCES usuario(id) ON DELETE CASCADE,
  institucion_id  uuid REFERENCES institucion(id) ON DELETE CASCADE,
  operador_id     uuid REFERENCES operador(id) ON DELETE CASCADE,
  entidad_usuario bytea NOT NULL,          -- el "user handle" de WebAuthn
  clave_publica   bytea NOT NULL,          -- COSE
  firmas          bigint NOT NULL CHECK (firmas >= 0),
  uv_inicializada boolean NOT NULL,
  transportes     text[] NOT NULL,
  respaldable     boolean NOT NULL,
  respaldada      boolean NOT NULL,
  atestacion      bytea,
  datos_cliente   bytea,
  etiqueta        text NOT NULL,
  creada_en       timestamptz NOT NULL,
  usada_en        timestamptz NOT NULL,
  CONSTRAINT llave_de_cuenta_o_de_operador CHECK (
    (usuario_id IS NOT NULL AND institucion_id IS NOT NULL AND operador_id IS NULL)
    OR (operador_id IS NOT NULL AND usuario_id IS NULL AND institucion_id IS NULL))
);
CREATE INDEX llave_acceso_usuario_idx ON llave_acceso (usuario_id);
CREATE INDEX llave_acceso_operador_idx ON llave_acceso (operador_id);

CREATE TABLE codigo_recuperacion (
  id             uuid PRIMARY KEY DEFAULT uuidv7(),
  usuario_id     uuid REFERENCES usuario(id) ON DELETE CASCADE,
  institucion_id uuid REFERENCES institucion(id) ON DELETE CASCADE,
  operador_id    uuid REFERENCES operador(id) ON DELETE CASCADE,
  codigo_hash    text NOT NULL,
  creado_en      timestamptz NOT NULL DEFAULT now(),
  usado_en       timestamptz,
  CONSTRAINT recuperacion_de_cuenta_o_de_operador CHECK (
    (usuario_id IS NOT NULL AND institucion_id IS NOT NULL AND operador_id IS NULL)
    OR (operador_id IS NOT NULL AND usuario_id IS NULL AND institucion_id IS NULL))
);
CREATE INDEX codigo_recuperacion_usuario_idx ON codigo_recuperacion (usuario_id) WHERE usado_en IS NULL;
CREATE INDEX codigo_recuperacion_operador_idx ON codigo_recuperacion (operador_id) WHERE usado_en IS NULL;

ALTER TABLE llave_acceso ENABLE ROW LEVEL SECURITY;
ALTER TABLE llave_acceso FORCE ROW LEVEL SECURITY;
ALTER TABLE codigo_recuperacion ENABLE ROW LEVEL SECURITY;
ALTER TABLE codigo_recuperacion FORCE ROW LEVEL SECURITY;

-- ¿Es este titular el que puede tocar la app ahora? Una cuenta: de la institución fijada. El operador: que exista.
CREATE FUNCTION llave_titular_valido(p_usuario uuid, p_institucion uuid, p_operador uuid) RETURNS boolean
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$ SELECT CASE
          WHEN p_usuario IS NOT NULL AND p_operador IS NULL THEN
            p_institucion = public.app_institucion_actual()
            AND EXISTS (SELECT 1 FROM public.usuario u WHERE u.id = p_usuario AND u.institucion_id = p_institucion)
          WHEN p_operador IS NOT NULL AND p_usuario IS NULL THEN
            EXISTS (SELECT 1 FROM public.operador o WHERE o.id = p_operador)
          ELSE false END $$;

CREATE FUNCTION llave_guardar(p_usuario uuid, p_institucion uuid, p_operador uuid, p_credencial bytea, p_entidad bytea,
                              p_clave bytea, p_firmas bigint, p_uv boolean, p_transportes text[], p_respaldable boolean,
                              p_respaldada boolean, p_atestacion bytea, p_datos_cliente bytea, p_etiqueta text,
                              p_creada timestamptz, p_usada timestamptz) RETURNS boolean
  LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$
BEGIN
  IF NOT public.llave_titular_valido(p_usuario, p_institucion, p_operador) THEN
    RETURN false;
  END IF;
  INSERT INTO public.llave_acceso (credencial_id, usuario_id, institucion_id, operador_id, entidad_usuario, clave_publica, firmas,
      uv_inicializada, transportes, respaldable, respaldada, atestacion, datos_cliente, etiqueta, creada_en, usada_en)
    VALUES (p_credencial, p_usuario, CASE WHEN p_usuario IS NULL THEN NULL ELSE p_institucion END, p_operador, p_entidad, p_clave,
      p_firmas, p_uv, p_transportes, p_respaldable, p_respaldada, p_atestacion, p_datos_cliente, p_etiqueta, p_creada, p_usada)
    ON CONFLICT (credencial_id) DO UPDATE SET firmas = EXCLUDED.firmas, uv_inicializada = EXCLUDED.uv_inicializada,
      respaldada = EXCLUDED.respaldada, usada_en = EXCLUDED.usada_en, etiqueta = EXCLUDED.etiqueta
      WHERE llave_acceso.usuario_id IS NOT DISTINCT FROM EXCLUDED.usuario_id
        AND llave_acceso.operador_id IS NOT DISTINCT FROM EXCLUDED.operador_id;
  RETURN FOUND;
END $$;

CREATE FUNCTION llaves_de(p_usuario uuid, p_institucion uuid, p_operador uuid)
  RETURNS SETOF llave_acceso
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$ SELECT * FROM public.llave_acceso l
        WHERE public.llave_titular_valido(p_usuario, p_institucion, p_operador)
          AND l.usuario_id IS NOT DISTINCT FROM p_usuario AND l.operador_id IS NOT DISTINCT FROM p_operador
        ORDER BY l.creada_en, l.credencial_id $$;

CREATE FUNCTION llave_borrar(p_usuario uuid, p_institucion uuid, p_operador uuid, p_credencial bytea) RETURNS boolean
  LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$
BEGIN
  IF NOT public.llave_titular_valido(p_usuario, p_institucion, p_operador) THEN
    RETURN false;
  END IF;
  DELETE FROM public.llave_acceso l WHERE l.credencial_id = p_credencial
    AND l.usuario_id IS NOT DISTINCT FROM p_usuario AND l.operador_id IS NOT DISTINCT FROM p_operador;
  RETURN FOUND;
END $$;

-- Los códigos de recuperación nuevos reemplazan a los que quedaban sin usar.
CREATE FUNCTION recuperacion_reemplazar(p_usuario uuid, p_institucion uuid, p_operador uuid, p_hashes text[]) RETURNS boolean
  LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$
BEGIN
  IF NOT public.llave_titular_valido(p_usuario, p_institucion, p_operador) THEN
    RETURN false;
  END IF;
  DELETE FROM public.codigo_recuperacion c WHERE c.usado_en IS NULL
    AND c.usuario_id IS NOT DISTINCT FROM p_usuario AND c.operador_id IS NOT DISTINCT FROM p_operador;
  INSERT INTO public.codigo_recuperacion (usuario_id, institucion_id, operador_id, codigo_hash)
    SELECT p_usuario, CASE WHEN p_usuario IS NULL THEN NULL ELSE p_institucion END, p_operador, h FROM unnest(p_hashes) AS h;
  RETURN true;
END $$;

CREATE FUNCTION recuperacion_vigentes(p_usuario uuid, p_institucion uuid, p_operador uuid) RETURNS TABLE (id uuid, codigo_hash text)
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$ SELECT c.id, c.codigo_hash FROM public.codigo_recuperacion c
        WHERE public.llave_titular_valido(p_usuario, p_institucion, p_operador) AND c.usado_en IS NULL
          AND c.usuario_id IS NOT DISTINCT FROM p_usuario AND c.operador_id IS NOT DISTINCT FROM p_operador
        ORDER BY c.id $$;

CREATE FUNCTION recuperacion_usar(p_usuario uuid, p_institucion uuid, p_operador uuid, p_id uuid, p_ahora timestamptz) RETURNS boolean
  LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$
BEGIN
  IF NOT public.llave_titular_valido(p_usuario, p_institucion, p_operador) THEN
    RETURN false;
  END IF;
  UPDATE public.codigo_recuperacion c SET usado_en = p_ahora WHERE c.id = p_id AND c.usado_en IS NULL
    AND c.usuario_id IS NOT DISTINCT FROM p_usuario AND c.operador_id IS NOT DISTINCT FROM p_operador;
  RETURN FOUND;
END $$;

DO $$
DECLARE
  f text;
BEGIN
  FOREACH f IN ARRAY ARRAY['llave_titular_valido(uuid, uuid, uuid)',
      'llave_guardar(uuid, uuid, uuid, bytea, bytea, bytea, bigint, boolean, text[], boolean, boolean, bytea, bytea, text, timestamptz, timestamptz)',
      'llaves_de(uuid, uuid, uuid)', 'llave_borrar(uuid, uuid, uuid, bytea)', 'recuperacion_reemplazar(uuid, uuid, uuid, text[])',
      'recuperacion_vigentes(uuid, uuid, uuid)', 'recuperacion_usar(uuid, uuid, uuid, uuid, timestamptz)'] LOOP
    EXECUTE format('REVOKE ALL ON FUNCTION %s FROM PUBLIC', f);
    EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO ${rol_app}', f);
  END LOOP;
END $$;
