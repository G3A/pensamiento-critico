-- Hito 10, entregable 8: el borrador no se pierde cuando el wifi se cae (tabla de la ronda 3).
-- La copia en el servidor de lo que una persona está escribiendo en un formulario (guardada cada 20 s y al perder el foco):
-- un borrador por persona y formulario, que se reemplaza en cada guardado y se borra al enviar. Es texto de la persona: RLS
-- por usuario, como sus ejecuciones; ni su docente ni quien administra lo ven.

CREATE TABLE borrador (
  usuario_id      uuid NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
  institucion_id  uuid NOT NULL REFERENCES institucion(id) ON DELETE RESTRICT,
  clave           text NOT NULL CHECK (clave ~ '^[a-z0-9:/_-]{1,120}$'),   -- el formulario, p. ej. "dojo:generalizacion-c1"
  campos          jsonb NOT NULL,                                          -- nombre del campo -> valor
  actualizado_en  timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (usuario_id, clave),
  CONSTRAINT borrador_tamano CHECK (octet_length(campos::text) <= 20000)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON borrador TO ${rol_app};
ALTER TABLE borrador ENABLE ROW LEVEL SECURITY;
ALTER TABLE borrador FORCE ROW LEVEL SECURITY;
CREATE POLICY borrador_por_usuario ON borrador USING (usuario_id = app_usuario_actual())
  WITH CHECK (usuario_id = app_usuario_actual() AND institucion_id = app_institucion_actual());
