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
also generates DFDL schemas from its engine-neutral description (counts as
`dfdl:occursCount`, conditions and choices as `dfdl:choiceDispatchKey`/
`dfdl:choiceBranchKey` and occurrence expressions, CSV records with
`dfdl:separator`/`dfdl:terminator`; and, when DFDL is among the chosen
languages, bit fields with `dfdl:lengthUnits="bits"`, explicit-length
records, nil values with `nillable`/`dfdl:nilValue`, quoted CSV values with
an escape-block `dfdl:defineEscapeScheme`, and `dfdl:textNumberPattern`
number formats).

`new DfdlFormatSpecification(schemaUri)` points the adapter at a schema; the
schema's default root element is parsed.

## What DFDL is used for

This project's own examples are deliberately small (a binary "point" record
and a CSV table, kept identical across the DFDL, Kaitai and DRB modules so
their results can be compared). DFDL itself is used for much more. It was
built for the text and binary *record* formats that predate XML and JSON,
for example:

- financial messaging: SWIFT MT, ISO 20022, FIX;
- legacy mainframe data: fixed-width and EBCDIC files described by COBOL
  copybooks;
- healthcare: HL7 v2 (pipe-delimited messages);
- defence and government: military message formats (USMTF, VMF) and EDI
  (X12);
- scientific data and telemetry: NASA/JPL has used DFDL for spacecraft
  instrument telemetry, one of the use cases that shaped the standard.

This list is a starting point, not exhaustive. The DFDL Schemas project on
GitHub (https://github.com/DFDLSchemas) publishes open DFDL schemas for many
of these formats.

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
- **Leftover bytes are reported, not ignored.** Daffodil stops once the
  schema's root element is complete and says nothing about data that follows.
  The adapter takes Daffodil's final position and sets the root node's
  `trailingBytes` attribute (`StructureNode.TRAILING_BYTES`) to the number of
  bytes left over.
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
- **End each CSV row with `dfdl:terminator="%NL; %ES;"`** (a newline, or
  the end of the data). A plain `%NL;` terminator makes a file whose last
  line has no newline lose that line -- silently, since Daffodil ignores
  unparsed data; an infix separator between rows instead produces an empty
  extra row from a final newline.
- **Guard a record repeated to the end of the data** with
  `<dfdl:assert testKind="pattern" testPattern="(?s)." .../>`. A text record
  that can be empty (e.g. a single text field) otherwise parses successfully
  at the very end of the data, and Daffodil stops with "consumed no data and
  is stuck in an infinite loop". The pattern assertion only lets another
  record start while at least one byte is left.
- **Bit fields** need `dfdl:lengthUnits="bits"` with
  `dfdl:alignmentUnits="bits"`; a byte-sized element after them is aligned
  to the next whole byte by the default one-byte alignment.

## Known limitations

- Only the schema's default root element can be used.
- No byte positions with Daffodil 3.11 (see above).
- The second, typed-value parse doubles the parsing work and keeps the whole
  Digital Object in memory.
