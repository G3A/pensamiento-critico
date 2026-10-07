# Secretos de la instalación

Esta carpeta está ignorada por git salvo este archivo. Antes del primer `docker compose up --build`
crea dos archivos de texto, cada uno con una sola línea y sin espacios al final:

- `db_password.txt`: contraseña de PostgreSQL (la misma para el rol administrador `pensamiento` y el rol
  de aplicación `app`, que no tiene `BYPASSRLS`).
- `admin_pin.txt`: PIN de la cuenta `administrador`, que se crea sola la primera vez que arranca la aplicación.

Ejemplo:

```sh
printf '%s' 'cambia-esta-clave' > secrets/db_password.txt
printf '%s' '2468' > secrets/admin_pin.txt
```
