# Taller de Pensamiento Crítico

Catálogo completo de 49 técnicas de pensamiento crítico en 8 familias, cada una configurable y con ejemplos,
más flujos guiados que las encadenan. Todo funciona offline, sin ninguna API externa, y se levanta con
docker-compose. La única IA es Ollama local con modelos de 4 GB o menos (qwen3:4b y bge-m3), y la
aplicación funciona igual sin ella ("modo plantillas").

Estado: **hito 0, "Esqueleto seguro"**. La especificación completa está en
[`docs/investigacion-y-propuestas.html`](docs/investigacion-y-propuestas.html); el prompt con que se
construyó este hito, en [`docs/prompt-hito-0.md`](docs/prompt-hito-0.md).

## Requisitos en la máquina

Solo **Docker con Compose v2** (Docker 24 o superior). Ni Java, ni Maven, ni Node, ni PostgreSQL, ni Ollama:
todo lo monta el compose.

Hardware medido en este hito (RNF-01): ver [Mediciones](#mediciones-del-hito-0).

## Los dos comandos

Antes del primer arranque crea los dos secretos (una línea cada uno, ver [`secrets/README.md`](secrets/README.md)):

```sh
printf '%s' 'cambia-esta-clave' > secrets/db_password.txt
printf '%s' '2468' > secrets/admin_pin.txt
cp .env.example .env
```

1. Levantar todo (la primera vez descarga 3,7 GB de modelos y compila; después arranca en segundos):

   ```sh
   docker compose up --build
   ```

   Abre <http://127.0.0.1:8080>. La pantalla de bloqueo pregunta "¿Quién eres?": entra como `administrador`
   con el PIN de `secrets/admin_pin.txt` y crea una cuenta por persona en **Usuarios**.

2. Correr las pruebas dentro del compose (rápidas, contratos contra Fakes, integración y aceptación por HTTP):

   ```sh
   docker compose --profile test run --rm tests
   ```

Respaldo (deja `respaldos/pensamiento-AAAA-MM-DD.dump` en el host):

```sh
docker compose --profile backup run --rm backup
```

## Variables de entorno

Se leen de `.env` (copia de [`.env.example`](.env.example)):

| Variable | Por defecto | Qué hace |
|---|---|---|
| `BIND_IP` | `127.0.0.1` | Dirección en la que `app` expone el puerto 8080. `0.0.0.0` sirve a toda la red local. |
| `CONTRACT_REAL` | `false` | `true` solo en el workflow nocturno: corre los `Real*ContractIT` contra Ollama y PostgreSQL reales. |
| `APP_ZONA` | `America/Bogota` | Zona horaria de fechas de revisión y auditoría. |

Los secretos no van en variables: van en `secrets/db_password.txt` (PostgreSQL) y `secrets/admin_pin.txt`
(PIN de la cuenta `administrador`, creada sola en el primer arranque). La carpeta `secrets/` está ignorada por git.

Las demás variables del servicio `app` (tiempos máximos de Ollama, Graphviz y PDF, URL de Ollama, modelos)
están en `docker-compose.yml` con sus valores del documento y no hace falta tocarlas.

## Respaldo y restauración

- **Respaldo**: `docker compose --profile backup run --rm backup` ejecuta `pg_dump -Fc` como el rol
  administrador de PostgreSQL y deja el archivo en `respaldos/` del host (carpeta ignorada por git).
- **Restauración** en una base vacía (misma máquina u otra con el compose levantado):

  ```sh
  docker compose cp respaldos/pensamiento-2026-10-07.dump db:/tmp/r.dump
  docker compose exec db sh -c 'pg_restore -U pensamiento -d pensamiento --clean --if-exists /tmp/r.dump'
  ```

- La prueba `RF11RespaldoYRestauracionIT` hace exactamente eso en cada corrida del perfil test: dump,
  restauración en una base nueva y conteo de 49 técnicas, 8 familias y las cuentas.
- Caducidad: los dumps contienen datos personales de cada cuenta; conserva solo los últimos 30 días y
  guárdalos fuera de la máquina si la instalación sirve a más de una persona.

## Arquitectura

Paquetes (sección 4 del documento), verificados con ArchUnit en el perfil test:

| Paquete | Contiene | Depende de |
|---|---|---|
| `nucleo` | Afirmación, Evidencia, Fuente, Argumento, Técnica, reglas R01 a R04 como código puro, puertos (`Ia`, `Grafico`, `Reloj`, repositorios), el contrato `Ejecutor<C, E, R>` y el record `Resultado<R>` | nadie: sin Spring, sin JPA, sin Ollama |
| `catalogo` | lectura del JSON del repo, semilla repeatable de Flyway, repositorio de técnicas, verificación al arrancar, intenciones | `nucleo` |
| `tecnicas.f1` a `f8` | un ejecutor por técnica (ninguno en el hito 0) | `nucleo`, `catalogo` |
| `flujos`, `expediente` | flujos guiados y expediente (en el hito 0 solo los repositorios JDBC de ejecución y expediente) | `tecnicas`, `catalogo`, `nucleo` |
| `argdown`, `graficos`, `ia`, `biblioteca`, `trabajos` | adaptadores: Graphviz como proceso hijo con saneador de SVG, Ollama vía Spring AI, extractor de PDF con límites | solo puertos de `nucleo` |
| `web` | controladores, plantillas JTE, seguridad (Spring Security, RLS, CSRF, CSP), usuarios | `flujos`, `tecnicas`, `catalogo` |

Stack fijado: Spring Boot 4.1.1 sobre Java 25 con hilos virtuales, JTE 3.2.4 precompilado, htmx 2.0.11 y
Alpine.js 3.17.4 (build CSP) vendorizados, PostgreSQL 18 con pgvector, Flyway, Spring AI 2.0.1 solo para el
adaptador de Ollama, Graphviz en la imagen final. Sin npm ni CDN.

### "Casa con llave" (seguridad del hito 0)

- Cuenta por persona con PIN obligatorio (hash Argon2), sin autoregistro: el administrador crea y desactiva.
- Sesión en servidor que expira a los 10 minutos de inactividad. Si expira en medio de un formulario, la
  petición htmx recibe el bloqueo "¿Quién eres?" como overlay sobre la misma página: el borrador no se pierde.
- Toda consulta filtra por el usuario de la sesión **y** PostgreSQL aplica RLS con `FORCE ROW LEVEL SECURITY`
  y `SET LOCAL app.usuario` en cada transacción; el rol de aplicación `app` no tiene `BYPASSRLS`.
- CSRF activo con el token en las cabeceras de htmx desde el `<body>`; CSP `script-src 'self'` sin inline ni
  eval (Alpine build CSP, htmx con `allowEval:false`).
- SVG de Graphviz saneado por lista blanca; Graphviz y `pdftotext` como procesos hijo con tiempo máximo
  (5 s y 60 s), `-Gnslimit` y límite de páginas; el contenedor `app` con `mem_limit`, `pids_limit` y `read_only`.
- Tabla `auditoria` de solo inserción, escrita al crear y desactivar cuentas y al crear expedientes.
- Puerto ligado por `BIND_IP`; imágenes y modelos fijados por digest; gates de PR con OWASP Dependency-Check
  y SpotBugs con FindSecBugs.

## Pruebas

Dos velocidades, al estilo Rainsberger (Fakes, sin `verify`), bajo `src/test/java/pensamiento`:

| Raíz | Qué hay | Cuándo corre |
|---|---|---|
| `unidad/` | collaboration tests de R01 a R04 con los ejemplos de la sección 5b como oráculo, propiedades jqwik (fuerza entre 0 y 8, estado total, R04 sin peso en ciclos), verificador del catálogo, servicio de usuarios, saneador de SVG | `mvn test` (sin red ni base) |
| `contrato/` | una suite abstracta por puerto (`Ia`, `Grafico`, `Reloj`, repositorios de técnica, ejecución, usuarios, expediente y auditoría), `Fake*ContractTest` | cada PR |
| `contrato/real/` | `Real*ContractIT` contra Ollama, Graphviz y PostgreSQL del compose | nocturno, `CONTRACT_REAL=true` |
| `integracion/`, `aceptacion/` | RLS por SQL; RF-01, RF-02, RF-03 y RF-11 por HTTP contra `app:8080` | perfil test del compose |
| `arquitectura/`, `sensores/` | ArchUnit; Fakes sin contrato; voseo sobre plantillas, catálogo y código; configuración segura | cada PR |

Sensores de línea de comandos: `sensores/voseo.sh` (mismo listado que `VoseoTest`, en `sensores/voseo-prohibido.txt`).

### Pipeline

- **PR** (`.github/workflows/pr.yml`): compose levantado, perfil test, sensores y gates `-Pgates`
  (Dependency-Check con `NVD_API_KEY` opcional como secreto del repo; SpotBugs con FindSecBugs).
- **Nocturno** (`.github/workflows/nocturno.yml`, cron `0 7 * * *` UTC): `CONTRACT_REAL=true` contra el
  Ollama y el PostgreSQL del compose, con caché de los modelos.
- **Última corrida nocturna en verde**: PENDIENTE_NOCTURNO

## Mediciones del hito 0

PENDIENTE_MEDICIONES

## Licencia

El archivo [`LICENSE`](LICENSE) del repo (MIT) queda **como está, pendiente de la decisión del dueño** antes del
hito 1: la mesa de expertos evaluó Apache-2.0 y AGPL-3.0 para el código, y CC BY-SA 4.0 para el contenido
(ejemplos, esquemas, escenarios, preguntas), separada de la del código.

## Decisiones tomadas en el hito 0

PENDIENTE_DECISIONES
