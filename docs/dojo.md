# Flujo B · Dojo de razonamiento (P19)

Un entrenamiento corto y diario: cada reto muestra un texto de uno de los tres escenarios (la familia, la panadería, el
barrio) y pide reconocer una falacia, un sesgo, un error con datos o el movimiento de SIFT que toca. Este documento se
escribió antes del código, como los ejemplos de cada técnica, y fija lo que el documento (secciones 5, 6 y 7) deja
abierto. Las reglas de cálculo de T48 · Taxonomía de Bloom y T49 · Repetición espaciada están en
[`docs/ejemplos/T48.md`](ejemplos/T48.md) y [`docs/ejemplos/T49.md`](ejemplos/T49.md); el Dojo las aplica a los intentos
guardados de cada persona.

## Qué decide el código y qué no hace el modelo

**Nada del Dojo pasa por el modelo.** Los retos salen de un banco versionado (`catalogo/dojo.json`), escritos a mano con su
respuesta correcta; el puntaje de cada reto, el nivel de Bloom y el próximo repaso salen de reglas en código. Con Ollama
apagado el Dojo funciona igual. Decisión: el nivel "crear" se califica con una rúbrica por reglas (sección 7 dice "con
Ollama, se compara contra una rúbrica"; aquí la rúbrica la revisa el código, sin el modelo), así que T48 queda "sin IA".

## El banco

- **Temas**: falacias (T13 · Falacias como esquemas fallidos), sesgos (T14 · Sesgos cognitivos), datos (T18 ·
  Correlación, causalidad y tasas base) y fuentes (T19 · SIFT).
- **Conceptos** (26): las once falacias del catálogo de esquemas de Walton (una por esquema, con el nombre de su falacia
  más común), los ocho sesgos de `catalogo/sesgos.json`, tres errores con datos (ignorar la tasa base, confundir
  correlación con causa y el riesgo relativo sin la cifra absoluta) y los cuatro movimientos de SIFT. El concepto es la
  unidad de la repetición espaciada. El banco los ordena intercalando temas, y ese orden decide qué concepto nuevo sale
  primero.
- **Retos** (152): por cada falacia, sesgo y error con datos, dos de identificar, dos de analizar, uno de evaluar y uno de
  crear; por cada movimiento de SIFT, dos de identificar y uno de cada otro nivel. Cada reto tiene su texto, su pregunta y
  su explicación.
  - **identificar**: cuatro nombres de conceptos del mismo tema; uno es el correcto;
  - **analizar**: cuatro fragmentos copiados del texto; uno es donde está el error;
  - **evaluar**: tres o cuatro respuestas razonadas; una es la correcta;
  - **crear**: la persona escribe su versión y una **rúbrica** de 2 a 4 chequeos la revisa: `sinMarcas` (no aparece
    ninguna de las expresiones que delatan el error), `conAlguna` (aparece al menos una de las expresiones esperadas) y
    `largo` (al menos N palabras). Se compara en minúsculas, sin tildes y por palabra o frase completa. El reto trae una
    **respuesta modelo**, que se muestra después de responder. La rúbrica mira palabras, no el sentido: la pantalla lo
    dice.
- Una prueba revisa el banco entero: identificadores únicos, una sola opción correcta, fragmentos de analizar que son
  literales del texto, la respuesta modelo de cada reto de crear que pasa su rúbrica y el texto original que no la pasa,
  y que ningún texto tenga voseo ni formas peninsulares.

## Qué reto sale

La pantalla tiene un filtro de tema (todos, falacias, sesgos, datos, fuentes). Con la configuración de la persona en T48 y
T49, y los intentos que ya hizo:

1. **Límite del día** (T49, retos por día): si hoy ya hizo ese número de retos (de cualquier tema), la pantalla dice "Por
   hoy terminaste: hiciste {n} retos." y muestra el progreso y el calendario.
2. **Repasos que tocan**: los conceptos del tema cuyo próximo repaso (SM-2 sobre todos sus intentos) es hoy o antes,
   ordenados por esa fecha y, si empatan, por el orden del banco.
3. **Conceptos nuevos**: si no toca ningún repaso, el primer concepto del tema que la persona nunca practicó, en el orden
   del banco.
4. Si no hay ni repasos ni conceptos nuevos: "Hoy no te toca ningún reto de este tema; el próximo repaso es {mañana | en
   k días}."
5. **Nivel**: con avance automático, el nivel actual del tema (regla de T48 sobre los intentos de ese tema); con avance
   manual, el que la persona elige en la pantalla (por defecto, el primero sin dominar).
6. **Reto**: entre los retos de ese concepto y ese nivel, el que la persona respondió menos veces; si empatan, el primero
   del banco. Si el concepto no tiene retos en ese nivel, el del nivel más cercano por debajo y, si no hay, por encima.

## Calificar, guardar y responder

1. **Calificación por reglas**: en identificar, analizar y evaluar, acierto si la opción elegida es la correcta; en crear,
   acierto si pasan todos los chequeos de la rúbrica. Una respuesta vacía no se califica: "Elige una opción." o "Escribe
   tu versión antes de responder."
2. **Guardar**: un intento por respuesta (tabla `intento_dojo`, solo inserción, con RLS forzada), con el reto, el concepto,
   la técnica, el nivel del reto, la respuesta, si fue acierto, el día según el reloj de la app y una clave contra el
   doble clic: responder dos veces el mismo formulario guarda un solo intento. En la misma transacción se reescribe la
   proyección `competencia` del tema (nivel actual, intentos, aciertos y última práctica), la tabla que el documento
   reserva para el aprendiz.
3. **Respuesta en pantalla**: "Acierto." o "No es esa: la respuesta es {correcta}." (siempre con texto, no solo color), la
   explicación, la respuesta modelo en crear con cada chequeo "cumple" o "no cumple", el próximo repaso del concepto
   ("Siguiente repaso: mañana" o "en {k} días") y el progreso del tema ("T13 · Falacias como esquemas fallidos: nivel
   analizar · 1 de 10 aciertos"). Si este intento dominó un nivel: "Dominaste «{nivel}»: se abre «{siguiente}»." y, si
   era el último, "Dominaste los {n} niveles activos."
4. **Racha**: la de T49 sobre los días con intentos.

## En el resto de la app

- **Inicio**: "B · {n} retos del Dojo para hoy · racha {r}", con n = el menor entre lo que falta del límite del día y los
  repasos que tocan más los conceptos nuevos. No aparece si n es 0.
- **Progreso** (`/dojo/progreso`): por cada tema, los cuatro niveles con su estado (T48, patrón V13b) y el calendario de
  los próximos 7 días con los conceptos (T49, patrón V13c).
- **Fichas de T48 y T49**: "Usar mis datos" llena el formulario con los intentos del Dojo; guardar deja una ejecución como
  cualquier otra técnica.
- **Respaldo**: el paquete de datos (versión 6) lleva los intentos y la competencia de cada tema.

## Ejemplo de punta a punta con el reloj adelantado

Configuración de la persona: T49 con 3 retos por día y facilidad 2,5; T48 con los cuatro niveles, avance automático y 2
aciertos para dominar. Tema: falacias. Las falacias del banco van en este orden: generalización apresurada, ataque a la
persona, falso dilema, pendiente resbaladiza, apelación a una autoridad inapropiada, falsa analogía, falsa causa, señal
débil tomada como prueba, apelación a la mayoría, equívoco y apelación a las consecuencias.

**Miércoles 7 de octubre de 2026.** Nunca practicó: nivel identificar.

1. Ningún repaso toca; concepto nuevo: generalización apresurada → reto `generalizacion-i1`. Acierto. Próximo repaso:
   mañana (8 oct). Identificar: 1 de 2.
2. Concepto nuevo: ataque a la persona → `ad_hominem-i1`. Error. Próximo: mañana. Identificar: 1 de 2.
3. Concepto nuevo: falso dilema → `falso_dilema-i1`. Acierto. Identificar: 2 de 2 → "Dominaste «identificar»: se abre
   «analizar»." Próximo: mañana.
4. "Por hoy terminaste: hiciste 3 retos." Racha 1.

**Jueves 8.** Tocan los tres (próximo 8 oct), en el orden del banco. Nivel analizar.

1. `generalizacion-a1`: acierto. Era su segunda repetición: próximo en 6 días (14 oct). Analizar 1 de 2.
2. `ad_hominem-a1`: acierto. Tras el error volvió a 0 repeticiones: próximo mañana (9 oct). Analizar 2 de 2 → "Dominaste
   «analizar»: se abre «evaluar»."
3. Falso dilema, ya en evaluar → `falso_dilema-e1`: error. Próximo mañana. Evaluar 0 de 2.
4. Terminó el día. Racha 2.

**Viernes 9.** Tocan ataque a la persona y falso dilema (9 oct). Nivel evaluar.

1. `ad_hominem-e1`: acierto. Próximo en 6 días (15 oct). Evaluar 1 de 2.
2. `falso_dilema-e1` (el único de evaluar de ese concepto): acierto. Próximo mañana (10 oct). Evaluar 2 de 2 → "Dominaste
   «evaluar»: se abre «crear»."
3. No toca otro repaso; concepto nuevo: pendiente resbaladiza → `pendiente_resbaladiza-c1`, respondida con la respuesta
   modelo: pasan todos los chequeos, acierto. Próximo mañana. Crear 1 de 2.
4. Racha 3.

**Sábado 10.** Tocan falso dilema y pendiente resbaladiza (10 oct).

1. `falso_dilema-c1`, con la respuesta modelo: acierto. Próximo en 6 días (16 oct). Crear 2 de 2 → "Dominaste los 4
   niveles activos."
2. `pendiente_resbaladiza-c1`: acierto. Próximo en 6 días (16 oct).
3. Concepto nuevo: apelación a una autoridad inapropiada → `autoridad-c1`, respondida con el texto original del reto (la
   falacia sin quitar): no pasa la rúbrica, error. Próximo mañana (11 oct).
4. Racha 4.

**Domingo 11.** No entra.

**Lunes 12.** Inicio dice "B · 3 retos del Dojo para hoy · racha 0": toca autoridad (atrasado desde el 11) y hay conceptos
nuevos. El calendario de `/dojo/progreso` dice: lun 12 · 1 (autoridad) · mar 13 · 0 · mié 14 · 1 (generalización) · jue 15
· 1 (ataque a la persona) · vie 16 · 2 (falso dilema y pendiente resbaladiza) · sáb 17 · 0 · dom 18 · 0.

La aceptación por HTTP del hito 7 recorre esta semana con el reloj de la app adelantado (`APP_RELOJ_AJUSTABLE=true`) y
deja el reloj en 0 al terminar.
