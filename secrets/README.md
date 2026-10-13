# Secretos de la instalación

Esta carpeta está ignorada por git salvo este archivo. Antes del primer `docker compose up --build`
crea tres archivos de texto, cada uno con una sola línea y sin espacios al final:

- `db_password.txt`: contraseña de PostgreSQL (la misma para el rol administrador `pensamiento` y el rol
  de aplicación `app`, que no tiene `BYPASSRLS`).
- `operador_clave.txt`: clave del operador del servicio (hito 8), de 12 caracteres o más. El operador se crea solo la
  primera vez que arranca la aplicación; entra por `/operador`, crea instituciones e invita a su primer administrador.
- `respaldo_clave.txt`: clave con que se cifran los respaldos (hito 8). Guarda una copia fuera de este equipo: sin ella
  los respaldos no se pueden restaurar.

Ejemplo:

```sh
printf '%s' 'cambia-esta-clave' > secrets/db_password.txt
printf '%s' 'una-clave-larga-del-operador' > secrets/operador_clave.txt
printf '%s' 'otra-clave-larga-para-los-respaldos' > secrets/respaldo_clave.txt
```

`admin_pin.txt` ya no se usa desde el hito 8: la institución que existía antes conserva su cuenta `administrador`, y las
instituciones nuevas reciben su primer administrador con una invitación del operador.

Las pruebas (`docker compose --profile test run --rm tests`) no leen esta carpeta: usan secretos propios, generados al
azar en el volumen `secretos-prueba`.
