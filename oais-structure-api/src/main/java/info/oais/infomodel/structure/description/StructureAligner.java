package info.oais.infomodel.structure.description;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import info.oais.infomodel.structure.StructureInterpretationException;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.StructureNodeKind;

/**
 * Lines up the {@link StructureNode} tree an engine decoded with the
 * {@link FormatDescription} it was decoded from, producing
 * {@link AlignedNode}s: the same shape and value types whichever engine did
 * the decoding, each linked to its element's description - and so to its
 * {@link Semantics} - with {@link AlignedNode.Presence#ABSENT} nodes for
 * optional elements the data doesn't have.
 *
 * <p>It absorbs each engine's conventions:</p>
 * <ul>
 *   <li>Kaitai Struct names fields in camelCase, holds a repeated element as
 *       one {@link StructureNodeKind#ARRAY} node, has no node for a choice's
 *       branch (the choice's node holds the branch's fields), and reports an
 *       absent optional element as a node with no value;</li>
 *   <li>DFDL, DRB and drb-python repeat elements as same-named siblings,
 *       hold the chosen branch as the choice's only child, and simply omit
 *       absent optional elements;</li>
 *   <li>DRB decodes raw bytes as a run of single-byte values, and text as
 *       text (so numbers in delimited text arrive as strings from some
 *       engines);</li>
 *   <li>Daffodil can't carry NUL characters, so trailing NULs are dropped
 *       from text;</li>
 *   <li>Kaitai Struct reads a one-bit field as a boolean; a nil value
 *       ({@link FieldDescription#nilValue()}) arrives as no value, an empty
 *       one, or the nil text itself, and becomes
 *       {@link AlignedNode.Presence#NIL}.</li>
 * </ul>
 */
public final class StructureAligner {

	private StructureAligner() {
	}

	/** @param decoded the node for the whole file, as returned by any structure engine */
	public static AlignedNode align(FormatDescription format, StructureNode decoded) {
		return alignRecord(format.root(), decoded, new ValueScope(null), null);
	}

	/** Field values decoded so far, looked up nearest record first (the same rule as {@link Scope}). */
	private static final class ValueScope {
		private final ValueScope parent;
		private final Map<String, Object> values = new HashMap<>();

		ValueScope(ValueScope parent) {
			this.parent = parent;
		}

		Object lookup(String name) {
			for (ValueScope s = this; s != null; s = s.parent) {
				if (s.values.containsKey(name)) {
					return s.values.get(name);
				}
			}
			return null;
		}
	}

	private static AlignedNode alignRecord(RecordDescription d, StructureNode node, ValueScope outer, Integer index) {
		ValueScope scope = new ValueScope(outer);
		List<StructureNode> decoded = node.getChildren();
		List<AlignedNode> children = new ArrayList<>();
		for (ElementDescription child : d.children()) {
			List<StructureNode> matches = decoded.stream().filter(n -> matchesName(n.getName(), child.name())).toList();
			children.addAll(alignOccurrences(child, matches, scope, d));
		}
		return new AlignedNode(d, index, AlignedNode.Presence.PRESENT, null, children, node.getSourceRange().orElse(null));
	}

	private static List<AlignedNode> alignOccurrences(ElementDescription d, List<StructureNode> matches, ValueScope scope,
			RecordDescription parent) {
		List<AlignedNode> out = new ArrayList<>();
		if (d.occurrence().isRepeated()) {
			List<StructureNode> items = matches;
			if (matches.size() == 1 && matches.get(0).getKind() == StructureNodeKind.ARRAY) {
				items = matches.get(0).getChildren();
			}
			for (int i = 0; i < items.size(); i++) {
				out.add(alignElement(d, items.get(i), scope, i));
			}
			return out;
		}
		if (d instanceof FieldDescription f && f.type() == PrimitiveType.BYTES && matches.size() > 1) {
			// DRB: raw bytes decoded as a run of single-byte values.
			byte[] bytes = new byte[matches.size()];
			for (int i = 0; i < bytes.length; i++) {
				bytes[i] = (byte) ((Number) coerceInteger(matches.get(i).getValue().orElseThrow())).intValue();
			}
			scope.values.put(f.name(), bytes);
			out.add(new AlignedNode(f, null, AlignedNode.Presence.PRESENT, bytes, List.of(),
					spanOf(matches).orElse(null)));
			return out;
		}
		if (d instanceof FieldDescription f && f.nilValue() != null && !matches.isEmpty()) {
			Object raw = matches.get(0).getValue().orElse(null);
			if (raw == null || raw.toString().isEmpty() || raw.toString().equals(f.nilValue())) {
				out.add(new AlignedNode(f, null, AlignedNode.Presence.NIL, null, List.of(),
						matches.get(0).getSourceRange().orElse(null)));
				return out;
			}
		}
		boolean absent = matches.isEmpty()
				|| (matches.get(0).getKind() == StructureNodeKind.LEAF && matches.get(0).getValue().isEmpty()
						&& !(d instanceof FieldDescription f2 && f2.type() == PrimitiveType.STRING));
		if (absent) {
			if (d.occurrence() instanceof Occurrence.Optional) {
				out.add(new AlignedNode(d, null, AlignedNode.Presence.ABSENT, null, List.of(), null));
				return out;
			}
			if (matches.isEmpty()) {
				throw new StructureInterpretationException("The decoded data has no '" + d.name() + "' in '"
						+ parent.name() + "'");
			}
		}
		out.add(alignElement(d, matches.get(0), scope, null));
		return out;
	}

	private static AlignedNode alignElement(ElementDescription d, StructureNode node, ValueScope scope, Integer index) {
		if (d instanceof FieldDescription f) {
			Object value = coerce(node.getValue().orElse(null), f.type());
			if (index == null && f.occurrence() instanceof Occurrence.Once) {
				scope.values.put(f.name(), value);
			}
			return new AlignedNode(f, index, AlignedNode.Presence.PRESENT, value, List.of(),
					node.getSourceRange().orElse(null));
		}
		if (d instanceof RecordDescription r) {
			return alignRecord(r, node, scope, index);
		}
		ChoiceDescription c = (ChoiceDescription) d;
		ChoiceDescription.Branch branch = null;
		StructureNode branchNode = node;
		// DFDL, DRB, drb-python: the chosen branch is the choice's only child, named after it.
		for (ChoiceDescription.Branch b : c.branches()) {
			Optional<StructureNode> child = node.getChildren().stream()
					.filter(n -> matchesName(n.getName(), b.record().name())).findFirst();
			if (child.isPresent() && node.getChildren().size() == 1) {
				branch = b;
				branchNode = child.get();
			}
		}
		if (branch == null) {
			// Kaitai Struct: the choice's node holds the branch's fields; work out which branch.
			branch = chooseBranch(c, scope).orElseThrow(() -> new StructureInterpretationException(
					"Can't tell which branch of '" + c.name() + "' was decoded"));
		}
		AlignedNode chosen = alignRecord(branch.record(), branchNode, scope, null);
		return new AlignedNode(c, index, AlignedNode.Presence.PRESENT, null, List.of(chosen),
				node.getSourceRange().orElse(null));
	}

	private static Optional<ChoiceDescription.Branch> chooseBranch(ChoiceDescription c, ValueScope scope) {
		Object key = ExpressionEvaluator.evaluate(c.discriminator(), ref -> scope.lookup(ref.name()));
		for (ChoiceDescription.Branch b : c.branches()) {
			boolean match = b.isIntegerKey() && key instanceof BigInteger k
					? k.equals(new BigInteger(b.key().strip()))
					: b.key().equals(String.valueOf(key));
			if (match) {
				return Optional.of(b);
			}
		}
		return Optional.empty();
	}

	/** A decoded value as its declared type (see {@link AlignedNode}). */
	static Object coerce(Object raw, PrimitiveType type) {
		if (raw == null) {
			return null;
		}
		if (type.isInteger()) {
			if (raw instanceof Boolean bit) {
				// Kaitai Struct reads a one-bit field as a boolean.
				return bit ? 1L : 0L;
			}
			Object value = coerceInteger(raw);
			// Java has no unsigned 64-bit type, so some engines report uint64 values past
			// Long.MAX_VALUE as negative; read them back as unsigned.
			if (!type.isSigned() && type.fixedWidth() > 0 && value instanceof Long l && l < 0) {
				return BigInteger.valueOf(l).add(BigInteger.ONE.shiftLeft(8 * type.fixedWidth()));
			}
			return value;
		}
		if (type.isFloat()) {
			return raw instanceof Number n ? n.doubleValue() : Double.valueOf(raw.toString().strip());
		}
		if (type == PrimitiveType.BYTES) {
			if (raw instanceof byte[] bytes) {
				return bytes;
			}
			String hex = raw.toString().strip();
			return HexFormat.of().parseHex(hex.startsWith("0x") ? hex.substring(2) : hex);
		}
		// Daffodil maps NUL, which XML can't hold, to U+E000; other engines keep it.
		String s = raw.toString();
		int end = s.length();
		while (end > 0 && (s.charAt(end - 1) == '\0' || s.charAt(end - 1) == '')) {
			end--;
		}
		return s.substring(0, end);
	}

	private static Object coerceInteger(Object raw) {
		BigInteger i = raw instanceof BigInteger b ? b
				: new BigDecimal(raw.toString().strip()).toBigIntegerExact();
		return i.bitLength() < 64 ? (Object) i.longValue() : i;
	}

	private static Optional<info.oais.infomodel.structure.ByteRange> spanOf(List<StructureNode> nodes) {
		Optional<info.oais.infomodel.structure.ByteRange> first = nodes.get(0).getSourceRange();
		Optional<info.oais.infomodel.structure.ByteRange> last = nodes.get(nodes.size() - 1).getSourceRange();
		if (first.isEmpty() || last.isEmpty()) {
			return Optional.empty();
		}
		long start = first.get().startBitOffset();
		return Optional.of(new info.oais.infomodel.structure.ByteRange(start,
				last.get().startBitOffset() + last.get().bitLength() - start));
	}

	/** Engines name elements as described, except Kaitai Struct, which uses lowerCamelCase. */
	static boolean matchesName(String decoded, String described) {
		return decoded.equals(described) || decoded.equals(lowerCamel(described));
	}

	static String lowerCamel(String snake) {
		StringBuilder sb = new StringBuilder();
		boolean upper = false;
		for (char c : snake.toCharArray()) {
			if (c == '_') {
				upper = sb.length() > 0;
			} else {
				sb.append(upper ? Character.toUpperCase(c) : c);
				upper = false;
			}
		}
		return sb.toString();
	}
}
