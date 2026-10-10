# Notas sobre oais-structure-dfdl

Este módulo conecta **DFDL** - el Data Format Description Language del Open
Grid Forum (GFD.207) - con el modelo común `StructureNode`, usando **Apache
Daffodil 3.11.0** (`daffodil-japi_2.13`, Apache License 2.0, de Maven
Central), la implementación de referencia de DFDL.

## Cómo se describe un formato

Un esquema DFDL es un XML Schema corriente cuyos elementos llevan anotaciones
`dfdl:` que dicen cómo se dispone cada uno en los datos: `dfdl:length` y
`dfdl:lengthKind`, `dfdl:byteOrder`, `dfdl:representation` (`binary`/`text`),
`dfdl:encoding`, `dfdl:separator`/`dfdl:terminator` para texto delimitado,
`dfdl:occursCount` para la repetición, y expresiones como
`dfdl:length="{ xs:int(../tns:labelLen) }"` que toman la longitud de un campo de
otro. Pueden describirse registros binarios, texto de ancho fijo y texto
delimitado (p. ej. CSV). Vea `src/test/resources` para un registro binario
"point" y un ejemplo CSV, y `oais-structure-demo` para más; las Herramientas
RepInfo de archive-manager también generan esquemas DFDL a partir de su
descripción independiente del motor (recuentos como `dfdl:occursCount`,
condiciones y elecciones como `dfdl:choiceDispatchKey`/`dfdl:choiceBranchKey` y
expresiones de aparición, registros CSV con `dfdl:separator`/`dfdl:terminator`;
y, cuando DFDL está entre los lenguajes elegidos, campos de bits con
`dfdl:lengthUnits="bits"`, registros de longitud explícita, valores nulos con
`nillable`/`dfdl:nilValue`, valores CSV entre comillas con un bloque de escape
`dfdl:defineEscapeScheme`, y formatos numéricos `dfdl:textNumberPattern`).

`new DfdlFormatSpecification(schemaUri)` indica el esquema al adaptador; se
analiza el elemento raíz predeterminado del esquema.

## Para qué se usa DFDL

Los ejemplos de este proyecto son deliberadamente pequeños (un registro binario
"point" y una tabla CSV, idénticos en los módulos DFDL, Kaitai y DRB para poder
comparar sus resultados). DFDL en sí se usa para mucho más. Se creó para los
formatos de *registros* textuales y binarios anteriores a XML y JSON, por
ejemplo:

- mensajería financiera: SWIFT MT, ISO 20022, FIX;
- datos heredados de mainframes: archivos de ancho fijo y EBCDIC descritos por
  copybooks COBOL;
- sanidad: HL7 v2 (mensajes delimitados por barras verticales);
- defensa y administración: formatos de mensajes militares (USMTF, VMF) y EDI
  (X12);
- datos científicos y telemetría: NASA/JPL ha usado DFDL para la telemetría de
  instrumentos de naves espaciales, uno de los casos de uso que dieron forma a
  la norma.

Esta lista es un punto de partida, no es exhaustiva. El proyecto DFDL Schemas en
GitHub publica esquemas DFDL abiertos para muchos de estos formatos (vea más
abajo).

## Colecciones de ejemplos

- [DFDL Schemas](https://github.com/DFDLSchemas) -- la colección comunitaria de
  esquemas DFDL abiertos en GitHub, un repositorio por formato (p. ej. PCAP,
  PNG, NITF, EDIFACT, ISO 8583), cada uno con datos de prueba.
- [Ejemplos de Apache Daffodil](https://daffodil.apache.org/examples/) --
  pequeños ejemplos resueltos del proyecto Daffodil.

## Cómo funciona el adaptador

- **Se compila una vez y se reutiliza.** Daffodil compila el esquema en el
  primer `apply` y el procesador compilado queda en caché en esa instancia de
  `DfdlStructureRepInfo` - compilar es la parte cara, analizar es
  comparativamente barato. Cree una instancia por esquema y reutilícela.
- **El árbol analizado es el de Daffodil.** El resultado procede del infoset W3C
  DOM de Daffodil y se envuelve elemento a elemento (`DomStructureNode`), no se
  copia. Los bytes del Objeto Digital se leen enteros en memoria, porque el
  adaptador los analiza dos veces (vea el punto siguiente).
- **Valores tipados.** Un segundo análisis con
  `PositionTrackingInfosetOutputter` recupera el valor tipado de cada elemento
  simple, de modo que un elemento `xs:int` vuelve como `Integer`,
  `xs:unsignedByte` como `Short`, etc., en lugar de como texto DOM. Verificado
  con Daffodil 3.11.
- **Sin posiciones de bytes con Daffodil 3.11.** El mismo segundo análisis
  prueba también, por reflexión, los accesores de posición que versiones
  anteriores y posteriores de Daffodil han expuesto; con la 3.11 no existe
  ninguno, así que `getSourceRange()` siempre está vacío. (Los adaptadores DRB y
  Kaitai sí informan de las posiciones.)
- **La repetición son hermanos con el mismo nombre.** Un elemento repetido
  (p. ej. una `row` CSV) aparece como varios hijos con el mismo nombre de su
  padre, la misma convención que el adaptador DRB y el XML simple; use
  `childrenNamed("row")`. El adaptador de Kaitai usa en cambio un único nodo
  `ARRAY`.
- **Los bytes sobrantes se informan, no se ignoran.** Daffodil se detiene en
  cuanto el elemento raíz del esquema está completo y no dice nada de los datos
  que siguen. El adaptador toma la posición final de Daffodil y pone en el
  atributo `trailingBytes` del nodo raíz (`StructureNode.TRAILING_BYTES`) el
  número de bytes sobrantes.
- **Los errores llevan los diagnósticos de Daffodil.** Un esquema que no
  compila, o datos que no se ajustan a él, lanzan
  `StructureInterpretationException`, cuyo mensaje lista los diagnósticos de
  Daffodil.
- **Registro.** El módulo trae `slf4j-simple` en tiempo de ejecución para el
  registro de Daffodil; una aplicación con su propio enlace SLF4J
  (archive-manager usa Logback) debería excluirlo.

## Escribir esquemas que Daffodil acepte

Todo esto surgió al conseguir que los esquemas de este proyecto compilaran con
el Daffodil real:

- **Incluya el `GeneralFormat` de Daffodil.** Varias propiedades de bajo nivel
  (`leadingSkip`, `initiatedContent`, `textBidi`, `floating`, ...) no tienen
  valor predeterminado, y Daffodil se niega a compilar un esquema que deje
  alguna sin definir.
  `<xs:include schemaLocation="/org/apache/daffodil/xsd/DFDLGeneralFormat.dfdl.xsd"/>`
  (resuelto desde el propio jar de Daffodil) las define todas; refiérase a él
  desde su propio `dfdl:defineFormat`/`dfdl:format` y sobrescriba solo lo que
  difiera, p. ej. `representation="binary"`.
- **La fuente de `xs:appinfo` es `http://www.ogf.org/dfdl/`**, no
  `.../dfdl-1.0/` (Daffodil avisa de esta última).
- **Dé al elemento raíz `dfdl:lengthKind="implicit"`** cuando el valor
  predeterminado del formato sea `explicit`: la longitud de un elemento
  compuesto es la suma de sus hijos, y "explicit" sin `dfdl:length` no compila.
- **Cualifique los nombres de elementos en las expresiones**
  (`../tns:labelLen`, no `../labelLen`) cuando el esquema use
  `elementFormDefault="qualified"`.
- **Termine cada fila CSV con `dfdl:terminator="%NL; %ES;"`** (un salto de
  línea, o el final de los datos). Un simple terminador `%NL;` hace que un
  archivo cuya última línea no tiene salto de línea pierda esa línea -- en
  silencio, ya que Daffodil ignora los datos no analizados; un separador infijo
  entre filas produce en cambio una fila vacía de más a partir de un salto de
  línea final.
- **Proteja un registro repetido hasta el final de los datos** con
  `<dfdl:assert testKind="pattern" testPattern="(?s)." .../>`. Un registro de
  texto que puede estar vacío (p. ej. un único campo de texto) se analiza si no
  con éxito justo al final de los datos, y Daffodil se detiene con "consumed no
  data and is stuck in an infinite loop". La aserción de patrón solo deja
  empezar otro registro mientras quede al menos un byte.
- **Convierta los enteros pequeños antes de la aritmética.** Daffodil 3.11
  falla con "Invariant broken ... ClassCastException: Integer cannot be cast to
  Short" cuando una expresión suma o compara directamente valores
  `xs:unsignedByte` (y similares), p. ej. `{ . eq (../a + ../b) mod 256 }`;
  escriba `{ xs:int(.) eq (xs:int(../a) + xs:int(../b)) mod 256 }`. Por eso el
  generador de archive-manager envuelve cada referencia a un campo entero en
  `xs:integer(...)`.
- **No hay `fn:sum`.** Daffodil no la admite, así que una suma de verificación
  sobre un número variable de valores no puede calcularse en una expresión
  DFDL; las comprobaciones sobre campos con nombre sí (vea los ejemplos de
  escritura a mano de archive-manager), y para algo más hace falta una capa
  propia escrita en Java.
- **Las capas** transforman parte de los datos antes del análisis: Daffodil
  3.11 tiene las capas integradas `gzip`, `base64_MIME`,
  `fourbyteswap`/`twobyteswap`, `lineFolded_IMF`/`lineFolded_iCalendar`,
  `boundaryMark` y `fixedLength`. Importe el esquema de la capa desde
  `/org/apache/daffodil/layers/xsd/` y ponga `dfdlx:layer="gz:gzip"` en una
  secuencia, delimitada por una capa `fl:fixedLength` que la contenga y cuya
  longitud se fija con `dfdl:newVariableInstance`.
- **Los campos de bits** necesitan `dfdl:lengthUnits="bits"` con
  `dfdl:alignmentUnits="bits"`; un elemento del tamaño de un byte que les siga
  se alinea al siguiente byte completo por la alineación predeterminada de un
  byte.

## Reescribir los datos

`DfdlStructureRepInfo` es un `WritableStructureRepInfo`: `write(dataObject, changes)` analiza en el
infoset DOM de Daffodil, fija el texto de cada elemento cambiado (`ElementPath` -> valor), y vuelve a
codificar todo el infoset con el unparser de Daffodil; `roundTrip(dataObject)` lo reescribe sin cambios
y compara. Todo lo que el esquema describe se vuelve a codificar a partir de su valor, así que longitudes
y recuentos pueden cambiar si los elementos que los dan se cambian en consonancia (o se calculan con
`dfdl:outputValueCalc`). Lo que el infoset no contiene no se escribe como se leyó: los bytes posteriores
a los datos descritos, los bytes no usados dentro de un elemento de longitud explícita (escritos como el
byte de relleno), y cuál de varios delimitadores o terminadores usaban los datos (se escribe el primero
- p. ej. un salto de línea tras una última línea que no lo tenía).

`encode(infoset)` escribe un archivo nuevo solo a partir de valores, sin original: el infoset es un
documento XML con los elementos del esquema, en su espacio de nombres y en su orden, que contiene los
valores como texto. `DfdlSchemaOutline.read(schemaText)` da el árbol de elementos que sigue ese infoset -
nombres, espacios de nombres, cuántas veces aparece cada uno, tipos de valor, cuáles se calculan
(`dfdl:outputValueCalc`) y cuáles son alternativas de una elección - leído del esquema como XML Schema,
siguiendo los tipos con nombre y las referencias a elementos dentro del mismo documento. La
Transformación de archive-manager construye así los infosets para reescribir un Objeto de Datos en otro
formato.

## Limitaciones conocidas

- Solo puede usarse el elemento raíz predeterminado del esquema.
- El segundo análisis para los valores tipados se hace lo mejor posible: si
  falla de un modo en que el primero no falla (Daffodil puede lanzar un error
  interno "Abort"), los valores vuelven simplemente como texto DOM.
- Sin posiciones de bytes con Daffodil 3.11 (vea arriba).
- El segundo análisis, el de los valores tipados, duplica el trabajo de
  análisis y mantiene todo el Objeto Digital en memoria.
