package info.oais.archive.manager.model.format;

import java.util.List;

/**
 * One row of an HDF5 logical schema: a slash-separated {@code path} carries the
 * hierarchy (e.g. {@code /observations/temperature} for a Dataset inside a
 * Group, {@code /observations/temperature@units} for an Attribute on that
 * Dataset) rather than a nested Java tree, so the editor can use the same
 * flat add/edit/delete/reorder row pattern as {@link FormatField} instead of
 * needing bespoke tree-editing UI. Document order in the definition's list
 * doubles as display order; a row's indentation when rendered is inferred
 * from how many {@code /} segments its path has.
 *
 * @param dtype       for DATASET/ATTRIBUTE only (e.g. "float64", "int32"); null for GROUP.
 * @param shape       for DATASET only; the dimensions in order (e.g. [100, 200] for a
 *                    100x200 array); null for GROUP/ATTRIBUTE (an attribute is scalar-ish
 *                    by convention here -- a multi-valued attribute can still be described
 *                    in {@code description}).
 */
public record Hdf5Node(Hdf5NodeKind kind, String path, String dtype, List<Integer> shape, String description) {

    /** This node's own name -- the path's final segment, on either side of an {@code @}. */
    public String name() {
        String p = path;
        int at = p.lastIndexOf('@');
        if (at >= 0 && kind == Hdf5NodeKind.ATTRIBUTE) {
            return p.substring(at + 1);
        }
        int slash = p.lastIndexOf('/');
        return slash >= 0 ? p.substring(slash + 1) : p;
    }

    /** How many path segments deep this node is, for indenting it under its parent when rendered. */
    public int depth() {
        String p = kind == Hdf5NodeKind.ATTRIBUTE ? path.substring(0, Math.max(path.lastIndexOf('@'), 0)) : path;
        if (p.isEmpty() || p.equals("/")) {
            return 0;
        }
        return (int) p.chars().filter(c -> c == '/').count();
    }
}
