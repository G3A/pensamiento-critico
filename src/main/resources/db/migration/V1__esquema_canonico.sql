-- V1: tabla canónica de tablas (sección 5b del documento de investigación).
-- Claves uuidv7 nativas de PostgreSQL 18. Núcleo tipado, hoja JSONB.
-- Toda tabla de usuario lleva institucion_id desnormalizado para RLS por tenant.
-- Corre con el rol administrador "pensamiento"; la aplicación consulta con el rol "${rol_app}",
-- que no tiene BYPASSRLS, y toda tabla de usuario tiene FORCE ROW LEVEL SECURITY.

CREATE EXTENSION IF NOT EXISTS vector;

-- Contexto de sesión: la aplicación ejecuta SET LOCAL app.usuario y app.institucion en cada transacción.
CREATE OR REPLACE FUNCTION app_usuario_actual() RETURNS uuid
  LANGUAGE sql STABLE AS $$ SELECT nullif(current_setting('app.usuario', true), '')::uuid $$;
CREATE OR REPLACE FUNCTION app_institucion_actual() RETURNS uuid
  LANGUAGE sql STABLE AS $$ SELECT nullif(current_setting('app.institucion', true), '')::uuid $$;

-- ---------------------------------------------------------------------------------------------
-- Instalación, personas y grupos
-- ---------------------------------------------------------------------------------------------
CREATE TABLE institucion (
  id         uuid PRIMARY KEY DEFAULT uuidv7(),
  nombre     text NOT NULL,
  creada_en  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE usuario (
  id             uuid PRIMARY KEY DEFAULT uuidv7(),
  institucion_id uuid NOT NULL REFERENCES institucion(id) ON DELETE RESTRICT,
  nombre         text NOT NULL,
  pin_hash       text NOT NULL,
  rol_global     text NOT NULL CHECK (rol_global IN ('administrador', 'persona')),
  activo         boolean NOT NULL DEFAULT true,
  creado_en      timestamptz NOT NULL DEFAULT now(),
  eliminado_en   timestamptz,
  UNIQUE (institucion_id, nombre)
);

CREATE TABLE grupo (
  id             uuid PRIMARY KEY DEFAULT uuidv7(),
  institucion_id uuid NOT NULL REFERENCES institucion(id) ON DELETE RESTRICT,
  nombre         text NOT NULL,
  docente_id     uuid REFERENCES usuario(id) ON DELETE SET NULL
);

CREATE TABLE miembro_grupo (
  grupo_id   uuid NOT NULL REFERENCES grupo(id) ON DELETE CASCADE,
  usuario_id uuid NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
  rol        text NOT NULL CHECK (rol IN ('docente', 'estudiante')),
  PRIMARY KEY (grupo_id, usuario_id)
);

-- ---------------------------------------------------------------------------------------------
-- Catálogo (sembrado idempotente, compartido por todos los usuarios; sin RLS)
-- ---------------------------------------------------------------------------------------------
CREATE TABLE familia (
  codigo text PRIMARY KEY CHECK (codigo ~ '^F[1-8]$'),
  nombre text NOT NULL,
  orden  integer NOT NULL
);

CREATE TABLE tecnica (
  id              text PRIMARY KEY CHECK (id ~ '^T(0[1-9]|[1-4][0-9])$'),
  familia_codigo  text NOT NULL REFERENCES familia(codigo) ON DELETE RESTRICT,
  nombre          text NOT NULL,
  nombre_llano    text NOT NULL,
  usala_cuando    text NOT NULL,
  definicion      text NOT NULL,
  tipo            text NOT NULL CHECK (tipo IN ('marco', 'representacion', 'criterio', 'procedimiento', 'practica', 'metodo_de_aprendizaje')),
  operacion       text NOT NULL CHECK (operacion IN ('analizar', 'evaluar', 'generar', 'decidir', 'cuestionar', 'reflexionar')),
  objeto          text NOT NULL CHECK (objeto IN ('argumento', 'afirmacion', 'fuente', 'decision', 'problema', 'uno_mismo')),
  modalidad       text NOT NULL CHECK (modalidad IN ('diagrama', 'formulario', 'dialogo', 'calculo', 'lista')),
  patron          text NOT NULL,
  origen          text NOT NULL,
  requiere_ia     text NOT NULL CHECK (requiere_ia IN ('no', 'opcional', 'si')),
  version_esquema integer NOT NULL DEFAULT 1,
  esquema_config  jsonb NOT NULL DEFAULT '{}'::jsonb,
  esquema_entrada jsonb NOT NULL DEFAULT '{}'::jsonb,
  config_default  jsonb NOT NULL DEFAULT '{}'::jsonb,
  estado          text NOT NULL CHECK (estado IN ('activa', 'pendiente'))
);

CREATE TABLE relacion_tecnica (
  origen_id  text NOT NULL REFERENCES tecnica(id) ON DELETE CASCADE,
  destino_id text NOT NULL REFERENCES tecnica(id) ON DELETE CASCADE,
  tipo       text NOT NULL CHECK (tipo IN ('prerrequisito', 'produce_entrada', 'variante', 'complementa', 'contrasta')),
  PRIMARY KEY (origen_id, destino_id, tipo)
);

CREATE TABLE esquema_walton (
  id                  text PRIMARY KEY,
  nombre              text NOT NULL,
  descripcion         text NOT NULL,
  preguntas_criticas  jsonb NOT NULL DEFAULT '[]'::jsonb,   -- cada una con la etiqueta de falacia asociada
  origen              text NOT NULL
);

CREATE TABLE regla_version (
  id            uuid PRIMARY KEY DEFAULT uuidv7(),
  regla         text NOT NULL CHECK (regla ~ '^R0[1-6]$'),
  version       integer NOT NULL,
  parametros    jsonb NOT NULL,
  vigente_desde timestamptz NOT NULL DEFAULT now(),
  UNIQUE (regla, version)          -- solo se agregan versiones; nunca se reescriben
);

CREATE TABLE ejemplo (
  id              uuid PRIMARY KEY DEFAULT uuidv7(),
  tecnica_id      text NOT NULL REFERENCES tecnica(id) ON DELETE CASCADE,
  version_esquema integer NOT NULL,
  ambito          text NOT NULL CHECK (ambito IN ('personal', 'trabajo', 'comunidad')),
  titulo          text NOT NULL,
  config          jsonb NOT NULL,
  datos           jsonb NOT NULL,
  resultado       jsonb NOT NULL,
  nota            text,
  UNIQUE (tecnica_id, titulo)
);

-- ---------------------------------------------------------------------------------------------
-- Datos de cada persona (RLS por usuario de sesión)
-- ---------------------------------------------------------------------------------------------
CREATE TABLE configuracion_usuario (
  usuario_id      uuid NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
  institucion_id  uuid NOT NULL REFERENCES institucion(id) ON DELETE RESTRICT,
  tecnica_id      text NOT NULL REFERENCES tecnica(id) ON DELETE CASCADE,
  version_esquema integer NOT NULL,
  valores         jsonb NOT NULL DEFAULT '{}'::jsonb,
  PRIMARY KEY (usuario_id, tecnica_id)
);

CREATE TABLE afirmacion (
  id               uuid PRIMARY KEY DEFAULT uuidv7(),
  usuario_id       uuid NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
  institucion_id   uuid NOT NULL REFERENCES institucion(id) ON DELETE RESTRICT,
  texto            text NOT NULL,
  tipo             text NOT NULL CHECK (tipo IN ('hecho', 'dato_estadistico', 'causal', 'generalizacion', 'definicion', 'testimonio', 'prediccion', 'juicio_de_valor')),
  origen           text NOT NULL CHECK (origen IN ('usuario', 'modelo', 'regla', 'ejemplo')),
  adoptada         boolean NOT NULL DEFAULT false,   -- lo que viene del modelo no cuenta hasta adoptarse
  confianza        numeric(5,2) CHECK (confianza IS NULL OR (confianza >= 0 AND confianza <= 100)),
  estado           text NOT NULL DEFAULT 'sin_verificar' CHECK (estado IN ('no_verificable', 'sin_verificar', 'disputada', 'verificada', 'refutada', 'en_verificacion')),
  fuerza_neta      integer NOT NULL DEFAULT 0,
  regla_version_id uuid REFERENCES regla_version(id) ON DELETE RESTRICT,
  creada_en        timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX afirmacion_usuario_idx ON afirmacion (usuario_id, creada_en DESC);

CREATE TABLE expediente (
  id             uuid PRIMARY KEY DEFAULT uuidv7(),
  usuario_id     uuid NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
  institucion_id uuid NOT NULL REFERENCES institucion(id) ON DELETE RESTRICT,
  nombre         text NOT NULL,
  postura_id     uuid REFERENCES afirmacion(id) ON DELETE SET NULL,
  estado         text NOT NULL DEFAULT 'abierto' CHECK (estado IN ('abierto', 'cerrado')),
  creado_en      timestamptz NOT NULL DEFAULT now(),
  eliminado_en   timestamptz                                   -- borrado lógico
);
CREATE INDEX expediente_usuario_idx ON expediente (usuario_id, creado_en DESC);

CREATE TABLE ejecucion (
  id                 uuid PRIMARY KEY DEFAULT uuidv7(),
  usuario_id         uuid NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
  institucion_id     uuid NOT NULL REFERENCES institucion(id) ON DELETE RESTRICT,
  tecnica_id         text NOT NULL REFERENCES tecnica(id) ON DELETE RESTRICT,
  version_esquema    integer NOT NULL,
  expediente_id      uuid REFERENCES expediente(id) ON DELETE SET NULL,
  config             jsonb NOT NULL DEFAULT '{}'::jsonb,
  datos              jsonb NOT NULL DEFAULT '{}'::jsonb,
  resultado          jsonb NOT NULL DEFAULT '{}'::jsonb,   -- identificadores de afirmación por rol, nunca copias
  resumen            text NOT NULL DEFAULT '',
  modelo             text,
  modelo_digest      text,
  prompt_version     text,
  temperatura        numeric(4,2),
  semilla            bigint,
  clave_idempotencia text NOT NULL UNIQUE,
  creada_en          timestamptz NOT NULL DEFAULT now(),
  eliminada_en       timestamptz                              -- borrado lógico
);
CREATE INDEX ejecucion_usuario_idx    ON ejecucion (usuario_id, creada_en DESC);
CREATE INDEX ejecucion_tecnica_idx    ON ejecucion (usuario_id, tecnica_id, creada_en DESC);
CREATE INDEX ejecucion_expediente_idx ON ejecucion (expediente_id, creada_en DESC);

CREATE TABLE ejecucion_afirmacion (
  ejecucion_id  uuid NOT NULL REFERENCES ejecucion(id) ON DELETE CASCADE,
  afirmacion_id uuid NOT NULL REFERENCES afirmacion(id) ON DELETE RESTRICT,
  rol           text NOT NULL CHECK (rol IN ('premisa', 'conclusion', 'hipotesis', 'prediccion', 'postura', 'supuesto', 'condicion_falsacion', 'opcion')),
  sentido       text NOT NULL CHECK (sentido IN ('consumida', 'producida')),
  PRIMARY KEY (ejecucion_id, afirmacion_id, rol, sentido)
);

CREATE TABLE argumento (
  id             uuid PRIMARY KEY DEFAULT uuidv7(),
  usuario_id     uuid NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
  institucion_id uuid NOT NULL REFERENCES institucion(id) ON DELETE RESTRICT,
  conclusion_id  uuid NOT NULL REFERENCES afirmacion(id) ON DELETE RESTRICT,
  esquema_id     text REFERENCES esquema_walton(id) ON DELETE SET NULL,
  peso           integer NOT NULL DEFAULT 1 CHECK (peso >= 0),
  sentido        text NOT NULL CHECK (sentido IN ('pro', 'contra')),
  estandar       text NOT NULL DEFAULT 'preponderancia' CHECK (estandar IN ('escrutinio', 'preponderancia', 'claro_y_convincente', 'mas_alla_de_duda_razonable')),
  texto_argdown  text
);

CREATE TABLE premisa_argumento (
  argumento_id  uuid NOT NULL REFERENCES argumento(id) ON DELETE CASCADE,
  afirmacion_id uuid NOT NULL REFERENCES afirmacion(id) ON DELETE RESTRICT,
  orden         integer NOT NULL,
  asumible      boolean NOT NULL DEFAULT false,
  PRIMARY KEY (argumento_id, afirmacion_id)
);

CREATE TABLE documento (
  id             uuid PRIMARY KEY DEFAULT uuidv7(),
  usuario_id     uuid NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
  institucion_id uuid NOT NULL REFERENCES institucion(id) ON DELETE RESTRICT,
  nombre         text NOT NULL,
  tipo           text NOT NULL CHECK (tipo IN ('pdf', 'markdown', 'texto', 'csv')),
  estado         text NOT NULL DEFAULT 'en_proceso' CHECK (estado IN ('en_proceso', 'indexado', 'error')),
  compartido     boolean NOT NULL DEFAULT false,   -- privado por defecto; compartir es un acto auditado
  hash           text NOT NULL,
  creado_en      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE fragmento (
  id           uuid PRIMARY KEY DEFAULT uuidv7(),
  documento_id uuid NOT NULL REFERENCES documento(id) ON DELETE CASCADE,
  orden        integer NOT NULL,
  texto        text NOT NULL,
  pagina       integer,
  embedding    vector(1024)                          -- bge-m3
);
CREATE INDEX fragmento_embedding_idx ON fragmento USING hnsw (embedding vector_cosine_ops);

CREATE TABLE fuente (
  id                     uuid PRIMARY KEY DEFAULT uuidv7(),
  usuario_id             uuid REFERENCES usuario(id) ON DELETE SET NULL,   -- "usuario retirado"
  institucion_id         uuid NOT NULL REFERENCES institucion(id) ON DELETE RESTRICT,
  titulo                 text NOT NULL,
  autor                  text,
  fecha                  date,
  tipo                   text NOT NULL CHECK (tipo IN ('primaria', 'secundaria', 'terciaria')),
  diseno_estudio         text CHECK (diseno_estudio IS NULL OR diseno_estudio IN ('revision_sistematica', 'ensayo_controlado', 'observacional', 'opinion_experto', 'testimonio')),
  grupo_origen           text,                       -- define independencia mutua entre fuentes
  independiente_del_autor boolean NOT NULL DEFAULT false,
  acceso_original        boolean NOT NULL DEFAULT false,
  craap                  jsonb,
  puntaje_craap          integer CHECK (puntaje_craap IS NULL OR (puntaje_craap >= 0 AND puntaje_craap <= 25)),
  documento_id           uuid REFERENCES documento(id) ON DELETE SET NULL   -- "documento retirado"
);

CREATE TABLE evidencia (
  id               uuid PRIMARY KEY DEFAULT uuidv7(),
  afirmacion_id    uuid NOT NULL REFERENCES afirmacion(id) ON DELETE CASCADE,
  fuente_id        uuid NOT NULL REFERENCES fuente(id) ON DELETE RESTRICT,
  fragmento_id     uuid REFERENCES fragmento(id) ON DELETE SET NULL,
  pasaje           text NOT NULL,                    -- copia literal
  postura          text NOT NULL CHECK (postura IN ('apoya', 'contradice', 'matiza')),
  fuerza           integer NOT NULL CHECK (fuerza >= 0 AND fuerza <= 8),
  regla_version_id uuid REFERENCES regla_version(id) ON DELETE RESTRICT,
  etiquetada_por   text NOT NULL CHECK (etiquetada_por IN ('usuario', 'modelo')),
  adoptada         boolean NOT NULL DEFAULT false
);

CREATE TABLE cambio_opinion (                        -- inmutable: solo inserción
  id                 uuid PRIMARY KEY DEFAULT uuidv7(),
  usuario_id         uuid NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
  institucion_id     uuid NOT NULL REFERENCES institucion(id) ON DELETE RESTRICT,
  afirmacion_id      uuid NOT NULL REFERENCES afirmacion(id) ON DELETE CASCADE,
  confianza_antes    numeric(5,2),
  confianza_despues  numeric(5,2),
  causa              text NOT NULL CHECK (causa IN ('evidencia', 'steelman', 'revision', 'regla', 'manual')),
  ejecucion_id       uuid REFERENCES ejecucion(id) ON DELETE SET NULL,
  creado_en          timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE prediccion (
  id             uuid PRIMARY KEY DEFAULT uuidv7(),
  usuario_id     uuid NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
  institucion_id uuid NOT NULL REFERENCES institucion(id) ON DELETE RESTRICT,
  afirmacion_id  uuid NOT NULL REFERENCES afirmacion(id) ON DELETE CASCADE,
  confianza      numeric(5,2) NOT NULL CHECK (confianza >= 0 AND confianza <= 100),
  fecha_revision date NOT NULL,
  resultado      text NOT NULL DEFAULT 'pendiente' CHECK (resultado IN ('pendiente', 'acierto', 'fallo')),
  resuelta_en    timestamptz                         -- resuelta es inmutable
);

CREATE TABLE pendiente (                             -- proyección escrita en la misma transacción que la ejecución
  id             uuid PRIMARY KEY DEFAULT uuidv7(),
  usuario_id     uuid NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
  institucion_id uuid NOT NULL REFERENCES institucion(id) ON DELETE RESTRICT,
  tipo           text NOT NULL CHECK (tipo IN ('revision', 'verificacion', 'objecion', 'repaso')),
  objeto_id      uuid,
  vence          date,
  ejecucion_id   uuid REFERENCES ejecucion(id) ON DELETE CASCADE,
  resuelto       boolean NOT NULL DEFAULT false
);
CREATE INDEX pendiente_usuario_idx ON pendiente (usuario_id, resuelto, vence);

CREATE TABLE competencia (
  usuario_id      uuid NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
  institucion_id  uuid NOT NULL REFERENCES institucion(id) ON DELETE RESTRICT,
  tecnica_id      text NOT NULL REFERENCES tecnica(id) ON DELETE CASCADE,
  nivel_bloom     text NOT NULL DEFAULT 'identificar' CHECK (nivel_bloom IN ('identificar', 'analizar', 'evaluar', 'crear')),
  intentos        integer NOT NULL DEFAULT 0,
  aciertos        integer NOT NULL DEFAULT 0,
  ultima_practica timestamptz,
  PRIMARY KEY (usuario_id, tecnica_id)
);

-- ---------------------------------------------------------------------------------------------
-- Sistema
-- ---------------------------------------------------------------------------------------------
CREATE TABLE trabajo (                               -- reencolado al arrancar
  id             uuid PRIMARY KEY DEFAULT uuidv7(),
  tipo           text NOT NULL,
  estado         text NOT NULL DEFAULT 'pendiente' CHECK (estado IN ('pendiente', 'en_proceso', 'hecho', 'error')),
  intentos       integer NOT NULL DEFAULT 0,
  payload        jsonb NOT NULL DEFAULT '{}'::jsonb,
  error          text,
  creado_en      timestamptz NOT NULL DEFAULT now(),
  actualizado_en timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE auditoria (                             -- solo inserción
  id             uuid PRIMARY KEY DEFAULT uuidv7(),
  usuario_id     uuid REFERENCES usuario(id) ON DELETE SET NULL,
  institucion_id uuid NOT NULL REFERENCES institucion(id) ON DELETE RESTRICT,
  accion         text NOT NULL CHECK (accion IN ('crear', 'importar', 'exportar', 'compartir', 'borrar', 'desactivar', 'sesion')),
  objeto_tipo    text NOT NULL,
  objeto_id      uuid,
  fecha          timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX auditoria_usuario_idx ON auditoria (usuario_id, fecha DESC);

-- ---------------------------------------------------------------------------------------------
-- Permisos del rol de aplicación
-- ---------------------------------------------------------------------------------------------
GRANT USAGE ON SCHEMA public TO ${rol_app};
GRANT SELECT ON familia, tecnica, relacion_tecnica, esquema_walton, regla_version, ejemplo, institucion TO ${rol_app};
GRANT INSERT ON institucion TO ${rol_app};
GRANT SELECT, INSERT, UPDATE, DELETE ON usuario, grupo, miembro_grupo, configuracion_usuario, afirmacion, expediente,
  ejecucion, ejecucion_afirmacion, argumento, premisa_argumento, documento, fragmento, fuente, evidencia,
  prediccion, pendiente, competencia, trabajo TO ${rol_app};
GRANT SELECT, INSERT ON cambio_opinion, auditoria TO ${rol_app};   -- inmutables: ni UPDATE ni DELETE
GRANT EXECUTE ON FUNCTION app_usuario_actual(), app_institucion_actual() TO ${rol_app};

-- ---------------------------------------------------------------------------------------------
-- Seguridad por fila: forzada incluso para el dueño de la tabla
-- ---------------------------------------------------------------------------------------------
ALTER TABLE usuario ENABLE ROW LEVEL SECURITY;
ALTER TABLE usuario FORCE ROW LEVEL SECURITY;
CREATE POLICY usuario_por_institucion ON usuario
  USING (institucion_id = app_institucion_actual())
  WITH CHECK (institucion_id = app_institucion_actual());

ALTER TABLE grupo ENABLE ROW LEVEL SECURITY;
ALTER TABLE grupo FORCE ROW LEVEL SECURITY;
CREATE POLICY grupo_por_institucion ON grupo
  USING (institucion_id = app_institucion_actual())
  WITH CHECK (institucion_id = app_institucion_actual());

ALTER TABLE miembro_grupo ENABLE ROW LEVEL SECURITY;
ALTER TABLE miembro_grupo FORCE ROW LEVEL SECURITY;
CREATE POLICY miembro_por_grupo ON miembro_grupo
  USING (EXISTS (SELECT 1 FROM grupo g WHERE g.id = grupo_id))
  WITH CHECK (EXISTS (SELECT 1 FROM grupo g WHERE g.id = grupo_id));

-- Tablas con usuario_id: solo el usuario de la sesión ve y escribe sus filas.
DO $$
DECLARE t text;
BEGIN
  FOREACH t IN ARRAY ARRAY['configuracion_usuario', 'afirmacion', 'expediente', 'ejecucion', 'argumento',
                           'cambio_opinion', 'prediccion', 'pendiente', 'competencia'] LOOP
    EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', t);
    EXECUTE format('ALTER TABLE %I FORCE ROW LEVEL SECURITY', t);
    EXECUTE format('CREATE POLICY %I ON %I USING (usuario_id = app_usuario_actual()) '
                   'WITH CHECK (usuario_id = app_usuario_actual() AND institucion_id = app_institucion_actual())',
                   t || '_por_usuario', t);
  END LOOP;
END $$;

ALTER TABLE ejecucion_afirmacion ENABLE ROW LEVEL SECURITY;
ALTER TABLE ejecucion_afirmacion FORCE ROW LEVEL SECURITY;
CREATE POLICY ejecucion_afirmacion_por_ejecucion ON ejecucion_afirmacion
  USING (EXISTS (SELECT 1 FROM ejecucion e WHERE e.id = ejecucion_id))
  WITH CHECK (EXISTS (SELECT 1 FROM ejecucion e WHERE e.id = ejecucion_id));

ALTER TABLE premisa_argumento ENABLE ROW LEVEL SECURITY;
ALTER TABLE premisa_argumento FORCE ROW LEVEL SECURITY;
CREATE POLICY premisa_por_argumento ON premisa_argumento
  USING (EXISTS (SELECT 1 FROM argumento a WHERE a.id = argumento_id))
  WITH CHECK (EXISTS (SELECT 1 FROM argumento a WHERE a.id = argumento_id));

ALTER TABLE documento ENABLE ROW LEVEL SECURITY;
ALTER TABLE documento FORCE ROW LEVEL SECURITY;
CREATE POLICY documento_propio_o_compartido ON documento
  USING (usuario_id = app_usuario_actual() OR (compartido AND institucion_id = app_institucion_actual()))
  WITH CHECK (usuario_id = app_usuario_actual() AND institucion_id = app_institucion_actual());

ALTER TABLE fragmento ENABLE ROW LEVEL SECURITY;
ALTER TABLE fragmento FORCE ROW LEVEL SECURITY;
CREATE POLICY fragmento_por_documento ON fragmento
  USING (EXISTS (SELECT 1 FROM documento d WHERE d.id = documento_id))
  WITH CHECK (EXISTS (SELECT 1 FROM documento d WHERE d.id = documento_id));

ALTER TABLE fuente ENABLE ROW LEVEL SECURITY;
ALTER TABLE fuente FORCE ROW LEVEL SECURITY;
CREATE POLICY fuente_por_usuario ON fuente
  USING (usuario_id = app_usuario_actual())
  WITH CHECK (usuario_id = app_usuario_actual() AND institucion_id = app_institucion_actual());

ALTER TABLE evidencia ENABLE ROW LEVEL SECURITY;
ALTER TABLE evidencia FORCE ROW LEVEL SECURITY;
CREATE POLICY evidencia_por_afirmacion ON evidencia
  USING (EXISTS (SELECT 1 FROM afirmacion a WHERE a.id = afirmacion_id))
  WITH CHECK (EXISTS (SELECT 1 FROM afirmacion a WHERE a.id = afirmacion_id));

ALTER TABLE auditoria ENABLE ROW LEVEL SECURITY;
ALTER TABLE auditoria FORCE ROW LEVEL SECURITY;
CREATE POLICY auditoria_escribe_cualquiera ON auditoria FOR INSERT
  WITH CHECK (institucion_id = app_institucion_actual());
CREATE POLICY auditoria_lee_lo_propio ON auditoria FOR SELECT
  USING (usuario_id = app_usuario_actual());
