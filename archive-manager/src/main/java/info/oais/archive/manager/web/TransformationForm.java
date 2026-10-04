package info.oais.archive.manager.web;

import info.oais.archive.manager.service.transform.PropertyCheck.Meaning;
import info.oais.archive.manager.service.transform.TransformationMapping;
import info.oais.archive.manager.service.transform.TransformationMapping.Constant;
import info.oais.archive.manager.service.transform.TransformationMapping.Copy;
import info.oais.archive.manager.service.transform.TransformationMapping.Count;
import info.oais.archive.manager.service.transform.TransformationMapping.ForEach;
import info.oais.archive.manager.service.transform.TransformationMapping.Rule;
import info.oais.infomodel.structure.dfdl.DfdlSchemaOutline.SchemaElement;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The Transformation page's form: one row per element of the new format, in
 * its order, saying what fills it -- the form of a {@link TransformationMapping}.
 * Fields are named by the element's path: {@code for.P} (the source element
 * it repeats over), {@code kind.P} ({@code copy}, {@code count},
 * {@code constant} or nothing), {@code src.P}, {@code scale.P},
 * {@code offset.P} and {@code const.P}. With nothing yet chosen, elements are
 * matched with source elements of the same name, or meaning.
 */
final class TransformationForm {

    private TransformationForm() {
    }

    /**
     * An element of the old format, to choose from.
     *
     * @param path    its path
     * @param value   whether it holds a value (else other elements)
     * @param repeats whether it may occur more than once
     * @param label   what it means, if known, e.g. "Right ascension (deg)"
     */
    record SourceElement(String path, boolean value, boolean repeats, String label) {

        public String display() {
            return label == null || label.isBlank() || label.equals(path) ? path : path + " — " + label;
        }
    }

    /** One element of the new format, with what fills it. */
    record Row(String path, String name, int depth, boolean value, boolean repeats, boolean optional,
               boolean computed, boolean alternative, String type, String meaning, String forSource, String kind,
               String source, String scale, String offset, String constant) {

        /** Whether it can be repeated over a source element. */
        public boolean repeatable() {
            return repeats || optional || alternative;
        }

        public String indent() {
            return "padding-left:" + (0.4 + 1.2 * depth) + "rem";
        }
    }

    /** The old format's elements, from its element tree. */
    static List<SourceElement> sourceElements(SchemaElement root, Map<String, Meaning> meanings) {
        List<SourceElement> found = new ArrayList<>();
        addSource(root, "", meanings, found);
        return found;
    }

    private static void addSource(SchemaElement e, String prefix, Map<String, Meaning> meanings,
                                  List<SourceElement> into) {
        for (SchemaElement child : e.children()) {
            String path = prefix.isEmpty() ? child.name() : prefix + "." + child.name();
            Meaning meaning = meanings.get(path);
            into.add(new SourceElement(path, child.isValue(), child.repeats(),
                    meaning == null ? null : meaning.describe(path)));
            addSource(child, path, meanings, into);
        }
    }

    /** The rows of the new format, filled from {@code mapping}. */
    static List<Row> rows(SchemaElement target, TransformationMapping mapping, Map<String, Meaning> meanings) {
        List<Row> rows = new ArrayList<>();
        addRows(target, "", 0, mapping, meanings, rows);
        return rows;
    }

    private static void addRows(SchemaElement e, String prefix, int depth, TransformationMapping mapping,
                                Map<String, Meaning> meanings, List<Row> into) {
        for (SchemaElement child : e.children()) {
            String path = prefix.isEmpty() ? child.name() : prefix + "." + child.name();
            Meaning meaning = meanings.get(path);
            String described = meaning != null ? meaning.describe(path) : child.documentation();
            String forSource = mapping.forEach(path).map(ForEach::source).orElse("");
            Optional<Rule> rule = mapping.value(path);
            String kind = rule.map(r -> r instanceof Copy ? "copy" : r instanceof Count ? "count" : "constant")
                    .orElse("");
            String source = rule.map(r -> r instanceof Copy c ? c.source() : r instanceof Count k ? k.source() : "")
                    .orElse("");
            String scale = rule.filter(r -> r instanceof Copy c && c.scale() != null)
                    .map(r -> ((Copy) r).scale().toPlainString()).orElse("");
            String offset = rule.filter(r -> r instanceof Copy c && c.offset() != null)
                    .map(r -> ((Copy) r).offset().toPlainString()).orElse("");
            String constant = rule.filter(r -> r instanceof Constant).map(r -> ((Constant) r).text()).orElse("");
            into.add(new Row(path, child.name(), depth, child.isValue(), child.repeats(), child.minOccurs() == 0,
                    child.computed(), child.alternativeGroup() > 0, child.valueType(), described, forSource, kind,
                    source, scale, offset, constant));
            addRows(child, path, depth + 1, mapping, meanings, into);
        }
    }

    /**
     * The mapping the form's fields give.
     *
     * @throws IllegalArgumentException if a scale or offset isn't a number
     */
    static TransformationMapping fromForm(SchemaElement target, Map<String, String> form) {
        List<Rule> rules = new ArrayList<>();
        for (Row row : rows(target, new TransformationMapping(List.of()), Map.of())) {
            String path = row.path();
            String forSource = field(form, "for." + path);
            if (!forSource.isEmpty()) {
                rules.add(new ForEach(path, forSource));
            }
            if (!row.value()) {
                continue;
            }
            String source = field(form, "src." + path);
            switch (field(form, "kind." + path)) {
                case "copy" -> {
                    if (!source.isEmpty()) {
                        rules.add(new Copy(path, source, number(form, "scale." + path, path),
                                number(form, "offset." + path, path)));
                    }
                }
                case "count" -> {
                    if (!source.isEmpty()) {
                        rules.add(new Count(path, source));
                    }
                }
                case "constant" -> rules.add(new Constant(path, form.getOrDefault("const." + path, "")));
                default -> {
                    // nothing fills it
                }
            }
        }
        return new TransformationMapping(rules);
    }

    private static String field(Map<String, String> form, String name) {
        String v = form.get(name);
        return v == null ? "" : v.strip();
    }

    private static BigDecimal number(Map<String, String> form, String name, String path) {
        String text = field(form, name);
        if (text.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("The " + (name.startsWith("scale") ? "scale" : "offset") + " for "
                    + path + " isn't a number: " + text);
        }
    }

    /**
     * A first mapping to start from: each element of the new format matched
     * with an element of the old of the same name -- or, failing that, the
     * same meaning -- preferring those inside what an enclosing element
     * repeats over.
     */
    static TransformationMapping guess(SchemaElement target, List<SourceElement> sources,
                                       Map<String, Meaning> targetMeanings) {
        List<Rule> rules = new ArrayList<>();
        guess(target, "", null, sources, targetMeanings, rules);
        return new TransformationMapping(rules);
    }

    private static void guess(SchemaElement e, String prefix, String binding, List<SourceElement> sources,
                              Map<String, Meaning> targetMeanings, List<Rule> into) {
        for (SchemaElement child : e.children()) {
            String path = prefix.isEmpty() ? child.name() : prefix + "." + child.name();
            if (child.computed()) {
                continue;
            }
            Meaning meaning = targetMeanings.get(path);
            String label = meaning == null ? null : meaning.label();
            if (child.repeats()) {
                Optional<SourceElement> match = match(child.name(), label, binding, sources,
                        s -> s.repeats() && s.value() == child.isValue());
                if (match.isPresent()) {
                    into.add(new ForEach(path, match.get().path()));
                    if (!child.isValue()) {
                        guess(child, path, match.get().path(), sources, targetMeanings, into);
                    }
                    continue;
                }
            }
            if (child.isValue()) {
                match(child.name(), label, binding, sources, SourceElement::value)
                        .ifPresent(s -> into.add(new Copy(path, s.path(), null, null)));
            } else {
                guess(child, path, binding, sources, targetMeanings, into);
            }
        }
    }

    private static Optional<SourceElement> match(String name, String label, String binding,
                                                 List<SourceElement> sources,
                                                 java.util.function.Predicate<SourceElement> kind) {
        List<SourceElement> candidates = sources.stream().filter(kind).toList();
        for (java.util.function.Predicate<SourceElement> same : List.<java.util.function.Predicate<SourceElement>>of(
                s -> normalise(last(s.path())).equals(normalise(name)),
                s -> label != null && s.label() != null
                        && normalise(s.label()).startsWith(normalise(label)))) {
            List<SourceElement> matching = candidates.stream().filter(same).toList();
            if (binding != null) {
                List<SourceElement> inside = matching.stream().filter(s -> s.path().startsWith(binding + "."))
                        .toList();
                if (inside.size() == 1) {
                    return Optional.of(inside.get(0));
                }
            }
            if (matching.size() == 1) {
                return Optional.of(matching.get(0));
            }
        }
        return Optional.empty();
    }

    private static String last(String path) {
        return path.substring(path.lastIndexOf('.') + 1);
    }

    private static String normalise(String name) {
        return name.replace("_", "").replace(" ", "").toLowerCase(Locale.ROOT);
    }
}
