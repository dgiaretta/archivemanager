# EAST (CCSDS 644.0-B-3)

EAST (Enhanced Ada SubseT) is the CCSDS data description language: an EAST
*Data Description Record* says, in a subset of Ada's declarations, exactly
how a set of data is laid out, down to the bit. It is standardised as CCSDS
644.0-B-3 (June 2010), with the conventions for real numbers in CCSDS
646.0-G-1, and was used for space data archived in Standard Formatted Data
Units (SFDUs). The `oais-structure-east` module has no third-party
dependencies, and does three things with EAST.

## Reading EAST into the element tree (`EastReader`)

A description's logical package (types, representation clauses, then the
variables in the order the data holds them) and physical package (byte order,
array storage, how numbers are represented) become an engine-neutral
`FormatDescription`, from which Kaitai Struct, DFDL and DRB descriptions are
generated:

- records become records, arrays repeated elements (the first index varying
  fastest unless `ARRAY_STORAGE` says otherwise);
- enumerations become integers whose codes (from an enumeration
  representation clause, or 0, 1, 2...) mean the literals; integer and real
  ranges become valid ranges;
- record representation clauses fix the order of components, with the
  unused space between them as `spare_n` fields;
- a variant part becomes a choice when each alternative has one value, and
  otherwise an optional record per alternative (for `|`, ranges, `others`,
  `null` and true/false discriminants);
- virtual discriminants are replaced by their actual values' expressions,
  an EAST path into a record read earlier (`LAST_DATE.DAY`) becoming a dotted
  reference (`last_date.day`);
- `OCTET_STORAGE` gives the byte order, and a field's physical
  representation its own byte order when its subfields are whole octets in
  reverse (little-endian) or one run (big-endian).

A description describes one set of data, applied repeatedly to the whole of
the data: the sets repeat to the end, in a record `set`, unless an EOF marker
ends the last variable's repetition.

What the element tree can't express is refused with the line and why:
markers other than EOF, `**` and the EAST functions on values from the data,
signed or `LOW_ORDER_FIRST` bit fields, integers in pieces or not in two's
complement or unsigned, and reals in conventions other than IEEE 754. The
interpreter reads all of these.

## Writing EAST from the element tree (`EastWriter`)

A `FormatDescription` is written as an EAST description: a record type per
record, an array per repeated element, an enumeration with a representation
clause per field with a code list, a range per field with a valid range, and
a physical package giving the byte order and the representation of every
real (IEEE 754, FCSTC000) and of every integer whose byte order isn't the
description's own. Counts, lengths, conditions and choices become virtual
discriminants whose actual values, with the EAST paths of the fields they
use, end the logical package. A variant part comes last in an EAST record,
so an optional element or a choice becomes a record of its own, with the
element's name. Meanings, units, scaling and fill values become comments.

Names are written in upper case; names that are EAST or Ada keywords
(`RECORD`, `BODY`, `DELTA`...) get `_1` added. What EAST can't describe is
refused: delimited text, elements at an absolute offset, compression,
records of a computed size, choices on text, repetition to the end except of
the last element, and repeated text or bytes of a computed length.

## Interpreting data with EAST (`EastStructureRepInfo`)

`EastStructureRepInfo` is an `ExecutableStructureRepInfo`
(`SpecificationLanguage.EAST`, registered by `EastStructureInterpreterProvider`)
that reads data directly as an EAST description says, giving a
`StructureNode` tree: a composite node per record, an array node per array or
repetition, and a leaf per value, each with the bits it was read from. It
follows the description itself, so it reads everything EAST can say:

- markers: an element repeated until a value (a string, a character such as
  `ASCII.CR`, or a number) is found, and EOF markers;
- components placed by record representation clauses, in bits or words
  (`WORD_16_BITS`, `WORD_32_BITS`), including overlapping alternatives and
  discriminants stored after what they choose;
- bits stored least significant first (`LOW_ORDER_FIRST`);
- integers whose bits are in several subfields, in any of the four sign
  conventions (unsigned, sign and magnitude, ones' and two's complement);
- reals in every convention of CCSDS 646.0-G-1: IEEE 754 (FCSTC000), DEC VAX
  (FCSTC001), MIL-STD-1750A (FCSTC002), CDC NOS-VE (FCSTC003) and NOS-BE
  (FCSTC004), and IBM hexadecimal (FCSTC005);
- numbers and enumerations in ASCII;
- virtual discriminants computed with any EAST operator (`**`, `mod`, `rem`,
  `abs`, `cos`, `ln`, `is_odd`, `!`...) from values anywhere in the data read
  so far, named by their EAST paths.

Names are given in lower case. A binary enumeration's leaf holds its code,
with its literal as the `meaning` attribute. The interpreter reads data; it
doesn't write it back.

Known limits: the CDC conventions follow CCSDS 646.0-G-1's algorithms but
haven't been checked against data written on CDC machines; reals are given as
Java doubles, so 128-bit reals lose precision.
