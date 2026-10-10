# Notas sobre oais-structure-drb

Este módulo conecta el **DRB** en Java de GAEL Consultant ("Data Request
Broker", `fr.gael.drb`) con el modelo común `StructureNode`, como hace
`oais-structure-dfdl` con Apache Daffodil. Está escrito y probado con **DRB
2.5.13**, con licencia GNU LGPL v3 y servido desde el propio
`third-party/maven-repo` de este repositorio (DRB no está en Maven Central) -
vea `third-party/README.md` para la licencia, el jar de fuentes, y qué
dependencias de DRB se usan y cuáles no.

## Para qué se usa DRB

DRB lo desarrolló GAEL Systems para la ESA como "sistema de archivos virtual"
federado: un único árbol navegable sobre productos de datos de Observación de
la Tierra heterogéneos, usado en las herramientas del segmento terreno de
Sentinel. Se usa normalmente para:

- contenedores de productos de satélite que combinan muchos archivos (el
  formato SAFE);
- netCDF, HDF5, imágenes de satélite JPEG2000, DIMAP, GeoTIFF;
- metadatos XML, y archivos ZIP/TAR que envuelven cualquiera de los
  anteriores.

Este módulo usa los esquemas SDF de DRB 2.5.13 y su compatibilidad integrada con
XML; las implementaciones de formatos para la mayoría de los productos de arriba
eran paquetes DRB aparte y no se incluyen aquí. Los ejemplos de este proyecto
(un registro binario "point" y una tabla CSV, los mismos que en los módulos DFDL
y Kaitai) están en `src/test/resources`.

## Colecciones de ejemplos

No hay una colección pública de esquemas SDF para el DRB en Java usado aquí; los
de este proyecto están en `src/test/resources`. Para drb-python, el sucesor en
Python de GAEL, la compatibilidad con formatos llega como paquetes de
controladores:

- [drb-python en GitLab](https://gitlab.com/drb-python) -- los controladores
  (p. ej. XML, ZIP, TAR, netCDF), los topics que reconocen productos (p. ej.
  Sentinel SAFE) y los complementos, como código fuente.
- [Controladores drb-python en PyPI](https://pypi.org/search/?q=drb-driver) --
  los mismos controladores como paquetes instalables.

## Dos formas de interpretar un Objeto Digital

`DrbFormatSpecification` elige una:

- **Un esquema SDF** - `new DrbFormatSpecification(schemaUri)`. El lenguaje de
  descripción declarativo de DRB, su equivalente de un esquema DFDL: un XML
  Schema corriente cuyos elementos llevan anotaciones `sdf:block` en el espacio
  de nombres `http://www.gael.fr/2004/12/drb/sdf` - `sdf:length`,
  `sdf:byteOrder` (`MSB`/`LSB`), `sdf:encoding` (`BINARY`/`ASCII`/`EBCDIC`),
  `sdf:occurrence`, `sdf:delimiter`, `sdf:offset`, ... Registros binarios,
  texto de ancho fijo y texto delimitado (p. ej. CSV, donde cada campo tiene un
  `sdf:delimiter`) pueden describirse todos así. Vea `src/test/resources` para
  un registro binario little-endian y un ejemplo CSV; las Herramientas RepInfo
  de archive-manager también generan estos esquemas a partir de su descripción
  independiente del motor (recuentos y condiciones como consultas
  `sdf:occurrence`, elecciones como consultas `sdf:signature`, registros CSV
  con `sdf:delimiter`).
- **El reconocimiento de formatos de DRB** -
  `DrbFormatSpecification.autoDetect("xml")`. DRB elige una de sus
  implementaciones integradas (XML, ...) por la **extensión del archivo**, así
  que hay que indicar la extensión habitual del Objeto Digital.

## Cómo funciona el adaptador

- **Reflexión, no una dependencia de compilación.** `DrbApi` es la única clase
  que toca DRB, a través de sus interfaces públicas (`DrbNode`,
  `DrbFactoryImpl`, `DrbAttribute`, ...). El módulo compila sin DRB, y
  `DrbStructureInterpreterProvider.isAvailable()` simplemente devuelve `false`
  cuando DRB no está en el classpath; las aplicaciones añaden `fr.gael.drb:drb`
  (más `org.slf4j:log4j-over-slf4j` para sus llamadas de registro log4j 1.x) en
  tiempo de ejecución. Las pruebas de este módulo usan el DRB real.
- **Todo en memoria, como los demás adaptadores.** DRB abre los datos por ruta,
  así que los bytes se escriben en un archivo temporal; el árbol de nodos que
  produce DRB se copia en `StructureNode` simples (limitados por
  `DrbApi.MAX_NODES`), los nodos de DRB se cierran, y el archivo se borra antes
  de que `apply` vuelva.
- **Valores tipados.** Los números vuelven como `Long` (`BigInteger` fuera de su
  rango) o `Double`, el texto como `String`, según el tipo XML Schema declarado
  de cada nodo.
- **Posiciones de bytes y documentación.** DRB informa del `offset` y la
  `length` absolutos en bytes de cada nodo decodificado, que se convierten en
  `getSourceRange()`, y de la `xs:documentation` de un elemento como atributo
  `documentation`.
- **Unos datos demasiado cortos son un error.** DRB por sí mismo no falla con
  datos más cortos de lo que describe el esquema (informa de posiciones más allá
  del final); el adaptador comprueba la posición de cada nodo frente al tamaño
  de los datos y lanza en su lugar `StructureInterpretationException`.
- **Bytes sobrantes.** El atributo `trailingBytes` del nodo raíz
  (`StructureNode.TRAILING_BYTES`) dice cuántos bytes siguen al final de lo que
  describe el esquema, de modo que una descripción que se queda corta se ve.
- **Serializado.** DRB no documenta ninguna garantía de seguridad entre hilos,
  así que las llamadas comparten un único cerrojo.

## Escribir esquemas SDF que DRB acepte

Esto surgió al generar esquemas a partir de las descripciones de
archive-manager:

- **Marque con `constant="false"` una consulta que dependa de los datos**
  (`<sdf:occurrence constant="false">../count</sdf:occurrence>`). Si no, DRB la
  evalúa una vez y reutiliza la primera respuesta en cada repetición.
- **Una elección son varios elementos opcionales con consultas
  `sdf:signature`** (p. ej. `../kind = 2`); DRB lee aquel cuya firma se
  cumple. Las consultas dentro de una rama están un nivel más abajo de lo que
  parece, ya que se evalúan desde el propio nodo de la rama.
- **Un delimitador es un único carácter.** `sdf:delimiter` no tiene la
  alternativa "o final de los datos".
- **Las consultas pueden llamar a Java.** El XQuery de DRB llama a cualquier
  método Java estático público mediante un espacio de nombres `java:`
  (`declare namespace s = "java:java.lang.System"`), y `doc()` lee archivos y
  URL. Use solo esquemas SDF de confianza; archive-manager rechaza los esquemas
  escritos a mano que hacen cualquiera de las dos cosas antes de ejecutarlos.

## Reescribir los datos

`DrbStructureRepInfo` es un `WritableStructureRepInfo` cuando tiene un esquema SDF: los bloques SDF de
DRB admiten `setValue`, que reescribe un valor donde se leyó, en una copia de los datos (el archivo
temporal admite escritura, así que DRB lo abre en lectura y escritura). `write(dataObject, changes)`
reescribe *todos* los valores - su nuevo valor, o el que se acaba de leer - de modo que reescribir sin
cambios (`roundTrip(dataObject)`) comprueba que DRB codifica cada valor tal como está guardado. Los bytes
que el esquema no describe se quedan como estaban, así que aquí un viaje de ida y vuelta idéntico no
demuestra que el esquema cubra cada byte (para eso la decodificación informa de los bytes sobrantes). Un
valor delimitado puede cambiar de longitud - DRB desplaza lo que sigue - pero uno de longitud fija no: DRB
rellena un valor de texto corto y recorta uno largo sin quejarse, así que el adaptador vuelve a leer los
datos escritos y rechaza un cambio que no ha salido como se pidió.

DRB 2.5.13 escribe un float de 4 bytes en big-endian incluso cuando el esquema dice `LSB` (intercambia
enteros y doubles, pero no floats); el adaptador anota cada float cuyos bytes guardados se leen como
little-endian y lo escribe él mismo en little-endian cuando DRB ha terminado.

## Limitaciones conocidas de DRB

- `xs:hexBinary`/`xs:base64Binary` se decodifican como nada; describa los
  bytes en bruto como `xs:unsignedByte` repetido (con `maxOccurs` y
  `sdf:occurrence`).
- La coma flotante little-endian solo se decodifica bien a partir de DRB 2.5
  (DRB 2.2 ignoraba `LSB` para `xs:float`/`xs:double`).
- DRB 2.5 no tiene implementación de HDF5.
- En texto delimitado, el último campo del archivo necesita su delimitador: un
  archivo CSV cuya última línea no tiene salto de línea final pierde esa línea.
  (DFDL, Kaitai y drb-python la leen todos.)
