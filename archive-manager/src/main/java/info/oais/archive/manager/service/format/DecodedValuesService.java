package info.oais.archive.manager.service.format;

import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.StructureNodeKind;
import info.oais.infomodel.structure.description.Semantics;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A Data Object's data values, decoded through its Representation
 * Information and printed a page at a time, as RepInfo Tools prints a
 * sample's: each element's name, value, where it is in the bits, and what it
 * means (from the Semantic Representation Information saved with it: a
 * code's meaning, a scaled value with its units, a fill value).
 *
 * <p>So that a large data file neither swamps the browser nor is decoded
 * again for every page, the data is decoded once and its rows are written to
 * a temporary file, with the position of every {@value #INDEX_EVERY}th row;
 * a page is then read straight from the file. The decoded rows of the last
 * {@value #KEPT} Data Objects are kept, each for {@value #KEPT_MINUTES}
 * minutes, and dropped as soon as anything they were decoded with
 * changes.</p>
 */
@Service
public class DecodedValuesService {

    /** Rows per page when none is asked for, and the most a page can have. */
    public static final int DEFAULT_PAGE_SIZE = 100;
    public static final int MAX_PAGE_SIZE = 1000;
    /** At most this many rows are written, so a huge file can't fill the disk. */
    public static final long MAX_ROWS = 5_000_000;
    static final int INDEX_EVERY = 64;
    static final int KEPT = 8;
    static final int KEPT_MINUTES = 30;

    /**
     * One page of rows.
     *
     * @param page          1 for the first
     * @param totalRows     how many rows the data has (up to {@link #MAX_ROWS})
     * @param truncated     whether the rows stopped at {@link #MAX_ROWS}
     * @param language      the structure description's language they were decoded with
     * @param languages     the languages it could be decoded with, preferred first
     * @param error         why it couldn't be decoded, or null
     */
    public record Page(int page, int pageSize, long totalRows, long totalPages, boolean truncated, String language,
                       List<String> languages, List<SampleDecodeResult.TreeRow> rows, Long trailingBytes,
                       String error) {
    }

    private record Key(String dataObject, String language) {
    }

    /** A Data Object's decoded rows, in a file. */
    private record Decoded(String fingerprint, String language, Path file, long rows, boolean truncated,
                           List<Long> index, Long trailingBytes, long decodedAt) {
    }

    private final DataObjectViewService views;
    private final Path directory;
    private final Map<Key, Decoded> kept = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<Key, Object> locks = new ConcurrentHashMap<>();

    public DecodedValuesService(DataObjectViewService views) throws IOException {
        this.views = views;
        this.directory = Files.createTempDirectory("archive-values-");
    }

    /**
     * Page {@code page} (1 for the first) of {@code dataObject}'s values,
     * decoded with its structure description in {@code language}, or its
     * first usable one when that's null or blank.
     */
    public Page page(String dataObject, String language, int page, int pageSize) {
        int size = Math.max(1, Math.min(pageSize <= 0 ? DEFAULT_PAGE_SIZE : pageSize, MAX_PAGE_SIZE));
        List<String> languages = views.decodingLanguages(dataObject);
        if (languages.isEmpty()) {
            return new Page(1, size, 0, 0, false, null, languages, List.of(), null,
                    "This Data Object has no storage location for its bits, or no structure description the "
                            + "server can decode them with.");
        }
        String chosen = language == null || language.isBlank() ? languages.get(0) : language;
        if (!languages.contains(chosen)) {
            return new Page(1, size, 0, 0, false, chosen, languages, List.of(), null,
                    "It has no " + chosen + " description the server can decode it with.");
        }
        Decoded decoded;
        try {
            decoded = decoded(dataObject, chosen);
        } catch (IOException | RuntimeException e) {
            return new Page(1, size, 0, 0, false, chosen, languages, List.of(), null,
                    e.getMessage() != null ? e.getMessage() : e.toString());
        }
        long pages = Math.max(1, (decoded.rows() + size - 1) / size);
        int p = (int) Math.max(1, Math.min(page, pages));
        long first = (long) (p - 1) * size;
        List<SampleDecodeResult.TreeRow> rows;
        try {
            rows = read(decoded, first, size);
        } catch (IOException e) {
            return new Page(p, size, decoded.rows(), pages, decoded.truncated(), chosen, languages, List.of(),
                    decoded.trailingBytes(), "The decoded values couldn't be read back: " + e.getMessage());
        }
        return new Page(p, size, decoded.rows(), pages, decoded.truncated(), chosen, languages, rows,
                decoded.trailingBytes(), null);
    }

    // ------------------------------------------------------------------ decoding, once

    private Decoded decoded(String dataObject, String language) throws IOException {
        Key key = new Key(dataObject, language);
        String fingerprint = views.fingerprint(dataObject);
        synchronized (locks.computeIfAbsent(key, k -> new Object())) {
            synchronized (kept) {
                Decoded d = kept.get(key);
                if (d != null && d.fingerprint().equals(fingerprint)
                        && System.currentTimeMillis() - d.decodedAt() < KEPT_MINUTES * 60_000L
                        && Files.exists(d.file())) {
                    return d;
                }
                if (d != null) {
                    drop(key);
                }
            }
            Map<String, Semantics> semantics = views.elementSemantics(dataObject);
            Path file = Files.createTempFile(directory, "values-", ".tsv");
            try {
                Decoded d = views.decode(dataObject, language, (data, root) -> write(root, semantics, file,
                        fingerprint, language));
                synchronized (kept) {
                    kept.put(key, d);
                    while (kept.size() > KEPT) {
                        drop(kept.keySet().iterator().next());
                    }
                }
                return d;
            } catch (IOException | RuntimeException e) {
                Files.deleteIfExists(file);
                throw e;
            }
        }
    }

    private void drop(Key key) {
        Decoded d = kept.remove(key);
        if (d != null) {
            try {
                Files.deleteIfExists(d.file());
            } catch (IOException ignored) {
                // A temporary file; it goes when the application stops.
            }
        }
    }

    @PreDestroy
    void deleteAll() throws IOException {
        synchronized (kept) {
            new ArrayList<>(kept.keySet()).forEach(this::drop);
        }
        try (var files = Files.list(directory)) {
            for (Path f : files.toList()) {
                Files.deleteIfExists(f);
            }
        }
        Files.deleteIfExists(directory);
    }

    /** Walks the decoded tree, writing a row per node, with the position of every {@link #INDEX_EVERY}th. */
    private Decoded write(StructureNode root, Map<String, Semantics> semantics, Path file, String fingerprint,
                          String language) throws IOException {
        List<Long> index = new ArrayList<>();
        long[] rows = {0};
        boolean[] truncated = {false};
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
            CountingWriter out = new CountingWriter(channel);
            walk(root, null, "", 0, new Meanings(semantics), out, index, rows, truncated);
            out.flush();
        }
        Long trailing = root.getAttributes().get(StructureNode.TRAILING_BYTES) instanceof Number n ? n.longValue() : null;
        return new Decoded(fingerprint, language, file, rows[0], truncated[0], List.copyOf(index), trailing,
                System.currentTimeMillis());
    }

    /**
     * @param shownName the node's name as listed, with its index among same-named siblings
     * @param path      its structural path (names from below the root, joined by dots, without indexes)
     */
    private void walk(StructureNode node, String shownName, String path, int depth, Meanings semantics,
                      CountingWriter out, List<Long> index, long[] rows, boolean[] truncated) throws IOException {
        if (rows[0] >= MAX_ROWS) {
            truncated[0] = true;
            return;
        }
        if (rows[0] % INDEX_EVERY == 0) {
            out.flush();
            index.add(out.position());
        }
        String name = shownName != null ? shownName : node.getName();
        boolean leaf = node.getKind() == StructureNodeKind.LEAF;
        Object value = leaf ? node.getValue().orElse(null) : null;
        Semantics s = semantics.of(path);
        out.row(depth, name, node.getKind().name(), leaf ? SampleDecodeResult.describe(value) : "",
                SampleDecodeResult.range(node.getSourceRange()), meaning(node, value, s));
        rows[0]++;
        List<StructureNode> children = node.getChildren();
        Map<String, Integer> counts = new HashMap<>();
        if (node.getKind() != StructureNodeKind.ARRAY) {
            for (StructureNode c : children) {
                counts.merge(c.getName(), 1, Integer::sum);
            }
        }
        Map<String, Integer> seen = new HashMap<>();
        for (StructureNode child : children) {
            String childName;
            String childPath;
            if (node.getKind() == StructureNodeKind.ARRAY) {
                // An array's elements share its name and meaning (Kaitai Struct, EAST).
                childName = node.getName() + "[" + (seen.merge("", 1, Integer::sum) - 1) + "]";
                childPath = path;
            } else {
                int i = seen.merge(child.getName(), 1, Integer::sum) - 1;
                childName = counts.get(child.getName()) > 1 ? child.getName() + "[" + i + "]" : child.getName();
                String part = snake(child.getName());
                childPath = path.isEmpty() ? part : path + "." + part;
            }
            walk(child, childName, childPath, depth + 1, semantics, out, index, rows, truncated);
            if (truncated[0]) {
                return;
            }
        }
    }

    /**
     * Which saved element meaning a decoded node's structural path has: the
     * one saved under that path, else the only one whose names all appear in
     * it, in order, ending with its own name. Engines whose trees have levels
     * the element tree doesn't -- EAST's records for sets, choices and
     * optional elements, its names for keywords ({@code body_1}) -- still find
     * their meanings.
     */
    static final class Meanings {
        private final Map<String, Semantics> byPath;
        private final Map<String, java.util.Optional<Semantics>> found = new HashMap<>();

        Meanings(Map<String, Semantics> byPath) {
            this.byPath = byPath;
        }

        Semantics of(String path) {
            return found.computeIfAbsent(path, this::find).orElse(null);
        }

        private java.util.Optional<Semantics> find(String path) {
            if (path.isEmpty()) {
                return java.util.Optional.empty();
            }
            Semantics exact = byPath.get(path);
            if (exact != null) {
                return java.util.Optional.of(exact);
            }
            String[] node = path.split("\\.");
            Semantics match = null;
            for (Map.Entry<String, Semantics> e : byPath.entrySet()) {
                String[] saved = e.getKey().split("\\.");
                if (same(node[node.length - 1], saved[saved.length - 1]) && inOrder(saved, node)) {
                    if (match != null) {
                        return java.util.Optional.empty();
                    }
                    match = e.getValue();
                }
            }
            return java.util.Optional.ofNullable(match);
        }

        private static boolean inOrder(String[] saved, String[] node) {
            int j = 0;
            for (String part : saved) {
                while (j < node.length && !same(node[j], part)) {
                    j++;
                }
                if (j == node.length) {
                    return false;
                }
                j++;
            }
            return true;
        }

        private static boolean same(String decoded, String saved) {
            return decoded.equals(saved)
                    || decoded.equals(info.oais.infomodel.structure.east.EastWriter.eastName(saved)
                            .toLowerCase(Locale.ROOT));
        }
    }

    /** Kaitai Struct's camelCase names as the descriptions' own: {@code tempBody} is {@code temp_body}. */
    private static String snake(String name) {
        return name.replaceAll("(?<=[a-z0-9])(?=[A-Z])", "_").toLowerCase(Locale.ROOT);
    }

    /** What a value means, as RepInfo Tools shows it: its semantic name, a code's meaning, a scaled value, fill. */
    static String meaning(StructureNode node, Object value, Semantics s) {
        if (s == null) {
            Object meaning = node.getAttributes().get("meaning");
            return meaning == null ? "" : String.valueOf(meaning);
        }
        List<String> parts = new ArrayList<>();
        if (s.semanticName() != null && !snake(s.semanticName()).equals(snake(node.getName()))) {
            parts.add(s.semanticName());
        }
        if (value == null) {
            return String.join("; ", parts);
        }
        String raw = SampleDecodeResult.describe(value).strip();
        String code = s.codes().get(raw);
        if (code == null && node.getAttributes().get("meaning") != null) {
            code = String.valueOf(node.getAttributes().get("meaning"));
        }
        if (code != null) {
            parts.add(code);
        }
        BigDecimal number = number(raw);
        if (s.fillValue() != null && (s.fillValue().equals(raw)
                || (number != null && number(s.fillValue()) != null && number.compareTo(number(s.fillValue())) == 0))) {
            parts.add("fill value: no data");
        } else if (number != null && (s.scale() != null || s.offset() != null)) {
            BigDecimal physical = number.multiply(s.scale() == null ? BigDecimal.ONE : s.scale())
                    .add(s.offset() == null ? BigDecimal.ZERO : s.offset());
            parts.add("= " + physical.stripTrailingZeros().toPlainString() + (s.units() != null ? " " + s.units() : ""));
        } else if (number != null && s.units() != null) {
            parts.add(s.units());
        }
        return String.join("; ", parts);
    }

    private static BigDecimal number(String text) {
        try {
            return new BigDecimal(text.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ the file

    private static List<SampleDecodeResult.TreeRow> read(Decoded d, long first, int count) throws IOException {
        List<SampleDecodeResult.TreeRow> rows = new ArrayList<>(count);
        if (first >= d.rows()) {
            return rows;
        }
        int block = (int) (first / INDEX_EVERY);
        try (FileChannel channel = FileChannel.open(d.file(), StandardOpenOption.READ)) {
            channel.position(d.index().get(block));
            BufferedReader in = new BufferedReader(new InputStreamReader(Channels.newInputStream(channel),
                    StandardCharsets.UTF_8));
            for (long skip = first - (long) block * INDEX_EVERY; skip > 0; skip--) {
                in.readLine();
            }
            for (int i = 0; i < count; i++) {
                String line = in.readLine();
                if (line == null) {
                    break;
                }
                rows.add(parse(line));
            }
        }
        return rows;
    }

    private static SampleDecodeResult.TreeRow parse(String line) {
        List<String> fields = new ArrayList<>(6);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\t') {
                fields.add(sb.toString());
                sb.setLength(0);
            } else if (c == '\\' && i + 1 < line.length()) {
                char e = line.charAt(++i);
                sb.append(e == 't' ? '\t' : e == 'n' ? '\n' : e == 'r' ? '\r' : e);
            } else {
                sb.append(c);
            }
        }
        fields.add(sb.toString());
        while (fields.size() < 6) {
            fields.add("");
        }
        return new SampleDecodeResult.TreeRow(Integer.parseInt(fields.get(0)), fields.get(1), fields.get(2),
                fields.get(3), fields.get(4), fields.get(5));
    }

    /** Writes rows, one per line, keeping count of the bytes written so far. */
    private static final class CountingWriter {
        private final FileChannel channel;
        private final BufferedWriter out;
        private long written;
        private long buffered;

        CountingWriter(FileChannel channel) {
            this.channel = channel;
            this.out = new BufferedWriter(new java.io.OutputStreamWriter(Channels.newOutputStream(channel),
                    StandardCharsets.UTF_8), 1 << 16);
        }

        void row(int depth, String name, String kind, String value, String range, String meaning) throws IOException {
            String line = depth + "\t" + escape(name) + "\t" + kind + "\t" + escape(value) + "\t" + escape(range)
                    + "\t" + escape(meaning) + "\n";
            out.write(line);
            buffered += line.getBytes(StandardCharsets.UTF_8).length;
        }

        void flush() throws IOException {
            out.flush();
            written += buffered;
            buffered = 0;
        }

        long position() {
            return written;
        }

        private static String escape(String s) {
            if (s == null) {
                return "";
            }
            return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r");
        }
    }

    /** For tests: whether {@code dataObject}'s values decoded with {@code language} are kept. */
    boolean isKept(String dataObject, String language) {
        synchronized (kept) {
            return kept.containsKey(new Key(dataObject, language));
        }
    }
}
