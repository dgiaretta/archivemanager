package info.oais.archive.manager.web;

import info.oais.infomodel.structure.description.ByteOrder;
import info.oais.infomodel.structure.description.ChoiceDescription;
import info.oais.infomodel.structure.description.DescriptionLanguage;
import info.oais.infomodel.structure.description.DescriptionValidator;
import info.oais.infomodel.structure.description.ElementDescription;
import info.oais.infomodel.structure.description.Expression;
import info.oais.infomodel.structure.description.FieldDescription;
import info.oais.infomodel.structure.description.FormatDescription;
import info.oais.infomodel.structure.description.Occurrence;
import info.oais.infomodel.structure.description.RecordDescription;
import info.oais.infomodel.structure.description.Semantics;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * What RepInfo Tools' structure editor shows for a byte-layout description:
 * the element tree as indented rows, and the form values for the element
 * being edited. Kept out of the controller so the page's wording lives in
 * one place.
 */
final class DescriptionEditorView {

    private DescriptionEditorView() {
    }

    /**
     * One row of the tree.
     *
     * @param kind      "record", "field", "choice" or "branch" (a choice's alternative record)
     * @param container whether elements can be added inside it (a record or branch)
     */
    record Row(String id, int depth, String kind, String name, String summary, String semantics,
               List<String> problems, boolean root, boolean container, boolean first, boolean last) {
    }

    /** @param targets the languages the description is meant for: problems include features they can't express */
    static List<Row> rows(FormatDescription format, Collection<DescriptionLanguage> targets) {
        Map<String, List<String>> problems = DescriptionValidator.validate(format, targets).stream()
                .collect(Collectors.groupingBy(p -> p.elementId() == null ? "" : p.elementId(), LinkedHashMap::new,
                        Collectors.mapping(DescriptionValidator.Problem::message, Collectors.toList())));
        List<Row> rows = new ArrayList<>();
        RecordDescription root = format.root();
        rows.add(new Row(root.id(), 0, "record", root.name(), "the whole file" + textSummary(root),
                semanticsSummary(root.semantics()), problems.getOrDefault(root.id(), List.of()), true, true, true, true));
        addChildren(rows, root, 1, format, problems);
        return rows;
    }

    private static void addChildren(List<Row> rows, RecordDescription record, int depth, FormatDescription format,
                                    Map<String, List<String>> problems) {
        List<ElementDescription> children = record.children();
        for (int i = 0; i < children.size(); i++) {
            ElementDescription e = children.get(i);
            boolean first = i == 0;
            boolean last = i == children.size() - 1;
            List<String> own = problems.getOrDefault(e.id(), List.of());
            if (e instanceof FieldDescription f) {
                rows.add(new Row(f.id(), depth, "field", f.name(), fieldSummary(f, record.isText(), format),
                        semanticsSummary(f.semantics()), own, false, false, first, last));
            } else if (e instanceof RecordDescription r) {
                rows.add(new Row(r.id(), depth, "record", r.name(), "record" + textSummary(r) + recordExtras(r)
                        + occurrenceSummary(r.occurrence()), semanticsSummary(r.semantics()), own, false, true, first, last));
                addChildren(rows, r, depth + 1, format, problems);
            } else if (e instanceof ChoiceDescription c) {
                rows.add(new Row(c.id(), depth, "choice", c.name(), "one of the branches below, chosen by "
                        + c.discriminator().text() + occurrenceSummary(c.occurrence()),
                        semanticsSummary(c.semantics()), own, false, false, first, last));
                List<ChoiceDescription.Branch> branches = c.branches();
                for (int b = 0; b < branches.size(); b++) {
                    ChoiceDescription.Branch branch = branches.get(b);
                    RecordDescription br = branch.record();
                    rows.add(new Row(br.id(), depth + 1, "branch", br.name(), "when " + c.discriminator().text()
                            + " = " + branch.key(), semanticsSummary(br.semantics()),
                            problems.getOrDefault(br.id(), List.of()), false, true, b == 0, b == branches.size() - 1));
                    addChildren(rows, br, depth + 2, format, problems);
                }
            }
        }
    }

    static String fieldSummary(FieldDescription f, boolean inText, FormatDescription format) {
        StringBuilder sb = new StringBuilder(f.type().name());
        if (inText) {
            sb.append(", as text");
            if (f.numberFormat() != null) {
                sb.append(" like ").append(f.numberFormat().pattern());
            }
            if (f.nilValue() != null) {
                sb.append(f.nilValue().isEmpty() ? ", empty means no value" : ", \"" + f.nilValue() + "\" means no value");
            }
        } else if (f.type() == info.oais.infomodel.structure.description.PrimitiveType.BITS && f.length() != null) {
            sb.append(", ").append(f.length().text()).append(" bits");
        } else if (f.length() != null) {
            sb.append(", ").append(f.length() instanceof Expression.IntLiteral lit
                    ? lit.value() + " bytes" : "length " + f.length().text());
        } else if (f.type().fixedWidth() > 1) {
            ByteOrder order = f.byteOrder() == null ? format.defaultByteOrder() : f.byteOrder();
            sb.append(order == ByteOrder.LITTLE_ENDIAN ? ", little-endian" : ", big-endian");
        }
        if (f.offset() != null) {
            sb.append(", at offset ").append(f.offset().text());
        }
        return sb + occurrenceSummary(f.occurrence());
    }

    private static String textSummary(RecordDescription r) {
        return r.isText() ? ", delimited text: fields separated by " + show(r.text().fieldSeparator())
                + ", ended by " + show(r.text().recordTerminator())
                + (r.text().quote() == null ? "" : ", values may be quoted with " + show(r.text().quote())) : "";
    }

    private static String recordExtras(RecordDescription r) {
        StringBuilder sb = new StringBuilder();
        if (r.size() != null) {
            sb.append(r.size() instanceof Expression.IntLiteral lit ? ", " + lit.value() + " bytes"
                    : ", size " + r.size().text());
        }
        if (r.compression() != null) {
            sb.append(", zlib-compressed");
        }
        if (r.offset() != null) {
            sb.append(", at offset ").append(r.offset().text());
        }
        return sb.toString();
    }

    static String occurrenceSummary(Occurrence o) {
        if (o instanceof Occurrence.Optional opt) {
            return ", only if " + opt.condition().text();
        }
        if (o instanceof Occurrence.Repeated r) {
            return ", repeated " + r.count().text() + " times";
        }
        return o instanceof Occurrence.UntilEnd ? ", repeated to the end of the data" : "";
    }

    static String semanticsSummary(Semantics s) {
        List<String> parts = new ArrayList<>();
        if (s.semanticName() != null) {
            parts.add(s.semanticName());
        }
        if (s.units() != null) {
            parts.add("[" + s.units() + "]");
        }
        if (s.definition() != null) {
            parts.add(s.definition().length() > 80 ? s.definition().substring(0, 77) + "..." : s.definition());
        }
        if (!s.codes().isEmpty()) {
            parts.add(s.codes().size() + " coded value" + (s.codes().size() == 1 ? "" : "s"));
        }
        if (s.isScaled()) {
            parts.add("scaled");
        }
        return String.join(" ", parts);
    }

    /** A separator or terminator as a person would name it. */
    static String show(String delimiter) {
        return switch (delimiter) {
            case "\n" -> "a newline";
            case "\t" -> "a tab";
            case "," -> "a comma";
            case ";" -> "a semicolon";
            case "|" -> "a vertical bar";
            case " " -> "a space";
            case "\"" -> "double quotes";
            case "'" -> "single quotes";
            default -> "\"" + delimiter + "\"";
        };
    }

    /**
     * The element being edited, as the edit form's values.
     *
     * @param inText    for a field: whether it's in a delimited-text record
     * @param position  where it's read, for an element at an absolute offset (not {@code offset},
     *                  which is the semantics' additive offset)
     */
    record Form(String id, String kind, boolean root, String name, String type, String length, String byteOrder,
                String occurs, String occursExpr, String discriminator, String branchKey,
                boolean text, String fieldSeparator, String recordTerminator,
                String semanticName, String definition, String units, String unitsUri, String conceptUri,
                String codes, String scale, String offset, String fillValue, String validMin, String validMax,
                boolean inText, String position, boolean nil, String nilValue, String numberPattern,
                String decimalSeparator, String groupingSeparator, String recordSize, boolean zlib, String quote) {
    }

    static Optional<Form> form(FormatDescription format, String id) {
        RecordDescription root = format.root();
        for (ElementDescription e : info.oais.infomodel.structure.description.Descriptions.all(root)) {
            if (!e.id().equals(id)) {
                continue;
            }
            String kind = e instanceof FieldDescription ? "field" : e instanceof ChoiceDescription ? "choice" : "record";
            String branchKey = branchKeyOf(root, id);
            if (branchKey != null) {
                kind = "branch";
            }
            String occurs = "once";
            String occursExpr = "";
            if (e.occurrence() instanceof Occurrence.Optional o) {
                occurs = "optional";
                occursExpr = o.condition().text();
            } else if (e.occurrence() instanceof Occurrence.Repeated r) {
                occurs = "repeated";
                occursExpr = r.count().text();
            } else if (e.occurrence() instanceof Occurrence.UntilEnd) {
                occurs = "until_end";
            }
            Semantics s = e.semantics();
            String codes = s.codes().entrySet().stream().map(c -> c.getKey() + " = " + c.getValue())
                    .collect(Collectors.joining("\n"));
            FieldDescription f = e instanceof FieldDescription fd ? fd : null;
            RecordDescription r = e instanceof RecordDescription rd ? rd : null;
            return Optional.of(new Form(e.id(), kind, e == root, e.name(),
                    f == null ? "" : f.type().name(),
                    f == null || f.length() == null ? "" : f.length().text(),
                    f == null || f.byteOrder() == null ? "" : f.byteOrder().name(),
                    occurs, occursExpr,
                    e instanceof ChoiceDescription c ? c.discriminator().text() : "",
                    branchKey == null ? "" : branchKey,
                    r != null && r.isText(),
                    r != null && r.isText() ? escapeControl(r.text().fieldSeparator()) : ",",
                    r != null && r.isText() ? escapeControl(r.text().recordTerminator()) : "\\n",
                    nz(s.semanticName()), nz(s.definition()), nz(s.units()),
                    s.unitsUri() == null ? "" : s.unitsUri().toString(),
                    s.conceptUri() == null ? "" : s.conceptUri().toString(), codes,
                    s.scale() == null ? "" : s.scale().toPlainString(),
                    s.offset() == null ? "" : s.offset().toPlainString(), nz(s.fillValue()),
                    s.validMin() == null ? "" : s.validMin().toPlainString(),
                    s.validMax() == null ? "" : s.validMax().toPlainString(),
                    f != null && parentIsText(root, f.id()),
                    f != null && f.offset() != null ? f.offset().text() : r != null && r.offset() != null ? r.offset().text() : "",
                    f != null && f.nilValue() != null, f == null ? "" : nz(f.nilValue()),
                    f == null || f.numberFormat() == null ? "" : f.numberFormat().pattern(),
                    f == null || f.numberFormat() == null ? "." : f.numberFormat().decimalSeparator(),
                    f == null || f.numberFormat() == null ? "" : nz(f.numberFormat().groupingSeparator()),
                    r == null || r.size() == null ? "" : r.size().text(),
                    r != null && r.compression() != null,
                    r != null && r.isText() ? nz(r.text().quote()) : ""));
        }
        return Optional.empty();
    }

    private static boolean parentIsText(RecordDescription root, String id) {
        for (ElementDescription e : info.oais.infomodel.structure.description.Descriptions.all(root)) {
            if (e instanceof RecordDescription r && r.isText() && r.children().stream().anyMatch(c -> c.id().equals(id))) {
                return true;
            }
        }
        return false;
    }

    /** The key selecting the branch whose record has this id, or null if it isn't a branch record. */
    static String branchKeyOf(RecordDescription root, String recordId) {
        for (ElementDescription e : info.oais.infomodel.structure.description.Descriptions.all(root)) {
            if (e instanceof ChoiceDescription c) {
                for (ChoiceDescription.Branch b : c.branches()) {
                    if (b.record().id().equals(recordId)) {
                        return b.key();
                    }
                }
            }
        }
        return null;
    }

    /** Newlines and tabs as {@code \n}/{@code \t}, so a delimiter can be shown and typed in a text box. */
    static String escapeControl(String s) {
        return s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    static String unescapeControl(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                sb.append(switch (n) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    default -> n;
                });
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
