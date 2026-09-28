"""
Add-in: AES-256 decryption (CTR mode).

The file is a 16-byte initial counter block followed by the encrypted data;
the element tree describes the data once decrypted. Needs the
"cryptography" package in the same Python as drb-python.

The key is read from the AM_DATA_KEY environment variable (64 hex digits).
A real key belongs there, in the server's environment, never in the add-in:
the add-in is saved in the archive with the description. Without it, this
example uses a demonstration key.
"""
import os

from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

_DEMO_KEY = "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"


def prepare(data):
    if len(data) < 16:
        raise ValueError("the file is too short to hold the 16-byte counter block")
    key = bytes.fromhex(os.environ.get("AM_DATA_KEY", _DEMO_KEY))
    decryptor = Cipher(algorithms.AES(key), modes.CTR(data[:16])).decryptor()
    return decryptor.update(data[16:]) + decryptor.finalize()
