# Demo resource files

This directory contains the runtime resources used by the demo application.

## Binary sample data

- `point.bin` — the primary sample payload for the point-format example. The bytes represent:
  - 4-byte big-endian signed integer `x`
  - 4-byte big-endian signed integer `y`
  - 1-byte unsigned integer `label_len`
  - `label_len` bytes of ASCII text for `label`

  The current sample payload decodes to:
  - `x = 42`
  - `y = -7`
  - `label = "hi"`

- `point-alt.bin` — a second sample payload with a different numeric pair and a different label:
  - `x = 7`
  - `y = 13`
  - `label = "demo"`

## Descriptive metadata

The XML files tell the semantic layer how to interpret the decoded tree structure:

- `point-table-view.xml` — describes the point record as a table
- `point.dfdl.xsd` — DFDL schema for the same point binary layout
- `timeseries-view.xml` — describes a time-series structure
- `vector-view.xml` — describes a vector/feature structure
- `image-view.xml` — describes a pixel image structure

These XML resources are not the data themselves; they are the interpretation rules that map a parsed tree into a semantic view.

## Notes

The demo no longer generates the point payload in Java memory. The data is now loaded from a real file in this resources folder so the project uses concrete sample bytes instead of synthetic in-memory values.
