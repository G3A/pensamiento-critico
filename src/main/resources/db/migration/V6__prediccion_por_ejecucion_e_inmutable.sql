-- V6 (hito 4, R05): cada predicción del Diario de decisiones (T32) queda ligada a la ejecución que la declaró, así se
-- exporta con ella, se borra con ella y otra persona recibe 404. Una predicción resuelta es inmutable: la base lo exige
-- además del dominio, para que ningún camino la cambie.

ALTER TABLE prediccion ADD COLUMN ejecucion_id uuid REFERENCES ejecucion(id) ON DELETE CASCADE;
CREATE INDEX prediccion_usuario_idx ON prediccion (usuario_id, resultado, fecha_revision);
CREATE INDEX prediccion_ejecucion_idx ON prediccion (ejecucion_id);

ALTER TABLE prediccion ADD CONSTRAINT prediccion_resuelta_con_fecha
  CHECK ((resultado = 'pendiente') = (resuelta_en IS NULL));

CREATE OR REPLACE FUNCTION prediccion_resuelta_inmutable() RETURNS trigger
  LANGUAGE plpgsql AS $$
BEGIN
  IF OLD.resultado <> 'pendiente' THEN
    RAISE EXCEPTION 'Una predicción resuelta no se puede modificar (%).', OLD.id USING ERRCODE = 'P0001';
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER prediccion_resuelta_inmutable BEFORE UPDATE ON prediccion
  FOR EACH ROW EXECUTE FUNCTION prediccion_resuelta_inmutable();

-- Importar rechaza también identificadores de predicción que ya son de otra persona (amplía V4).
CREATE OR REPLACE FUNCTION app_id_de_otro_usuario(candidato uuid) RETURNS boolean
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
  SELECT EXISTS (SELECT 1 FROM expediente WHERE id = candidato AND usuario_id IS DISTINCT FROM app_usuario_actual())
      OR EXISTS (SELECT 1 FROM ejecucion  WHERE id = candidato AND usuario_id IS DISTINCT FROM app_usuario_actual())
      OR EXISTS (SELECT 1 FROM afirmacion WHERE id = candidato AND usuario_id IS DISTINCT FROM app_usuario_actual())
      OR EXISTS (SELECT 1 FROM argumento  WHERE id = candidato AND usuario_id IS DISTINCT FROM app_usuario_actual())
      OR EXISTS (SELECT 1 FROM prediccion WHERE id = candidato AND usuario_id IS DISTINCT FROM app_usuario_actual())
$$;

REVOKE ALL ON FUNCTION app_id_de_otro_usuario(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app_id_de_otro_usuario(uuid) TO ${rol_app};
