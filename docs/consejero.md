# Flujo C · Consejero socrático (P15 y P16)

Un asistente que nunca da la respuesta: solo pregunta, y va llenando a la vista los elementos de tu razonamiento. Esta
página fija lo que el documento (secciones 4, 6 y 7) deja abierto y es la referencia de las pruebas del flujo. Las
reglas de cada técnica están en su archivo de [`docs/ejemplos/`](ejemplos/).

## Qué decide el código y qué hace el modelo

**El código lleva la estrategia; el modelo solo redacta.** En cada turno el motor híbrido (`flujos.Consejero`, dominio
puro) elige qué toca preguntar con la estrategia de la técnica del modo y saca la pregunta del banco
(`catalogo/preguntas-socraticas.json` y `catalogo/ataques.json`). Si la sesión usa el modelo y está disponible, el
modelo redacta esa misma pregunta con las palabras de la persona; el validador del turno la rechaza y reintenta hasta
dos veces si no termina en "?", si pasa del largo, si trae una frase de veredicto, voseo o "usted". Si sigue fallando, o
el modelo no está, el turno sale del banco y la burbuja lo dice ("del banco"). Ningún puntaje, ningún estado y ninguna
etiqueta salen del modelo.

| Modo | Estrategia (técnica) | Un turno es |
|---|---|---|
| ensayo | T08 · Preguntas socráticas, orden del modo ensayo | una pregunta de un tipo socrático sobre un elemento de Paul-Elder |
| decisión | T08 · Preguntas socráticas, orden del modo decisión | ídem |
| escalera | T10 · Escalera de inferencia | un peldaño, en el sentido de la configuración, sin volver atrás |
| sombreros | T35 · Seis Sombreros | una ronda por sombrero activo y después la síntesis |
| debate | T36 · Equipo rojo / abogado del diablo | un ataque sobre una debilidad que el código identificó |

Cada modo usa la configuración que la persona tiene guardada en esas técnicas (tipos activos, orden, turnos máximos,
peldaños, sombreros, intensidad, número de ataques). Toda sesión termina con la **pregunta de cierre** de T11 ·
Falsación y "qué tendría que ser cierto": "¿Qué te haría cambiar de opinión sobre «{postura}»?". La persona puede pedir
el cierre en cualquier momento ("Ir al cierre").

## El turno, por SSE (sección 4, "Streaming")

1. La persona envía su respuesta: se guarda su turno y el motor elige el siguiente. El turno del Consejero se guarda
   enseguida con la pregunta del banco y el estado "redactando" si va a intervenir el modelo.
2. Con el modelo, la respuesta trae la burbuja vacía con `sse-connect`: una conexión por turno, eventos `tiempo` (cada
   segundo), `token` (texto provisional, escapado) y un solo `fin`, que reemplaza la burbuja con la versión validada y
   actualiza el panel fuera de banda. Cancelar corta la espera y deja la pregunta del banco.
3. **Cola visible** (decisión): Ollama atiende una petición a la vez. Si al pedir hay turnos de otras personas en curso,
   la burbuja de espera dice "En cola: N pedido(s) antes que el tuyo." mientras dura la espera.
4. **Ollama que vuelve** (decisión): un pedido que falla por "no disponible" deja el turno en el banco y no fuerza una
   revisión del monitor; el monitor sigue revisando cada 30 s. Así un Ollama caído no hace esperar cada turno.
5. Sin el modelo, no hay SSE: la pregunta del banco llega en la misma respuesta.

## Panel de Paul-Elder

- **Modo plantillas**: la respuesta a una pregunta llena el elemento que el motor eligió para ese turno (origen
  `usuario`).
- **Con el modelo**: un segundo paso clasifica la respuesta contra los ocho elementos más "ninguno", con salida
  estructurada (prompt `t04-elemento`, el de T04 · Elementos y estándares de Paul-Elder). Si propone un elemento
  distinto del que el motor eligió y ese elemento sigue vacío, el panel lo muestra como "propuesta del modelo · sin
  adoptar · no cuenta" con su porqué; **Adoptar** lo llena con origen `modelo`.
- **Estándares**: claridad, exactitud, profundidad, amplitud y lógica, puntuados al cierre **por reglas** (las de
  T08); los demás se puntúan a mano en T04. Solo en los modos ensayo y decisión.

En los otros modos el panel cambia: la escalera con sus peldaños y el débil marcado (T10), la rejilla de sombreros
(T35) o el panel de debilidades del equipo rojo (T36).

## Debate (P16)

1. Al empezar, la persona escribe su postura y de 1 a 3 razones con "en qué se apoya".
2. Equipo rojo en el diálogo: ataque y respuesta alternados, con las reglas de T36.
3. Terminados los ataques, el panel abre en orden tres formularios de técnica ya llenos con lo de la sesión, que se
   guardan en el expediente de la sesión como cualquier ficha:
   - T34 · Steelmanning: la postura contraria (la persona la escribe; con el modelo puede pedir la propuesta de
     steelman, que no cuenta hasta adoptarla);
   - T37 · Test de Turing ideológico: la persona escribe la postura contraria como la defendería quien la cree, con el
     steelman como referencia; el puntaje sale de la rúbrica;
   - T38 · Double crux: la postura de la persona de un lado y la contraria del otro; el crux común queda como pendiente
     de verificación.

## Cierre de sesión

- La pregunta de falsación es **obligatoria**: sin respuesta no se puede cerrar.
- Después, la **reflexión** con las preguntas de T47 · Reflexión estructurada, que es del hito 7 (decisión: solo sus
  preguntas, sin la técnica): "¿Qué cambió en lo que piensas?" (opcional) y la confianza al terminar (opcional), con la
  causa del cambio (evidencia, steelman o manual).
- En la escalera, la persona marca qué peldaños comprobó con alguien o con un dato (T10 los necesita para marcar el
  débil).
- Cerrar guarda, en una transacción, la ejecución de la técnica del modo (T08, T10, T35 o T36) con lo que pasó en la
  sesión, y la deja en el expediente de la sesión si tiene uno. Las preguntas que redactó el modelo quedan en esa
  ejecución con origen `modelo`.
- **Cambio de opinión** (decisión: se escribe ya, porque R05 lo pide y la tabla `cambio_opinion` existe desde V1): si la
  confianza al empezar y al terminar son distintas, se inserta un cambio sobre la afirmación de la postura de esa
  ejecución (o la de su conclusión, si la técnica no tiene postura), con la causa elegida. El registro global de cambios
  (T46, P20) es del hito 7.
- Una sesión cerrada queda en solo lectura y aparece en el historial del Consejero; la ejecución, en el historial de su
  técnica y en el expediente.

## Ejemplo de punta a punta en modo plantillas

Sin Ollama. La configuración de T08 es la de fábrica: los seis tipos, orden adaptativo, 8 turnos.

1. **Nueva sesión**: modo decisión, postura "Conviene abrir la segunda sucursal en el centro este año.", confianza al
   empezar 80, sin modelo, asociada al expediente "La segunda sucursal".
2. **Turno 1** (banco): "¿Qué quieres lograr con esta decisión? ¿Cómo sabrías que lo lograste?" (propósito).
   Respuesta: "Quiero vender más, unos 200 panes más por día, sin descuidar el local que ya tenemos."
3. **Turno 2** (banco): "¿Qué estás dando por sentado para que eso sea cierto?" (supuestos). Respuesta: "Que en el centro
   pasa mucha gente y que la gente que pasa compra pan."
4. **Turno 3** (banco, salto adaptativo por «mucha»): "¿Qué quieres decir exactamente con «mucha»? Dame un ejemplo
   concreto y uno que no cuente." La persona pide **Ir al cierre**.
5. **Cierre**: "¿Qué te haría cambiar de opinión sobre «conviene abrir la segunda sucursal en el centro este año»?"
   Respuesta: "Que el conteo de una semana completa dé menos de 600 personas por mañana."
6. **Cerrar**: reflexión "Necesito contar una semana entera antes de firmar.", confianza al terminar 60, causa evidencia.
7. Queda una ejecución de T08 en el expediente con 2 turnos, 2 de 8 elementos (propósito y supuestos), el resumen "2
   turnos · 2 de 8 elementos · cierre respondido." y un cambio de opinión de 80 a 60 por evidencia.
