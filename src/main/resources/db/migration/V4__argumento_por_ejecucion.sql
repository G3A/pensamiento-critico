-- V4 (hito 2, RF-09): cada argumento que produce una ejecución (T01, T02, T06 y el Taller) queda ligado a ella,
-- con su orden dentro de la ejecución. Así el mapa se vuelve a leer completo, el respaldo de una persona los
-- exporta con su ejecución y borrar la ejecución (físicamente, al borrar al usuario) se los lleva.

ALTER TABLE argumento ADD COLUMN ejecucion_id uuid REFERENCES ejecucion(id) ON DELETE CASCADE;
ALTER TABLE argumento ADD COLUMN orden integer NOT NULL DEFAULT 1;
CREATE INDEX argumento_ejecucion_idx ON argumento (ejecucion_id, orden);
CREATE INDEX argumento_usuario_idx ON argumento (usuario_id);
CREATE INDEX premisa_argumento_afirmacion_idx ON premisa_argumento (afirmacion_id);

-- Importar rechaza también identificadores de argumento que ya son de otra persona (amplía V3).
CREATE OR REPLACE FUNCTION app_id_de_otro_usuario(candidato uuid) RETURNS boolean
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
  SELECT EXISTS (SELECT 1 FROM expediente WHERE id = candidato AND usuario_id IS DISTINCT FROM app_usuario_actual())
      OR EXISTS (SELECT 1 FROM ejecucion  WHERE id = candidato AND usuario_id IS DISTINCT FROM app_usuario_actual())
      OR EXISTS (SELECT 1 FROM afirmacion WHERE id = candidato AND usuario_id IS DISTINCT FROM app_usuario_actual())
      OR EXISTS (SELECT 1 FROM argumento  WHERE id = candidato AND usuario_id IS DISTINCT FROM app_usuario_actual())
$$;

REVOKE ALL ON FUNCTION app_id_de_otro_usuario(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app_id_de_otro_usuario(uuid) TO ${rol_app};
