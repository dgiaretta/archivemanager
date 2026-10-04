package info.oais.archive.manager.service.transform;

import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.dfdl.DfdlSchemaOutline.SchemaElement;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The check of one Transformation Information Property -- the values of one
 * element of the old format -- against the new data, decoded again with the
 * new format's own description, so the check doesn't trust the writing.
 * Physical values are compared: each side's raw values times its scale plus
 * its offset ({@code im:scaleFactor}, {@code im:addOffset}), within a
 * tolerance -- given, or else what the types carry: about 7 significant
 * digits for a single-precision number, 15 for a double, half a step for a
 * scaled whole number, exact otherwise. Text is compared without trailing
 * spaces and NULs, which fixed-length fields pad with.
 *
 * @param sourcePath where the property's values are in the old format
 * @param targetPath where they went in the new format, or null if nowhere
 * @param label      what the values are, e.g. "Right ascension (deg)"
 * @param outcome    whether they were preserved
 * @param compared   how many values were compared
 * @param detail     in words, e.g. the largest difference found
 */
public record PropertyCheck(String sourcePath, String targetPath, String label, Outcome outcome, int compared,
                            String detail) {

    public enum Outcome {
        PRESERVED("preserved"), CHANGED("changed"), NOT_CHECKED("not checked");

        private final String text;

        Outcome(String text) {
            this.text = text;
        }

        public String text() {
            return text;
        }
    }

    /**
     * What an element means, from its Semantic Representation Information.
     *
     * @param iri    the Semantic Representation Information
     * @param label  its label, e.g. "Right ascension"
     * @param scale  {@code im:scaleFactor}, or null
     * @param offset {@code im:addOffset}, or null
     * @param units  its unit's label, or null
     */
    public record Meaning(String iri, String label, BigDecimal scale, BigDecimal offset, String units) {

        BigDecimal physical(BigDecimal raw) {
            BigDecimal v = scale == null ? raw : raw.multiply(scale, MathContext.DECIMAL64);
            return offset == null ? v : v.add(offset, MathContext.DECIMAL64);
        }

        public String describe(String path) {
            String name = label == null || label.isBlank() ? path : label;
            return units == null || units.isBlank() ? name : name + " (" + units + ")";
        }
    }

    private static final BigDecimal FLOAT = new BigDecimal("1e-6");
    private static final BigDecimal DOUBLE = new BigDecimal("1e-12");

    /**
     * Checks the values at {@code sourcePath} in {@code source} against those
     * the mapping copied them to in {@code written}.
     *
     * @param tolerance the largest physical difference allowed, or null for what the types carry
     */
    public static PropertyCheck check(String sourcePath, BigDecimal tolerance, TransformationMapping mapping,
                                      StructureNode source, StructureNode written, SchemaElement target,
                                      Map<String, Meaning> sourceMeanings, Map<String, Meaning> targetMeanings) {
        Meaning from = sourceMeanings.getOrDefault(sourcePath, new Meaning(null, null, null, null, null));
        String label = from.describe(sourcePath);
        Optional<TransformationMapping.Copy> copy = mapping.copyOf(sourcePath);
        if (copy.isEmpty()) {
            return new PropertyCheck(sourcePath, null, label, Outcome.CHANGED, 0,
                    "Nothing in the new format is mapped from it, so its values aren't in the new data.");
        }
        String targetPath = copy.get().target();
        Meaning to = targetMeanings.getOrDefault(targetPath, new Meaning(null, null, null, null, null));
        if (from.units() != null && to.units() != null && !from.units().equalsIgnoreCase(to.units())) {
            return new PropertyCheck(sourcePath, targetPath, label, Outcome.NOT_CHECKED, 0,
                    "The units differ (" + from.units() + " in the old format, " + to.units()
                            + " in the new), so the values can't be compared directly.");
        }
        List<StructureNode> before = StructurePaths.find(source, sourcePath);
        List<StructureNode> after = StructurePaths.find(written, targetPath);
        if (before.size() != after.size()) {
            return new PropertyCheck(sourcePath, targetPath, label, Outcome.CHANGED, 0,
                    before.size() + " values in the old data, " + after.size() + " in the new.");
        }
        SchemaElement targetElement = element(target, targetPath);
        BigDecimal largest = BigDecimal.ZERO;
        String rule = null;
        for (int i = 0; i < before.size(); i++) {
            String a = StructurePaths.text(before.get(i)).orElse("");
            String b = StructurePaths.text(after.get(i)).orElse("");
            BigDecimal x = number(a);
            BigDecimal y = number(b);
            if (x == null || y == null) {
                if (!trimmed(a).equals(trimmed(b)) && !(isSpecial(a) && a.strip().equals(b.strip()))) {
                    return differs(sourcePath, targetPath, label, i, a, b, null);
                }
                continue;
            }
            BigDecimal p = from.physical(x);
            BigDecimal q = to.physical(y);
            BigDecimal difference = p.subtract(q).abs();
            BigDecimal allowed;
            if (tolerance != null) {
                allowed = tolerance;
                rule = tolerance.toPlainString();
            } else {
                BigDecimal relative = relative(StructurePaths.floatingType(before.get(i)),
                        targetElement == null ? StructurePaths.floatingType(after.get(i)) : targetElement.valueType());
                allowed = relative.multiply(p.abs().max(q.abs()));
                if (targetElement != null && targetElement.isInteger() && to.scale() != null) {
                    allowed = allowed.max(to.scale().abs().divide(BigDecimal.valueOf(2)));
                }
                if (allowed.signum() > 0) {
                    rule = relative.signum() > 0 ? relative.toString() + " of the value" : "half a step";
                }
            }
            if (difference.compareTo(allowed) > 0) {
                return differs(sourcePath, targetPath, label, i, p.toString(), q.toString(), difference);
            }
            largest = largest.max(difference);
        }
        String detail;
        if (before.isEmpty()) {
            detail = "There are no values, in the old data or the new.";
        } else if (largest.signum() == 0) {
            detail = "All " + before.size() + " values are the same.";
        } else {
            detail = "All " + before.size() + " values agree within " + rule + "; the largest difference is "
                    + largest.round(new MathContext(3)) + ".";
        }
        return new PropertyCheck(sourcePath, targetPath, label, Outcome.PRESERVED, before.size(), detail);
    }

    private static PropertyCheck differs(String sourcePath, String targetPath, String label, int index, String a,
                                         String b, BigDecimal difference) {
        return new PropertyCheck(sourcePath, targetPath, label, Outcome.CHANGED, index + 1,
                "Value " + (index + 1) + " differs: " + a.strip() + " in the old data, " + b.strip() + " in the new"
                        + (difference == null ? "" : " (difference " + difference.round(new MathContext(3)) + ")")
                        + ".");
    }

    private static BigDecimal relative(String sourceType, String targetType) {
        if ("float".equals(sourceType) || "float".equals(targetType)) {
            return FLOAT;
        }
        if ("double".equals(sourceType) || "double".equals(targetType)) {
            return DOUBLE;
        }
        return BigDecimal.ZERO;
    }

    private static SchemaElement element(SchemaElement root, String path) {
        SchemaElement current = root;
        for (String step : StructurePaths.steps(path)) {
            current = current.child(step).orElse(null);
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private static BigDecimal number(String text) {
        try {
            return new BigDecimal(text.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isSpecial(String text) {
        return List.of("NaN", "INF", "-INF").contains(text.strip());
    }

    private static String trimmed(String text) {
        int end = text.length();
        while (end > 0 && (text.charAt(end - 1) == ' ' || text.charAt(end - 1) == '\0')) {
            end--;
        }
        return text.substring(0, end);
    }
}
