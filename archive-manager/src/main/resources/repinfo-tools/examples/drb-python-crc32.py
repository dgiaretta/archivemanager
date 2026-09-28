"""
Add-in: a CRC-32 check.

The file's last four bytes are a big-endian CRC-32 of everything before
them. Describe them in the element tree as the last field (e.g. "crc",
uint32); this checks the value, and adds it to the metadata add-on's result.
"""
import zlib


def _crc(root):
    data = root.decoded_bytes
    if len(data) < 4:
        return None, None
    return int.from_bytes(data[-4:], "big"), zlib.crc32(data[:-4])


def check(root):
    stored, computed = _crc(root)
    if stored is None:
        return ["the file is too short to hold a CRC-32"]
    if stored != computed:
        return [f"CRC-32 mismatch: the file says {stored:08x}, but its data gives {computed:08x}"]
    return []


def metadata(root):
    stored, computed = _crc(root)
    return {"crc32": None if stored is None else f"{stored:08x}", "crc32_ok": stored is not None and stored == computed}
