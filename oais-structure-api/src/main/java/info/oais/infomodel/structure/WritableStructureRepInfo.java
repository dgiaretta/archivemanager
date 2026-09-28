package info.oais.infomodel.structure;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;

import info.oais.infomodel.implementation.DigitalObjectRefImpl;
import info.oais.infomodel.interfaces.DigitalObject;

/**
 * Executable Structure Representation Information that can also write data
 * back: decode a Digital Object, change some of its values, and encode the
 * result with the same description. With no changes this is a round trip
 * ({@link #roundTrip}), whose identical bytes show the description is
 * complete.
 *
 * <p>How much can change depends on the engine: one that re-encodes the
 * whole structure (DFDL's unparse, Kaitai Struct's read-write mode) can
 * change lengths and counts, if the fields that give them are changed to
 * match; one that writes values in place (DRB's SDF) keeps every element's
 * position and length, so a longer value is cut short or refused.</p>
 */
public interface WritableStructureRepInfo extends ExecutableStructureRepInfo {

	/**
	 * Decodes {@code original}, sets each changed element's value (given as
	 * text, converted to the element's type), and encodes the result.
	 *
	 * @param original the data to start from
	 * @param changes  new values by element path; empty to write the data back unchanged
	 * @return the written bytes
	 * @throws StructureInterpretationException if the data can't be decoded or encoded, a path names no element
	 *                                           (or a group rather than a value), or a value doesn't fit its element
	 */
	byte[] write(DigitalObject original, Map<ElementPath, String> changes);

	/**
	 * Decodes {@code data} and writes it back unchanged, then compares.
	 *
	 * @throws StructureInterpretationException if the data can't be decoded or encoded
	 */
	default RoundTrip roundTrip(DigitalObject data) {
		byte[] original;
		try (InputStream in = data.getObject()) {
			original = in.readAllBytes();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		byte[] written = write(new DigitalObjectRefImpl(new java.io.ByteArrayInputStream(original)), Map.of());
		return RoundTrip.compare(original, written);
	}
}
