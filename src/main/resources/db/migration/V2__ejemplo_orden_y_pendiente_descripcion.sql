-- V2 (hito 1): orden de los ejemplos dentro de su técnica y descripción accionable de cada pendiente.
-- "Qué falta para cerrar" del Expediente se lee de la tabla pendiente sin abrir el JSONB de la ejecución.

ALTER TABLE ejemplo ADD COLUMN orden integer NOT NULL DEFAULT 1;

ALTER TABLE pendiente ADD COLUMN descripcion text NOT NULL DEFAULT '';
CREATE INDEX pendiente_ejecucion_idx ON pendiente (ejecucion_id);
