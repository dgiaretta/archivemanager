package info.oais.infomodel.structure.description;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import info.oais.infomodel.structure.ByteRange;

/**
 * One element of decoded data lined up with the {@link ElementDescription}
 * it was decoded from (see {@link StructureAligner}) - the same shape
 * whichever engine did the decoding.
 *
 * <p>A record or choice has {@link #children()}; a chosen choice has one
 * child, the branch's record. A field has a {@link #value()} typed by its
 * declared {@link PrimitiveType}: {@code Long} (or {@code BigInteger} beyond
 * its range) for integers, {@code Double} for floating point, {@code String}
 * for text and {@code byte[]} for raw bytes. An element that the description
 * allows but the data doesn't have (an optional element whose condition was
 * false) is present as an {@link Presence#ABSENT} node.</p>
 *
 * @param index       for one of several repetitions, its position (0-based); otherwise null
 * @param sourceRange where in the data the engine read it from, when the engine reports that
 */
public record AlignedNode(ElementDescription description, Integer index, Presence presence, Object value,
		List<AlignedNode> children, ByteRange sourceRange) {

	/** Whether the data has the element. */
	public enum Presence {
		PRESENT, ABSENT
	}

	public AlignedNode {
		children = children == null ? List.of() : List.copyOf(children);
	}

	public String name() {
		return description.name();
	}

	public Semantics semantics() {
		return description.semantics();
	}

	/** For a coded value, what it means (see {@link Semantics#codes()}). */
	public Optional<String> meaning() {
		return Optional.ofNullable(semantics().meaningOf(value));
	}

	/** For a scaled value, the physical value (see {@link Semantics#scale()}). */
	public Optional<BigDecimal> physicalValue() {
		return Optional.ofNullable(semantics().physicalValue(value));
	}

	/** Whether the value is the element's fill ("no data") value. */
	public boolean isFill() {
		return semantics().isFill(value);
	}

	/**
	 * A plain-text rendering, one line per element: {@code path = value}.
	 * Engine-independent, so two engines decoding the same data the same way
	 * produce identical lines.
	 */
	public List<String> canonicalLines() {
		List<String> lines = new ArrayList<>();
		appendLines("", lines);
		return lines;
	}

	private void appendLines(String parentPath, List<String> lines) {
		String path = (parentPath.isEmpty() ? "" : parentPath + ".") + name() + (index == null ? "" : "[" + index + "]");
		if (presence == Presence.ABSENT) {
			lines.add(path + " = <absent>");
			return;
		}
		if (description instanceof FieldDescription) {
			lines.add(path + " = " + render(value));
			return;
		}
		if (description instanceof ChoiceDescription && !children.isEmpty()) {
			lines.add(path + " -> " + children.get(0).name());
		}
		for (AlignedNode child : children) {
			child.appendLines(path, lines);
		}
	}

	static String render(Object value) {
		if (value == null) {
			return "<none>";
		}
		if (value instanceof byte[] bytes) {
			return "0x" + HexFormat.of().formatHex(bytes);
		}
		if (value instanceof Double || value instanceof Float) {
			return new BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
		}
		return value.toString();
	}
}
