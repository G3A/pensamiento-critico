-- Hito 8 · Servicio para varias instituciones (sección 11 del documento).
-- 1. Cada institución tiene una ruta de entrada (/<ruta>) y un estado; la existente recibe la ruta "local".
-- 2. La tabla institucion queda bajo RLS: el rol de la app solo ve la suya y ya no puede insertar. La ruta de entrada se
--    resuelve con una función SECURITY DEFINER que devuelve solo el id de una institución activa.
-- 3. El nombre de usuario es único sin distinguir mayúsculas, tildes ni espacios repetidos dentro de cada institución.

ALTER TABLE institucion
  ADD COLUMN ruta   text,
  ADD COLUMN estado text NOT NULL DEFAULT 'activa' CHECK (estado IN ('activa', 'suspendida'));

-- La primera institución (la de la instalación de la versión 1) entra por /local; cualquier otra que exista (bases de
-- prueba con instituciones sembradas) recibe una ruta derivada de su id.
UPDATE institucion SET ruta = 'local' WHERE id = (SELECT id FROM institucion ORDER BY creada_en, id LIMIT 1);
UPDATE institucion SET ruta = 'i-' || replace(id::text, '-', '') WHERE ruta IS NULL;

ALTER TABLE institucion
  ALTER COLUMN ruta SET NOT NULL,
  ADD CONSTRAINT institucion_ruta_unica UNIQUE (ruta),
  -- minúsculas, dígitos y guiones, sin tildes; de 3 a 40 caracteres; sin guion al principio ni al final
  ADD CONSTRAINT institucion_ruta_formato CHECK (ruta ~ '^[a-z0-9][a-z0-9-]{1,38}[a-z0-9]$');

REVOKE INSERT, UPDATE, DELETE ON institucion FROM ${rol_app};
ALTER TABLE institucion ENABLE ROW LEVEL SECURITY;
ALTER TABLE institucion FORCE ROW LEVEL SECURITY;
CREATE POLICY institucion_propia ON institucion FOR SELECT USING (id = app_institucion_actual());

-- Resolver la ruta de entrada antes de que haya sesión: solo el id, y solo si la institución está activa.
CREATE FUNCTION app_institucion_por_ruta(p_ruta text) RETURNS uuid
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$ SELECT id FROM public.institucion WHERE ruta = p_ruta AND estado = 'activa' $$;
REVOKE ALL ON FUNCTION app_institucion_por_ruta(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app_institucion_por_ruta(text) TO ${rol_app};

-- Clave del nombre: sin espacios al borde ni repetidos, sin tildes ni diéresis, en minúsculas.
CREATE FUNCTION app_clave_nombre(p_nombre text) RETURNS text
  LANGUAGE sql IMMUTABLE PARALLEL SAFE
  AS $$ SELECT lower(translate(btrim(regexp_replace(p_nombre, '\s+', ' ', 'g')),
                               'ÁÀÂÄÃÉÈÊËÍÌÎÏÓÒÔÖÕÚÙÛÜÑÇáàâäãéèêëíìîïóòôöõúùûüñç',
                               'AAAAAEEEEIIIIOOOOOUUUUNCaaaaaeeeeiiiiooooouuuunc')) $$;

ALTER TABLE usuario ADD COLUMN nombre_clave text GENERATED ALWAYS AS (app_clave_nombre(nombre)) STORED;
ALTER TABLE usuario DROP CONSTRAINT usuario_institucion_id_nombre_key;
ALTER TABLE usuario ADD CONSTRAINT usuario_nombre_unico UNIQUE (institucion_id, nombre_clave);
