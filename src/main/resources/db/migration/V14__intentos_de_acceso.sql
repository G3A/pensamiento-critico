-- Hito 9 · Límite de intentos con retardo progresivo (innegociable 3, RF-15).
-- Un contador por clave: la cuenta ("cuenta:<clave del nombre>", exista o no) y la IP ("ip:<dirección>") dentro de una
-- institución, y la IP sin institución para el tope global contra quien prueba en muchas. Se lee y se escribe antes de
-- saber quién es la persona, así que el rol de la app no tiene permisos sobre la tabla: todo pasa por funciones
-- SECURITY DEFINER con search_path fijo, acotadas a la institución que reciben.

CREATE TABLE intento_acceso (
  institucion_id uuid REFERENCES institucion(id) ON DELETE CASCADE,   -- NULL: el tope global por IP
  clave          text NOT NULL,
  fallos         integer NOT NULL CHECK (fallos >= 0),
  ventana_desde  timestamptz NOT NULL,
  espera_hasta   timestamptz
);
CREATE UNIQUE INDEX intento_acceso_clave_idx ON intento_acceso (coalesce(institucion_id, '00000000-0000-0000-0000-000000000000'::uuid), clave);

ALTER TABLE intento_acceso ENABLE ROW LEVEL SECURITY;
ALTER TABLE intento_acceso FORCE ROW LEVEL SECURITY;   -- sin políticas: el rol de la app no ve ninguna fila directamente

CREATE FUNCTION intento_leer(p_institucion uuid, p_clave text)
  RETURNS TABLE (fallos integer, ventana_desde timestamptz, espera_hasta timestamptz)
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$ SELECT i.fallos, i.ventana_desde, i.espera_hasta FROM public.intento_acceso i
        WHERE i.institucion_id IS NOT DISTINCT FROM p_institucion AND i.clave = p_clave $$;

CREATE FUNCTION intento_guardar(p_institucion uuid, p_clave text, p_fallos integer, p_ventana_desde timestamptz, p_espera_hasta timestamptz)
  RETURNS void
  LANGUAGE sql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$ INSERT INTO public.intento_acceso (institucion_id, clave, fallos, ventana_desde, espera_hasta)
        VALUES (p_institucion, p_clave, p_fallos, p_ventana_desde, p_espera_hasta)
        ON CONFLICT (coalesce(institucion_id, '00000000-0000-0000-0000-000000000000'::uuid), clave)
        DO UPDATE SET fallos = EXCLUDED.fallos, ventana_desde = EXCLUDED.ventana_desde, espera_hasta = EXCLUDED.espera_hasta $$;

CREATE FUNCTION intento_borrar(p_institucion uuid, p_clave text) RETURNS void
  LANGUAGE sql SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$ DELETE FROM public.intento_acceso WHERE institucion_id IS NOT DISTINCT FROM p_institucion AND clave = p_clave $$;

-- Las cuentas de una institución que esperan ahora (para que el administrador levante la espera).
CREATE FUNCTION intento_esperas(p_institucion uuid, p_ahora timestamptz) RETURNS TABLE (clave text, espera_hasta timestamptz)
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, pg_temp
  AS $$ SELECT i.clave, i.espera_hasta FROM public.intento_acceso i
        WHERE i.institucion_id = p_institucion AND i.clave LIKE 'cuenta:%' AND i.espera_hasta > p_ahora ORDER BY i.clave $$;

DO $$
DECLARE
  f text;
BEGIN
  FOREACH f IN ARRAY ARRAY['intento_leer(uuid, text)', 'intento_guardar(uuid, text, integer, timestamptz, timestamptz)',
      'intento_borrar(uuid, text)', 'intento_esperas(uuid, timestamptz)'] LOOP
    EXECUTE format('REVOKE ALL ON FUNCTION %s FROM PUBLIC', f);
    EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO ${rol_app}', f);
  END LOOP;
END $$;
