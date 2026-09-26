meta:
  id: point2d
  title: Demo 2D point with a label (oais-structure-kaitai example)
  endian: be
doc: |
  Tiny demo binary format used to exercise the Kaitai Struct adapter:

    4-byte big-endian signed int   x
    4-byte big-endian signed int   y
    1-byte unsigned int            label_len
    label_len bytes, ASCII         label

  Deliberately the same layout as oais-structure-dfdl's point.dfdl.xsd, so
  the two adapters can be pointed at the same bytes.
seq:
  - id: x
    type: s4
  - id: y
    type: s4
  - id: label_len
    type: u1
  - id: label
    type: str
    size: label_len
    encoding: ASCII
