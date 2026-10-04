package info.oais.archive.manager.service.transform;

import info.oais.archive.manager.service.transform.TransformationMapping.Constant;
import info.oais.archive.manager.service.transform.TransformationMapping.Copy;
import info.oais.archive.manager.service.transform.TransformationMapping.Count;
import info.oais.archive.manager.service.transform.TransformationMapping.ForEach;
import info.oais.archive.manager.service.transform.TransformationMapping.Rule;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.dfdl.DfdlSchemaOutline;
import info.oais.infomodel.structure.dfdl.DfdlSchemaOutline.SchemaElement;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the DFDL infoset of a file in a new format from a decoded file in
 * the old one, following a {@link TransformationMapping}: the new format's
 * elements in its schema's order, each filled by its rule. The infoset is
 * then encoded with the new format's DFDL description.
 *
 * <ul>
 *   <li>An element with a {@code for} rule occurs once for each occurrence of
 *       its source; a repeated value element with a {@code for} and no value
 *       rule takes each occurrence's value.</li>
 *   <li>A repeated value element copying a source that occurs several times
 *       occurs once for each.</li>
 *   <li>An optional element with no rule in it is left out; a required one is
 *       an error, unless its schema computes its value when writing.</li>
 *   <li>Of a choice's alternatives, the first with a rule in it is used.</li>
 * </ul>
 */
public final class InfosetBuilder {

    private final TransformationMapping mapping;
    private final StructureNode source;
    private final Set<String> notes = new LinkedHashSet<>();
    private Document document;

    private InfosetBuilder(TransformationMapping mapping, StructureNode source) {
        this.mapping = mapping;
        this.source = source;
    }

    /**
     * The infoset, and what happened to values on the way: e.g. that some
     * were rounded to fit a whole-number element.
     */
    public record Result(Document infoset, List<String> notes) {
    }

    /**
     * @param target the new format's element tree
     * @param source the old file, decoded
     * @throws TransformationException saying which element couldn't be made, and why
     */
    public static Result build(SchemaElement target, StructureNode source, TransformationMapping mapping) {
        InfosetBuilder builder = new InfosetBuilder(mapping, source);
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            builder.document = factory.newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
        Element root = builder.element(target);
        builder.document.appendChild(root);
        builder.children(target, "", new Context(null, null, null), root);
        return new Result(builder.document, List.copyOf(builder.notes));
    }

    /** Which source occurrence each enclosing {@code for} is at, innermost first. */
    private record Context(Context outer, String sourcePath, StructureNode node) {

        Context bind(String path, StructureNode occurrence) {
            return new Context(this, path, occurrence);
        }
    }

    /** The source elements at {@code path} where {@code context} is. */
    private List<StructureNode> resolve(String path, Context context) {
        for (Context c = context; c != null && c.sourcePath() != null; c = c.outer()) {
            if (path.equals(c.sourcePath())) {
                return List.of(c.node());
            }
            if (path.startsWith(c.sourcePath() + ".")) {
                return StructurePaths.find(c.node(), path.substring(c.sourcePath().length() + 1));
            }
        }
        return StructurePaths.find(source, path);
    }

    private void children(SchemaElement parent, String parentPath, Context context, Element into) {
        List<SchemaElement> elements = parent.children();
        Set<SchemaElement> chosen = chooseAlternatives(elements, parentPath);
        for (SchemaElement child : elements) {
            if (child.computed() || child.alternativeGroup() > 0 && !chosen.contains(child)) {
                continue;
            }
            occurrences(child, pathOf(parentPath, child), context, into);
        }
    }

    /** Of each choice among {@code elements}, the first alternative with a rule in it. */
    private Set<SchemaElement> chooseAlternatives(List<SchemaElement> elements, String parentPath) {
        Set<SchemaElement> chosen = new HashSet<>();
        Set<Integer> groups = new LinkedHashSet<>();
        elements.stream().filter(e -> e.alternativeGroup() > 0).forEach(e -> groups.add(e.alternativeGroup()));
        for (int group : groups) {
            List<SchemaElement> alternatives = elements.stream().filter(e -> e.alternativeGroup() == group).toList();
            alternatives.stream().filter(e -> mapping.mapsWithin(pathOf(parentPath, e))).findFirst().ifPresentOrElse(
                    chosen::add, () -> {
                        if (alternatives.stream().allMatch(e -> e.minOccurs() > 0)) {
                            throw new TransformationException("Map one of " + String.join(", ", alternatives.stream()
                                    .map(e -> pathOf(parentPath, e)).toList()) + ": the new format needs one of them");
                        }
                    });
        }
        return chosen;
    }

    private static String pathOf(String parentPath, SchemaElement e) {
        return parentPath.isEmpty() ? e.name() : parentPath + "." + e.name();
    }

    private void occurrences(SchemaElement e, String path, Context context, Element into) {
        var forEach = mapping.forEach(path);
        if (forEach.isPresent()) {
            ForEach f = forEach.get();
            List<StructureNode> found = resolve(f.source(), context);
            checkCount(e, path, found.size(), f.source());
            for (StructureNode occurrence : found) {
                Context inner = context.bind(f.source(), occurrence);
                Element element = element(e);
                into.appendChild(element);
                if (e.isValue()) {
                    String value = mapping.value(path).isPresent()
                            ? values(e, path, inner).stream().findFirst().orElse(null)
                            : StructurePaths.text(occurrence).map(t -> convert(e, path, t, null, null)).orElse(null);
                    if (value == null) {
                        throw new TransformationException(path + " has no value from " + f.source() + " here");
                    }
                    element.setTextContent(value);
                } else {
                    children(e, path, inner, element);
                }
            }
            return;
        }
        if (e.isValue()) {
            if (mapping.value(path).isEmpty()) {
                if (e.minOccurs() > 0) {
                    throw new TransformationException("Nothing is mapped to " + path + ", which the new format needs");
                }
                return;
            }
            List<String> values = values(e, path, context);
            if (values.size() > 1 && !e.repeats()) {
                throw new TransformationException(path + " occurs once in the new format, but what's mapped to it "
                        + "occurs " + values.size() + " times here: map an element it's inside with \"for\"");
            }
            checkCount(e, path, values.size(), null);
            for (String value : values) {
                Element element = element(e);
                element.setTextContent(value);
                into.appendChild(element);
            }
            return;
        }
        if (!mapping.mapsWithin(path)) {
            if (e.minOccurs() > 0) {
                throw new TransformationException("Nothing is mapped to anything in " + path
                        + ", which the new format needs");
            }
            return;
        }
        Element element = element(e);
        into.appendChild(element);
        children(e, path, context, element);
    }

    private static void checkCount(SchemaElement e, String path, int count, String source) {
        String from = source == null ? "" : " (one for each " + source + ")";
        if (count < e.minOccurs()) {
            throw new TransformationException(path + " must occur at least " + e.minOccurs() + " times in the new "
                    + "format, but would occur " + count + from);
        }
        if (e.maxOccurs() != DfdlSchemaOutline.UNBOUNDED && count > e.maxOccurs()) {
            throw new TransformationException(path + " may occur at most " + e.maxOccurs() + " times in the new "
                    + "format, but would occur " + count + from);
        }
    }

    /** The values {@code path}'s rule gives here: none if its source is absent, several if it repeats. */
    private List<String> values(SchemaElement e, String path, Context context) {
        Rule rule = mapping.value(path).orElseThrow();
        if (rule instanceof Constant c) {
            return List.of(convert(e, path, c.text(), null, null));
        }
        if (rule instanceof Count c) {
            return List.of(convert(e, path, String.valueOf(resolve(c.source(), context).size()), null, null));
        }
        Copy copy = (Copy) rule;
        List<String> values = new ArrayList<>();
        for (StructureNode node : resolve(copy.source(), context)) {
            StructurePaths.text(node).ifPresent(t -> values.add(convert(e, path, t, copy.scale(), copy.offset())));
        }
        return values;
    }

    /** {@code text}, scaled and offset if asked, as the new element's type writes it. */
    private String convert(SchemaElement e, String path, String text, BigDecimal scale, BigDecimal offset) {
        if (!e.isNumeric()) {
            if (scale != null || offset != null) {
                throw new TransformationException(path + " isn't a number, so it can't be scaled or offset");
            }
            return text;
        }
        String t = text.strip();
        if (scale == null && offset == null && !e.isInteger() && List.of("NaN", "INF", "-INF").contains(t)) {
            return t;
        }
        BigDecimal value;
        try {
            value = new BigDecimal(t);
        } catch (NumberFormatException ex) {
            throw new TransformationException(path + " is a number in the new format, but \"" + text
                    + "\" isn't a number");
        }
        if (scale != null) {
            value = value.multiply(scale, MathContext.DECIMAL64);
        }
        if (offset != null) {
            value = value.add(offset, MathContext.DECIMAL64);
        }
        if (e.isInteger()) {
            BigDecimal whole = value.setScale(0, RoundingMode.HALF_EVEN);
            if (whole.compareTo(value) != 0) {
                notes.add(path + ": values were rounded to whole numbers to fit the new format");
            }
            return whole.toPlainString();
        }
        return "decimal".equals(e.valueType()) ? value.toPlainString() : value.toString();
    }

    private Element element(SchemaElement e) {
        return document.createElementNS(e.namespace(), e.name());
    }
}
