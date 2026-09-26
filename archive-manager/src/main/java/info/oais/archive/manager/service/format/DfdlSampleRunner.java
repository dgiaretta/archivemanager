package info.oais.archive.manager.service.format;

import info.oais.infomodel.implementation.DigitalObjectRefImpl;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.StructureNodeKind;
import info.oais.infomodel.structure.dfdl.DfdlFormatSpecification;
import info.oais.infomodel.structure.dfdl.DfdlStructureRepInfo;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs a generated DFDL schema (see {@link DfdlGenerator}) against a sample
 * data file through {@code oais-structure-dfdl}'s {@link DfdlStructureRepInfo}
 * -- the same Apache Daffodil-backed executable Structure Representation
 * Information the adapter modules use -- so RepInfo Tools can show whether
 * the description actually decodes real bytes before it's saved.
 *
 * <p>Only DFDL is run, not Kaitai or DRB: Daffodil compiles a
 * {@code .dfdl.xsd} at runtime, whereas {@code oais-structure-kaitai} needs a
 * Java class generated ahead of time by the Kaitai Struct compiler, and
 * {@code oais-structure-drb} needs the (non-Maven-Central) DRB library.
 */
@Component
public class DfdlSampleRunner {

    /** Upper bound on rendered rows, so a large sample can't produce an unbounded page. */
    static final int MAX_ROWS = 2000;

    /** One node of the parsed tree, flattened in document order for display. */
    public record TreeRow(int depth, String name, String kind, String value, String byteRange) { }

    /**
     * @param rows      the flattened tree, empty on failure
     * @param truncated whether rows stopped at {@link #MAX_ROWS}
     * @param error     Daffodil's compile/parse diagnostics, or {@code null} on success
     */
    public record Result(List<TreeRow> rows, boolean truncated, String error) {
        public boolean ok() {
            return error == null;
        }

        /**
         * Byte positions are best-effort in {@code oais-structure-dfdl} (see its
         * {@code PositionTrackingInfosetOutputter}): they depend on internal
         * Daffodil accessors that not every Daffodil version exposes.
         */
        public boolean hasPositions() {
            return rows.stream().anyMatch(r -> !r.byteRange().isEmpty());
        }
    }

    public Result run(String dfdlSchema, byte[] sample) {
        Path schemaFile = null;
        try {
            // Daffodil compiles from a URI, not a string.
            schemaFile = Files.createTempFile("repinfo-tools-", ".dfdl.xsd");
            Files.writeString(schemaFile, dfdlSchema, StandardCharsets.UTF_8);
            DfdlStructureRepInfo repInfo = new DfdlStructureRepInfo(new DfdlFormatSpecification(schemaFile.toUri()));
            StructureNode root = repInfo.apply(new DigitalObjectRefImpl(new ByteArrayInputStream(sample)));
            List<TreeRow> rows = new ArrayList<>();
            boolean complete = flatten(root, 0, rows);
            return new Result(rows, !complete, null);
        } catch (IOException e) {
            return new Result(List.of(), false, "Could not write the schema to a temporary file: " + e.getMessage());
        } catch (RuntimeException e) {
            // StructureInterpretationException carries Daffodil's own diagnostics in its message.
            return new Result(List.of(), false, e.getMessage() != null ? e.getMessage() : e.toString());
        } finally {
            if (schemaFile != null) {
                try {
                    Files.deleteIfExists(schemaFile);
                } catch (IOException ignored) {
                    // A leftover temp file isn't worth failing the request over.
                }
            }
        }
    }

    /** @return {@code false} if {@link #MAX_ROWS} was reached before the whole tree was visited. */
    private static boolean flatten(StructureNode node, int depth, List<TreeRow> rows) {
        if (rows.size() >= MAX_ROWS) {
            return false;
        }
        String value = node.getKind() == StructureNodeKind.LEAF
                ? node.getValue().map(DfdlSampleRunner::describe).orElse("")
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
}
