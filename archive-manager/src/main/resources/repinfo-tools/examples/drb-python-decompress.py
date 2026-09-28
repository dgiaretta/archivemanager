"""
Add-in: xz (LZMA) and bzip2 compression.

The whole file is compressed; the element tree describes the data once
decompressed. The compression is recognised by its magic number, so
uncompressed files are read as they are. Python's standard library also
has gzip, zlib and zipfile.
"""
import bz2
import lzma


def prepare(data):
    if data.startswith(b"\xfd7zXZ\x00"):
        return lzma.decompress(data)
    if data.startswith(b"BZh"):
        return bz2.decompress(data)
    return data
