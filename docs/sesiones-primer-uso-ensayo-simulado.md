# Ensayo simulado de las sesiones de primer uso (RF-08)

**Esto no es la medición de RF-08.** Es un ensayo del guion de `docs/sesiones-primer-uso.md` hecho el 13 de
octubre de 2026 con seis agentes que interpretaron a participantes sin vocabulario técnico. Cada agente manejó el
navegador sin leer el código ni la documentación. Los tiempos y la comprensión de un agente no equivalen a los de
una persona, así que nada de esto va a "Mediciones del hito 1" del README. Sirve para corregir los obstáculos
evidentes antes de las sesiones reales, que siguen pendientes.

## Preparación

- La instancia de desarrollo (`:8080`) tiene 922 cuentas que dejaron las pruebas automáticas y todas aparecen en
  "¿Quién eres?". **No sirve para las sesiones reales.** Para el ensayo se levantó una instancia limpia y aparte
  (proyecto `pensamiento-sesiones`, puerto 8081, sin Ollama), sin tocar el volumen de desarrollo.
- Se crearon seis cuentas, de "participante 1" a "participante 6", con PIN de cuatro dígitos. Al terminar quedaron
  desactivadas.

## Hoja de registro (simulada)

| # | Ámbito | Dispositivo | Camino de la tarea 1 | Tiempo* | Éxito T1 | Abandono | Éxito T2 | Éxito T3 | Fácil (1 a 5) | Cita textual relevante |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | Personal | Celular | Inicio → "Entender por qué pasó algo" → T28 (Qué es) → Usar → ejemplo 1 → Evaluar | 66 s, 5 acciones | sí | — | sí | no | 4 | "Menos refutada… no sé qué es, pero dice 'Ninguna evidencia la contradice'. O sea, fue la feria." |
| 2 | Personal | Computador | Igual que el 1 | 60 s, 5 acciones | sí | — | sí (con errores entendidos) | parcial | 4 | "Ganó la feria, tiene la etiqueta verde. Aunque abajo dice 'eso no la confirma'… entonces ¿sí o no?" |
| 3 | Trabajo | Celular | Igual que el 1 | 67 s, 5 acciones | sí | — | sí | no | 4 | "Ganó la feria. Ah, pero dice 'eso no la confirma'… bueno, pero ganó." |
| 4 | Trabajo | Computador | Catálogo → buscar "ventas" (0) → "causa" → T09 · 5 porqués (desvío) → filtro por intención → T28 → Usar → ejemplo 1 → Evaluar | 173 s, 13 acciones | sí | — | sí (con errores entendidos) | sí | 3 | "Cero resultados con 'ventas'." |
| 5 | Comunidad | Computador | Igual que el 1 | 70 s, 5 acciones | sí | — | sí | no | 4 | "Dice 'eso no la confirma'… pero igual quedó marcada como la ganadora." |
| 6 | Comunidad | Celular | Intención → T09 · 5 porqués (desvío) → Inicio → "Empieza con un ejemplo" → Evaluar | 87 s, 6 acciones | sí | — | sí (al tercer intento) | no | 3 | "Cuando apreté guardar y no pasaba nada." |

\* Tiempo de un agente, no comparable con el de una persona.

Aplicando la regla de decisión solo como ensayo: 6 de 6 llegan a la matriz y ningún ámbito queda por encima de los
cinco minutos. La tarea 3, en cambio, **falla en 4 de 6**: leyeron "menos refutada" como "confirmada" o "la
ganadora". Según el guion, es un hallazgo de prioridad alta (corrección 13 de la mesa).

## Hallazgos que se repiten en dos o más participantes

| Prioridad | Pantalla | Hallazgo | Quiénes | Propuesta |
|---|---|---|---|---|
| Alta | Resultado de T28 · Análisis de hipótesis en competencia (ACH) | La etiqueta verde "menos refutada" se lee como "ganó". El aviso "eso no la confirma" y "ACH elimina, no confirma" no alcanzan: nadie sabe qué es ACH. "Más refutada" se lee como "descartada". | 1, 2, 3, 4, 5, 6 | Un titular llano: "Ninguna de tus pistas va en contra de X; todavía no está probada", con color neutro (no verde). Quitar la sigla ACH del mensaje y decir cuántas pistas van en contra de cada una. |
| Alta | Formulario de T28 con un ejemplo cargado | No hay forma de empezar en blanco. Todos escribieron su caso encima de la panadería y quedaron marcas y pesos del ejemplo que no eligieron. | 1, 2, 3, 4, 5, 6 (arrastre de marcas visto en 3 y 6) | Botón "Empezar con mi propio caso" que vacíe el formulario. Al cambiar el texto de una hipótesis o una evidencia, volver sus celdas a sin marcar o resaltarlas para revisar. |
| Alta | Formulario de T28 después de editar o de un error | La matriz de la panadería sigue a la vista mientras se edita otro caso, también después de un Guardar fallido. | 2, 4, 6 | Atenuarla y rotularla "resultado anterior" en cuanto cambia un dato. |
| Media | Ficha de ejemplos de T28 | Se ve una nota interna: "Es el ejemplo del mockup de la pestaña Usar" (en `src/main/resources/catalogo/ejemplos/T28.json`). En T09 se ven además "docs/ejemplos/T09.md" y "DOT esperado". | 1, 2, 3, 4, 5, 6 | Quitar la frase de las notas de los ejemplos (también aparece en T08, T40 y T43) y ocultar la ruta y el DOT en la vista de usuario. |
| Media | Pestaña "Qué es" de T28 | Es lo primero que se ve y está en jerga: "pondera las inconsistencias", "Escala de cada celda: C, I, N", "Sobre qué trabaja: afirmacion" (sin tilde), "Heuer 1999". Todos se la saltaron. | 1, 2, 3, 4, 5 | Si se llega desde una intención, abrir en "Usar". Empezar con una frase llana y dejar la ficha técnica plegada. |
| Media | Celdas C/I/N | "Consistente / inconsistente / neutral" no es vocabulario de la persona primaria. | 1, 2, 3, 5, 6 | "Encaja / va en contra / no dice nada". |
| Media | Catálogo con la intención "Entender por qué pasó algo" | Son 8 técnicas y la primera es T09 · 5 porqués (dos participantes se desviaron por ahí). T28 · Análisis de hipótesis en competencia (ACH) y T33 · Inferencia a la mejor explicación suenan igual. | 1, 2, 3, 4, 5, 6 (desvío en 4 y 6) | Destacar arriba "¿Tienes varias explicaciones? Empieza con un ejemplo" o poner T28 primero. Agregar una línea que distinga T28 de T33. |
| Media | Inicio | Nadie usó "Empieza con un ejemplo" de entrada: el botón de intención atrae más y el atajo queda debajo de las seis opciones. | 1, 2, 3, 5 | Llevar el atajo al camino de la intención (ver la fila anterior) o subirlo con un rótulo orientado a la tarea. |
| Media | "guardado" | La confirmación queda debajo de la matriz y no se ve sin desplazarse, en celular ni a 1366×768. | 2, 6 | Mostrarla junto al botón "Guardar en historial" o desplazar la vista hasta ella. |
| Media | "Asociar a un expediente" | No se entiende el término; dos lo ignoraron. "Sin expediente." no dice qué hacer. | 1, 3, 4 | "Guardar en una carpeta (opcional)" y "Elige una carpeta o escribe un nombre para crear una". |
| Baja | Aviso "sin IA local / Ollama no está disponible" | Alarma ("¿se dañó algo?") y ocupa espacio en el celular. | 2, 3 | Un chip discreto, una sola vez y sin el nombre técnico. |
| Baja | Celular, 360 px | El formulario es muy largo (9 grupos de tres opciones más los pesos) y la tabla corta la columna H3. | 3, 6 | Una tarjeta por evidencia en pantallas angostas. |

## Hallazgos de un solo participante (verificar en las sesiones reales)

- **Guardar con error en celular (6):** el mensaje junto al campo es claro, pero queda fuera de la vista y el foco
  no salta a él. Pareció que "no pasaba nada" y el participante apretó Guardar tres veces. Propuesta: mover foco y
  scroll al primer error y poner un aviso junto al botón.
- **Fila vacía (6):** borrar el texto de una evidencia no la quita, la vuelve un error. Propuesta: ignorar las filas
  vacías o preguntar si se quitan.
- **Errores que siguen tras guardar (2):** los recuadros rojos "Marca esta celda" siguieron después de "guardado". No
  se verificó por separado.
- **Buscador del catálogo (4):** "ventas" da 0 resultados porque solo busca en el nombre de la técnica. Propuesta:
  buscar también en "Úsala cuando" y en los títulos de los ejemplos.
- **"Siguiente paso: Verificar E…" (2, 5):** propone verificar una evidencia que no separa las hipótesis, o que la
  persona ya da por segura. Propuesta: decir por qué conviene verificarla.
- **No reproducido:** el participante 2 informó el texto `Usa "" para conservarlo`. La plantilla
  (`src/main/jte/fragmentos/ficha/resultado.jte:19`) dice "Guardar en historial", así que parece un error de lectura
  del agente.

## Correcciones aplicadas después del ensayo

- **Resultado de T28 · Análisis de hipótesis en competencia (ACH):**
  - El titular dice qué no la contradice y que "todavía no está probada".
  - Se cuentan las pistas en contra de cada idea, en color neutro y con "sigue en pie".
  - "Qué hacer ahora" explica por qué conviene comprobar esa pista.
  - La tabla queda plegada y la sigla ya no aparece.
- **Formulario de T28:**
  - Las etiquetas son "Tus ideas", "Tus pistas" y "Encaja / Va en contra / No dice nada", y cada celda nombra su idea.
  - En celular las opciones son botones grandes.
- **Usar con un ejemplo cargado:**
  - Aparece el aviso "Estás viendo un ejemplo" con "Empezar con mi propio caso".
  - Cambiar el texto de una fila borra las marcas del ejemplo para esa fila.
  - El resultado anterior se atenúa al cambiar los datos.
- **Errores y guardado:**
  - Junto al botón aparecen el resumen de errores con "Ir al primero" y el aviso "guardado".
  - Una fila vacía dice cómo quitarla.
- **Catálogo:**
  - En "Entender por qué pasó algo", arriba va "Empieza aquí", con la guía para elegir entre T28, T33 · Inferencia a la mejor explicación y T09 · 5 porqués.
  - La búsqueda también mira "Úsala cuando" y los títulos de los ejemplos.
- **Pestaña "Qué es" de las 49 técnicas:** primero "Úsala cuando" y los ejemplos; la ficha técnica va plegada.
- **Notas de los ejemplos:** 53 notas sin referencias internas.
- **Otros textos:** "Guardar también en un expediente (opcional)" y un aviso discreto cuando no hay asistente de IA.

Quedan pendientes:

- El resumen de una línea de T28, que todavía dice "Menos refutada", en el historial, el expediente y el Inicio.
- La vista de una pista a la vez en celular.
- El salto automático al primer error, porque `app.js` tiene que quedar por debajo de 100 líneas.

## Qué hace falta para cerrar RF-08

1. ~~Corregir antes, como mínimo, los tres hallazgos de prioridad alta y la nota "mockup".~~ Hecho (ver la sección anterior).
2. Hacer las sesiones con 6 a 8 personas reales en una instalación limpia, no en la de desarrollo.
3. Llenar la hoja de `docs/sesiones-primer-uso.md` y pasar al README solo esos resultados.
