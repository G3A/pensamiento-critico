#!/bin/bash
# Crea el rol de aplicación "app": con LOGIN, sin SUPERUSER y sin BYPASSRLS, para que las políticas
# de seguridad por fila (RLS con FORCE) se apliquen a toda consulta de la aplicación.
# Flyway corre con el rol administrador "pensamiento"; la aplicación consulta con "app".
set -euo pipefail
CLAVE="$(cat /run/secrets/db_password)"
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -v clave="$CLAVE" <<'SQL'
CREATE ROLE app LOGIN NOSUPERUSER NOBYPASSRLS NOCREATEDB NOCREATEROLE PASSWORD :'clave';
GRANT CONNECT ON DATABASE pensamiento TO app;
SQL
