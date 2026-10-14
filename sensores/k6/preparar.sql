-- Hito 9: siembra en db-prueba la institución de carga de k6 (/k6-carga), solo en la instalación de pruebas.
-- Un administrador con contraseña y una llave de acceso de prueba (la clave privada está en llave-de-carga.js: no es un
-- secreto, solo sirve aquí), 12 personas con contraseña y 500 cuentas sin canjear para medir /usuarios con volumen.
-- La contraseña de todas es "la carga de k6 no es real" (hash Argon2 calculado una vez). Se puede correr varias veces.
\set ON_ERROR_STOP on
INSERT INTO institucion (nombre, ruta) VALUES ('Carga con k6', 'k6-carga')
  ON CONFLICT (ruta) DO UPDATE SET estado = 'activa';
CREATE TEMP TABLE k6 AS SELECT id AS institucion, '$argon2id$v=19$m=16384,t=2,p=1$rMYLJbRYf0XpSwbwFvRSXg$S83MufhK4ywINMPZb8MUa4xC3r6jq+3LduNvpl9Jwm0'::text AS hash FROM institucion WHERE ruta = 'k6-carga';
INSERT INTO usuario (institucion_id, nombre, clave_hash, tipo_clave, rol_global)
  SELECT institucion, 'administrador k6', hash, 'contrasena', 'administrador' FROM k6
  ON CONFLICT (institucion_id, nombre_clave) DO UPDATE SET clave_hash = EXCLUDED.clave_hash, tipo_clave = 'contrasena', activo = true;
INSERT INTO usuario (institucion_id, nombre, clave_hash, tipo_clave, rol_global)
  SELECT institucion, 'vecino k6 ' || n, hash, 'contrasena', 'persona' FROM k6, generate_series(1, 12) n
  ON CONFLICT (institucion_id, nombre_clave) DO UPDATE SET clave_hash = EXCLUDED.clave_hash, tipo_clave = 'contrasena', activo = true;
INSERT INTO usuario (institucion_id, nombre, rol_global)
  SELECT institucion, 'cuenta de carga ' || lpad(n::text, 3, '0'), 'persona' FROM k6, generate_series(1, 500) n
  ON CONFLICT (institucion_id, nombre_clave) DO NOTHING;
DELETE FROM llave_acceso WHERE credencial_id = decode('cac263a8c84fe328d8458ee257a7e820', 'hex');
INSERT INTO llave_acceso (credencial_id, usuario_id, institucion_id, entidad_usuario, clave_publica, firmas, uv_inicializada, transportes,
                          respaldable, respaldada, atestacion, datos_cliente, etiqueta, creada_en, usada_en)
  SELECT decode('cac263a8c84fe328d8458ee257a7e820', 'hex'), u.id, u.institucion_id,
         decode('01' || replace(u.id::text, '-', ''), 'hex'),
         decode('a5010203262001215820ccbabc026af38e94451097c6ec1ad27a02ae0433060ed6d91c3788e69151fc6a22582099ce0c3ad5cc05402ace0f3d404c1c405c786ea7cd1c220c4269e531501a2261', 'hex'), 0, true, ARRAY['internal'], false, false,
         decode('a363666d74646e6f6e656761747453746d74a0686175746844617461589449960de5880e8c687434170f6476605b8fe4aeb9a28632c7995cf3ba831d97634500000000000000000000000000000000000000000010cac263a8c84fe328d8458ee257a7e820a5010203262001215820ccbabc026af38e94451097c6ec1ad27a02ae0433060ed6d91c3788e69151fc6a22582099ce0c3ad5cc05402ace0f3d404c1c405c786ea7cd1c220c4269e531501a2261', 'hex'), decode('7b2274797065223a22776562617574686e2e637265617465222c226368616c6c656e6765223a22593246795a3245745a475574617a59222c226f726967696e223a22687474703a2f2f6c6f63616c686f73743a38303830222c2263726f73734f726967696e223a66616c73657d', 'hex'),
         'Llave de carga de k6', now(), now()
  FROM usuario u, k6 WHERE u.institucion_id = k6.institucion AND u.nombre = 'administrador k6';
DELETE FROM intento_acceso WHERE institucion_id = (SELECT institucion FROM k6);
SELECT count(*) AS cuentas FROM usuario u, k6 WHERE u.institucion_id = k6.institucion;
