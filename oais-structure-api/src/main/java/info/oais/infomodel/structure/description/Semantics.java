package info.oais.infomodel.structure.description;

import java.io.Serializable;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What an element <em>means</em>, as opposed to how it is laid out - the
 * Semantic Representation Information for one field, record or choice. Every
 * part is optional; which apply depends on the element:
 *
 * @param semanticName the concept the element represents (e.g. "Temperature"), as
 *                     distinct from its structural name (e.g. {@code temp_k})
 * @param definition   what the value means, in prose
 * @param units        units of measurement as text (e.g. "K", "Jy/beam")
 * @param unitsUri     the same units in a controlled vocabulary (e.g. a QUDT unit IRI)
 * @param conceptUri   the concept in a controlled vocabulary or ontology
 * @param codes        for coded values: raw value (as text) -> meaning, in order,
 *                     e.g. {@code "1" -> "housekeeping"}
 * @param scale        with {@code offset}: physical value = raw * scale + offset
 *                     (e.g. FITS {@code BSCALE}); null means 1
 * @param offset       see {@code scale}; null means 0
 * @param fillValue    the raw value (as text) that means "no data"
 * @param validMin     the smallest meaningful physical value, if bounded
 * @param validMax     the largest meaningful physical value, if bounded
 */
public record Semantics(String semanticName, String definition, String units, URI unitsUri, URI conceptUri,
		Map<String, String> codes, BigDecimal scale, BigDecimal offset, String fillValue,
		BigDecimal validMin, BigDecimal validMax) implements Serializable {

	public static final Semantics NONE = new Semantics(null, null, null, null, null, Map.of(), null, null, null, null,
			null);

	public Semantics {
		semanticName = blankToNull(semanticName);
		definition = blankToNull(definition);
		units = blankToNull(units);
		fillValue = blankToNull(fillValue);
		codes = codes == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(codes));
	}

	/** The common case: just a semantic name, definition and units. */
	public static Semantics of(String semanticName, String definition, String units) {
		return new Semantics(semanticName, definition, units, null, null, Map.of(), null, null, null, null, null);
	}

	public boolean isEmpty() {
		return equals(NONE);
	}

	/** Whether raw values need converting to physical ones ({@code scale}/{@code offset} set). */
	public boolean isScaled() {
		return scale != null || offset != null;
	}

	/** The meaning of a coded raw value, if {@code codes} names one. */
	public String meaningOf(Object rawValue) {
		return rawValue == null ? null : codes.get(normalise(rawValue));
	}

	/**
	 * Applies {@code scale}/{@code offset} to a numeric raw value; returns
	 * {@code null} for a non-number or when there is nothing to apply.
	 */
	public BigDecimal physicalValue(Object rawValue) {
		if (!isScaled() || !(rawValue instanceof Number number)) {
			return null;
		}
		BigDecimal raw = new BigDecimal(number.toString());
		BigDecimal scaled = scale == null ? raw : raw.multiply(scale);
		return offset == null ? scaled : scaled.add(offset);
	}

	/** Whether a raw value is this element's fill ("no data") value. */
	public boolean isFill(Object rawValue) {
		return fillValue != null && rawValue != null && fillValue.equals(normalise(rawValue));
	}

	/**
	 * Raw values as they're compared with {@code codes} and {@code fillValue}:
	 * numbers in plain form (so {@code 1}, {@code 1L} and {@code (short) 1}
	 * all match {@code "1"}), text trimmed.
	 */
	static String normalise(Object rawValue) {
		if (rawValue instanceof Number number) {
			try {
				return new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
			} catch (NumberFormatException e) {
				return number.toString();
			}
		}
		return rawValue.toString().strip();
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.strip();
	}
}
