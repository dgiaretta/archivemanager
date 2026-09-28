# oais-structure-drb notes

This module bridges GAEL Consultant's Java **DRB** ("Data Request Broker",
`fr.gael.drb`) onto the common `StructureNode` model, like
`oais-structure-dfdl` does for Apache Daffodil. It is written and tested against
**DRB 2.5.13**, which is licensed under the GNU LGPL v3 and is served from this
repository's own `third-party/maven-repo` (DRB is not on Maven Central) - see
`third-party/README.md` for the licence, the sources jar, and which of DRB's
own dependencies are and aren't used.

## What DRB is used for

DRB was developed by GAEL Systems for ESA as a federated "virtual
filesystem": one navigable tree over heterogeneous Earth Observation data
products, used in Sentinel ground-segment tooling. It is typically used for:

- satellite product containers that combine many files (the SAFE format);
- netCDF, HDF5, JPEG2000 satellite imagery, DIMAP, GeoTIFF;
- XML metadata, and ZIP/TAR archives wrapping any of the above.

This module uses DRB 2.5.13's SDF schemas and its built-in XML support; the
format implementations for most of the products above were separate DRB
packages and are not included here. This project's own examples (a binary
"point" record and a CSV table, the same as in the DFDL and Kaitai modules)
are in `src/test/resources`.

## Two ways to interpret a Digital Object

`DrbFormatSpecification` selects one:

- **An SDF schema** - `new DrbFormatSpecification(schemaUri)`. DRB's
  declarative description language, its counterpart of a DFDL schema: an
  ordinary XML Schema whose elements carry `sdf:block` annotations in the
  `http://www.gael.fr/2004/12/drb/sdf` namespace - `sdf:length`,
  `sdf:byteOrder` (`MSB`/`LSB`), `sdf:encoding` (`BINARY`/`ASCII`/`EBCDIC`),
  `sdf:occurrence`, `sdf:delimiter`, `sdf:offset`, ... Binary records,
  fixed-width text and delimited text (e.g. CSV, where each field has an
  `sdf:delimiter`) can all be described this way. See `src/test/resources` for
  a little-endian binary record and a CSV example; archive-manager's RepInfo
  Tools also generates these schemas from its engine-neutral description
  (counts and conditions as `sdf:occurrence` queries, choices as
  `sdf:signature` queries, CSV records with `sdf:delimiter`).
- **DRB's own format recognition** - `DrbFormatSpecification.autoDetect("xml")`.
  DRB picks one of its built-in implementations (XML, ...) by **file
  extension**, so the Digital Object's usual extension has to be given.

## How the adapter works

- **Reflection, not a compile-time dependency.** `DrbApi` is the one class that
  touches DRB, through its public interfaces (`DrbNode`, `DrbFactoryImpl`,
  `DrbAttribute`, ...). The module compiles without DRB, and
  `DrbStructureInterpreterProvider.isAvailable()` simply reports `false` when
  DRB isn't on the classpath; applications add `fr.gael.drb:drb` (plus
  `org.slf4j:log4j-over-slf4j` for its log4j 1.x logging calls) at runtime.
  This module's own tests use the real DRB.
- **Fully in memory, like the other adapters.** DRB opens data by path, so the
  bytes are written to a temporary file; the node tree DRB produces is copied
  into plain `StructureNode`s (bounded by `DrbApi.MAX_NODES`), DRB's nodes are
  closed, and the file is deleted before `apply` returns.
- **Typed values.** Numbers come back as `Long` (`BigInteger` beyond its range)
  or `Double`, text as `String`, by each node's declared XML Schema type.
- **Byte positions and documentation.** DRB reports each decoded node's
  absolute byte `offset` and `length`, which become `getSourceRange()`, and an
  element's `xs:documentation` as a `documentation` attribute.
- **Short data is an error.** DRB itself does not fail on data shorter than the
  schema describes (it reports positions past the end); the adapter checks every
  node's position against the data's size and throws
  `StructureInterpretationException` instead.
- **Leftover bytes.** The root node's `trailingBytes` attribute
  (`StructureNode.TRAILING_BYTES`) says how many bytes follow the end of what
  the schema describes, so a description that stops early is visible.
- **Serialised.** DRB documents no thread-safety guarantee, so calls into it
  share one lock.

## Writing SDF schemas DRB will accept

These came up while generating schemas from archive-manager's descriptions:

- **Mark a query that depends on the data `constant="false"`**
  (`<sdf:occurrence constant="false">../count</sdf:occurrence>`). Otherwise
  DRB evaluates it once and reuses the first answer for every repetition.
- **A choice is several optional elements with `sdf:signature` queries**
  (e.g. `../kind = 2`); DRB reads the one whose signature holds. Queries
  inside a branch are one level deeper than they look, since they're
  evaluated from the branch's own node.
- **A delimiter is a single character.** `sdf:delimiter` has no
  "or end of data" alternative.
- **Queries can call Java.** DRB's XQuery calls any public static Java
  method through a `java:` namespace (`declare namespace s =
  "java:java.lang.System"`), and `doc()` reads files and URLs. Only use SDF
  schemas you trust; archive-manager refuses hand-written schemas that do
  either before running them.

## Known DRB limitations

- `xs:hexBinary`/`xs:base64Binary` decode as nothing; describe raw bytes as
  repeated `xs:unsignedByte` (with `maxOccurs` and `sdf:occurrence`).
- Little-endian floating point is only decoded correctly from DRB 2.5 on
  (DRB 2.2 ignored `LSB` for `xs:float`/`xs:double`).
- DRB 2.5 has no HDF5 implementation.
- In delimited text, the last field of the file needs its delimiter: a CSV
  file whose last line has no final newline loses that line. (DFDL, Kaitai
  and drb-python all read it.)
