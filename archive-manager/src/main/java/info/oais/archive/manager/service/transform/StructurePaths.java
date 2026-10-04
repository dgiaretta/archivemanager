package info.oais.archive.manager.service.transform;

import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.StructureNodeKind;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Finds elements in a decoded {@link StructureNode} tree by path, e.g.
 * {@code star.ra} -- the path from the top of the file, without the top
 * element itself, as in {@code im:structuralPath}. Every occurrence is found,
 * in file order, whichever way the engine holds a repeated element: as
 * same-named siblings (DFDL, DRB) or as one {@link StructureNodeKind#ARRAY}
 * node (Kaitai Struct). Names match ignoring case and underscores, since
 * Kaitai Struct renames {@code star_name} to {@code starName}.
 */
final class StructurePaths {

    private StructurePaths() {
    }

    static List<String> steps(String path) {
        return path == null || path.isEmpty() ? List.of() : Arrays.asList(path.split("\\."));
    }

    /** Every element at {@code path} under {@code from}, in file order. */
    static List<StructureNode> find(StructureNode from, String path) {
        List<StructureNode> current = List.of(from);
        for (String step : steps(path)) {
            List<StructureNode> next = new ArrayList<>();
            for (StructureNode node : current) {
                for (StructureNode child : node.getChildren()) {
                    if (sameName(child.getName(), step)) {
                        if (child.getKind() == StructureNodeKind.ARRAY) {
                            next.addAll(child.getChildren());
                        } else {
                            next.add(child);
                        }
                    }
                }
            }
            current = next;
        }
        return current;
    }

    static boolean sameName(String a, String b) {
        return normalise(a).equals(normalise(b));
    }

    static String normalise(String name) {
        return name.replace("_", "").toLowerCase(Locale.ROOT);
    }

    /**
     * A value element's value as text, as the engine decoded it; empty for
     * an element holding other elements, or with no value.
     */
    static Optional<String> text(StructureNode node) {
        if (node.getKind() != StructureNodeKind.LEAF) {
            return Optional.empty();
        }
        return node.getValue().map(v -> {
            if (v instanceof byte[] bytes) {
                return new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
            }
            if (v instanceof Double d && d.isInfinite()) {
                return d > 0 ? "INF" : "-INF";
            }
            if (v instanceof Float f && f.isInfinite()) {
                return f > 0 ? "INF" : "-INF";
            }
            return v.toString();
        });
    }

    /** The precision a decoded value carries, from its Java type: float, double, or neither (null). */
    static String floatingType(StructureNode node) {
        Object v = node.getValue().orElse(null);
        return v instanceof Float ? "float" : v instanceof Double ? "double" : null;
    }
}
