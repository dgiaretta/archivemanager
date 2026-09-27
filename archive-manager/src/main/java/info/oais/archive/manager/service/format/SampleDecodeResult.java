package info.oais.archive.manager.service.format;

import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.StructureNodeKind;

import java.util.ArrayList;
import java.util.List;

/**
 * What decoding a sample data file with a generated description produced
 * -- shared by {@link DfdlSampleRunner}, {@link DrbSampleRunner} and {@link DrbPythonSampleRunner} so
 * RepInfo Tools shows them all the same way.
 *
 * @param rows      the decoded tree, flattened in document order; empty on failure
 * @param truncated whether rows stopped at {@link #MAX_ROWS}
 * @param error     the engine's diagnostics, or {@code null} on success
 */
public record SampleDecodeResult(List<TreeRow> rows, boolean truncated, String error) {

    /** Upper bound on rendered rows, so a large sample can't produce an unbounded page. */
    public static final int MAX_ROWS = 2000;

    /** One node of the decoded tree. {@code byteRange} is empty when the engine doesn't report one. */
    public record TreeRow(int depth, String name, String kind, String value, String byteRange) { }

    public static SampleDecodeResult failure(String error) {
        return new SampleDecodeResult(List.of(), false, error);
    }

    /** A successfully decoded {@link StructureNode} tree, flattened in document order. */
    public static SampleDecodeResult of(StructureNode root) {
        List<TreeRow> rows = new ArrayList<>();
        boolean complete = flatten(root, 0, rows);
        return new SampleDecodeResult(rows, !complete, null);
    }

    /** @return {@code false} if {@link #MAX_ROWS} was reached before the whole tree was visited. */
    private static boolean flatten(StructureNode node, int depth, List<TreeRow> rows) {
        if (rows.size() >= MAX_ROWS) {
            return false;
        }
        String value = node.getKind() == StructureNodeKind.LEAF
                ? node.getValue().map(SampleDecodeResult::describe).orElse("")
                : "";
        String range = node.getSourceRange()
                .filter(r -> r.isByteAligned() && r.bitLength() > 0)
                .map(r -> "bytes " + r.startByteOffset() + "–" + (r.startByteOffset() + r.byteLength() - 1))
                .or(() -> node.getSourceRange().map(r -> "bits " + r.startBitOffset() + "+" + r.bitLength()))
                .orElse("");
        rows.add(new TreeRow(depth, node.getName(), node.getKind().name(), value, range));
        for (StructureNode child : node.getChildren()) {
            if (!flatten(child, depth + 1, rows)) {
                return false;
            }
        }
        return true;
    }

    private static String describe(Object value) {
        if (value instanceof byte[] bytes) {
            return bytes.length + " byte(s)";
        }
        return String.valueOf(value);
    }

    public boolean ok() {
        return error == null;
    }

    public boolean hasPositions() {
        return rows.stream().anyMatch(r -> !r.byteRange().isEmpty());
    }
}
