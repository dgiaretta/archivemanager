meta:
  id: secret_note
  endian: be
  encoding: ASCII
doc: A note encrypted with a one-byte XOR key, with checks on its header.
seq:
  - id: magic
    contents: [0x4e, 0x4f]
    doc: The file must start with the bytes "NO".
  - id: key
    type: u1
    doc: The XOR key the body is encrypted with.
  - id: length
    type: u1
    valid:
      min: 1
      max: 64
    doc: Length of the encrypted body; anything outside 1 to 64 is rejected.
  - id: body
    size: length
    process: xor(key)
    type: note
    doc: Decrypted by XOR-ing each byte with the key (rol/ror rotations are also built in).
types:
  note:
    seq:
      - id: text
        type: str
        size-eos: true
