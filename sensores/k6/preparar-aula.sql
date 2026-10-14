-- Hito 10, entregable 10: siembra el aula de carga en la institución /k6-carga (que preparar.sql ya creó), solo en db-prueba.
-- Cuatro grupos de 35 estudiantes con la misma contraseña de prueba que el resto ("la carga de k6 no es real"), el tema
-- T13 asignado y su clase abierta por 50 minutos (así sus sesiones son de clase y el modelo está apagado, como en un aula).
-- El panel lo pide el administrador de carga, que administra todos los grupos. Se puede correr varias veces.
\set ON_ERROR_STOP on
DROP TABLE IF EXISTS k6;
CREATE TEMP TABLE k6 AS SELECT id AS institucion, '$argon2id$v=19$m=16384,t=2,p=1$rMYLJbRYf0XpSwbwFvRSXg$S83MufhK4ywINMPZb8MUa4xC3r6jq+3LduNvpl9Jwm0'::text AS hash FROM institucion WHERE ruta = 'k6-carga';
INSERT INTO grupo (institucion_id, nombre, tema_dojo, tema_asignado_en)
  SELECT institucion, 'aula k6 ' || g, 'T13', now() FROM k6, generate_series(1, 4) g
  ON CONFLICT (institucion_id, lower(nombre)) DO UPDATE SET tema_dojo = 'T13', tema_hasta = NULL, lista_en_clase = false, ia_en_clase = false;
INSERT INTO usuario (institucion_id, nombre, clave_hash, tipo_clave, rol_global)
  SELECT institucion, 'estudiante k6 ' || g || '-' || lpad(n::text, 2, '0'), hash, 'contrasena', 'estudiante' FROM k6, generate_series(1, 4) g, generate_series(1, 35) n
  ON CONFLICT (institucion_id, nombre_clave) DO UPDATE SET clave_hash = EXCLUDED.clave_hash, tipo_clave = 'contrasena', activo = true;
INSERT INTO miembro_grupo (grupo_id, usuario_id, rol)
  SELECT gr.id, u.id, 'estudiante' FROM k6
  JOIN grupo gr ON gr.institucion_id = k6.institucion AND gr.nombre LIKE 'aula k6 %'
  JOIN usuario u ON u.institucion_id = k6.institucion AND u.nombre LIKE 'estudiante k6 ' || substr(gr.nombre, 9) || '-%'
  ON CONFLICT DO NOTHING;
-- Los intentos de la corrida anterior se borran: así cada corrida empieza con el reto de hoy por responder.
DELETE FROM intento_dojo WHERE institucion_id = (SELECT institucion FROM k6);
DELETE FROM competencia WHERE institucion_id = (SELECT institucion FROM k6);
UPDATE sesion_aula SET terminada_en = now() WHERE institucion_id = (SELECT institucion FROM k6) AND terminada_en IS NULL;
INSERT INTO sesion_aula (institucion_id, grupo_id, codigo_hash, cierra_en)
  SELECT k6.institucion, gr.id, 'carga-k6-' || gr.id, now() + interval '50 minutes' FROM k6 JOIN grupo gr ON gr.institucion_id = k6.institucion
  WHERE gr.nombre LIKE 'aula k6 %';
SELECT count(*) AS estudiantes FROM miembro_grupo m JOIN grupo gr ON gr.id = m.grupo_id JOIN k6 ON gr.institucion_id = k6.institucion;
