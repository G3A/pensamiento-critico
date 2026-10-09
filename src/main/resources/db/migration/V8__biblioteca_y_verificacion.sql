-- V8 (hito 6): la biblioteca, las fichas de fuente con sus evidencias y la ficha de verificación.
-- documento, fragmento, fuente, evidencia y trabajo existen desde V1; aquí se completan. Se agrega verificacion.

-- ---------------------------------------------------------------------------------------------
-- Biblioteca (P13): el archivo original, su tamaño, páginas y el motivo del error
-- ---------------------------------------------------------------------------------------------
ALTER TABLE documento ADD COLUMN contenido bytea;
ALTER TABLE documento ADD COLUMN tamano bigint NOT NULL DEFAULT 0 CHECK (tamano >= 0 AND tamano <= 52428800);   -- RNF-08: 50 MB
ALTER TABLE documento ADD COLUMN paginas integer CHECK (paginas IS NULL OR paginas >= 0);
ALTER TABLE documento ADD COLUMN error text;
CREATE UNIQUE INDEX documento_hash_por_usuario ON documento (usuario_id, hash);
CREATE INDEX documento_usuario_idx ON documento (usuario_id, creado_en DESC);
CREATE INDEX documento_compartido_idx ON documento (institucion_id) WHERE compartido;

-- Texto completo en español: la búsqueda sin Ollama.
ALTER TABLE fragmento ADD COLUMN tsv tsvector GENERATED ALWAYS AS (to_tsvector('spanish', texto)) STORED;
CREATE INDEX fragmento_tsv_idx ON fragmento USING gin (tsv);
ALTER TABLE fragmento ADD CONSTRAINT fragmento_orden_unico UNIQUE (documento_id, orden);

-- ---------------------------------------------------------------------------------------------
-- Ficha de fuente (P12): notas de SIFT, el documento citado (aunque se retire) y la página
-- ---------------------------------------------------------------------------------------------
ALTER TABLE fuente ADD COLUMN sift jsonb NOT NULL DEFAULT '{}'::jsonb;
ALTER TABLE fuente ADD COLUMN documento_nombre text;
ALTER TABLE fuente ADD COLUMN pagina integer CHECK (pagina IS NULL OR pagina >= 1);
CREATE INDEX fuente_usuario_idx ON fuente (usuario_id);

-- Las evidencias se leen en el orden en que se registraron, también dentro de una misma transacción.
ALTER TABLE evidencia ADD COLUMN creada_en timestamptz NOT NULL DEFAULT clock_timestamp();
CREATE INDEX evidencia_afirmacion_idx ON evidencia (afirmacion_id, creada_en);
CREATE INDEX evidencia_fuente_idx ON evidencia (fuente_id);

-- ---------------------------------------------------------------------------------------------
-- Ficha de verificación (P10): las preguntas críticas marcadas
-- ---------------------------------------------------------------------------------------------
CREATE TABLE verificacion (
  afirmacion_id  uuid PRIMARY KEY REFERENCES afirmacion(id) ON DELETE CASCADE,
  usuario_id     uuid NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
  institucion_id uuid NOT NULL REFERENCES institucion(id) ON DELETE RESTRICT,
  preguntas      jsonb NOT NULL DEFAULT '[]'::jsonb,   -- textos de las preguntas críticas que la persona marcó
  actualizada_en timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX verificacion_usuario_idx ON verificacion (usuario_id, actualizada_en);

ALTER TABLE verificacion ENABLE ROW LEVEL SECURITY;
ALTER TABLE verificacion FORCE ROW LEVEL SECURITY;
CREATE POLICY verificacion_por_usuario ON verificacion USING (usuario_id = app_usuario_actual())
  WITH CHECK (usuario_id = app_usuario_actual() AND institucion_id = app_institucion_actual());
GRANT SELECT, INSERT, UPDATE, DELETE ON verificacion TO ${rol_app};

-- ---------------------------------------------------------------------------------------------
-- Trabajos largos: reintentos con espera
-- ---------------------------------------------------------------------------------------------
ALTER TABLE trabajo ADD COLUMN disponible_en timestamptz NOT NULL DEFAULT now();
CREATE INDEX trabajo_cola_idx ON trabajo (estado, disponible_en, creado_en);

-- ---------------------------------------------------------------------------------------------
-- Importar datos (RF-12) también rechaza fuentes, evidencias, documentos y verificaciones de otra persona
-- ---------------------------------------------------------------------------------------------
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
$$;

REVOKE ALL ON FUNCTION app_id_de_otro_usuario(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app_id_de_otro_usuario(uuid) TO ${rol_app};

-- ---------------------------------------------------------------------------------------------
-- T22 · Triangulación guardaba sus fuentes en el JSONB "hasta el hito 6": las que cuentan pasan a fuente y evidencia,
-- ligadas a la afirmación que produjo cada ejecución. El JSONB no se toca; Ejecutor.migrar lo lee al abrirlo.
-- Flyway corre como el administrador de la base, así que aquí no aplica RLS.
-- ---------------------------------------------------------------------------------------------
CREATE TEMP TABLE t22_v1 ON COMMIT DROP AS
SELECT uuidv7() AS fuente_id, e.usuario_id, e.institucion_id, ea.afirmacion_id, f.elem AS fuente, ev.elem AS evaluada, f.n
FROM ejecucion e
JOIN ejecucion_afirmacion ea ON ea.ejecucion_id = e.id AND ea.sentido = 'producida' AND ea.rol = 'hipotesis'
CROSS JOIN LATERAL (
  SELECT x.elem, row_number() OVER (ORDER BY x.ord) AS n
  FROM jsonb_array_elements(coalesce(e.datos -> 'fuentes', '[]'::jsonb)) WITH ORDINALITY AS x(elem, ord)
  WHERE coalesce(btrim(x.elem ->> 'titulo'), '') <> ''
) f
JOIN LATERAL (
  SELECT y.elem
  FROM jsonb_array_elements(coalesce(e.resultado -> 'evidencias', '[]'::jsonb)) WITH ORDINALITY AS y(elem, ord)
  WHERE y.ord = f.n
) ev ON true
WHERE e.tecnica_id = 'T22' AND e.version_esquema = 1
  AND coalesce((ev.elem ->> 'cuenta')::boolean, false)
  AND ev.elem ->> 'postura' IN ('apoya', 'contradice', 'matiza');

INSERT INTO fuente (id, usuario_id, institucion_id, titulo, fecha, tipo, diseno_estudio, grupo_origen, independiente_del_autor,
                    acceso_original, puntaje_craap)
SELECT fuente_id, usuario_id, institucion_id, btrim(fuente ->> 'titulo'), nullif(fuente ->> 'fecha', '')::date, fuente ->> 'tipoFuente',
       CASE WHEN fuente ->> 'diseno' IN ('revision_sistematica', 'ensayo_controlado', 'observacional', 'opinion_experto', 'testimonio')
            THEN fuente ->> 'diseno' END,
       nullif(lower(btrim(coalesce(fuente ->> 'grupo', ''))), ''),
       coalesce((fuente ->> 'independiente')::boolean, false), coalesce((fuente ->> 'original')::boolean, false),
       (fuente ->> 'craap')::integer
FROM t22_v1;

INSERT INTO evidencia (afirmacion_id, fuente_id, pasaje, postura, fuerza, regla_version_id, etiquetada_por, adoptada, creada_en)
SELECT afirmacion_id, fuente_id, evaluada ->> 'pasaje', evaluada ->> 'postura', (evaluada ->> 'fuerza')::integer,
       (SELECT id FROM regla_version WHERE regla = 'R01' AND version = 1),
       CASE WHEN evaluada ->> 'etiquetadaPor' = 'modelo' THEN 'modelo' ELSE 'usuario' END, true,
       clock_timestamp() + make_interval(secs => n / 1000.0)
FROM t22_v1
ORDER BY afirmacion_id, n;
