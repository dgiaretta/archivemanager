meta:
  id: stanzas
  encoding: ASCII
doc: Records of several lines each, separated by a blank line.
seq:
  - id: records
    type: record
    repeat: eos
types:
  record:
    seq:
      - id: lines
        type: str
        terminator: 10
        eos-error: false
        repeat: until
        repeat-until: _ == "" or _io.eof
        doc: One line of the record; an empty line ends it.
