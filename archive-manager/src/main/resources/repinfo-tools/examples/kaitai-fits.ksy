meta:
  id: fits
  title: FITS (Flexible Image Transport System), FITS Standard 4.0
  file-extension:
    - fits
    - fit
    - fts
  endian: be
  encoding: ASCII
doc: |
  Any FITS file, as the FITS Standard 4.0 (IAU FITS Working Group, 2018)
  defines it: a primary HDU, then any extensions, each HDU a header of
  80-character keyword records ending with END, filled with blank records to a
  2880-byte block, then its data, filled with zeros to a 2880-byte block.

  Sizes come from the mandatory keywords at their fixed positions: SIMPLE or
  XTENSION, BITPIX, NAXIS, NAXIS1 ... NAXISn, and in an extension PCOUNT and
  GCOUNT. The data has |BITPIX| x (NAXIS1 x ... x NAXISm) bits in a primary
  HDU (Eq. 1) and |BITPIX| x GCOUNT x (PCOUNT + NAXIS1 x ... x NAXISm) bits in
  an extension (Eq. 2); the product is written out for up to 9 axes.

  The primary array and IMAGE extensions are read as values of BITPIX's type,
  big-endian, the first axis varying fastest; a BINTABLE's rows as bytes, then
  its heap; a TABLE's rows as text; any other extension as bytes. A binary
  table's columns (its TFORMn keywords) and random groups (GROUPS, PCOUNT,
  GCOUNT) are given by keywords that can be anywhere in the header, so they
  aren't read here.
seq:
  - id: hdus
    type: hdu
    repeat: eos
types:
  hdu:
    seq:
      - id: first
        doc: SIMPLE (the primary HDU) or XTENSION (an extension).
        type: keyword_record
      - id: bitpix
        type: integer_record
      - id: naxis
        type: integer_record
      - id: naxisn
        type: integer_record
        repeat: expr
        repeat-expr: naxis.value
      - id: pcount
        type: integer_record
        if: is_extension
      - id: gcount
        type: integer_record
        if: is_extension
      - id: records
        doc: The other keyword records, and END.
        type: keyword_record
        repeat: until
        repeat-until: _.keyword == "END     "
      - id: header_fill
        doc: Blank records filling the header's last 2880-byte block (36 records).
        size: '((36 - (3 + naxis.value + (is_extension ? 2 : 0) + records.size) % 36) % 36) * 80'
      - id: data
        size: data_bytes
        type:
          switch-on: layout
          cases:
            1: array
            2: binary_table
            3: ascii_table
      - id: data_fill
        doc: Zeros filling the data's last 2880-byte block.
        size: (2880 - data_bytes % 2880) % 2880
    instances:
      is_extension:
        value: first.keyword == "XTENSION"
      elements:
        doc: NAXIS1 x NAXIS2 x ... x NAXISm, or 0 when NAXIS is 0.
        value: >-
          naxis.value == 0 ? 0 :
          naxisn[0].value
          * (naxis.value >= 2 ? naxisn[1].value : 1)
          * (naxis.value >= 3 ? naxisn[2].value : 1)
          * (naxis.value >= 4 ? naxisn[3].value : 1)
          * (naxis.value >= 5 ? naxisn[4].value : 1)
          * (naxis.value >= 6 ? naxisn[5].value : 1)
          * (naxis.value >= 7 ? naxisn[6].value : 1)
          * (naxis.value >= 8 ? naxisn[7].value : 1)
          * (naxis.value >= 9 ? naxisn[8].value : 1)
      data_bytes:
        value: >-
          (bitpix.value < 0 ? -bitpix.value : bitpix.value) / 8
          * (is_extension ? gcount.value * (pcount.value + elements) : elements)
      layout:
        doc: 1 for an array (primary or IMAGE), 2 a binary table, 3 an ASCII table, 0 anything else.
        value: >-
          not is_extension ? 1 :
          first.text.substring(2, 8) == "'IMAGE" ? 1 :
          first.text.substring(2, 11) == "'BINTABLE" ? 2 :
          first.text.substring(2, 8) == "'TABLE" ? 3 : 0
  keyword_record:
    doc: One 80-character keyword record (Sect. 4.1); bytes 9 to 80 as text.
    seq:
      - id: keyword
        type: str
        size: 8
      - id: text
        type: str
        size: 72
  integer_record:
    doc: A keyword record whose value is a fixed-format integer (Sect. 4.2.3), e.g. BITPIX or NAXIS1.
    seq:
      - id: keyword
        type: str
        size: 8
      - id: indicator
        type: str
        size: 2
      - id: value_field
        size: 20
        type: fixed_integer
      - id: comment
        type: str
        size: 50
    instances:
      value:
        value: value_field.value
  fixed_integer:
    doc: |
      A fixed-format integer, right-justified in its 20 bytes (Sect. 4.2.3):
      the spaces before it, then the integer itself, with its sign if it has
      one. Only worked out when it's used, so other values are left as text.
    seq:
      - id: lead
        doc: The spaces before the integer, and its first character.
        type: u1
        repeat: until
        repeat-until: _ != 0x20 or _io.eof
    instances:
      text:
        pos: lead.size - 1
        size-eos: true
        type: str
      value:
        value: text.to_i
  array:
    doc: The primary array or an IMAGE extension's, as BITPIX says.
    seq:
      - id: values
        type:
          switch-on: _parent.bitpix.value
          cases:
            8: u1
            16: s2
            32: s4
            64: s8
            -32: f4
            -64: f8
        repeat: expr
        repeat-expr: _parent.elements
  binary_table:
    doc: NAXIS2 rows of NAXIS1 bytes, then the heap (PCOUNT bytes).
    seq:
      - id: rows
        size: _parent.naxisn[0].value
        repeat: expr
        repeat-expr: _parent.naxisn[1].value
      - id: heap
        size-eos: true
  ascii_table:
    doc: NAXIS2 rows of NAXIS1 characters.
    seq:
      - id: rows
        type: str
        size: _parent.naxisn[0].value
        repeat: expr
        repeat-expr: _parent.naxisn[1].value
