#!/usr/bin/env bash
# Sensor de voseo y español peninsular (RNF-10). Sale con 1 si encuentra alguna forma prohibida y con 2 si
# grep falla: un error del propio sensor nunca debe leerse como "sin hallazgos".
# Uso: sensores/voseo.sh [rutas...]   (por defecto: plantillas, catálogo, estáticos, código, README y docs)
set -euo pipefail
cd "$(dirname "$0")/.."
export LC_ALL=C.UTF-8
LISTA="sensores/voseo-prohibido.txt"
RUTAS=("$@")
if [ ${#RUTAS[@]} -eq 0 ]; then
  RUTAS=(src/main/jte src/main/resources/catalogo src/main/resources/static/app.js src/main/resources/static/app.css src/main/java src/test/java README.md docs/ejemplos .github)
fi
PATRON="$(grep -v '^#' "$LISTA" | sed '/^$/d' | paste -sd '|' -)"
# Palabra completa: sin letra, dígito, guion ni guion bajo pegados antes o después. Solo -P (con -E grep aborta).
set +e
HALLAZGOS="$(grep -rniP --exclude='*.min.js' "(?<![\p{L}\p{N}_-])(${PATRON})(?![\p{L}\p{N}_-])" "${RUTAS[@]}")"
CODIGO=$?
set -e
if [ "$CODIGO" -ge 2 ]; then
  echo "El sensor de voseo no pudo correr (grep salió con $CODIGO)." >&2
  exit 2
fi
if [ -n "$HALLAZGOS" ]; then
  echo "Voseo o español peninsular encontrado:"
  echo "$HALLAZGOS"
  exit 1
fi
echo "Sensor de voseo: sin hallazgos en ${RUTAS[*]}"
