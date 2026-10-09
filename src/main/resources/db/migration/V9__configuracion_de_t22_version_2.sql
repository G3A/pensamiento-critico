-- V9 (hito 6): T22 · Triangulación sube a la versión de esquema 2 (identificador de la fuente y de la afirmación en la
-- entrada; las fuentes nuevas van a sus tablas). La configuración no cambió de forma: la de cada persona sigue valiendo.
UPDATE configuracion_usuario SET version_esquema = 2 WHERE tecnica_id = 'T22' AND version_esquema = 1;
