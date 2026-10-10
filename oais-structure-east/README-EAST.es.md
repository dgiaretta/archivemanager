# EAST (CCSDS 644.0-B-3)

EAST (Enhanced Ada SubseT) es el lenguaje de descripción de datos del CCSDS: un
*Data Description Record* EAST dice, en un subconjunto de las declaraciones de
Ada, exactamente cómo está dispuesto un conjunto de datos, hasta el bit. Está
normalizado como CCSDS 644.0-B-3 (junio de 2010), con las convenciones para
números reales en CCSDS 646.0-G-1, y se usó para datos espaciales archivados en
Standard Formatted Data Units (SFDU). El módulo `oais-structure-east` no tiene
dependencias de terceros, y hace tres cosas con EAST.

## Leer EAST en el árbol de elementos (`EastReader`)

El paquete lógico de una descripción (tipos, cláusulas de representación, y
luego las variables en el orden en que las contienen los datos) y su paquete
físico (orden de bytes, almacenamiento de vectores, cómo se representan los
números) se convierten en una `FormatDescription` independiente del motor, a
partir de la cual se generan descripciones Kaitai Struct, DFDL y DRB:

- los registros se convierten en registros, los vectores en elementos
  repetidos (con el primer índice variando más rápido salvo que
  `ARRAY_STORAGE` diga otra cosa);
- las enumeraciones se convierten en enteros cuyos códigos (de una cláusula de
  representación de la enumeración, o 0, 1, 2...) significan los literales; los
  rangos enteros y reales se convierten en intervalos válidos;
- las cláusulas de representación de registros fijan el orden de los
  componentes, con el espacio no usado entre ellos como campos `spare_n`;
- una parte variante se convierte en una elección cuando cada alternativa
  tiene un único valor, y si no en un registro opcional por alternativa (para
  `|`, rangos, `others`, `null` y discriminantes verdadero/falso);
- los discriminantes virtuales se sustituyen por las expresiones de sus valores
  reales, y una ruta EAST dentro de un registro leído antes (`LAST_DATE.DAY`) se
  convierte en una referencia con puntos (`last_date.day`);
- `OCTET_STORAGE` da el orden de bytes, y la representación física de un campo
  su propio orden de bytes cuando sus subcampos son octetos enteros en orden
  inverso (little-endian) o una única secuencia (big-endian).

Una descripción describe un conjunto de datos, aplicado repetidamente a la
totalidad de los datos: los conjuntos se repiten hasta el final, en un registro
`set`, salvo que un marcador EOF termine la repetición de la última variable.

Lo que el árbol de elementos no puede expresar se rechaza con la línea y el
motivo: marcadores distintos de EOF, `**` y las funciones EAST sobre valores de
los datos, campos de bits con signo o `LOW_ORDER_FIRST`, enteros en varias
partes o que no están ni en complemento a dos ni sin signo, y reales en
convenciones distintas de IEEE 754. El intérprete los lee todos.

## Escribir EAST a partir del árbol de elementos (`EastWriter`)

Una `FormatDescription` se escribe como una descripción EAST: un tipo registro
por registro, un vector por elemento repetido, una enumeración con una cláusula
de representación por campo con lista de códigos, un rango por campo con
intervalo válido, y un paquete físico que da el orden de bytes y la
representación de cada real (IEEE 754, FCSTC000) y de cada entero cuyo orden de
bytes no es el de la descripción. Recuentos, longitudes, condiciones y
elecciones se convierten en discriminantes virtuales cuyos valores reales, con
las rutas EAST de los campos que usan, cierran el paquete lógico. En un
registro EAST la parte variante va al final, así que un elemento opcional o una
elección se convierte en un registro propio, con el nombre del elemento.
Significados, unidades, escala y valores de relleno se convierten en
comentarios.

Los nombres se escriben en mayúsculas; a los nombres que son palabras clave de
EAST o Ada (`RECORD`, `BODY`, `DELTA`...) se les añade `_1`. Lo que EAST no
puede describir se rechaza: texto delimitado, elementos en una posición
absoluta, compresión, registros de tamaño calculado, elecciones sobre texto,
repetición hasta el final salvo del último elemento, y texto o bytes repetidos
de longitud calculada.

## Interpretar datos con EAST (`EastStructureRepInfo`)

`EastStructureRepInfo` es un `ExecutableStructureRepInfo`
(`SpecificationLanguage.EAST`, registrado por
`EastStructureInterpreterProvider`) que lee los datos directamente como dice una
descripción EAST, dando un árbol de `StructureNode`: un nodo compuesto por
registro, un nodo vector por vector o repetición, y una hoja por valor, cada uno
con los bits de los que se leyó. Sigue la propia descripción, así que lee todo
lo que EAST puede decir:

- marcadores: un elemento repetido hasta encontrar un valor (una cadena, un
  carácter como `ASCII.CR`, o un número), y marcadores EOF;
- componentes colocados por cláusulas de representación de registros, en bits o
  palabras (`WORD_16_BITS`, `WORD_32_BITS`), incluidas alternativas que se
  solapan y discriminantes guardados después de lo que eligen;
- bits guardados del menos significativo al más (`LOW_ORDER_FIRST`);
- enteros cuyos bits están en varios subcampos, en cualquiera de las cuatro
  convenciones de signo (sin signo, signo y magnitud, complemento a uno y a
  dos);
- reales en todas las convenciones de CCSDS 646.0-G-1: IEEE 754 (FCSTC000), DEC
  VAX (FCSTC001), MIL-STD-1750A (FCSTC002), CDC NOS-VE (FCSTC003) y NOS-BE
  (FCSTC004), y hexadecimal de IBM (FCSTC005);
- números y enumeraciones en ASCII;
- discriminantes virtuales calculados con cualquier operador EAST (`**`, `mod`,
  `rem`, `abs`, `cos`, `ln`, `is_odd`, `!`...) a partir de valores de cualquier
  parte de los datos leídos hasta ese momento, nombrados por sus rutas EAST.

Los nombres se dan en minúsculas. La hoja de una enumeración binaria contiene su
código, con su literal como atributo `meaning`. El intérprete lee los datos; no
los reescribe.

Límites conocidos: las convenciones CDC siguen los algoritmos de CCSDS
646.0-G-1 pero no se han comprobado con datos escritos en máquinas CDC; los
reales se dan como doubles de Java, así que los reales de 128 bits pierden
precisión.
