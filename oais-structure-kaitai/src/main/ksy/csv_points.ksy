meta:
  id: csv_points
  title: Demo CSV of points (oais-structure-kaitai example)
  encoding: ASCII
doc: |
  Tiny demo CSV format used to exercise the Kaitai Struct adapter on a
  variable number of repeated, delimited (rather than fixed-width binary)
  records:

    one or more lines, each   x,y,label\n

  x and y are kept as raw comma-terminated text here, the same way Kaitai
  Struct's own str/terminator idiom for delimited text always does - a .ksy
  wanting them as actual integers would add a computed `instance` calling
  `.to_i` on the raw text (left out here to keep this hand-written stand-in,
  see generated/CsvPoints.java, simple; TableCombiner's row-selector coerces
  the text automatically - see StructureNodeBackedTable's Javadoc on
  "Coercion"). label is the rest of the line up to the newline.

  Deliberately the same three-column shape (x, y, label) as point2d.ksy /
  point.dfdl.xsd's single point record, so the same TableSemanticRepInfo
  columns used there also describe a whole CSV file's worth of rows here -
  see oais-structure-demo's points-table-view-kaitai.xml. Unlike point2d.ksy,
  this format repeats (`repeat: eos`), so it also exercises
  StructureNodeKind.ARRAY - see that enum's Javadoc on ARRAY vs. repeated
  COMPOSITE siblings, and TableViewSpecificationReader's "array" row select
  mode added to read rows out of one.
seq:
  - id: rows
    type: row
    repeat: eos
types:
  row:
    seq:
      - id: x
        type: str
        terminator: 0x2c
        encoding: ASCII
      - id: y
        type: str
        terminator: 0x2c
        encoding: ASCII
      - id: label
        type: str
        terminator: 0x0a
        encoding: ASCII
        eos-error: false
