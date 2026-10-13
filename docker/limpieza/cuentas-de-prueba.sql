-- Hito 8, entregable 2: limpieza única de las cuentas que la suite y k6 dejaron en la base de uso antes del hito 8.
-- No es una migración de Flyway: se corre a mano y solo con la confirmación del dueño.
--
-- Revisar (no borra nada; todo queda en una transacción que se deshace):
--   docker compose exec -T db psql -U pensamiento -d pensamiento -v confirmar=no -f - < docker/limpieza/cuentas-de-prueba.sql
-- Borrar (con la misma lista que mostró la revisión):
--   docker compose exec -T db psql -U pensamiento -d pensamiento -v confirmar=si -f - < docker/limpieza/cuentas-de-prueba.sql
--
-- Una cuenta es de prueba si su nombre termina en un espacio y 8 caracteres hexadecimales (Instalacion.personaNueva:
-- "<rol> 1a2b3c4d") o empieza con "vecino k6 " (sensores/k6/navegacion.js). Nunca la cuenta "administrador".
\set ON_ERROR_STOP on
BEGIN;

CREATE TEMP TABLE cuentas_prueba ON COMMIT DROP AS
  SELECT id, nombre, creado_en FROM usuario
  WHERE nombre <> 'administrador' AND (nombre ~ ' [0-9a-f]{8}$' OR nombre ~ '^vecino k6 ');

\echo 'Cuentas que se borran (por prefijo) y las que se quedan:'
SELECT split_part(nombre, ' ', 1) AS prefijo, count(*) AS cuentas, min(creado_en)::date AS desde, max(creado_en)::date AS hasta
  FROM cuentas_prueba GROUP BY 1 ORDER BY 2 DESC;
SELECT nombre AS se_queda, rol_global FROM usuario WHERE id NOT IN (SELECT id FROM cuentas_prueba) ORDER BY nombre;

\echo 'Filas de esas cuentas por tabla:'
SELECT 'afirmacion' AS tabla, count(*) FROM afirmacion WHERE usuario_id IN (SELECT id FROM cuentas_prueba)
UNION ALL SELECT 'expediente', count(*) FROM expediente WHERE usuario_id IN (SELECT id FROM cuentas_prueba)
UNION ALL SELECT 'ejecucion', count(*) FROM ejecucion WHERE usuario_id IN (SELECT id FROM cuentas_prueba)
UNION ALL SELECT 'argumento', count(*) FROM argumento WHERE usuario_id IN (SELECT id FROM cuentas_prueba)
UNION ALL SELECT 'documento', count(*) FROM documento WHERE usuario_id IN (SELECT id FROM cuentas_prueba)
UNION ALL SELECT 'fuente', count(*) FROM fuente WHERE usuario_id IN (SELECT id FROM cuentas_prueba)
UNION ALL SELECT 'prediccion', count(*) FROM prediccion WHERE usuario_id IN (SELECT id FROM cuentas_prueba)
UNION ALL SELECT 'cambio_opinion', count(*) FROM cambio_opinion WHERE usuario_id IN (SELECT id FROM cuentas_prueba)
UNION ALL SELECT 'sesion_consejero', count(*) FROM sesion_consejero WHERE usuario_id IN (SELECT id FROM cuentas_prueba)
UNION ALL SELECT 'intento_dojo', count(*) FROM intento_dojo WHERE usuario_id IN (SELECT id FROM cuentas_prueba)
UNION ALL SELECT 'auditoria', count(*) FROM auditoria WHERE usuario_id IN (SELECT id FROM cuentas_prueba)
          OR (objeto_tipo = 'usuario' AND objeto_id IN (SELECT id FROM cuentas_prueba));

-- Orden por las claves foráneas: lo que restringe va antes que lo restringido.
DELETE FROM evidencia WHERE afirmacion_id IN (SELECT id FROM afirmacion WHERE usuario_id IN (SELECT id FROM cuentas_prueba))
   OR fuente_id IN (SELECT id FROM fuente WHERE usuario_id IN (SELECT id FROM cuentas_prueba));
DELETE FROM turno_consejero WHERE usuario_id IN (SELECT id FROM cuentas_prueba);
DELETE FROM sesion_consejero WHERE usuario_id IN (SELECT id FROM cuentas_prueba);
DELETE FROM argumento WHERE usuario_id IN (SELECT id FROM cuentas_prueba);
DELETE FROM ejecucion WHERE usuario_id IN (SELECT id FROM cuentas_prueba);
DELETE FROM cambio_opinion WHERE usuario_id IN (SELECT id FROM cuentas_prueba);
DELETE FROM prediccion WHERE usuario_id IN (SELECT id FROM cuentas_prueba);
DELETE FROM pendiente WHERE usuario_id IN (SELECT id FROM cuentas_prueba);
DELETE FROM verificacion WHERE usuario_id IN (SELECT id FROM cuentas_prueba);
DELETE FROM expediente WHERE usuario_id IN (SELECT id FROM cuentas_prueba);
DELETE FROM afirmacion WHERE usuario_id IN (SELECT id FROM cuentas_prueba);
DELETE FROM fuente WHERE usuario_id IN (SELECT id FROM cuentas_prueba);
DELETE FROM documento WHERE usuario_id IN (SELECT id FROM cuentas_prueba);
DELETE FROM configuracion_usuario WHERE usuario_id IN (SELECT id FROM cuentas_prueba);
DELETE FROM competencia WHERE usuario_id IN (SELECT id FROM cuentas_prueba);
DELETE FROM intento_dojo WHERE usuario_id IN (SELECT id FROM cuentas_prueba);
DELETE FROM miembro_grupo WHERE usuario_id IN (SELECT id FROM cuentas_prueba);
DELETE FROM auditoria WHERE usuario_id IN (SELECT id FROM cuentas_prueba)
   OR (objeto_tipo = 'usuario' AND objeto_id IN (SELECT id FROM cuentas_prueba));
DELETE FROM usuario WHERE id IN (SELECT id FROM cuentas_prueba);

SELECT count(*) AS cuentas_que_quedan FROM usuario;

\if :{?confirmar}
\else
  \set confirmar no
\endif
SELECT :'confirmar' = 'si' AS borrar \gset
\if :borrar
  COMMIT;
  \echo 'Borradas. Constancia: la fecha y estos conteos quedan en la salida de este comando.'
\else
  ROLLBACK;
  \echo 'Revisión: no se borró nada (falta -v confirmar=si).'
\endif
