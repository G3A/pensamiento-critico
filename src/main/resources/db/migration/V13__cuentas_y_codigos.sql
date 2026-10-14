-- Hito 9 · Puerta de entrada (sección 11 del documento), entregables 1 a 4.
-- 1. La credencial deja de ser un PIN: usuario.pin_hash pasa a clave_hash con su tipo. Las cuentas que ya existen quedan
--    con tipo 'pin' (entran una última vez con su PIN y eligen su contraseña); una cuenta recién creada no tiene clave
--    ('ninguna') hasta que la persona canjea su código de alta.
-- 2. Códigos de un uso para dar de alta y para restablecer: solo el hash (SHA-256 del código normalizado), con caducidad.
-- 3. Lo que se consulta antes de saber quién es la persona (buscar por nombre al entrar, canjear un código) pasa por
--    funciones SECURITY DEFINER con search_path fijo, acotadas a la institución de la ruta.

ALTER TABLE usuario RENAME COLUMN pin_hash TO clave_hash;
ALTER TABLE usuario ALTER COLUMN clave_hash DROP NOT NULL;
ALTER TABLE usuario ADD COLUMN tipo_clave text NOT NULL DEFAULT 'pin' CHECK (tipo_clave IN ('pin', 'contrasena', 'ninguna'));
ALTER TABLE usuario ALTER COLUMN tipo_clave SET DEFAULT 'ninguna';
ALTER TABLE usuario ADD CONSTRAINT usuario_clave_segun_tipo CHECK ((tipo_clave = 'ninguna') = (clave_hash IS NULL));

-- Búsqueda paginada de /usuarios: por la clave del nombre, que ya no distingue mayúsculas ni tildes.
CREATE INDEX usuario_busqueda_idx ON usuario (institucion_id, nombre_clave text_pattern_ops);

CREATE TABLE codigo_cuenta (
  id             uuid PRIMARY KEY DEFAULT uuidv7(),
  institucion_id uuid NOT NULL REFERENCES institucion(id) ON DELETE RESTRICT,
  usuario_id     uuid NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
  tipo           text NOT NULL CHECK (tipo IN ('alta', 'restablecer')),
  codigo_hash    text NOT NULL,
  creado_por     uuid REFERENCES usuario(id) ON DELETE SET NULL,
  creado_en      timestamptz NOT NULL DEFAULT now(),
  caduca_en      timestamptz NOT NULL,
  usado_en       timestamptz
);
CREATE INDEX codigo_cuenta_hash_idx ON codigo_cuenta (institucion_id, codigo_hash) WHERE usado_en IS NULL;
CREATE INDEX codigo_cuenta_usuario_idx ON codigo_cuenta (usuario_id);

GRANT SELECT, INSERT, UPDATE ON codigo_cuenta TO ${rol_app};
ALTER TABLE codigo_cuenta ENABLE ROW LEVEL SECURITY;
ALTER TABLE codigo_cuenta FORCE ROW LEVEL SECURITY;
CREATE POLICY codigo_cuenta_por_institucion ON codigo_cuenta
  USING (institucion_id = app_institucion_actual())
  WITH CHECK (institucion_id = app_institucion_actual());

-- Entrar: la cuenta de esa institución cuyo nombre coincide sin distinguir mayúsculas, tildes ni espacios repetidos.
CREATE FUNCTION cuenta_para_entrar(p_institucion uuid, p_nombre text)
  RETURNS TABLE (id uuid, institucion_id uuid, nombre text, clave_hash text, tipo_clave text, rol_global text, activo boolean)
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$ SELECT u.id, u.institucion_id, u.nombre, u.clave_hash, u.tipo_clave, u.rol_global, u.activo
        FROM public.usuario u JOIN public.institucion i ON i.id = u.institucion_id
        WHERE u.institucion_id = p_institucion AND i.estado = 'activa' AND u.nombre_clave = public.app_clave_nombre(p_nombre) $$;

-- Canjear un código: lo marca como usado una sola vez y devuelve la cuenta y el tipo; nada si no existe, ya se usó,
-- caducó, la cuenta está desactivada o la institución suspendida.
CREATE FUNCTION cuenta_canjear_codigo(p_institucion uuid, p_codigo_hash text, p_ahora timestamptz)
  RETURNS TABLE (usuario_id uuid, tipo text)
  LANGUAGE plpgsql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$
DECLARE
  el_codigo uuid;
BEGIN
  SELECT c.id INTO el_codigo FROM public.codigo_cuenta c
    JOIN public.usuario u ON u.id = c.usuario_id
    JOIN public.institucion i ON i.id = c.institucion_id
    WHERE c.institucion_id = p_institucion AND c.codigo_hash = p_codigo_hash AND c.usado_en IS NULL
      AND c.caduca_en > p_ahora AND u.activo AND i.estado = 'activa'
    ORDER BY c.creado_en DESC LIMIT 1
    FOR UPDATE OF c;
  IF el_codigo IS NULL THEN
    RETURN;
  END IF;
  RETURN QUERY UPDATE public.codigo_cuenta SET usado_en = p_ahora WHERE id = el_codigo AND usado_en IS NULL
    RETURNING codigo_cuenta.usuario_id, codigo_cuenta.tipo;
END $$;

REVOKE ALL ON FUNCTION cuenta_para_entrar(uuid, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION cuenta_canjear_codigo(uuid, text, timestamptz) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION cuenta_para_entrar(uuid, text) TO ${rol_app};
GRANT EXECUTE ON FUNCTION cuenta_canjear_codigo(uuid, text, timestamptz) TO ${rol_app};

-- La auditoría registra también lo que pasa con las cuentas y al entrar (nunca la contraseña).
ALTER TABLE auditoria DROP CONSTRAINT auditoria_accion_check;
ALTER TABLE auditoria ADD CONSTRAINT auditoria_accion_check CHECK (accion IN ('crear', 'importar', 'exportar', 'compartir', 'borrar',
  'desactivar', 'sesion', 'reactivar', 'codigo_alta', 'codigo_restablecer', 'canjear_codigo', 'cambiar_clave', 'entrar',
  'fallo_entrar', 'espera_entrar', 'levantar_espera', 'registrar_llave', 'usar_recuperacion'));
ALTER TABLE auditoria ADD COLUMN detalle text;   -- p. ej., la clave del nombre con que se probó; nunca una contraseña

-- "¿No tienes cuenta?" (RF-18): a quién pedirla, configurable por institución ("tu profe", "quien instaló la app").
ALTER TABLE institucion ADD COLUMN quien_da_cuentas text NOT NULL DEFAULT 'quien administra la app en tu institución'
  CHECK (length(quien_da_cuentas) BETWEEN 1 AND 120);

-- Se lee antes de entrar: solo ese texto y solo de una institución activa.
CREATE FUNCTION institucion_quien_da_cuentas(p_institucion uuid) RETURNS text
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$ SELECT quien_da_cuentas FROM public.institucion WHERE id = p_institucion AND estado = 'activa' $$;

-- Lo cambia el administrador, solo en la institución fijada en su transacción.
CREATE FUNCTION institucion_cambiar_quien_da_cuentas(p_institucion uuid, p_texto text) RETURNS boolean
  LANGUAGE sql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$ UPDATE public.institucion SET quien_da_cuentas = p_texto
        WHERE id = p_institucion AND id = public.app_institucion_actual() RETURNING true $$;

REVOKE ALL ON FUNCTION institucion_quien_da_cuentas(uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION institucion_cambiar_quien_da_cuentas(uuid, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION institucion_quien_da_cuentas(uuid) TO ${rol_app};
GRANT EXECUTE ON FUNCTION institucion_cambiar_quien_da_cuentas(uuid, text) TO ${rol_app};

-- Mirar un código sin gastarlo (para validar la contraseña según el rol de la cuenta antes de canjearlo).
CREATE FUNCTION cuenta_codigo_vigente(p_institucion uuid, p_codigo_hash text, p_ahora timestamptz)
  RETURNS TABLE (usuario_id uuid, tipo text)
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$ SELECT c.usuario_id, c.tipo FROM public.codigo_cuenta c
        JOIN public.usuario u ON u.id = c.usuario_id
        JOIN public.institucion i ON i.id = c.institucion_id
        WHERE c.institucion_id = p_institucion AND c.codigo_hash = p_codigo_hash AND c.usado_en IS NULL
          AND c.caduca_en > p_ahora AND u.activo AND i.estado = 'activa'
        ORDER BY c.creado_en DESC LIMIT 1 $$;
REVOKE ALL ON FUNCTION cuenta_codigo_vigente(uuid, text, timestamptz) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION cuenta_codigo_vigente(uuid, text, timestamptz) TO ${rol_app};
