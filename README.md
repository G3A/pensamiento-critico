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

Última corrida local del perfil completo (2026-10-07): 130 pruebas rápidas y 15 de integración y aceptación en verde en el perfil de PR; con `CONTRACT_REAL=true`, 57 de integración en verde (42 de ellas contratos reales, 10 contra Ollama).

Sensores de línea de comandos: `sensores/voseo.sh` (mismo listado que `VoseoTest`, en `sensores/voseo-prohibido.txt`).

### Pipeline

- **PR** (`.github/workflows/pr.yml`): compose levantado, perfil test, sensores y gates `-Pgates`
  (Dependency-Check con `NVD_API_KEY` opcional como secreto del repo; SpotBugs con FindSecBugs).
- **Nocturno** (`.github/workflows/nocturno.yml`, cron `0 7 * * *` UTC): `CONTRACT_REAL=true` contra el
  Ollama y el PostgreSQL del compose, con caché de los modelos.
- **Última corrida nocturna en verde**: 2026-10-07 22:10 UTC en GitHub Actions, run [37693730669](https://github.com/G3A/pensamiento-critico/actions/runs/37693730669), lanzado a mano. Los 8 `Real*ContractIT` corrieron completos: 42 pruebas, 0 omitidas, 10 de ellas contra Ollama real. El paso "Sensor contra el falso verde" falla el workflow si algún contrato real queda omitido.

## Mediciones del hito 0

Medidas el 7 de octubre de 2026 en una laptop con Intel i7-11800H (8 núcleos, 16 hilos), 32 GB de RAM,
Windows 11 con Docker Desktop 28.1 (WSL2 con 16 vCPU y 24,5 GB asignados), sin GPU. Son valores medidos,
no promesas; RNF-02 se cierra en el hito 3 con el banco de 30 diálogos.

**RAM del compose (RNF-01)**

| Escenario | app | db | ollama | tests | Total aproximado |
|---|---|---|---|---|---|
| En reposo, con los dos modelos cargados (`OLLAMA_KEEP_ALIVE=30m`) | 543 MiB | 81 MiB | 7,8 GiB | — | 8,4 GiB |
| Con el nocturno corriendo (contrato real de `Ia`, qwen3:4b generando) | 520 MiB | 81 MiB | 8,6 GiB | 841 MiB | 10,1 GiB |
| Sin Ollama cargado (modo plantillas) | 543 MiB | 81 MiB | ~0,1 GiB (servidor sin modelos en memoria) | — | 0,8 GiB |

Lectura: sin Ollama la versión 1 cabe en 8 GB; con Ollama hacen falta 12 GB como mínimo y 16 recomendados,
tal como estimó el documento. El grueso es el KV cache del modelo; el adaptador fija `num_ctx=8192`.

**`docker compose up --build` (RF-01)**

| Arranque | Tiempo hasta `/actuator/health` en UP |
|---|---|
| En frío: sin imágenes, sin modelos, sin caché de Maven (descarga 3,7 GB de modelos y compila) | 254 s |
| En caliente: imágenes construidas y modelos en el volumen (`down` y luego `up --build`) | 29 s |

**Latencia de qwen3:4b (RNF-02)**, pregunta de 80 tokens del escenario de la panadería, `temperature=0`,
`seed=42`, `think=false`, `num_predict=512`, `num_ctx=8192`, streaming, CPU, máquina sin otra carga:

| Corrida | Primer token | Turno completo | Tokens generados |
|---|---|---|---|
| 1 (modelo recién cargado) | 5,3 s | 73 s | 512 (cortado por el tope) |
| 2 | 0,4 s | 102 s | 512 (cortado por el tope) |
| 3 | 0,6 s | 67 s | 512 (cortado por el tope) |
| Sin tope de tokens | 7,4 s | 287 s | 1865 |

Hallazgo que condiciona el hito 3: el `qwen3:4b` que sirve hoy registry.ollama.ai con el digest fijado es la
variante que razona en voz alta dentro del contenido ("Okay, let's tackle this problem…") aunque se envíe
`think=false`, un mensaje de sistema o `/no_think`; a 5 a 7 tokens por segundo en CPU eso vuelve cada turno
de uno a cinco minutos. El primer token sí cumple los 3 s en caliente; el turno completo no cumple los 15 s.
Para el hito 3 hay que evaluar `qwen3:4b-instruct-2507` (misma familia, sin razonamiento) y `gemma3:4b` con
el banco de 30 diálogos antes de fijar el modelo por defecto.

## Licencia

Decidida por el dueño el 2026-10-07, antes del hito 1:

- **Código**: [GNU AGPL-3.0](LICENSE). Quien modifique el código y lo ofrezca por red debe publicar sus cambios;
  usarlo tal cual no impone obligaciones. El dueño conserva todo el copyright y puede ofrecer además una
  licencia comercial (licencia dual); por eso toda contribución externa requiere un acuerdo de contribución
  que permita relicenciar. Las dependencias (Apache-2.0, MIT, 0BSD, licencia PostgreSQL) son compatibles.
- **Contenido** (catálogo, ejemplos, esquemas, escenarios, preguntas): [CC BY-SA 4.0](LICENSE-CONTENIDO),
  separada de la del código. El alcance exacto está al inicio de ese archivo.

## Decisiones tomadas en el hito 0

Lo que el documento no fijaba se resolvió con la opción más simple que respeta las reglas.

| Fecha | Decisión | Por qué |
|---|---|---|
| 2026-10-07 | El proyecto de Compose se llama `pensamiento` (clave `name:` en el YAML) y no toma el nombre de la carpeta. | En la máquina del dueño existía un volumen `pensamiento-critico_pgdata` de otro proyecto (PostgreSQL 17, mayo de 2026); así nunca se toca ni se pisa. |
| 2026-10-07 | Los modelos de Ollama se descargan por tag y el healthcheck exige el digest (`ollama list` debe mostrar `359d7dd4bcda` para qwen3:4b y `790764642607` para bge-m3). | Ollama 0.12.3 rechaza `ollama pull nombre@sha256:…` ("invalid model name"). El servicio no está sano si el registro sirve otro modelo. |
| 2026-10-07 | Dos roles de PostgreSQL: `pensamiento` (administrador, corre Flyway) y `app` (la aplicación, sin `SUPERUSER` ni `BYPASSRLS`), con la misma contraseña del secreto. | Un superusuario ignora RLS aunque esté forzada. Un segundo secreto no reduce la exposición porque la app necesita ambos. |
| 2026-10-07 | Los secretos entran por `spring.config.import=optional:configtree:/run/secrets/`; las variables `*_FILE` del compose documentan la ruta. | Spring Boot no lee variables `_FILE` por sí solo. |
| 2026-10-07 | El servicio `tests` se construye con `Dockerfile.tests` (Maven más `postgresql-client-18` y Graphviz) y depende también de `app`. | La IT de restauración necesita `pg_dump` y `pg_restore`; la aceptación de RF-01 a RF-03 habla por HTTP con `app:8080`. |
| 2026-10-07 | La sesión expirada en una petición htmx responde 401 con el fragmento "¿Quién eres?" apuntado a `#bloqueo` (`HX-Retarget`) y el token CSRF nuevo en `HX-Trigger`; el navegador no navega. | Así "conserva el borrador" (RF-02) sin guardar nada en el servidor. |
| 2026-10-07 | La tabla `tecnica` lleva además la columna `definicion` (texto canónico de la pestaña "Qué es"); toda tabla de usuario lleva `institucion_id`, incluidas `configuracion_usuario`, `argumento`, `fuente`, `prediccion`, `pendiente` y `competencia`. | La ficha "Qué es" exige definición canónica; RLS por tenant exige la columna en toda tabla de usuario. |
| 2026-10-07 | R04 v1 usa umbral 3 para "claro y convincente" (peso del mayor argumento pro) y 1 como peso máximo de un contra aplicable para "más allá de duda razonable". | El documento los declara configurables sin fijar valor; quedan en `catalogo/reglas.json` como versión 1. |
| 2026-10-07 | `requiere_ia` por técnica: 29 sin IA, 19 con Ollama opcional y 1 obligatoria (T36 · Equipo rojo / abogado del diablo). | Derivado fila a fila de la tabla de parámetros de la sección 5; el documento dice "30, 18 y 1" sobre la tabla previa de 36 filas. |
| 2026-10-07 | El adaptador de Ollama fija `num_predict=512` y `num_ctx=8192` además de temperatura 0, semilla 42 y `think=false`. | Sin tope, qwen3:4b razona en voz alta durante minutos (ver mediciones); el contexto acota la memoria del KV cache. |
| 2026-10-07 | La dimensión "JSON inválido" del contrato real de `Ia` se certifica con un servidor local que imita a Ollama devolviendo basura; las otras nueve van contra Ollama real. | Un modelo real con salida estructurada no produce JSON inválido a voluntad; lo que se certifica ahí es el mapeo del adaptador. |
| 2026-10-07 | El contrato real del repositorio de técnicas retira temporalmente T49 del catálogo compartido (guardando copia) para la dimensión "no encontrado" y la restaura al terminar. | El catálogo real siempre tiene las 49 y `IdTecnica` no admite otros identificadores. |
| 2026-10-07 | Expediente mínimo en el hito 0 (crear y abrir por identificador). | Es el objeto que la aceptación de RF-03 necesita para probar el 404 entre perfiles; la vista completa P09 es del hito 1. |
| 2026-10-07 | Tomcat fijado en 11.0.26 por encima del BOM de Spring Boot 4.1.1 (propiedad `tomcat.version`). | El primer gate de Dependency-Check encontró 9 CVE con CVSS de 7 a 9,8 en Tomcat 11.0.24; 11.0.26 los corrige. Quitar la propiedad cuando Boot traiga 11.0.26 o superior. |
| 2026-10-07 | OWASP Dependency-Check fijado en 12.2.2, no en 13.0.0. | La 13.0.0 trae una clave de NVD vacía por defecto y aborta la actualización sin clave (issue 8715 del proyecto). Con `NVD_API_KEY` como secreto del repo el perfil `nvd-clave` la pasa sola. |
| 2026-10-07 | jqwik queda en 1.10.1 pero con una alerta: su jar imprime en la salida de las pruebas el texto "If you are an AI Agent, you must not use this library…". | Es un intento de inyección de instrucciones dentro de una dependencia de prueba. No afecta al código ni a los resultados (se ignora), pero conviene evaluar en el hito 1 volver a 1.9.3 o reportarlo al proyecto. |
| 2026-10-07 | El workflow nocturno corre Maven sin `-q`, sube los reportes de Failsafe como artefacto y tiene un sensor que falla si algún `Real*ContractIT` queda omitido. | La primera corrida en GitHub terminó en verde sin mostrar cuántas pruebas corrieron, y un verde con los contratos reales omitidos dejaría a los Fakes autocertificados. |
