-- V10 (hito 7, flujo B y módulo T): los intentos del Dojo de razonamiento y la causa "presión social" de los cambios de
-- opinión. El Dojo no guarda su estado: el próximo repaso (SM-2, T49) y el nivel de Bloom (T48) se calculan con reglas
-- sobre los intentos, que son de solo inserción. La tabla competencia de V1 queda como proyección escrita en la misma
-- transacción que cada intento. Reglas en docs/dojo.md.

CREATE TABLE intento_dojo (                          -- solo inserción
  id              uuid PRIMARY KEY DEFAULT uuidv7(),
  usuario_id      uuid NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
  institucion_id  uuid NOT NULL REFERENCES institucion(id) ON DELETE RESTRICT,
  clave           text NOT NULL,                     -- contra el doble clic: un formulario, un intento
  reto_id         text NOT NULL,
  tecnica_id      text NOT NULL REFERENCES tecnica(id) ON DELETE CASCADE,
  concepto        text NOT NULL,
  nivel           text NOT NULL CHECK (nivel IN ('identificar', 'analizar', 'evaluar', 'crear')),
  respuesta       text NOT NULL,
  acierto         boolean NOT NULL,
  dia             date NOT NULL,                     -- el día del reloj de la app en su zona
  creado_en       timestamptz NOT NULL DEFAULT now(),
  UNIQUE (usuario_id, clave)
);
CREATE INDEX intento_dojo_usuario_idx ON intento_dojo (usuario_id, creado_en);

ALTER TABLE intento_dojo ENABLE ROW LEVEL SECURITY;
ALTER TABLE intento_dojo FORCE ROW LEVEL SECURITY;
CREATE POLICY intento_dojo_por_usuario ON intento_dojo USING (usuario_id = app_usuario_actual())
  WITH CHECK (usuario_id = app_usuario_actual() AND institucion_id = app_institucion_actual());

GRANT SELECT, INSERT ON intento_dojo TO ${rol_app};   -- inmutable: ni UPDATE ni DELETE

-- La causa "presión social" (presion) que pide el mockup de P20 para el resumen anual de T46 · Registro de cambios de opinión.
ALTER TABLE cambio_opinion DROP CONSTRAINT cambio_opinion_causa_check;
ALTER TABLE cambio_opinion ADD CONSTRAINT cambio_opinion_causa_check
  CHECK (causa IN ('evidencia', 'steelman', 'revision', 'regla', 'manual', 'presion'));

-- Importar rechaza también identificadores de intentos del Dojo que ya son de otra persona (amplía V8).
CREATE OR REPLACE FUNCTION app_id_de_otro_usuario(candidato uuid) RETURNS boolean
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
  SELECT EXISTS (SELECT 1 FROM expediente WHERE id = candidato AND usuario_id IS DISTINCT FROM app_usuario_actual())
      OR EXISTS (SELECT 1 FROM ejecucion  WHERE id = candidato AND usuario_id IS DISTINCT FROM app_usuario_actual())
      OR EXISTS (SELECT 1 FROM afirmacion WHERE id = candidato AND usuario_id IS DISTINCT FROM app_usuario_actual())
      OR EXISTS (SELECT 1 FROM argumento  WHERE id = candidato AND usuario_id IS DISTINCT FROM app_usuario_actual())
      OR EXISTS (SELECT 1 FROM prediccion WHERE id = candidato AND usuario_id IS DISTINCT FROM app_usuario_actual())
      OR EXISTS (SELECT 1 FROM cambio_opinion WHERE id = candidato AND usuario_id IS DISTINCT FROM app_usuario_actual())
      OR EXISTS (SELECT 1 FROM sesion_consejero WHERE id = candidato AND usuario_id IS DISTINCT FROM app_usuario_actual())
      OR EXISTS (SELECT 1 FROM turno_consejero WHERE id = candidato AND usuario_id IS DISTINCT FROM app_usuario_actual())
      OR EXISTS (SELECT 1 FROM fuente WHERE id = candidato AND usuario_id IS DISTINCT FROM app_usuario_actual())
      OR EXISTS (SELECT 1 FROM evidencia e JOIN afirmacion a ON a.id = e.afirmacion_id
                 WHERE e.id = candidato AND a.usuario_id IS DISTINCT FROM app_usuario_actual())
      OR EXISTS (SELECT 1 FROM documento WHERE id = candidato AND usuario_id IS DISTINCT FROM app_usuario_actual())
      OR EXISTS (SELECT 1 FROM fragmento f JOIN documento d ON d.id = f.documento_id
                 WHERE f.id = candidato AND d.usuario_id IS DISTINCT FROM app_usuario_actual())
      OR EXISTS (SELECT 1 FROM intento_dojo WHERE id = candidato AND usuario_id IS DISTINCT FROM app_usuario_actual())
$$;

REVOKE ALL ON FUNCTION app_id_de_otro_usuario(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app_id_de_otro_usuario(uuid) TO ${rol_app};
