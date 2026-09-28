# oais-structure-dfdl notes

This module bridges **DFDL** - the Open Grid Forum's Data Format Description
Language (GFD.207) - onto the common `StructureNode` model, using **Apache
Daffodil 3.11.0** (`daffodil-japi_2.13`, Apache License 2.0, from Maven
Central), the reference DFDL implementation.

## How a format is described

A DFDL schema is an ordinary XML Schema whose elements carry `dfdl:`
annotations saying how each one is laid out in the data: `dfdl:length` and
`dfdl:lengthKind`, `dfdl:byteOrder`, `dfdl:representation` (`binary`/`text`),
`dfdl:encoding`, `dfdl:separator`/`dfdl:terminator` for delimited text,
`dfdl:occursCount` for repetition, and expressions such as
`dfdl:length="{ xs:int(../tns:labelLen) }"` that take one field's length from
another. Binary records, fixed-width text and delimited text (e.g. CSV) can all
be described. See `src/test/resources` for a binary "point" record and a CSV
example, and `oais-structure-demo` for more; archive-manager's RepInfo Tools
also generates DFDL schemas from a field list.

`new DfdlFormatSpecification(schemaUri)` points the adapter at a schema; the
schema's default root element is parsed.

## How the adapter works

- **Compiled once, then reused.** Daffodil compiles the schema on the first
  `apply` and the compiled processor is cached by that `DfdlStructureRepInfo`
  instance - compiling is the expensive part, parsing is comparatively cheap.
  Create one instance per schema and reuse it.
- **The parsed tree is Daffodil's own.** The result comes from Daffodil's
  W3C DOM infoset and is wrapped element by element (`DomStructureNode`), not
  copied. The Digital Object's bytes are read fully into memory, because the
  adapter parses them twice (see the next point).
- **Typed values.** A second parse with `PositionTrackingInfosetOutputter`
  recovers each simple element's typed value, so an `xs:int` element comes back
  as an `Integer`, `xs:unsignedByte` as a `Short`, and so on, rather than as DOM
  text. Verified with Daffodil 3.11.
- **No byte positions with Daffodil 3.11.** The same second parse also tries,
  by reflection, the position accessors older and newer Daffodil versions have
  exposed; with 3.11 none of them exist, so `getSourceRange()` is always empty.
  (The DRB and Kaitai adapters do report positions.)
- **Repetition is same-named siblings.** A repeated element (e.g. a CSV `row`)
  appears as several same-named children of its parent, the same convention as
  the DRB adapter and plain XML; use `childrenNamed("row")`. Kaitai's adapter
  uses a single `ARRAY` node instead.
- **Errors carry Daffodil's own diagnostics.** A schema that doesn't compile,
  or data that doesn't match it, raises `StructureInterpretationException`
  whose message lists Daffodil's diagnostics.
- **Logging.** The module brings `slf4j-simple` at runtime for Daffodil's
  logging; an application with its own SLF4J binding (archive-manager uses
  Logback) should exclude it.

## Writing schemas Daffodil will accept

These all came up while getting this project's schemas to compile with real
Daffodil:

- **Include Daffodil's `GeneralFormat`.** Several low-level properties
  (`leadingSkip`, `initiatedContent`, `textBidi`, `floating`, ...) have no
  default, and Daffodil refuses to compile a schema that leaves any unset.
  `<xs:include schemaLocation="/org/apache/daffodil/xsd/DFDLGeneralFormat.dfdl.xsd"/>`
  (resolved from Daffodil's own jar) sets them all; refer to it from your own
  `dfdl:defineFormat`/`dfdl:format` and override only what differs, e.g.
  `representation="binary"`.
- **The `xs:appinfo` source is `http://www.ogf.org/dfdl/`**, not
  `.../dfdl-1.0/` (Daffodil warns about the latter).
- **Give the root element `dfdl:lengthKind="implicit"`** when the format's
  default is `explicit`: a composite element's length is the sum of its
  children, and "explicit" with no `dfdl:length` fails to compile.
- **Qualify element names in expressions** (`../tns:labelLen`, not
  `../labelLen`) when the schema uses `elementFormDefault="qualified"`.
- **Use an infix `dfdl:separator` rather than a terminator between CSV
  rows**, so a file's final newline doesn't produce an empty extra row.

## Known limitations

- Only the schema's default root element can be used.
- No byte positions with Daffodil 3.11 (see above).
- The second, typed-value parse doubles the parsing work and keeps the whole
  Digital Object in memory.
