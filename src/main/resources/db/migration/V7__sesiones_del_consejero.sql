-- V7 (hito 5, flujo C): las sesiones del Consejero socrático y sus turnos. La sesión guarda la postura, el modo, la
-- configuración de la técnica del modo tal como estaba al empezar (así el motor rehace los mismos turnos aunque la
-- persona cambie su configuración después), la confianza de entrada y, al cerrar, la reflexión y la ejecución que
-- produjo. Cada turno es de la persona o del Consejero; el del Consejero dice si salió del banco o lo redactó el modelo
-- (con modelo, digest y prompt, RNF-07) y queda "redactando" mientras llega por SSE.

CREATE TABLE sesion_consejero (
  id                 uuid PRIMARY KEY DEFAULT uuidv7(),
  usuario_id         uuid NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
  institucion_id     uuid NOT NULL REFERENCES institucion(id) ON DELETE RESTRICT,
  expediente_id      uuid REFERENCES expediente(id) ON DELETE SET NULL,
  modo               text NOT NULL CHECK (modo IN ('ensayo', 'decision', 'escalera', 'sombreros', 'debate')),
  postura            text NOT NULL,
  razones            jsonb NOT NULL DEFAULT '[]',
  config             text NOT NULL,                 -- JSON tal cual: el motor lo vuelve a leer, no se consulta
  usa_modelo         boolean NOT NULL DEFAULT false,
  confianza_antes    integer CHECK (confianza_antes BETWEEN 0 AND 100),
  cierre_pedido      boolean NOT NULL DEFAULT false,
  estado             text NOT NULL DEFAULT 'abierta' CHECK (estado IN ('abierta', 'cerrada')),
  reflexion          text,
  confianza_despues  integer CHECK (confianza_despues BETWEEN 0 AND 100),
  ejecucion_id       uuid REFERENCES ejecucion(id) ON DELETE SET NULL,
  creada_en          timestamptz NOT NULL DEFAULT now(),
  cerrada_en         timestamptz,
  CONSTRAINT sesion_cerrada_con_fecha CHECK ((estado = 'cerrada') = (cerrada_en IS NOT NULL))
);
CREATE INDEX sesion_consejero_usuario_idx ON sesion_consejero (usuario_id, creada_en DESC);

CREATE TABLE turno_consejero (
  id                  uuid PRIMARY KEY DEFAULT uuidv7(),
  sesion_id           uuid NOT NULL REFERENCES sesion_consejero(id) ON DELETE CASCADE,
  usuario_id          uuid NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
  institucion_id      uuid NOT NULL REFERENCES institucion(id) ON DELETE RESTRICT,
  numero              integer NOT NULL CHECK (numero >= 1),
  rol                 text NOT NULL CHECK (rol IN ('persona', 'consejero')),
  paso                text NOT NULL,
  texto               text NOT NULL,
  origen              text NOT NULL CHECK (origen IN ('persona', 'banco', 'modelo')),
  estado              text NOT NULL DEFAULT 'listo' CHECK (estado IN ('listo', 'redactando')),
  intentos            integer NOT NULL DEFAULT 0,
  modelo              text,
  digest              text,
  prompt_version      text,
  elemento_propuesto  text,
  porque_propuesto    text,
  propuesta_adoptada  boolean NOT NULL DEFAULT false,
  creado_en           timestamptz NOT NULL DEFAULT now(),
  UNIQUE (sesion_id, numero)
);
CREATE INDEX turno_consejero_usuario_idx ON turno_consejero (usuario_id);

ALTER TABLE sesion_consejero ENABLE ROW LEVEL SECURITY;
ALTER TABLE sesion_consejero FORCE ROW LEVEL SECURITY;
CREATE POLICY sesion_consejero_por_usuario ON sesion_consejero USING (usuario_id = app_usuario_actual())
  WITH CHECK (usuario_id = app_usuario_actual() AND institucion_id = app_institucion_actual());

ALTER TABLE turno_consejero ENABLE ROW LEVEL SECURITY;
ALTER TABLE turno_consejero FORCE ROW LEVEL SECURITY;
CREATE POLICY turno_consejero_por_usuario ON turno_consejero USING (usuario_id = app_usuario_actual())
  WITH CHECK (usuario_id = app_usuario_actual() AND institucion_id = app_institucion_actual());

GRANT SELECT, INSERT, UPDATE, DELETE ON sesion_consejero, turno_consejero TO ${rol_app};

-- Importar rechaza también identificadores de sesión y de turno que ya son de otra persona (amplía V6).
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
$$;

REVOKE ALL ON FUNCTION app_id_de_otro_usuario(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app_id_de_otro_usuario(uuid) TO ${rol_app};
