# Notas sobre oais-structure-kaitai

Este módulo conecta **Kaitai Struct** con el modelo común `StructureNode`. Usa el
runtime de Java de Kaitai Struct `io.kaitai:kaitai-struct-runtime:0.11`
(licencia MIT, de Maven Central), y se comprobó con clases generadas por el
compilador de Kaitai Struct 0.11.

## Cómo se describe un formato

Una descripción Kaitai Struct es un archivo `.ksy` (YAML): una `seq` de campos,
cada uno con un `id` y un `type` (`s4`, `u1`, `f8`, `str`, un tipo definido por
el usuario, ...), además de `size`, `encoding`, `terminator`, `repeat` (`eos`,
`expr`, `until`), `if`, `switch-on` para registros variantes, `instances`
calculadas, y `endian` en el nivel superior. Está pensado para la ingeniería
inversa de formatos binarios arbitrarios; la colección pública de formatos
(https://formats.kaitai.io) tiene cientos de especificaciones reales. Vea
`src/main/ksy` para los dos ejemplos de este módulo: un registro binario "point"
y un archivo CSV.

**A diferencia de DFDL y DRB, un `.ksy` no se lee en tiempo de ejecución.** El
compilador de Kaitai Struct (`ksc`) lo traduce de antemano a código fuente -
aquí, una clase Java - y es esa clase la que analiza los bytes. Así que:

- `KaitaiFormatSpecification` nombra una **clase generada**
  (`new KaitaiFormatSpecification(Point2d.class)`), no un archivo `.ksy`.
- Las clases generadas deben compilarse dentro de la aplicación. Las de este
  módulo están en el repositorio en `src/main/java/.../generated/`, así que su
  compilación no necesita el compilador.
- Las Herramientas RepInfo de archive-manager generan un `.ksy` a partir de su
  descripción independiente del motor (un tipo por registro y por rama de
  elección, `repeat: expr`/`eos`, `if`, `switch-on`, campos de texto con
  `terminator`; y, cuando Kaitai está entre los lenguajes elegidos, campos de
  bits `bN`, registros `size:`, `process: zlib` e `instances` con `pos:` para
  elementos en una posición). **Pueden** probarlo con un archivo de muestra:
  incluyen el compilador de Kaitai Struct, lo ejecutan como un proceso Java
  aparte, compilan el Java generado en el mismo proceso y lo cargan
  (`KaitaiSampleRunner`; vea el README de archive-manager).

## Para qué se usa Kaitai Struct

Más allá de los dos pequeños ejemplos de este proyecto, Kaitai Struct se usa
sobre todo para describir y hacer ingeniería inversa de formatos binarios
existentes. Las especificaciones de la colección incluyen:

- contenedores multimedia: MP4, AVI, WAV, MIDI;
- ejecutables y otros formatos binarios: ELF, PE/EXE, Mach-O, `.class` de Java;
- sistemas de archivos e informática forense: NTFS, ext2, colmenas del registro
  de Windows, archivos prefetch, registros de eventos (populares en el análisis
  de malware y en los retos CTF);
- formatos de archivo y compresión, formatos de recursos de juegos, y formatos
  de protocolos de red y de captura de paquetes (PCAP).

Cualquier `.ksy` de la colección puede compilarse y usarse con este adaptador
del mismo modo que los ejemplos de aquí.

## Colecciones de ejemplos

- [Colección de formatos de Kaitai Struct](https://formats.kaitai.io) --
  cientos de descripciones `.ksy` de formatos reales, por categoría, cada una
  con sus analizadores generados y su documentación.
- [kaitai_struct_formats](https://github.com/kaitai-io/kaitai_struct_formats)
  -- las mismas descripciones como código fuente, en GitHub.
- [Kaitai Struct Web IDE](https://ide.kaitai.io) -- pruebe una descripción con
  un archivo en el navegador.

## Generar las clases

Después de editar un `.ksy`, regenere con el compilador de Kaitai Struct (0.11,
https://kaitai.io), desde `src/main/ksy`:

    kaitai-struct-compiler -t java --java-package info.oais.infomodel.structure.kaitai.generated --debug --outdir ../java point2d.ksy csv_points.ksy

`--debug` es lo que hace que las clases generadas registren de dónde se leyó cada
campo, lo que este adaptador convierte en posiciones de bytes (vea abajo). Sin
él todo lo demás sigue funcionando, solo que sin posiciones.

## Cómo funciona el adaptador

- **Genérico, por reflexión.** `KaitaiReflectiveStructureNode` recorre
  cualquier clase generada sin código propio del formato, usando las
  convenciones del destino Java de Kaitai: cada campo es un accesor público sin
  argumentos con el nombre del campo en camelCase y sin prefijo `get`
  (`label_len` pasa a ser `labelLen()`), un tipo anidado es otro
  `KaitaiStruct`, y un campo `repeat` es una `List`. Los accesores internos de
  Kaitai (`_io`, `_parent`, `_root`, `_read`, ...) se omiten. Los hijos vienen
  en **el orden del archivo**: de la lista `_seqFields` de una clase `--debug`,
  y si no del orden de los campos declarados de la clase (la reflexión de Java
  no garantiza el orden de los métodos).
- **Bytes sobrantes.** El atributo `trailingBytes` del nodo raíz
  (`StructureNode.TRAILING_BYTES`) dice cuántos bytes no alcanzó el análisis (la
  posición final del flujo frente a su tamaño).
- **Valores tipados.** Los valores vuelven con los tipos Java de los accesores
  generados (`int`, `long`, `double`, `String`, `byte[]`, ...).
- **La repetición es un nodo `ARRAY`.** Un campo `repeat` se convierte en un
  nodo de tipo `ARRAY` cuyos hijos se llaman `0`, `1`, ... (los adaptadores DFDL
  y DRB usan en cambio hermanos con el mismo nombre). Las vistas de tabla
  seleccionan estas filas con `<rows select="array">`.
- **Posiciones de bytes con compilaciones `--debug`.** Una clase compilada con
  `--debug` tiene los mapas públicos `_attrStart`/`_attrEnd` (el desplazamiento
  de inicio y de fin de cada campo, por nombre de accesor) y
  `_arrStart`/`_arrEnd` (una entrada por elemento de un campo repetido). El
  adaptador los lee, así que cada campo - y cada elemento de un campo repetido
  - informa de su intervalo de bytes. Una clase `--debug` además no analiza en
  su constructor; `KaitaiStructureRepInfo` lo detecta y llama a `_read()` él
  mismo (llámelo usted si construye directamente una clase así).
- **Tipos switch.** Para un campo `switch-on`, el nombre de tipo del nodo es el
  tipo concreto realmente analizado, no el tipo común declarado.
- **En memoria.** El Objeto Digital se lee entero en un búfer de bytes antes del
  análisis.

## Reescribir los datos

`KaitaiStructureRepInfo` es un `WritableStructureRepInfo`, para clases compiladas con el modo de
lectura y escritura de Kaitai Struct (`kaitai-struct-compiler -w`, solo Java y Python, desde la 0.11);
una clase de solo lectura se rechaza con esa explicación. `write(dataObject, changes)` lee los datos,
obtiene las instancias de lectura diferida, fija los valores cambiados mediante los setters generados
(los pasos de la ruta nombran los campos como en el `.ksy`, `sample_count`, o como el accesor,
`sampleCount`; `[n]` elige un elemento de un campo repetido), hace que cada objeto cambiado se compruebe a
sí mismo (`_check()`, que rechaza p. ej. un recuento de repeticiones que ya no coincide con su lista, o una
cadena que ya no cabe en su tamaño), y lo escribe todo con `_write`. Kaitai escribe en un flujo de tamaño
fijo, y un elemento repetido o dimensionado hasta el final de los datos debe terminar exactamente donde
termina el flujo, así que el escritor parte del tamaño de los propios datos y lo ajusta según lo que informa
Kaitai. Los bytes no usados dentro de un tipo de `size` declarado se escriben como ceros.

## Limitaciones conocidas

- Las descripciones no pueden cargarse en tiempo de ejecución: cada formato
  necesita que su clase se genere y compile primero (archive-manager hace ambas
  cosas bajo demanda para su prueba con una muestra, lo que tarda unos
  segundos).
- El compilador de Kaitai Struct no renombra los campos cuyos nombres son
  palabras reservadas de Java (`class`, `int`, `null`, ...), así que su Java no
  compila; el editor de archive-manager rechaza esos nombres cuando Kaitai es
  uno de los destinos. Los nombres que YAML lee como booleanos o null (`on`,
  `no`, `null`) deben ir entre comillas en un `.ksy`, como hace el generador de
  archive-manager.
- El análisis es inmediato y en memoria; para archivos muy grandes, una variante
  `RandomAccessFileKaitaiStream` de `KaitaiStructureRepInfo` sería la extensión
  natural.
