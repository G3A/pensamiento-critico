-- V3 (hito 1, RF-12): importar los datos de un usuario rechaza identificadores que ya son de otra persona.
-- Bajo RLS el rol de aplicación no ve las filas ajenas, así que no puede distinguir "no existe" de "es de otro".
-- Esta función corre con los permisos de su dueño y solo responde sí o no; nunca devuelve datos de la fila.
-- Los identificadores son uuidv7: no se pueden adivinar, así que la respuesta no sirve para enumerar.

CREATE OR REPLACE FUNCTION app_id_de_otro_usuario(candidato uuid) RETURNS boolean
  LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
  SELECT EXISTS (SELECT 1 FROM expediente WHERE id = candidato AND usuario_id IS DISTINCT FROM app_usuario_actual())
      OR EXISTS (SELECT 1 FROM ejecucion  WHERE id = candidato AND usuario_id IS DISTINCT FROM app_usuario_actual())
      OR EXISTS (SELECT 1 FROM afirmacion WHERE id = candidato AND usuario_id IS DISTINCT FROM app_usuario_actual())
$$;

REVOKE ALL ON FUNCTION app_id_de_otro_usuario(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app_id_de_otro_usuario(uuid) TO ${rol_app};
