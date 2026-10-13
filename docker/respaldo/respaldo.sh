#!/bin/bash
# Respaldo completo cifrado (hito 8): pg_dump -Fc del rol administrador, cifrado con GnuPG en modo simétrico (AES-256)
# con la clave de secrets/respaldo_clave.txt, que vive fuera del repositorio. El archivo sin cifrar nunca toca el disco.
#   docker compose --profile backup run --rm backup                      -> respaldos/pensamiento-AAAA-MM-DD.dump.gpg
#   docker compose --profile backup run --rm backup restaurar ARCHIVO    -> restaura en la base vacía del compose
# Variables: PGHOST, PGUSER, PGDATABASE, CLAVE_DB (archivo), CLAVE_RESPALDO (archivo), DESTINO (carpeta).
set -euo pipefail

: "${PGHOST:=db}" "${PGUSER:=pensamiento}" "${PGDATABASE:=pensamiento}"
: "${CLAVE_DB:=/run/secrets/db_password}" "${CLAVE_RESPALDO:=/run/secrets/respaldo_clave}" "${DESTINO:=/respaldos}"
export PGHOST PGUSER PGDATABASE
PGPASSWORD="$(cat "$CLAVE_DB")"
export PGPASSWORD

if [ ! -s "$CLAVE_RESPALDO" ]; then
  echo "Falta la clave de respaldo ($CLAVE_RESPALDO): sin ella no se respalda en claro" >&2
  exit 2
fi
export GNUPGHOME
GNUPGHOME="$(mktemp -d)"
trap 'rm -rf "$GNUPGHOME"' EXIT

cifrar() {
  gpg --batch --quiet --yes --pinentry-mode loopback --passphrase-file "$CLAVE_RESPALDO" \
      --symmetric --cipher-algo AES256 --s2k-digest-algo SHA512 --output "$1"
}

descifrar() {
  gpg --batch --quiet --pinentry-mode loopback --passphrase-file "$CLAVE_RESPALDO" --decrypt "$1"
}

case "${1:-respaldar}" in
  respaldar)
    archivo="$DESTINO/${PGDATABASE}-$(date +%F).dump.gpg"
    pg_dump -Fc | cifrar "$archivo"
    echo "Respaldo cifrado: $archivo ($(stat -c %s "$archivo") bytes, sha256 $(sha256sum "$archivo" | cut -d' ' -f1))"
    ;;
  restaurar)
    # Restaura en la base indicada por PGDATABASE (vacía o con --clean). Segundo argumento: el archivo .dump.gpg.
    descifrar "${2:?falta el archivo cifrado}" | pg_restore --no-owner --dbname "$PGDATABASE" ${RESTAURAR_OPCIONES:-}
    echo "Restaurado en $PGDATABASE desde $2"
    ;;
  *)
    echo "Uso: respaldo.sh [respaldar | restaurar ARCHIVO]" >&2
    exit 2
    ;;
esac
