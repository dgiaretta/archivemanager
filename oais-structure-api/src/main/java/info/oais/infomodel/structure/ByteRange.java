package info.oais.infomodel.structure;

/**
 * The span of the original bitstream (relative to the start of the Digital
 * Object) that a {@link StructureNode} was decoded from, when the engine
 * that produced the node is able to say. Expressed in bits so that engines
 * which parse bit-level fields (DFDL packed decimals, bitfields, etc.) can
 * be precise; engines that only know byte offsets (most of them) can still
 * populate this by multiplying by 8.
 *
 * @param startBitOffset offset, in bits, from the start of the Digital Object
 * @param bitLength      length, in bits, of the encoded field
 */
public record ByteRange(long startBitOffset, long bitLength) {

	public ByteRange {
		if (startBitOffset < 0) {
			throw new IllegalArgumentException("startBitOffset must not be negative: " + startBitOffset);
		}
		if (bitLength < 0) {
			throw new IllegalArgumentException("bitLength must not be negative: " + bitLength);
		}
	}

	public static ByteRange ofBytes(long startByteOffset, long byteLength) {
		return new ByteRange(startByteOffset * 8L, byteLength * 8L);
	}

	public long startByteOffset() {
		return startBitOffset / 8L;
	}

	public long byteLength() {
		return bitLength / 8L;
	}

	/** True if this range starts and ends on a byte boundary. */
	public boolean isByteAligned() {
		return startBitOffset % 8L == 0 && bitLength % 8L == 0;
	}
}
