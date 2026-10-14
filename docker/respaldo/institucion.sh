#!/bin/bash
# Exportar y borrar una institución completa (hito 8), como administrador de la base y con constancia fechada.
#   docker compose --profile backup run --rm institucion exportar <ruta>
#   docker compose --profile backup run --rm institucion borrar <ruta>
# exportar: una tabla por archivo CSV con todas las filas de la institución, empaquetadas y cifradas con la clave de respaldo
#   (respaldos/institucion-<ruta>-<fecha>.tar.gpg), más una constancia en texto con las filas por tabla y el sha256.
# borrar: exporta primero y después borra todo en una sola transacción; la constancia dice cuántas filas salieron de cada
#   tabla y queda una línea "borrar_institucion" en la auditoría del operador. Sin exportación no hay borrado.
# Variables: las de respaldo.sh (PGHOST, PGUSER, PGDATABASE, CLAVE_DB, CLAVE_RESPALDO, DESTINO).
set -euo pipefail

: "${PGHOST:=db}" "${PGUSER:=pensamiento}" "${PGDATABASE:=pensamiento}"
: "${CLAVE_DB:=/run/secrets/db_password}" "${CLAVE_RESPALDO:=/run/secrets/respaldo_clave}" "${DESTINO:=/respaldos}"
export PGHOST PGUSER PGDATABASE
PGPASSWORD="$(cat "$CLAVE_DB")"
export PGPASSWORD

accion="${1:-}"
ruta="${2:-}"
if [[ ! "$accion" =~ ^(exportar|borrar)$ ]] || [[ ! "$ruta" =~ ^[a-z0-9][a-z0-9-]{1,38}[a-z0-9]$ ]]; then
  echo "Uso: institucion.sh exportar|borrar <ruta>" >&2
  exit 2
fi
if [ ! -s "$CLAVE_RESPALDO" ]; then
  echo "Falta la clave de respaldo ($CLAVE_RESPALDO)" >&2
  exit 2
fi

psql_valor() {
  psql -X -q -t -A -v ON_ERROR_STOP=1 "$@"
}

# La ruta ya pasó la expresión de arriba (minúsculas, dígitos y guiones): se puede poner en el SQL.
id="$(psql_valor -c "SELECT id FROM institucion WHERE ruta = '$ruta'")"
if [ -z "$id" ]; then
  echo "No existe la institución /$ruta" >&2
  exit 3
fi

# Cada tabla con datos de la institución y la condición que elige sus filas (las hijas, por su madre).
TABLAS=(
  "institucion|id = '$id'"
  "usuario|institucion_id = '$id'"
  "grupo|institucion_id = '$id'"
  "miembro_grupo|grupo_id IN (SELECT id FROM grupo WHERE institucion_id = '$id')"
  "configuracion_usuario|institucion_id = '$id'"
  "afirmacion|institucion_id = '$id'"
  "expediente|institucion_id = '$id'"
  "ejecucion|institucion_id = '$id'"
  "ejecucion_afirmacion|ejecucion_id IN (SELECT id FROM ejecucion WHERE institucion_id = '$id')"
  "argumento|institucion_id = '$id'"
  "premisa_argumento|argumento_id IN (SELECT id FROM argumento WHERE institucion_id = '$id')"
  "documento|institucion_id = '$id'"
  "fragmento|documento_id IN (SELECT id FROM documento WHERE institucion_id = '$id')"
  "fuente|institucion_id = '$id'"
  "evidencia|afirmacion_id IN (SELECT id FROM afirmacion WHERE institucion_id = '$id')"
  "cambio_opinion|institucion_id = '$id'"
  "prediccion|institucion_id = '$id'"
  "pendiente|institucion_id = '$id'"
  "competencia|institucion_id = '$id'"
  "auditoria|institucion_id = '$id'"
  "sesion_consejero|institucion_id = '$id'"
  "turno_consejero|institucion_id = '$id'"
  "verificacion|institucion_id = '$id'"
  "intento_dojo|institucion_id = '$id'"
  "invitacion|institucion_id = '$id'"
  "codigo_cuenta|institucion_id = '$id'"
  "sesion_aula|institucion_id = '$id'"
  "intento_acceso|institucion_id = '$id'"
  "llave_acceso|institucion_id = '$id'"
  "codigo_recuperacion|institucion_id = '$id'"
)

fecha="$(date +%F)"
momento="$(date -u +%FT%TZ)"
trabajo="$(mktemp -d)"
export GNUPGHOME="$trabajo/gnupg"
mkdir -m 700 "$GNUPGHOME"
trap 'rm -rf "$trabajo"' EXIT
mkdir "$trabajo/$ruta"

conteos=""
for t in "${TABLAS[@]}"; do
  tabla="${t%%|*}"
  donde="${t#*|}"
  psql -X -q -v ON_ERROR_STOP=1 -c "\\copy (SELECT * FROM $tabla WHERE $donde) TO '$trabajo/$ruta/$tabla.csv' WITH (FORMAT csv, HEADER)"
  filas="$(psql_valor -c "SELECT count(*) FROM $tabla WHERE $donde")"
  conteos+="$tabla: $filas"$'\n'
done

paquete="$DESTINO/institucion-$ruta-$fecha.tar.gpg"
tar -C "$trabajo" -cf - "$ruta" | gpg --batch --quiet --yes --pinentry-mode loopback --passphrase-file "$CLAVE_RESPALDO" \
  --symmetric --cipher-algo AES256 --s2k-digest-algo SHA512 --output "$paquete"
huella="$(sha256sum "$paquete" | cut -d' ' -f1)"

constancia="$DESTINO/constancia-$accion-$ruta-$fecha.txt"
{
  echo "Constancia de $accion de la institución /$ruta"
  echo "Fecha (UTC): $momento"
  echo "Institución: $id"
  echo "Exportación cifrada: $(basename "$paquete") (sha256 $huella)"
  echo "Filas por tabla:"
  printf '%s' "$conteos"
} > "$constancia"

if [ "$accion" = "borrar" ]; then
  # Orden por las claves foráneas: lo que restringe va antes que lo restringido. Una sola transacción.
  psql -X -q -v ON_ERROR_STOP=1 <<SQL
BEGIN;
DELETE FROM evidencia WHERE afirmacion_id IN (SELECT id FROM afirmacion WHERE institucion_id = '$id');
DELETE FROM turno_consejero WHERE institucion_id = '$id';
DELETE FROM sesion_consejero WHERE institucion_id = '$id';
DELETE FROM argumento WHERE institucion_id = '$id';
DELETE FROM ejecucion WHERE institucion_id = '$id';
DELETE FROM cambio_opinion WHERE institucion_id = '$id';
DELETE FROM prediccion WHERE institucion_id = '$id';
DELETE FROM pendiente WHERE institucion_id = '$id';
DELETE FROM verificacion WHERE institucion_id = '$id';
DELETE FROM expediente WHERE institucion_id = '$id';
DELETE FROM afirmacion WHERE institucion_id = '$id';
DELETE FROM fuente WHERE institucion_id = '$id';
DELETE FROM documento WHERE institucion_id = '$id';
DELETE FROM configuracion_usuario WHERE institucion_id = '$id';
DELETE FROM competencia WHERE institucion_id = '$id';
DELETE FROM intento_dojo WHERE institucion_id = '$id';
DELETE FROM auditoria WHERE institucion_id = '$id';
DELETE FROM sesion_aula WHERE institucion_id = '$id';
DELETE FROM grupo WHERE institucion_id = '$id';
DELETE FROM codigo_cuenta WHERE institucion_id = '$id';
DELETE FROM intento_acceso WHERE institucion_id = '$id';
DELETE FROM llave_acceso WHERE institucion_id = '$id';
DELETE FROM codigo_recuperacion WHERE institucion_id = '$id';
DELETE FROM usuario WHERE institucion_id = '$id';
DELETE FROM invitacion WHERE institucion_id = '$id';
DELETE FROM institucion WHERE id = '$id';
INSERT INTO auditoria_operador (operador_id, accion, institucion_id, ruta) VALUES (NULL, 'borrar_institucion', '$id', '$ruta');
COMMIT;
SQL
  echo "Borrada: sí, en una sola transacción, después de exportar." >> "$constancia"
fi

echo "$constancia"
cat "$constancia"
