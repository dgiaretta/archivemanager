package info.oais.infomodel.structure;

/**
 * How data written back compares with the bytes it was decoded from. When
 * nothing was changed, identical bytes are direct evidence that a
 * description accounts for all of the data and encodes every value the way
 * the data does -- which matters for preservation: the description is then
 * enough to re-create the file from its decoded values.
 *
 * @param originalLength  the original data's length in bytes
 * @param writtenLength   the written data's length in bytes
 * @param firstDifference the offset of the first byte that differs (including one
 *                        past the end of the shorter), or -1 if the two are identical
 * @param differingBytes  how many byte positions differ, counting extra length
 */
public record RoundTrip(long originalLength, long writtenLength, long firstDifference, long differingBytes) {

	/** Compares {@code written} with {@code original}. */
	public static RoundTrip compare(byte[] original, byte[] written) {
		int common = Math.min(original.length, written.length);
		long first = -1;
		long differing = Math.abs((long) original.length - written.length);
		for (int i = 0; i < common; i++) {
			if (original[i] != written[i]) {
				differing++;
				if (first < 0) {
					first = i;
				}
			}
		}
		if (first < 0 && original.length != written.length) {
			first = common;
		}
		return new RoundTrip(original.length, written.length, first, differing);
	}

	public boolean identical() {
		return firstDifference < 0;
	}

	/** In plain words, e.g. for a page or a log. */
	public String describe() {
		if (identical()) {
			return "Identical: all " + originalLength + " bytes were written back exactly.";
		}
		StringBuilder sb = new StringBuilder("Different: ");
		if (originalLength != writtenLength) {
			sb.append(writtenLength).append(" bytes were written for ").append(originalLength).append(" read; ");
		}
		sb.append(differingBytes).append(differingBytes == 1 ? " byte differs" : " bytes differ")
				.append(", the first at offset ").append(firstDifference).append('.');
		return sb.toString();
	}
}
