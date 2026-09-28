package info.oais.archive.manager.service.format;

import info.oais.infomodel.structure.ByteRange;
import info.oais.infomodel.structure.DefaultStructureNode;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.StructureNodeKind;
import info.oais.infomodel.structure.description.AlignedNode;
import info.oais.infomodel.structure.description.ChoiceDescription;
import info.oais.infomodel.structure.description.FieldDescription;
import info.oais.infomodel.structure.description.FormatDescription;
import info.oais.infomodel.structure.description.Semantics;
import info.oais.infomodel.structure.description.StructureAligner;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * What decoding a sample data file with a generated description produced
 * -- shared by {@link DfdlSampleRunner}, {@link DrbSampleRunner} and
 * {@link DrbPythonSampleRunner} so RepInfo Tools shows them all the same way.
 *
 * <p>When the description is known (always, in RepInfo Tools), the engine's
 * tree is first lined up against it ({@link #of(FormatDescription, StructureNode)},
 * see {@code StructureAligner}): every engine then shows the same rows, absent
 * optional elements are listed as absent, a choice shows which branch was
 * taken, and each value's meaning is shown alongside it (a coded value's
 * meaning, a scaled value's physical value, a fill value).</p>
 *
 * @param rows          the decoded tree, flattened in document order; empty on failure
 * @param truncated     whether rows stopped at {@link #MAX_ROWS}
 * @param error         the engine's diagnostics, or {@code null} on success
 * @param trailingBytes how many bytes of the sample came after the data the description
 *                      accounts for, when the engine reports it; null otherwise
 * @param warning       a problem worth showing that didn't stop the decode, or null
 */
public record SampleDecodeResult(List<TreeRow> rows, boolean truncated, String error, Long trailingBytes,
                                 String warning) {

    /** Upper bound on rendered rows, so a large sample can't produce an unbounded page. */
    public static final int MAX_ROWS = 2000;

    /**
     * One node of the decoded tree. {@code byteRange} is empty when the engine
     * doesn't report one; {@code meaning} is empty unless the description gives
     * the value one.
     */
    public record TreeRow(int depth, String name, String kind, String value, String byteRange, String meaning) {
        public TreeRow(int depth, String name, String kind, String value, String byteRange) {
            this(depth, name, kind, value, byteRange, "");
        }
    }

    public static SampleDecodeResult failure(String error) {
        return new SampleDecodeResult(List.of(), false, error, null, null);
    }

    /**
     * An engine's decoded tree, lined up against the description it was
     * decoded with. Falls back to the engine's own tree, with a warning, if the
     * two don't line up.
     */
    public static SampleDecodeResult of(FormatDescription format, StructureNode root) {
        Long trailing = trailingBytes(root);
        try {
            AlignedNode aligned = StructureAligner.align(format, root);
            List<TreeRow> rows = new ArrayList<>();
            boolean complete = flatten(aligned, 0, rows);
            return new SampleDecodeResult(rows, !complete, null, trailing, null);
        } catch (RuntimeException e) {
            SampleDecodeResult raw = of(root);
            return new SampleDecodeResult(raw.rows(), raw.truncated(), null, trailing,
                    "The decoded data doesn't line up with the description (" + e.getMessage()
                            + "), so the engine's own tree is shown.");
        }
    }

    /** A decoded tree as the engine produced it, flattened in document order. */
    public static SampleDecodeResult of(StructureNode root) {
        List<TreeRow> rows = new ArrayList<>();
        boolean complete = flatten(root, 0, rows);
        return new SampleDecodeResult(rows, !complete, null, trailingBytes(root), null);
    }

    private static Long trailingBytes(StructureNode root) {
        return root.getAttributes().get(StructureNode.TRAILING_BYTES) instanceof Number n ? n.longValue() : null;
    }

    private static boolean flatten(AlignedNode node, int depth, List<TreeRow> rows) {
        if (rows.size() >= MAX_ROWS) {
            return false;
        }
        String name = node.name() + (node.index() == null ? "" : "[" + node.index() + "]");
        String range = range(Optional.ofNullable(node.sourceRange()));
        if (node.presence() == AlignedNode.Presence.ABSENT) {
            rows.add(new TreeRow(depth, name, kindOf(node), "", "", "absent: its condition is false"));
            return true;
        }
        if (node.description() instanceof FieldDescription) {
            rows.add(new TreeRow(depth, name, "FIELD", describe(node.value()), range, meaning(node)));
            return true;
        }
        String note = node.description() instanceof ChoiceDescription && !node.children().isEmpty()
                ? "chose " + node.children().get(0).name() : "";
        rows.add(new TreeRow(depth, name, kindOf(node), "", range, note));
        for (AlignedNode child : node.children()) {
            if (!flatten(child, depth + 1, rows)) {
                return false;
            }
        }
        return true;
    }

    private static String kindOf(AlignedNode node) {
        return node.description() instanceof FieldDescription ? "FIELD"
                : node.description() instanceof ChoiceDescription ? "CHOICE" : "RECORD";
    }

    /** What a field's value means, from its semantics: a code's meaning, a physical value, fill. */
    private static String meaning(AlignedNode node) {
        Semantics s = node.semantics();
        List<String> parts = new ArrayList<>();
        node.meaning().ifPresent(parts::add);
        node.physicalValue().ifPresent(v -> parts.add("= " + v.stripTrailingZeros().toPlainString()
                + (s.units() != null ? " " + s.units() : "")));
        if (node.physicalValue().isEmpty() && s.units() != null && node.value() instanceof Number) {
            parts.add(s.units());
        }
        if (node.isFill()) {
            parts.add("fill value: no data");
        }
        return String.join("; ", parts);
    }

    /** @return {@code false} if {@link #MAX_ROWS} was reached before the whole tree was visited. */
    private static boolean flatten(StructureNode node, int depth, List<TreeRow> rows) {
        if (rows.size() >= MAX_ROWS) {
            return false;
        }
        String value = node.getKind() == StructureNodeKind.LEAF
                ? node.getValue().map(SampleDecodeResult::describe).orElse("")
                : "";
        rows.add(new TreeRow(depth, node.getName(), node.getKind().name(), value, range(node.getSourceRange())));
        for (StructureNode child : node.getChildren()) {
            if (!flatten(child, depth + 1, rows)) {
                return false;
            }
        }
        return true;
    }

    private static String range(Optional<ByteRange> range) {
        return range.filter(r -> r.isByteAligned() && r.bitLength() > 0)
                .map(r -> "bytes " + r.startByteOffset() + "–" + (r.startByteOffset() + r.byteLength() - 1))
                .or(() -> range.map(r -> "bits " + r.startBitOffset() + "+" + r.bitLength()))
                .orElse("");
    }

    /**
     * Rebuilds a node tree from flattened rows (depth-first, with depths), as
     * drb-python's runner returns them, so it can be lined up like the others.
     */
    static StructureNode toStructureNode(List<TreeRow> rows) {
        record Pending(TreeRow row, List<StructureNode> children) {
        }
        Deque<Pending> stack = new ArrayDeque<>();
        List<StructureNode> roots = new ArrayList<>();
        java.util.function.Consumer<Pending> close = p -> {
            StructureNode node = p.row().kind().equals("LEAF")
                    ? DefaultStructureNode.leaf(p.row().name(), p.row().value())
                    : DefaultStructureNode.builder(p.row().name(), StructureNodeKind.COMPOSITE)
                            .addChildren(p.children()).build();
            if (stack.isEmpty()) {
                roots.add(node);
            } else {
                stack.peek().children().add(node);
            }
        };
        for (TreeRow row : rows) {
            while (stack.size() > row.depth()) {
                close.accept(stack.pop());
            }
            stack.push(new Pending(row, new ArrayList<>()));
        }
        while (!stack.isEmpty()) {
            close.accept(stack.pop());
        }
        return roots.isEmpty() ? DefaultStructureNode.builder("empty", StructureNodeKind.COMPOSITE).build() : roots.get(0);
    }

    /** Raw bytes as hex, shortened past {@link #MAX_HEX_BYTES}. */
    static String hex(byte[] bytes) {
        String hex = java.util.HexFormat.of().formatHex(bytes, 0, Math.min(bytes.length, MAX_HEX_BYTES));
        return "0x" + hex + (bytes.length > MAX_HEX_BYTES ? "… (" + bytes.length + " bytes)" : "");
    }

    private static final int MAX_HEX_BYTES = 64;

    private static String describe(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof byte[] bytes) {
            return hex(bytes);
        }
        if (value instanceof Double || value instanceof Float) {
            return new java.math.BigDecimal(value.toString()).stripTrailingZeros().toPlainString();
        }
        return String.valueOf(value);
    }

    public boolean ok() {
        return error == null;
    }

    public boolean hasPositions() {
        return rows.stream().anyMatch(r -> !r.byteRange().isEmpty());
    }

    public boolean hasMeanings() {
        return rows.stream().anyMatch(r -> !r.meaning().isEmpty());
    }
}
