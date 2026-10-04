package info.oais.archive.manager.service.transform;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How a Transformation makes the elements of a new format from those of the
 * old: one rule per element of the new format. Elements are named by their
 * path from the top of the file, without the top element itself, e.g.
 * {@code star.ra} -- the same paths as {@code im:structuralPath}. The rules,
 * one per line ({@code #} starts a comment):
 * <pre>
 * for star in stars.entry          one star for each stars.entry
 * star.ra = stars.entry.ra         copy a value
 * star.ra_rad = stars.entry.ra * 0.0174532925 + 0     scale and offset it
 * count = count(stars.entry)       how many there are
 * version = "2"                    a constant
 * </pre>
 * Inside a {@code for}, a source path under the one it repeats over means
 * that occurrence's element; any other means the first in the file. Kept as
 * this text with the Transformation, so it can be read and repeated.
 */
public record TransformationMapping(List<Rule> rules) {

    public TransformationMapping {
        rules = List.copyOf(rules);
    }

    /** One rule: what fills one element of the new format. */
    public sealed interface Rule permits ForEach, Copy, Count, Constant {
        String target();
    }

    /** One occurrence of {@code target} for each occurrence of {@code source}. */
    public record ForEach(String target, String source) implements Rule {
    }

    /** {@code target}'s value is {@code source}'s, times {@code scale} plus {@code offset} if they're given. */
    public record Copy(String target, String source, BigDecimal scale, BigDecimal offset) implements Rule {

        public boolean changesValue() {
            return scale != null && scale.compareTo(BigDecimal.ONE) != 0
                    || offset != null && offset.signum() != 0;
        }
    }

    /** {@code target}'s value is how many {@code source} elements there are. */
    public record Count(String target, String source) implements Rule {
    }

    /** {@code target}'s value is {@code text}. */
    public record Constant(String target, String text) implements Rule {
    }

    private static final String PATH = "[A-Za-z_][\\w-]*(?:\\.[A-Za-z_][\\w-]*)*";
    private static final String NUMBER = "[-+]?(?:\\d+\\.?\\d*|\\.\\d+)(?:[eE][-+]?\\d+)?";
    private static final Pattern FOR = Pattern.compile("for\\s+(" + PATH + ")\\s+in\\s+(" + PATH + ")");
    private static final Pattern COPY = Pattern.compile("(" + PATH + ")\\s*=\\s*(" + PATH + ")"
            + "(?:\\s*\\*\\s*(" + NUMBER + "))?(?:\\s*([-+])\\s*(" + NUMBER + "))?");
    private static final Pattern COUNT = Pattern.compile("(" + PATH + ")\\s*=\\s*count\\(\\s*(" + PATH + ")\\s*\\)");
    private static final Pattern CONSTANT = Pattern.compile("(" + PATH + ")\\s*=\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    /**
     * Reads a mapping written as {@link #text()} writes it.
     *
     * @throws IllegalArgumentException naming the line that isn't a rule, or an element given two rules
     */
    public static TransformationMapping parse(String text) {
        List<Rule> rules = new ArrayList<>();
        String[] lines = text == null ? new String[0] : text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = stripComment(lines[i]).strip();
            if (line.isEmpty()) {
                continue;
            }
            Matcher m;
            Rule rule;
            if ((m = FOR.matcher(line)).matches()) {
                rule = new ForEach(m.group(1), m.group(2));
            } else if ((m = COUNT.matcher(line)).matches()) {
                rule = new Count(m.group(1), m.group(2));
            } else if ((m = CONSTANT.matcher(line)).matches()) {
                rule = new Constant(m.group(1), m.group(2).replaceAll("\\\\(.)", "$1"));
            } else if ((m = COPY.matcher(line)).matches()) {
                BigDecimal offset = m.group(5) == null ? null : new BigDecimal(m.group(5));
                rule = new Copy(m.group(1), m.group(2), m.group(3) == null ? null : new BigDecimal(m.group(3)),
                        offset != null && "-".equals(m.group(4)) ? offset.negate() : offset);
            } else {
                throw new IllegalArgumentException("Line " + (i + 1) + " isn't a mapping rule: " + line);
            }
            for (Rule other : rules) {
                if (other.target().equals(rule.target()) && (other instanceof ForEach) == (rule instanceof ForEach)) {
                    throw new IllegalArgumentException("Line " + (i + 1) + " gives " + rule.target()
                            + " a second rule");
                }
            }
            rules.add(rule);
        }
        return new TransformationMapping(rules);
    }

    /** {@code #} outside a quoted constant starts a comment. */
    private static String stripComment(String line) {
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && quoted) {
                i++;
            } else if (c == '"') {
                quoted = !quoted;
            } else if (c == '#' && !quoted) {
                return line.substring(0, i);
            }
        }
        return line;
    }

    /** The mapping as text, one rule per line. */
    public String text() {
        StringBuilder sb = new StringBuilder();
        for (Rule rule : rules) {
            if (rule instanceof ForEach f) {
                sb.append("for ").append(f.target()).append(" in ").append(f.source());
            } else if (rule instanceof Copy c) {
                sb.append(c.target()).append(" = ").append(c.source());
                if (c.scale() != null) {
                    sb.append(" * ").append(c.scale().toPlainString());
                }
                if (c.offset() != null) {
                    sb.append(c.offset().signum() < 0 ? " - " : " + ").append(c.offset().abs().toPlainString());
                }
            } else if (rule instanceof Count c) {
                sb.append(c.target()).append(" = count(").append(c.source()).append(')');
            } else if (rule instanceof Constant c) {
                sb.append(c.target()).append(" = \"")
                        .append(c.text().replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    public Optional<ForEach> forEach(String target) {
        return rules.stream().filter(r -> r instanceof ForEach && r.target().equals(target)).map(r -> (ForEach) r)
                .findFirst();
    }

    /** The rule giving {@code target} its value, if any. */
    public Optional<Rule> value(String target) {
        return rules.stream().filter(r -> !(r instanceof ForEach) && r.target().equals(target)).findFirst();
    }

    /** Whether any rule is for {@code target} or an element inside it. */
    public boolean mapsWithin(String target) {
        return rules.stream().anyMatch(r -> r.target().equals(target) || r.target().startsWith(target + "."));
    }

    /** The first rule copying {@code source}, if any. */
    public Optional<Copy> copyOf(String source) {
        return rules.stream().filter(r -> r instanceof Copy c && c.source().equals(source)).map(r -> (Copy) r)
                .findFirst();
    }
}
