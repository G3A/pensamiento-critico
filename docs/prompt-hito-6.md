# Prompt para arrancar el hito 6 en otra sesión

Copia todo lo que sigue como primer mensaje de una sesión nueva de Claude Code abierta en la raíz de este repositorio.

---

Vas a construir el **hito 6, "Fuentes y biblioteca"**, del proyecto Taller de Pensamiento Crítico. Los hitos 0 a 5 están
cerrados en `main`: la versión 1 (hitos 0 a 2), Ollama en técnicas, opcional (hito 3), Decisiones y problemas con el
Diario de decisiones (hito 4) y Cuestionamiento y perspectivas con el Consejero socrático (hito 5). Este hito arma la
**ficha de verificación** completa (A+, P10), la **ficha de fuente** (P12), la **biblioteca** local (P11 y P13) con el
importador como proceso hijo y pgvector con HNSW, y las técnicas de F4 · Evaluación de fuentes e información que faltan.

Entran cuatro técnicas (fila del hito 6 en la sección 9: "T19, T20, T21, T23 (T22 ya en el hito 3)"): T19 · SIFT,
T20 · Lectura lateral, T21 · CRAAP y T23 · Jerarquía de evidencia. T22 · Triangulación ya existe desde el hito 3 y la
verificación la reúsa; T17 · Hecho, inferencia, juicio es el paso 1 de la ficha.

La especificación está en `docs/investigacion-y-propuestas.html`. Antes de escribir código, léela con atención en estas
secciones:

- **4**: fila "Verificar si una premisa es cierta" de la tabla de capacidades offline (la app nunca dice "verdadero": dice
  "verificada por ti con N fuentes bajo el estándar X"), la fila "Trabajos largos" (tabla `trabajo` con reencolado,
  semáforos Ollama 1 y Graphviz 4), el bloque "Modelo de amenazas" (procesos hijo con tiempo máximo y límite de páginas,
  50 MB, tipos por contenido, documentos privados por defecto, compartir auditado) y la tabla de hardware por concurrencia.
- **5**: el bloque "Verificar si una premisa es cierta" y los parámetros y resultados de T19, T20, T21 y T23.
- **5b**: Fuente (con `grupo_origen` para la independencia mutua), Evidencia, documento, fragmento, y las reglas **R01 ·
  Fuerza de una evidencia**, **R02 · Fuerza neta**, **R03 · Estado de una afirmación** y **R04 · Aceptabilidad bajo
  estándar de prueba**, con sus ejemplos numéricos.
- **5c**: RNF-08 (50 MB, PDF, Markdown, texto y CSV detectados por contenido, sin OCR) y RF-14 (T22 etiqueta pasajes).
- **6**: flujo A+ y la tabla de técnicas por flujo (qué paso de la ficha cubre cada técnica).
- **7**: mockups de P10 · Ficha de verificación, P11 · Panel de biblioteca, P12 · Ficha de fuente y P13 · Biblioteca, y las
  tarjetas de T19, T20, T21 y T23 en las 49.
- **9**: fila del hito 6.
- **10**: decisiones cerradas sobre biblioteca, inyección de prompt y documentos adversarios.

Lo que está escrito ahí manda sobre cualquier preferencia tuya.

Lee también el `README.md` completo, en especial las seis tablas de decisiones (hitos 0 a 5) y las mediciones; lee
`docs/evaluacion-modelo.md` (los bancos, los umbrales escritos antes de medir y lo que quedó "experimental"),
`docs/consejero.md`, `docs/ejemplos/T22.md` como modelo de una técnica de fuentes con el modelo opcional, `docs/ejemplos/T28.md`
como modelo de ejemplos en prosa y `docs/ejemplos/T11.md` y `T38.md`, que dejaron condiciones y cruxes como pendientes de
verificación para este hito.

## Reglas que no se negocian

1. Español latinoamericano neutro con tuteo en todo texto: código, comentarios, plantillas, ejemplos, prompts del modelo,
   mensajes de commit, documentación. Nunca voseo ni español peninsular. Al terminar cada archivo con texto, pasa
   `sensores/voseo.sh`. Ojo: el sensor también prohíbe la palabra peninsular de asentimiento, aunque se use como verbo.
2. Cien por ciento offline. Ninguna API externa. La única IA es Ollama del compose con modelos de 4 GB o menos
   (`qwen3:4b-instruct-2507-q4_K_M` para chat y clasificación, `bge-m3` para embeddings), y todo debe funcionar igual con
   Ollama apagado: la búsqueda en la biblioteca cae a texto completo de PostgreSQL y el etiquetado de pasajes, a la persona.
3. "El modelo propone, nunca califica". La app **nunca** dice "verdadero" ni "falso": el estado de una afirmación sale de
   R03 sobre evidencias adoptadas por la persona. Toda salida del modelo pasa por el mecanismo del hito 3 (`ConModelo`,
   `Propuesta`, adoptar explícito) o por el validador del turno del hito 5. Escribe los bancos y los umbrales **antes** de
   medir; lo que no los cumpla queda "experimental".
4. Stack y versiones sin cambios: Spring Boot 4.1.1 sobre Java 25, Spring AI 2.0.1 solo dentro del paquete `ia`, JTE 3.2.4
   precompilado, htmx 2.0.11 con `ext/sse.js` y Alpine 3.17.4 build CSP vendorizados, PostgreSQL 18 con pgvector, Graphviz
   y `pdftotext` como procesos hijo, json-schema-validator 3.0.8, Playwright 1.63.0 y k6 2.3.0 en pruebas, Ollama 0.40.1
   fijado por digest.
   - Ninguna dependencia nueva sin anotarla con su motivo en las decisiones del README y sin confirmar su versión en la
     fuente oficial.
   - Sin npm ni CDN. `app.js` sigue por debajo de 100 líneas y la CSP sigue siendo `script-src 'self'`, sin inline ni eval.
     La subida de archivos usa el progreso de htmx que ya está en `app.js`.
5. Pruebas al estilo Rainsberger con las skills `pruebas-de-unidad` y `pruebas-de-contrato`:
   - Collaboration tests con Fakes y aserciones sobre resultado o estado; prohibido `verify(...)`.
   - Todo Fake nuevo lleva su `Fake*ContractTest` y su `Real*ContractIT` gated por `CONTRACT_REAL=true`, y se agrega a la
     lista del sensor del workflow nocturno (hoy son 15 contratos reales). Un repositorio nuevo (fuentes, evidencias,
     documentos, fragmentos, trabajos) va detrás de un puerto de `nucleo` con su contrato.
   - Nunca H2. Valores esperados como literales escritos a mano. El oráculo de las técnicas es el **modo plantillas**.
   - Propiedades con jqwik donde la lógica las tenga (por ejemplo: R01 entre 0 y 8 ya existe; R03 con dos fuentes del
     mismo `grupo_origen` nunca da "verificada"; el troceado nunca pierde ni repite texto; un documento privado nunca
     aparece en la búsqueda de otra persona).
6. Arquitectura de la sección 4, verificada por `ArquitecturaTest`: ejecutores en `tecnicas.f4`; el flujo A+ en `flujos`;
   el importador, el troceado y pgvector en `biblioteca` (adaptadores), la tabla de trabajos en `trabajos`; repositorios
   nuevos detrás de puertos de `nucleo`; nadie fuera de `ia` habla con Spring AI.
7. Una técnica individual se cita siempre como código más nombre, por ejemplo `T21 · CRAAP`; los rangos como `T19 a T23`.
8. Ejemplos y datos ficticios solo con los tres escenarios del documento (una familia, una panadería con dos sucursales,
   un barrio con junta de vecinos), roles anónimos y sin nombres de personas reales. Los documentos de prueba de la
   biblioteca también son ficticios y van en `src/test/resources`.
9. Commits pequeños y frecuentes, en español, con el identificador del requisito que cierran. No hagas push ni crees ramas
   sin que te lo pidan.

## Lo que el hito 5 dejó dicho y hay que atender

- **El nocturno de GitHub** debe estar en verde con los 15 `Real*ContractIT` del hito 5 (se suman sesiones, cambios de
  opinión y las dimensiones nuevas de `Grafico` y del registro de identificadores). Revisa la última corrida antes de sumar
  contratos reales nuevos y, si falla, arréglalo primero.
- **Pendientes de verificación que esperan la ficha**: T11 · Falsación deja sus condiciones verificables, T38 · Double crux
  su crux común, T09 · 5 porqués sus causas raíz, T10 · Escalera de inferencia su peldaño débil y T41 · Primeros principios
  sus supuestos, todos como pendientes de tipo verificación sobre una afirmación. La ficha debe poder abrirse desde ese
  pendiente y, al cerrar la verificación, cerrar el pendiente (`RepositorioEjecucion.cerrarPendientes`).
- **El validador del turno y la redacción con reintentos** (`tecnicas.comun.ValidadorTurno` y `Redaccion`) existen: si una
  pregunta o un texto del modelo llega directo a la persona, pásalo por ahí.
- **Los cambios de opinión ya se escriben** (`cambio_opinion`, R05): si un veredicto de la ficha cambia la confianza que
  la persona declaró sobre una postura, decide si este hito lo registra con causa "evidencia" y anótalo.
- **El paquete de datos va en la versión 4**: si hay tablas nuevas (fuentes, evidencias, documentos), sube a la 5 y migra.
  Los documentos importados son privados por defecto: decide si el respaldo de una persona lleva sus archivos o solo sus
  metadatos y anótalo.
- **`requiere_ia` quedó en 37 sin IA, 12 opcionales y 0 obligatorias**. Revisa T19, T20, T21 y T23 contra lo que el modelo
  haga de verdad en este hito.
- **Dos constructores en un `@Component` de `tecnicas` impiden que la app arranque** (Spring no elige y ArchUnit prohíbe
  `@Autowired` ahí): un solo constructor público, o uno sin argumentos más otro para las pruebas.
- **Los heredocs largos por Bash se rompen** con comillas o acentos: escribe archivos con las herramientas de escritura.

## Lo que el hito 4 y el 3 dejaron dicho y sigue vigente

- La aceptación del flujo D adelanta el reloj por HTTP y necesita `APP_RELOJ_AJUSTABLE=true` (está en el `.env` local y en
  `docker-compose.ci.yml`); si tus pruebas mueven el reloj, devuélvelo a 0 al terminar.
- Una excepción que cruza un repositorio `@Transactional` deja la transacción marcada para deshacer: valida antes en el
  servicio.
- El modelo por defecto es `qwen3:4b-instruct-2507-q4_K_M` en CPU; RNF-02 no se cumple en la máquina de referencia (primer
  token). No cambies de modelo sin repetir `EvaluacionModeloIT` y `EvaluacionConsejeroIT` con los mismos bancos.
- T22 · Triangulación guarda sus fuentes en el JSONB de la entrada "hasta el hito 6": pásalas a la tabla `fuente` con su
  `grupo_origen` y migra las ejecuciones viejas al leerlas (`Ejecutor.migrar`).
- La evaluación incluye 10 documentos adversarios (`banco-adversarios.json`); la biblioteca agrega el riesgo de un PDF con
  instrucciones embebidas: el pasaje va siempre como material citado.
- RF-08 sigue abierto hasta que el dueño haga las sesiones moderadas de primer uso.

## Antes del código: los ejemplos y los bancos

Escribe `docs/ejemplos/T19.md`, `T20.md`, `T21.md` y `T23.md`, y un `docs/verificacion.md` con un ejemplo de punta a punta
de la ficha (tipificar con T17, preguntas críticas, buscar en la biblioteca, registrar fuentes con SIFT y CRAAP, etiquetar
pasajes con T22, R01 a R04 recalculadas y el veredicto bajo un estándar), y haz commit antes de escribir código. Cada
técnica lleva tres ejemplos, uno por ámbito, con configuración, datos ficticios y resultado escrito a mano en modo
plantillas; los cálculos de R01, R02 y R03 se escriben a mano con los números del ejemplo.

Escribe también, **antes de medir**, los bancos y umbrales nuevos (por ejemplo, consultas a la biblioteca con los pasajes
que deberían aparecer, para medir la búsqueda con bge-m3 contra la de texto completo) en `src/test/resources/` y en
`docs/evaluacion-modelo.md`.

## Entregables del hito 6 (fila del hito 6 en la sección 9)

1. **Ejecutores** en `tecnicas.f4` para T19, T20, T21 y T23, activos en el catálogo con sus esquemas en el lenguaje de
   campos y sus patrones de la sección 7.
2. **Biblioteca** (P13): importar PDF, Markdown, texto y CSV detectados por contenido (RNF-08, 50 MB, sin OCR), con
   `pdftotext` como proceso hijo con tiempo máximo y límite de páginas; troceado, embeddings con bge-m3 en la tabla
   `trabajo` con reencolado al arrancar; pgvector con índice HNSW; documentos privados por defecto y compartir auditado.
3. **Ficha de fuente** (P12): SIFT y CRAAP, independencia por `grupo_origen`, acceso al original.
4. **Ficha de verificación** (P10 y P11): desde una premisa o un pendiente de verificación; tipificar (T17), preguntas
   críticas, buscar en la biblioteca, evidencias con su fuerza (R01), fuerza neta (R02), estado (R03) y aceptabilidad bajo
   estándar (R04) recalculadas; el veredicto nunca dice "verdadero".
5. **Exportar e importar** con las tablas nuevas; RF-03 extendido (otra persona recibe 404 y no ve documentos privados).
6. **Evaluación**: búsqueda semántica contra texto completo con su banco y umbrales; adversarios en documentos.
7. **k6** con la importación de un documento como trabajo largo sin romper el p95 de las pantallas sin IA.
8. **Accesibilidad y navegador** (RNF-06, RNF-09): la subida con progreso, la ficha a 360 px, en Chromium.
9. **Aceptación por HTTP**: importar un PDF y encontrarlo desde una premisa dentro del compose; un veredicto recalcula R04.

## Definición de hecho

- Los ejemplos de las cuatro técnicas pasan como oráculo en modo plantillas, también leídos desde la tabla `ejemplo`.
- Importar un PDF de prueba y encontrar su pasaje desde una premisa pasa por HTTP y en Chromium.
- Un veredicto en la ficha recalcula R01 a R04 y cierra el pendiente de verificación de donde vino.
- Los contratos nuevos pasan contra el Fake en cada PR y contra PostgreSQL, Ollama y `pdftotext` reales en el nocturno.
- `docker compose --profile test run --rm tests` (también con `CONTRACT_REAL=true`) y los gates están en verde; ArchUnit, el
  sensor de Fakes sin contrato y el sensor de voseo pasan; k6 en verde.
- El README dice qué se puede hacer en el hito 6, con lo verificado y una tabla "Decisiones tomadas en el hito 6".

## Cómo trabajar

1. Lee el HTML, este archivo, el README, `docs/evaluacion-modelo.md`, `docs/consejero.md` y los ejemplos de T22, T28, T11 y
   T38.
2. Revisa la última corrida del nocturno y el estado de RF-08.
3. Escribe primero los ejemplos, `docs/verificacion.md` y los bancos con sus umbrales, y haz commit.
4. Haz un plan corto por entregable y ejecútalo en orden: puertos y contratos de fuentes, evidencias y documentos; R01 a
   R04 sobre evidencias reales; técnicas de F4; biblioteca e importador; ficha de fuente; ficha de verificación; respaldo y
   RF-03; evaluación; k6; accesibilidad y Playwright; aceptación.
5. Después de cada entregable corre el perfil test dentro del compose y no sigas hasta que esté en verde.
6. Si una decisión no está en el documento ni en el README, elige la opción más simple que respete las reglas y agrégala a
   una tabla nueva "Decisiones tomadas en el hito 6" del README, con fecha.
7. Cuando termines, entrega un resumen con lo verificado, lo que quedó fuera y lo que recomiendas para el hito 7
   (metacognición y práctica: Dojo con repetición espaciada y niveles de Bloom, T45 a T49, el registro global de cambios de
   opinión y el manual por familia).
