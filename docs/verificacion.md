# Ficha de verificación (A+): un ejemplo de punta a punta

Este documento se escribió antes del código (sección 10, propuesta 3), como los ejemplos de cada técnica. Dice qué hace la
ficha de verificación (P10), su panel de biblioteca (P11), la ficha de fuente (P12) y la biblioteca (P13), con un ejemplo
calculado a mano que sirve de oráculo para la aceptación por HTTP del hito 6. La app nunca dice "verdadero": dice
"verificada por ti con N fuentes de grupos distintos, bajo R03".

## Reglas de la ficha

1. **Desde dónde se abre.** Desde una premisa de un argumento guardado (`/argumentos/{id}`, botón "Verificar" junto a cada
   premisa) o desde un pendiente de verificación cuyo objeto es una afirmación (Inicio y Expediente: T11 · Falsación, T38 ·
   Double crux, T09 · 5 porqués, T10 · Escalera de inferencia, T41 · Primeros principios, T06 · Reconstrucción de premisas
   ocultas, T19 a T23). La ficha es de una afirmación de la persona: la de otra persona responde 404.
2. **Paso 1 · Tipo (T17 · Hecho, inferencia, juicio).** Los ocho tipos de la sección 5b. Cada tipo dice si es verificable y
   qué evidencia lo probaría (contenido en `catalogo/verificacion.json`). Un juicio de valor o una definición es "no
   verificable": la ficha lo dice y el veredicto no pide fuentes. Con el modelo, "Proponer el tipo" clasifica la afirmación
   con el prompt de T17 (`t17-tipo`) y deja una propuesta sin adoptar.
3. **Paso 2 · Preguntas críticas.** Si la afirmación es premisa de un argumento con esquema de Walton, las preguntas de ese
   esquema; si no, tres preguntas del tipo de afirmación (`catalogo/verificacion.json`). La persona marca las que
   respondió. Decisión: no cambian el cálculo; el veredicto avisa cuántas quedan sin responder.
4. **Paso 3 · Evidencia.** Dos entradas:
   - **la biblioteca** (P11): la búsqueda parte del texto de la afirmación y devuelve pasajes literales con su documento y
     su página. Con Ollama y el documento vectorizado, la búsqueda es semántica (bge-m3, similitud coseno en pgvector);
     sin Ollama, o si el documento no tiene vectores, es de texto completo (PostgreSQL en español). El modelo, si se le
     pide, solo **etiqueta** un pasaje como apoya, contradice, matiza o irrelevante, citando el pasaje tal cual (prompt
     `t22-postura`, el pasaje entre marcas como material citado, nunca como instrucciones). La etiqueta es una propuesta
     sin adoptar y no cuenta. "Usar como evidencia" abre la ficha de fuente con el documento, la página y el pasaje;
   - **la ficha de fuente** (P12): título, autor u organización, fecha, tipo (primaria, secundaria o terciaria), diseño del
     estudio, grupo de origen, ubicación, postura, los tres movimientos de SIFT que dejan nota (investigué la fuente,
     mejor cobertura, contexto original), independencia y acceso al original (lectura lateral) y los cinco criterios de
     CRAAP con los pesos de la configuración de T21 · CRAAP de la persona. Antes de guardar dice qué aporta: la fuerza de
     la evidencia (R01) y el estado al que pasaría la afirmación (R03).
5. **Fuerza y estado, recalculados en cada cambio**: R01 por evidencia con la fecha del reloj, R02 sobre las evidencias
   adoptadas, R03 con el mínimo de grupos de la configuración de T22 · Triangulación de la persona (por defecto 2). Una
   evidencia que viene de una etiqueta del modelo cuenta solo si la persona la adoptó; queda "etiquetada por el modelo".
6. **Chequeos automáticos por reglas** (decisión: solo reglas en este hito):
   - "Afirma un dato sin dar cifra ni fecha." si es dato estadístico y no trae ningún número;
   - "Afirma una comparación o tendencia sin cifra ni fecha." si trae "más", "menos", "mejor", "peor", "subió", "bajó",
     "aumentó", "disminuyó" o "creció" y ningún número;
   - "Dice casi lo mismo que la conclusión: puede ser circular." si es premisa y comparte más de la mitad de sus palabras
     con la conclusión (sin tildes ni palabras vacías, como la regla de solapes de T42 · Árbol de hipótesis MECE);
   - contradicción entre premisas: "no revisada" (queda para cuando el modelo tenga banco y umbrales).
7. **Paso 4 · Veredicto.** El estado de R03, la fuerza neta de R02, la confianza que declara la persona (0 a 100) y el
   estándar de prueba del argumento. **Guardar veredicto**, en una sola transacción:
   1. escribe en la afirmación el tipo, el estado, la fuerza neta, la confianza y la versión de las reglas;
   2. cierra los pendientes de verificación y de revisión que había sobre esa afirmación (`cerrarPendientes`);
   3. guarda una ejecución de T22 · Triangulación con las evidencias de la ficha, que consume la afirmación (no crea otra)
      y proyecta sus propios pendientes: si sigue en verificación, "Buscar una fuente independiente para: …"; si queda
      disputada, "Revisar la evidencia en conflicto sobre: …";
   4. si la afirmación ya tenía una confianza declarada y la nueva es distinta, un **cambio de opinión** con causa
      "evidencia" (R05).
   La ejecución va al expediente del argumento o del pendiente, si lo hay.
8. **R04 recalculado.** Para cada argumento de la persona donde la afirmación es premisa, la ficha dice si la conclusión
   es aceptable bajo el estándar del argumento con el estado guardado de cada premisa: una verificada cuenta como
   aceptada; una disputada queda cuestionada (bloquea salvo en escrutinio); una asumible sin objeción sigue aceptada; las
   demás no están aceptadas. La vista del argumento muestra el mismo cálculo como "Con lo que verificaste".
9. **Firma del veredicto.** "Verificada por ti con {n} fuentes de {g} grupos distintos, bajo R03." · "Refutada por ti con
   …" · "Disputada: hay evidencia fuerte a favor y en contra." · "En verificación: {motivo de R03}." · "Sin verificar:
   ninguna evidencia cuenta todavía." · "No verificable: es un juicio de valor o una definición.". Nunca "verdadero" ni
   "falso".

## Reglas de la biblioteca

1. **Importar** (P13): PDF, Markdown, texto y CSV, **detectados por el contenido** y no por la extensión (RNF-08): `%PDF-`
   al principio es PDF; si no, el archivo tiene que ser texto UTF-8 válido sin bytes nulos; es CSV si las primeras líneas
   tienen el mismo número de comas o de puntos y comas (al menos uno); es Markdown si alguna línea empieza con `#`, `- `,
   `* `, un número y punto, o trae un enlace `[texto](destino)`; si no, es texto. Cualquier otra cosa (una imagen, un
   ejecutable, un ZIP) se rechaza con "Tipo no permitido: solo PDF, Markdown, texto o CSV.". Más de 50 MB se rechaza
   antes de leerlo. No hay OCR.
2. **Privado por defecto.** Un documento importado es de quien lo importa; nadie más lo ve ni lo encuentra al buscar.
   Compartirlo con la institución es un acto explícito que queda en la auditoría ("compartir"), igual que importarlo y
   borrarlo.
3. **Indexar es un trabajo largo** (tabla `trabajo`): extraer el texto (PDF con `pdftotext` como proceso hijo, tiempo
   máximo de 60 s y hasta 500 páginas), trocear y guardar los fragmentos; el documento pasa a "indexado" y ya se
   encuentra por texto completo. Después, otro trabajo **vectoriza** los fragmentos con bge-m3 de a poco (Ollama atiende
   una petición a la vez); mientras tanto la lista dice "vectorizando N%". Sin Ollama, el trabajo espera y se reintenta;
   el documento sigue encontrándose por texto. Los trabajos en proceso al apagar la app vuelven a la cola al arrancar.
4. **Trocear** (decisión): PDF, por página (`pdftotext` separa páginas con salto de página) y dentro de la página por
   párrafos, juntando párrafos hasta unos 800 caracteres sin partir un párrafo salvo que pase de 1.600 (entonces por
   oraciones); Markdown y texto, igual sin páginas; CSV, una fila por fragmento, con el encabezado: "columna: valor ·
   columna: valor". El troceado nunca pierde ni repite texto: las palabras de los fragmentos, en orden, son las del
   documento. Un PDF sin texto (escaneado) queda en "error · sin texto extraíble (es imagen): la app no hace OCR".
5. **Buscar**: por texto completo, cualquiera de las palabras de la consulta (con las raíces del español y sin palabras
   vacías), ordenado por cuánto coincide (`ts_rank_cd`); semántica, por similitud coseno con el vector de la consulta,
   sobre los fragmentos ya vectorizados. Solo entre los documentos propios y los compartidos; hasta 5 pasajes.
6. **Borrar** un documento borra sus fragmentos; las fuentes que lo citaban se conservan y dicen "documento retirado".
7. **Acceso al original**: la ficha de fuente de un documento de la biblioteca enlaza a su descarga, que solo la
   persona (o la institución, si lo compartió) puede abrir.

## Ejemplo · Trabajo: el tráfico del centro

**Datos ficticios.** La dueña de la panadería tiene en el Taller de argumentos este mapa, guardado con estándar
preponderancia:

```
[Sucursal]: Conviene abrir la segunda sucursal en el centro.
  + <Más ventas>: Con más gente pasando, se vende más. {peso: 3}
    + [Tráfico]: El centro tiene más tráfico peatonal que el barrio.
    + [Conversión]: Más tráfico da más ventas. #asumible
  - [Personal]: Falta personal para atender dos locales. {peso: 2}
```

**Antes de verificar (R04).** "Más ventas" (pro, peso 3) tiene una premisa sin verificar (Tráfico): no es aplicable. El
ataque de Personal (contra, peso 2) tampoco: Personal está sin verificar. Ningún argumento a favor es aplicable: la
conclusión **no es aceptable** bajo preponderancia.

Antes, la dueña importó a su biblioteca el PDF `conteo-peatonal-municipio-2025.pdf` (ficticio, 3 páginas). En la página 2
dice: "En el centro pasan en promedio 1.200 personas por hora; en el barrio, 300." Queda "indexado" con un fragmento por
página.

### Paso 1 · Tipo

Desde la premisa Tráfico abre la ficha. Elige **hecho**: "Puede verificarse. Lo probaría: un registro, documento o conteo
con lugar y fecha." Chequeo automático: "Afirma una comparación o tendencia sin cifra ni fecha." (trae "más" y ningún
número).

### Paso 2 · Preguntas críticas

El argumento no tiene esquema de Walton: las tres preguntas de un hecho. Marca dos como respondidas: "¿Quién lo registró y
cómo?" y "¿De cuándo es el dato?". Queda sin responder "¿Hay registros que digan otra cosa?".

### Paso 3 · Evidencia

Busca en la biblioteca con el texto de la premisa. Por texto completo, el fragmento de la página 2 aparece primero porque
comparte "centro" y "barrio" con la consulta (las palabras vacías como "el", "más" y "que" no cuentan). Pide al modelo que lo etiquete y la propuesta dice **apoya** · "Da 1.200 personas por hora en el centro contra 300
en el barrio.". La adopta y pulsa "Usar como evidencia": la ficha de fuente llega con el documento, la página 2 y el pasaje.

| Fuente | Tipo | Fecha | Grupo | Indep. | Original | CRAAP (A, R, Au, E, P) | Postura | Etiquetada por |
|---|---|---|---|---|---|---|---|---|
| F1 Conteo peatonal del municipio | primaria | 2025-03-15 | municipio | sí | sí | 4, 5, 4, 4, 3 = 20 | apoya | modelo (adoptada) |

SIFT de F1: "Investigué la fuente: la oficina de movilidad del municipio." · "Mejor cobertura: la cámara de comercio cita el
mismo conteo." · "Contexto original: conteo de marzo, días hábiles."

**Cálculo a mano** (hoy, 2026-10-07; pesos de T21 en 1, así que CRAAP es la suma). Tipo hecho: base por tipo de fuente.

- F1: primaria 2 + reciente 1 + independiente 1 + original 1 + CRAAP 20 ≥ 18 1 = **6**.
- Antes de guardar, la ficha de fuente dice: "Aporta fuerza 6 a favor (R01). Con esta fuente, la afirmación pasa de «sin
  verificar» a «en verificación». Para «verificada» hace falta una fuente de otro grupo de origen."
- Con F1: neta +6, fuerte; un solo grupo a favor (municipio): **en verificación** · "Fuerza neta +6 (fuerte), pero las
  fuentes a favor son de 1 grupo de origen: falta una fuente independiente (regla R03)."

Agrega una segunda fuente, escrita a mano:

| Fuente | Tipo | Fecha | Grupo | Indep. | Original | CRAAP (A, R, Au, E, P) | Postura | Etiquetada por |
|---|---|---|---|---|---|---|---|---|
| F2 Informe de la cámara de comercio | secundaria | 2024-11-01 | cámara de comercio | sí | no | 4, 4, 3, 3, 4 = 18 | apoya | usuario |

- F2: secundaria 1 + reciente 1 + independiente 1 + CRAAP 18 ≥ 18 1 = **4**.
- Neta: 6 + 4 = **+10**, fuerte. Nada en contra. Grupos a favor: municipio y cámara de comercio = 2 ≥ 2: **verificada** ·
  "2 grupos de origen distintos a favor y fuerza neta +10 (fuerte) (regla R03)."

### Paso 4 · Veredicto

Declara confianza 80. La afirmación no tenía confianza: no hay cambio de opinión. Guarda el veredicto:

- Afirmación: tipo hecho · estado verificada · fuerza neta 10 · confianza 80.
- Ejecución de T22 · Triangulación en el expediente del argumento, con resumen "Verificada · fuerza neta +10 (fuerte) · 2
  fuentes, 2 cuentan." y sin pendientes.
- Firma: "Verificada por ti con 2 fuentes de 2 grupos distintos, bajo R03." y "1 pregunta crítica sin responder."
- **R04 recalculado**: "Más ventas" ya es aplicable (Tráfico verificada, Conversión asumible sin objeción), peso 3; el
  ataque de Personal sigue sin ser aplicable. Pro 3 > contra 0: la conclusión es **aceptable bajo preponderancia**.
  También bajo claro y convincente (el mayor pro, 3, alcanza el umbral 3), pero **no más allá de duda razonable** porque
  "Más ventas" se apoya en un supuesto (Conversión).

### Una semana después: aparece evidencia en contra

La dueña importa `estudio-movilidad-universidad-2026.md` y la búsqueda le muestra: "Los sábados, el barrio recibe más
peatones que el centro." Lo registra a mano:

| Fuente | Tipo | Fecha | Grupo | Indep. | Original | CRAAP (A, R, Au, E, P) | Postura | Etiquetada por |
|---|---|---|---|---|---|---|---|---|
| F3 Estudio de movilidad de la universidad regional | primaria | 2026-02-01 | universidad regional | sí | sí | 5, 4, 4, 4, 4 = 21 | contradice | usuario |

- F3: primaria 2 + reciente 1 + independiente 1 + original 1 + CRAAP 21 1 = **6**.
- Hay evidencia de fuerza 6 a favor (F1) y de fuerza 6 en contra (F3): **disputada** (rama 3 de R03, antes de mirar la
  neta, que es 6 + 4 − 6 = +4, media).
- Baja su confianza a 50 y guarda: cambio de opinión 80 → 50 con causa "evidencia"; ejecución de T22 con resumen
  "Disputada · fuerza neta +4 (media) · 3 fuentes, 3 cuentan." y el pendiente de revisión "Revisar la evidencia en
  conflicto sobre: El centro tiene más tráfico peatonal que el barrio.".
- **R04 recalculado**: Tráfico está disputada, así que queda cuestionada y "Más ventas" deja de ser aplicable bajo
  preponderancia: la conclusión **no es aceptable bajo preponderancia**. Bajo escrutinio sí lo sería (basta un
  argumento a favor aplicable aunque tenga premisas cuestionadas).

## Ejemplo · Desde un pendiente: la esquina de la sucursal

La dueña guardó T11 · Falsación con la condición "Pasan al menos 1.000 personas por la esquina cada mañana." (ejemplo 2 de
[`docs/ejemplos/T11.md`](ejemplos/T11.md)), que dejó el pendiente "Verificar la condición: Pasan al menos 1.000 personas
por la esquina cada mañana.". Lo abre desde Inicio.

- Tipo: **dato estadístico** ("Lo probaría: un estudio o encuesta con muestra, método y fecha."). La afirmación trae un
  número: no hay aviso de cifra.
- La biblioteca le muestra, en la página 3 del mismo conteo del municipio: "En la esquina de la plaza pasan en promedio
  1.150 personas entre las 7 y las 10 de la mañana." Lo registra con diseño **observacional**, primaria, 2025-03-15, grupo
  municipio, independiente, con el original y CRAAP 20, postura apoya (elegida por ella).
- F1: dato estadístico, base por diseño: observacional 2 + reciente 1 + independiente 1 + original 1 + CRAAP 1 = **6**.
  Neta +6, fuerte, un grupo: **en verificación** · "Fuerza neta +6 (fuerte), pero las fuentes a favor son de 1 grupo de
  origen: falta una fuente independiente (regla R03)."
- Declara confianza 70 y guarda: el pendiente "Verificar la condición: …" se **cierra**; la ejecución de T22 deja el nuevo
  "Buscar una fuente independiente para: Pasan al menos 1.000 personas por la esquina cada mañana." y el resumen "En
  verificación · fuerza neta +6 (fuerte) · 1 fuente, 1 cuenta.".

## Respaldo

El paquete de datos sube a la versión 5 con las fuentes, las evidencias, las verificaciones (preguntas marcadas) y los
documentos de la biblioteca. Decisión: de cada documento viajan sus metadatos y el texto de sus fragmentos con su página,
no el archivo original ni los vectores; al importar, el documento queda "indexado" y se vuelve a vectorizar. El archivo
original queda en el respaldo con `pg_dump` del compose. Una persona nunca recibe ni importa documentos de otra.
