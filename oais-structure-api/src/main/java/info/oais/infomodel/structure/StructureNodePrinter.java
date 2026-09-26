package info.oais.infomodel.structure;

/**
 * A small, dependency-free pretty-printer for {@link StructureNode} trees.
 * Handy in tests and demos to eyeball what an adapter produced, regardless
 * of which engine built the tree.
 */
public final class StructureNodePrinter {

	private StructureNodePrinter() {
	}

	public static String print(StructureNode node) {
		StringBuilder sb = new StringBuilder();
		print(node, sb, 0);
		return sb.toString();
	}

	private static void print(StructureNode node, StringBuilder sb, int depth) {
		sb.append("  ".repeat(depth)).append(node.getName());
		node.getTypeName().ifPresent(t -> sb.append(" : ").append(t));

		if (node.getKind() == StructureNodeKind.LEAF) {
			sb.append(" = ").append(describeValue(node.getValue().orElse(null)));
		} else {
			sb.append(" (").append(node.getKind()).append(")");
		}
		node.getSourceRange().ifPresent(r -> sb.append("  [bit ").append(r.startBitOffset())
				.append("..+").append(r.bitLength()).append("]"));
		sb.append(System.lineSeparator());

		for (StructureNode child : node.getChildren()) {
			print(child, sb, depth + 1);
		}
	}

	private static String describeValue(Object value) {
		if (value == null) {
			return "null";
		}
		if (value instanceof byte[] bytes) {
			return bytes.length + " byte(s)";
		}
		return "\"" + value + "\" (" + value.getClass().getSimpleName() + ")";
	}
}
