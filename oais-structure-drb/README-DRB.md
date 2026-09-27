# oais-structure-drb notes

This module bridges GAEL Consultant's Java **DRB** ("Data Request Broker",
`fr.gael.drb`) onto the common `StructureNode` model, like
`oais-structure-dfdl` does for Apache Daffodil. It is written and tested against
**DRB 2.5.13**, which is licensed under the GNU LGPL v3 and is served from this
repository's own `third-party/maven-repo` (DRB is not on Maven Central) - see
`third-party/README.md` for the licence, the sources jar, and which of DRB's
own dependencies are and aren't used.

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
  Tools also generates these schemas from a field list.
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
- **Serialised.** DRB documents no thread-safety guarantee, so calls into it
  share one lock.

## Known DRB limitations

- `xs:hexBinary`/`xs:base64Binary` decode as nothing; describe raw bytes as
  repeated `xs:unsignedByte` (with `maxOccurs` and `sdf:occurrence`).
- Little-endian floating point is only decoded correctly from DRB 2.5 on
  (DRB 2.2 ignored `LSB` for `xs:float`/`xs:double`).
- DRB 2.5 has no HDF5 implementation.
