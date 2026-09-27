package info.oais.archive.manager.service.format;

import java.util.List;

/**
 * What decoding a sample data file with a generated description produced
 * -- shared by {@link DfdlSampleRunner} and {@link DrbPythonSampleRunner} so
 * RepInfo Tools shows both the same way.
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

    public boolean ok() {
        return error == null;
    }

    public boolean hasPositions() {
        return rows.stream().anyMatch(r -> !r.byteRange().isEmpty());
    }
}
