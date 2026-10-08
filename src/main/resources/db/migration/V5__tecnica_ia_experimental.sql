-- V5 (hito 3): una técnica cuyas propuestas del modelo no cumplieron los umbrales del informe de evaluación
-- (docs/evaluacion-modelo.md) queda marcada experimental. La semilla del catálogo escribe el valor desde tecnicas.json.

ALTER TABLE tecnica ADD COLUMN ia_experimental boolean NOT NULL DEFAULT false;
