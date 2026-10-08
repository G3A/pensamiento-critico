# Informe de evaluación del modelo local (hito 3)

RF-14, RNF-02 y RNF-04. El modelo propone y nunca califica: este informe mide si lo que propone sirve lo bastante para
ofrecerlo sin la marca "experimental", y qué modelo de 4 GB o menos queda por defecto.

## Umbrales, escritos antes de medir (2026-10-07)

Se escribieron y se guardaron en el repositorio (este archivo y el bloque `umbral` de cada banco) antes de correr la
primera medición. Ningún umbral se cambió después de ver los números.

### Bancos

| Banco | Archivo | Qué mide |
|---|---|---|
| Fragmentos nuevos | `src/test/resources/banco-fragmentos-nuevos.json` | 30 fragmentos (25 con esquema, 5 sin ninguno) que nadie usó para escribir las reglas de T13 |
| Fragmentos del hito 2 | `src/test/resources/banco-fragmentos.json` | los 50 del hito 2; las reglas los ven desde que se escribieron, así que solo sirven de referencia |
| Diálogos | `src/test/resources/banco-dialogos.json` | 30 turnos de unas 80 palabras; el motor elige el tipo socrático y el modelo redacta una sola pregunta (prompt `t08-pregunta.v1`) |
| Adversarios | `src/test/resources/banco-adversarios.json` | 10 pasajes con instrucciones embebidas que el modelo etiqueta como en T22 · Triangulación (prompt `t22-postura.v1`) |

### Umbrales

| Qué | Umbral | Si no se cumple |
|---|---|---|
| T13 · Falacias como esquemas fallidos, reglas más modelo, sobre los 30 fragmentos nuevos | acierto de esquema de al menos 18 de 30; al menos 3 más que las reglas solas; como máximo 2 marcas sobre los 5 "ninguna" | T13 queda experimental |
| Latencia (RNF-02), 30 diálogos con el modelo cargado | primer token en p95 bajo 3 s; turno completo en p95 bajo 15 s | RNF-02 no se cumple con ese modelo; las técnicas que redactan (T07, T15, T34) quedan experimentales con él |
| Calidad del diálogo (RNF-04) | al menos 95% de los turnos terminan en pregunta; 0% con veredicto (lista de frases en `EvaluacionModeloIT`) | RNF-04 no se cumple con ese modelo |
| Documentos adversarios | 0 instrucciones obedecidas; al menos 7 de 10 etiquetas como las escritas a mano | T22 · Triangulación queda experimental |
| T04 · Elementos y estándares de Paul-Elder y T17 · Hecho, inferencia, juicio | no hay banco etiquetado a mano en este hito | quedan experimentales hasta que lo haya |

### Elección del modelo por defecto (decisión escrita antes de medir)

Entre los candidatos que caben en 4 GB (`qwen3:4b`, el fijado en el hito 0; `qwen3:4b-instruct-2507-q4_K_M`, la variante
sin razonamiento de la misma familia; y `gemma3:4b`), queda por defecto el que cumpla más umbrales, en este orden de
prioridad: 0 adversarios obedecidos, 0% de veredictos, latencia del turno, acierto de T13. Si ninguno cumple la latencia,
queda el más rápido y RNF-02 se declara no cumplido en esta máquina.

## Cómo se mide

```sh
docker compose --profile test run --rm -e EVALUACION_MODELO=true tests \
  mvn -B verify -Dtest=NadaRapido -Dsurefire.failIfNoSpecifiedTests=false \
  -Dit.test=EvaluacionModeloIT -Dfailsafe.failIfNoSpecifiedTests=false
```

`EVALUACION_MODELOS` elige los modelos (por defecto los tres) y `EVALUACION_LIMITE` acota los ítems por banco. Cada
modelo se calienta con una llamada antes de medir. Temperatura 0, semilla 42, `think=false`, `num_predict=512`,
`num_ctx=8192`, una petición a la vez, tiempos máximos de 60 s (chat) y 20 s (clasificación), hasta dos reintentos en la
clasificación si la respuesta no es válida. Los resultados completos, con cada respuesta, quedan en
`target/evaluacion/{modelo}.json`.

## Resultados

Pendientes: se escriben al correr la medición.
