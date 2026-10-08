# Guion de las sesiones moderadas de primer uso (RF-08)

Criterio de aceptación del hito 1 (sección 9 del documento): seis a ocho personas de los tres ámbitos obtienen
un resultado útil en menos de cinco minutos sin conocer el nombre de la técnica. Este guion está listo para que
el dueño del producto realice las sesiones. **Las sesiones no se han hecho todavía**: el hito 1 no se da por
cerrado en RF-08 hasta tener las mediciones de la tabla final.

## Quiénes participan

Entre 6 y 8 personas adultas, sin vocabulario de lógica ni de estadística (la persona primaria de la versión 1),
repartidas en los tres ámbitos de los escenarios del producto:

| Ámbito | Perfil sugerido | Cuántas |
|---|---|---|
| Personal | alguien que toma decisiones de estudio, salud o dinero en su familia | 2 o 3 |
| Trabajo | dueña o empleado de un negocio pequeño | 2 o 3 |
| Comunidad | integrante o vecino de una junta de vecinos u organización de barrio | 2 o 3 |

Criterios de exclusión: haber visto la aplicación antes, trabajar en el proyecto o conocer el término "ACH" o
"análisis de hipótesis en competencia". Se pregunta al reclutar, sin explicar qué es.

## Preparación (una vez, antes de la primera sesión)

1. Levantar la aplicación desde cero en la máquina de las sesiones: `docker compose up --build`. Ollama puede
   estar apagado: T28 no lo usa.
2. Como `administrador`, crear una cuenta por participante en **Usuarios** con un nombre neutro
   ("participante 1", "participante 2"…) y un PIN de cuatro dígitos. Ninguna cuenta debe tener datos: el
   participante tiene que ver el Inicio vacío.
3. Abrir el navegador en la pantalla "¿Quién eres?" y dejarlo en pantalla completa. Tener un segundo dispositivo
   (celular) por si el participante prefiere usarlo; anotar cuál usó.
4. Preparar un cronómetro, la hoja de registro (al final) y una grabación de pantalla solo si el participante
   firma el consentimiento. Sin grabación de cámara ni de voz si no lo autoriza.

## Consentimiento (lectura en voz alta, 1 minuto)

> Gracias por venir. Vamos a probar una aplicación, no a ti: si algo no se entiende, es un problema de la
> aplicación. Te voy a pedir que pienses en voz alta mientras la usas. No te voy a ayudar durante las tareas,
> pero puedes rendirte cuando quieras: eso también nos sirve. No guardamos tu nombre; los datos que escribas son
> inventados y se borran al terminar. ¿Te parece bien que grabemos la pantalla?

## Desarrollo (30 minutos por persona)

| Minuto | Qué pasa | Qué hace quien modera |
|---|---|---|
| 0 a 2 | Consentimiento y preguntas de contexto (¿cómo decides cuando algo salió mal y hay varias explicaciones?) | Anotar ámbito y dispositivo |
| 2 a 3 | El participante entra con su nombre y PIN | Entregar nombre y PIN escritos; no explicar nada más |
| 3 a 10 | **Tarea 1 (la que mide RF-08)** | Cronometrar; no ayudar |
| 10 a 18 | **Tarea 2**: un caso propio | Cronometrar; no ayudar |
| 18 a 24 | **Tarea 3**: explicar la tarjeta de resultado con sus palabras | Escuchar y anotar textual |
| 24 a 30 | Preguntas finales | Anotar textual |

### Tarea 1 · primer resultado (mide el criterio de RF-08)

Se lee tal cual, sin nombrar la técnica ni la pantalla:

> Imagina que tienes una panadería y este mes las ventas de los sábados bajaron bastante. Tienes algunas ideas
> de por qué pasó. Usa la aplicación para ver cuál de esas ideas se sostiene mejor con lo que sabes.

- **Inicio del cronómetro**: cuando el participante ve el Inicio después de entrar.
- **Fin**: cuando en pantalla aparece la matriz con la tarjeta "Menos refutada" (o "Empate").
- **Éxito**: llega a la matriz en menos de 5 minutos, por cualquier camino (intención, "Empieza con un ejemplo",
  catálogo o búsqueda).
- **Abandono**: dice que se rinde, o pasan 7 minutos sin llegar. Se anota en qué pantalla estaba.
- Anotar el camino exacto (por ejemplo: Inicio → "Empieza con un ejemplo" → Evaluar) y cada duda en voz alta.

### Tarea 2 · un caso propio

> Ahora piensa en algo de tu vida, tu trabajo o tu barrio que salió distinto de lo esperado y tenga al menos dos
> explicaciones posibles. Escríbelo en la aplicación y guárdalo para revisarlo después.

- Éxito: guarda una ejecución propia en el historial (aparece "guardado").
- Anotar: si cambió la configuración, si añadió o quitó filas, si apareció un error y si lo entendió con el
  mensaje junto al campo.
- Opcional, si sobra tiempo: pedir que lo asocie a un expediente nuevo.

### Tarea 3 · explicar la tarjeta

> Mirando el resultado de tu caso, ¿qué te dice la aplicación? ¿Qué harías ahora?

- Éxito: explica que la hipótesis "menos refutada" no queda probada y nombra el siguiente paso (verificar la
  evidencia que propone la tarjeta).
- Señal de alarma, anotarla textual: si dice que la aplicación "le confirmó" o "le dio la respuesta correcta".

### Preguntas finales

1. ¿Qué fue lo más confuso?
2. Si un amigo te pregunta qué hace esta aplicación, ¿qué le dirías?
3. ¿La usarías otra vez para algo real? ¿Para qué?
4. Del 1 al 5, ¿qué tan fácil fue llegar al primer resultado?

## Al terminar cada sesión

1. Como `administrador`, desactivar la cuenta del participante en **Usuarios**.
2. Guardar la hoja de registro con el número de participante, nunca con su nombre.
3. Si hubo grabación, guardarla fuera del repositorio y borrarla cuando se termine el análisis.

## Hoja de registro (una fila por participante)

| # | Ámbito | Dispositivo | Camino de la tarea 1 | Tiempo al primer resultado | Éxito T1 (< 5 min) | Abandono (pantalla) | Éxito T2 | Éxito T3 | Fácil (1 a 5) | Cita textual relevante |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | | | | | | | | | | |
| 2 | | | | | | | | | | |
| 3 | | | | | | | | | | |
| 4 | | | | | | | | | | |
| 5 | | | | | | | | | | |
| 6 | | | | | | | | | | |
| 7 | | | | | | | | | | |
| 8 | | | | | | | | | | |

## Cómo se decide

- **RF-08 se cumple** si al menos 5 de 6 participantes (o 6 de 8) llegan a la matriz en menos de 5 minutos y
  ninguno de los tres ámbitos queda con todos sus participantes por encima de ese tiempo.
- Cada abandono y cada duda repetida por dos o más personas se registra como hallazgo con la pantalla (P02 a
  P09) y una propuesta de cambio, antes de empezar el hito 2.
- Si alguien interpreta "menos refutada" como "confirmada", es un hallazgo de prioridad alta sobre la tarjeta
  de resultado (corrección 13 de la mesa).
- Los resultados (tabla y hallazgos, sin nombres) se agregan al README en "Mediciones del hito 1".
