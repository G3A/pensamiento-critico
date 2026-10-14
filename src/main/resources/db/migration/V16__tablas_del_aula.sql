-- Hito 9, entregable 11: lo que el hito 10 (Aula) necesita en la base, para no migrar dos veces. Sin interfaz todavía.
-- Decisión: el rol de la persona en la institución va en usuario.rol_global (docente y estudiante se suman a administrador
-- y persona: el docente es un adulto que confirma con su llave, el estudiante no), y la pertenencia a cada grupo sigue en
-- miembro_grupo (con su rol en ese grupo). El código de grupo y el de cada sesión de aula se guardan solo como hash.

ALTER TABLE usuario DROP CONSTRAINT usuario_rol_global_check;
ALTER TABLE usuario ADD CONSTRAINT usuario_rol_global_check CHECK (rol_global IN ('administrador', 'docente', 'persona', 'estudiante'));

-- Un grupo (un curso de un colegio): su código para que un estudiante se una, que caduca y se guarda solo como hash.
ALTER TABLE grupo
  ADD COLUMN codigo_hash     text,
  ADD COLUMN codigo_caduca_en timestamptz,
  ADD COLUMN creado_en       timestamptz NOT NULL DEFAULT now(),
  ADD COLUMN activo          boolean NOT NULL DEFAULT true,
  ADD CONSTRAINT grupo_codigo_con_caducidad CHECK ((codigo_hash IS NULL) = (codigo_caduca_en IS NULL));
CREATE INDEX grupo_codigo_idx ON grupo (institucion_id, codigo_hash) WHERE codigo_hash IS NOT NULL;
CREATE UNIQUE INDEX grupo_nombre_idx ON grupo (institucion_id, lower(nombre));

-- Una sesión de aula: el docente la abre para su grupo con un código (o QR) de un uso por clase; dura 60 minutos o hasta
-- "Terminar clase".
CREATE TABLE sesion_aula (
  id             uuid PRIMARY KEY DEFAULT uuidv7(),
  institucion_id uuid NOT NULL REFERENCES institucion(id) ON DELETE RESTRICT,
  grupo_id       uuid NOT NULL REFERENCES grupo(id) ON DELETE CASCADE,
  docente_id     uuid REFERENCES usuario(id) ON DELETE SET NULL,
  codigo_hash    text NOT NULL,
  abierta_en     timestamptz NOT NULL DEFAULT now(),
  cierra_en      timestamptz NOT NULL,
  terminada_en   timestamptz,
  CONSTRAINT sesion_aula_dura_hasta_60_minutos CHECK (cierra_en > abierta_en AND cierra_en <= abierta_en + interval '60 minutes')
);
CREATE INDEX sesion_aula_abierta_idx ON sesion_aula (grupo_id) WHERE terminada_en IS NULL;
CREATE INDEX sesion_aula_codigo_idx ON sesion_aula (institucion_id, codigo_hash) WHERE terminada_en IS NULL;

GRANT SELECT, INSERT, UPDATE ON sesion_aula TO ${rol_app};
ALTER TABLE sesion_aula ENABLE ROW LEVEL SECURITY;
ALTER TABLE sesion_aula FORCE ROW LEVEL SECURITY;
CREATE POLICY sesion_aula_por_institucion ON sesion_aula
  USING (institucion_id = app_institucion_actual())
  WITH CHECK (institucion_id = app_institucion_actual());
