# Subconjunto Argdown del Taller (RF-09)

El mapa de argumentos (T01 · Mapeo de argumentos, T06 · Reconstrucción de premisas ocultas y el Taller) se
escribe en un subconjunto pequeño de [Argdown](https://argdown.org): lo suficiente para enunciados, argumentos,
apoyo y ataque por indentación, premisa oculta y título del argumento. Todo lo que no está aquí queda fuera
(secciones, YAML, estructuras premisa-conclusión numeradas, relaciones `<+` y `+>`, etiquetas libres).

El parser vive en `pensamiento.argdown.ParserArgdown`, detrás del puerto `nucleo.puertos.Argdown`.

## Un ejemplo

```
[Sucursal]: Conviene abrir la segunda sucursal en el centro.
  + <Más ventas>: Con más gente pasando, se vende más. {peso: 3}
    + [Tráfico]: El centro tiene más tráfico peatonal que el barrio. #asumible
    + [Conversión]: Más tráfico da más ventas. #asumible
  - [Personal]: Falta personal para atender dos locales.
    - La sobrina de la dueña puede atender las mañanas.
```

- La primera línea es una **conclusión**: un enunciado sin indentación.
- `+` es **apoyo** y `-` es **ataque** al enunciado de la línea de arriba con un nivel menos de indentación.
- `<Más ventas>` es un **argumento con nombre**: sus hijos `+` son sus premisas, todas juntas.
- `#asumible` marca un supuesto; `#oculta`, una premisa oculta (también es supuesto).
- `{peso: 3}` es el peso del argumento que crea la línea; si no se escribe, el peso es 1.

## Gramática (EBNF, ISO 14977)

```ebnf
documento      = { linea-vacia } , bloque , { { linea-vacia } , bloque } , { linea-vacia } ;
bloque         = conclusion , salto , { relacion , salto } ;
linea-vacia    = { " " } , salto ;
salto          = "\n" | "\r\n" | fin-del-texto ;

conclusion     = enunciado , [ " " , marca ] ;
relacion       = sangria , signo , " " , { " " } , destino , [ " " , peso ] ;
sangria        = "  " , { "  " } ;               (* dos espacios por nivel, de 1 a 8 niveles *)
signo          = "+" | "-" ;                     (* + apoyo, - ataque *)
destino        = enunciado , [ " " , marca ] | argumento ;

enunciado      = "[" , titulo , "]" , [ ":" , " " , texto ]   (* sin texto: referencia *)
               | texto-libre ;
argumento      = "<" , titulo , ">" , [ ":" , " " , texto ] ;
marca          = "#oculta" | "#asumible" ;
peso           = "{peso: " , digito , "}" ;      (* de 0 a 9 *)

titulo         = caracter-titulo , { caracter-titulo } ;      (* hasta 80, sin espacios en los bordes *)
texto          = caracter , { caracter } ;                    (* hasta 300, sin espacios en los bordes *)
texto-libre    = primer-caracter , { caracter } ;
caracter-titulo = caracter - ( "[" | "]" | "<" | ">" ) ;
primer-caracter = caracter - ( "[" | "<" | "+" | "-" | " " ) ;
caracter       = ? cualquier carácter Unicode salvo salto de línea y tabulación ? ;
digito         = "0" | "1" | "2" | "3" | "4" | "5" | "6" | "7" | "8" | "9" ;
```

## Reglas que la gramática no alcanza a decir

1. **Indentación.** Solo espacios, de dos en dos. Una línea indentada cuelga del último enunciado o argumento
   con un nivel menos; saltar un nivel es error.
2. **Argumentos.** Un argumento con nombre solo aparece después de un `+` o un `-`, lleva al menos una premisa,
   y sus hijos son todos `+` con enunciados, sin peso. El peso va en la línea del argumento.
3. **Marcas y peso al final.** Se leen de derecha a izquierda: primero `{peso: N}`, después la marca. Por eso un
   texto no puede terminar en ` #oculta`, ` #asumible` ni `{peso: N}`. Una referencia no lleva marca: la marca
   va donde se define el enunciado. Los argumentos no llevan marca.
4. **Títulos.** Un título se define una sola vez con `[Título]: texto`; las demás apariciones son referencias
   `[Título]`, que pueden estar antes o después de la definición. Los nombres de argumento también son únicos.
5. **Topes.** 8000 caracteres, 200 líneas, 60 enunciados con texto, 8 niveles.
6. **Forma canónica.** Escribir el árbol produce: dos espacios por nivel, un espacio después del signo, `: `
   entre título y texto, la marca y el peso separados por un espacio, una línea en blanco entre conclusiones y
   ningún espacio ni salto de línea sobrante. Leer y volver a escribir un texto canónico devuelve el mismo
   texto (propiedad de ida y vuelta, `ArgdownContract`).

## Errores

Cada error dice línea y columna (desde 1) y cómo corregirlo, en español. Por ejemplo:

| Texto | Error |
|---|---|
| `  + Sin conclusión arriba` en la primera línea | Línea 1, columna 3: La indentación salta más de un nivel: esta línea necesita arriba un enunciado con un nivel menos. |
| `   + Tres espacios` | Línea 2, columna 4: La indentación va de dos en dos espacios. |
| `  * Viñeta` | Línea 2, columna 3: Se esperaba + (apoyo) o - (ataque) al inicio de la línea indentada. |
| `  + [Tráfico` | Línea 2, columna 5: Falta cerrar el título del enunciado con ]. |
| `  + [Otro]` sin definición | Línea 2, columna 5: El enunciado [Otro] no está definido: escribe [Otro]: y su texto en alguna línea. |
| `  - <Ventas>` con un hijo `-` | Línea 3, columna 5: Un argumento solo lleva premisas con +; para atacarlo, ataca lo que concluye o una de sus premisas. |

## Del árbol al mapa

Lo que significa cada línea para el mapa, los roles por clase (`conclusion`, `premisa`, `objecion`, `oculta`)
y R04 está en las reglas de cálculo de [`docs/ejemplos/T01.md`](ejemplos/T01.md).
