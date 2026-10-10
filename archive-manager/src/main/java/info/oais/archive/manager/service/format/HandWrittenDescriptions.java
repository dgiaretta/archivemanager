package info.oais.archive.manager.service.format;

import info.oais.infomodel.structure.description.DescriptionLanguage;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Descriptions written by hand in an engine's own language -- for what the
 * editor's model can't express (checksums, encryption, other compression,
 * multi-line records, ...). Holds worked examples per language, and checks a
 * hand-written description before any engine sees it.
 *
 * <p>The checks keep a description to <em>describing data</em>: a sample
 * test runs it inside this application, and each language has a way to reach
 * further than that, which these refuse:</p>
 * <ul>
 *   <li><strong>DRB</strong>'s query language calls any static Java method
 *       through a {@code java:} namespace, and can read files and URLs with
 *       {@code doc()} and similar functions;</li>
 *   <li><strong>Kaitai Struct</strong>'s {@code process:} can name a custom
 *       Java class, {@code meta/imports} reads other files, and the compiler
 *       copies {@code doc} text into Java comments (so a {@code *}{@code /}
 *       would end one);</li>
 *   <li>XML Schema (DFDL, DRB) can include other schemas by location, and a
 *       DTD can read files through external entities. DFDL schemas may include
 *       Daffodil's own built-in schemas (e.g. {@code DFDLGeneralFormat}, the
 *       layer definitions) and nothing else.</li>
 * </ul>
 * For drb-python, what's written by hand is an <em>add-in</em>: Python code
 * the generated driver calls (see {@code drb-python/interpreter.py}), rather
 * than a replacement for the driver. Being code, nothing here can make it
 * safe; {@code DrbPythonSampleRunner} parses it without running it, and runs
 * it only if the server allows it.
 */
public final class HandWrittenDescriptions {

    private static final int MAX_LENGTH = 1_000_000;
    private static final String XSD = XMLConstants.W3C_XML_SCHEMA_NS_URI;
    private static final Pattern KAITAI_ID = Pattern.compile("[a-z][a-z0-9_]*");
    private static final Pattern BUILT_IN_PROCESS = Pattern.compile("(zlib|(xor|rol|ror)\\(.*\\))");
    private static final Pattern FILE_FUNCTION = Pattern.compile(
            "(^|[^a-z0-9_.-])(fn:)?(doc|doc-available|collection|uri-collection|unparsed-text|unparsed-text-lines"
                    + "|unparsed-text-available|environment-variable|available-environment-variables)\\(");

    private HandWrittenDescriptions() {
    }

    /** A worked example, shown in the hand-writing editor and run by the tests. */
    public record Example(String id, DescriptionLanguage language, String title, String explanation, String file) {
        public String text() {
            try (InputStream in = HandWrittenDescriptions.class.getResourceAsStream("/repinfo-tools/examples/" + file)) {
                if (in == null) {
                    throw new IllegalStateException("Missing example " + file);
                }
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private static final List<Example> EXAMPLES = List.of(
            new Example("kaitai-encrypted-and-checked", DescriptionLanguage.KAITAI, "Encrypted body and header checks",
                    "A fixed magic number (contents), a range check (valid), and a body decrypted with a one-byte "
                            + "XOR key (process: xor). Kaitai also has rol/ror rotations and zlib; it can't compute a "
                            + "CRC without a custom Java routine, which isn't allowed here.",
                    "kaitai-encrypted-and-checked.ksy"),
            new Example("kaitai-fits", DescriptionLanguage.KAITAI, "Any FITS file (FITS Standard 4.0)",
                    "Every HDU of any FITS file, as the DFDL example reads them: the header's keyword records to "
                            + "END, filled to a 2880-byte block, and the data, sized by the mandatory keywords: arrays "
                            + "read as BITPIX's type, table rows as bytes or text. A right-justified integer value is "
                            + "worked out from its digits, since Kaitai's to_i doesn't take the spaces before it.",
                    "kaitai-fits.ksy"),
            new Example("kaitai-stanzas", DescriptionLanguage.KAITAI, "Records of several lines",
                    "Records separated by a blank line: each record's lines repeat until an empty one.",
                    "kaitai-stanzas.ksy"),
            new Example("dfdl-checksum", DescriptionLanguage.DFDL, "Header checks and a header checksum",
                    "Assertions check a magic string, a range, and that a checksum byte is the sum of three "
                            + "header fields, modulo 256. DFDL expressions have no loops (Daffodil doesn't support "
                            + "sum()), so a checksum over a variable amount of data, or a CRC, needs a custom "
                            + "Daffodil layer written in Java.",
                    "dfdl-checksum.dfdl.xsd"),
            new Example("dfdl-gzip", DescriptionLanguage.DFDL, "gzip-compressed section",
                    "Daffodil's built-in layers: fixedLength bounds a section, gzip decompresses it. Daffodil also "
                            + "has base64, byte-swap and line-folding layers; encryption needs a custom layer.",
                    "dfdl-gzip.dfdl.xsd"),
            new Example("dfdl-fits", DescriptionLanguage.DFDL, "Any FITS file (FITS Standard 4.0)",
                    "Every HDU of any FITS file: the header's keyword records to END, filled to a 2880-byte block, "
                            + "and the data, sized by the mandatory keywords (BITPIX, NAXISn, PCOUNT, GCOUNT) as the "
                            + "Standard's Eqs. 1 and 2 say: arrays read as BITPIX's type, table rows as bytes or text. "
                            + "A binary table's columns and random groups need keywords that can be anywhere in the "
                            + "header, which DFDL can't look up by name, so they aren't read.",
                    "dfdl-fits.dfdl.xsd"),
            new Example("dfdl-stanzas", DescriptionLanguage.DFDL, "Records of several lines",
                    "Records separated by a blank line: each line ends with a newline, and a record's lines "
                            + "stop at an empty one.",
                    "dfdl-stanzas.dfdl.xsd"),
            new Example("drb-multiline", DescriptionLanguage.DRB, "Records of several lines",
                    "Records of two lines each, every field ended by a newline. DRB's SDF schemas have no "
                            + "checksums, encryption or compression.",
                    "drb-multiline.drb.xsd"),
            new Example("drb-python-crc32", DescriptionLanguage.DRB_PYTHON, "CRC-32 check",
                    "The file's last four bytes are a CRC-32 of the rest (describe them as the last field). "
                            + "check() compares them, and metadata() adds the CRC to the metadata add-on. Any "
                            + "checksum Python can compute works the same way (hashlib has MD5, SHA-2, ...).",
                    "drb-python-crc32.py"),
            new Example("drb-python-decompress", DescriptionLanguage.DRB_PYTHON, "xz and bzip2 compression",
                    "prepare() decompresses the whole file before the element tree is read, recognising xz "
                            + "(LZMA) and bzip2 by their magic numbers.",
                    "drb-python-decompress.py"),
            new Example("drb-python-aes", DescriptionLanguage.DRB_PYTHON, "AES decryption",
                    "prepare() decrypts the file with AES-256 in CTR mode, the key coming from the server's "
                            + "environment, and restore() encrypts it again when it's written back. Needs the "
                            + "cryptography package next to drb-python.",
                    "drb-python-aes.py"),
            new Example("east-markers", DescriptionLanguage.EAST, "Repetition ended by a marker",
                    "Readings repeated until the string END, then names of characters each ended by a carriage "
                            + "return: what the element tree can't express, but the EAST interpreter reads.",
                    "east-markers.east"),
            new Example("east-vax", DescriptionLanguage.EAST, "VAX reals and ones' complement",
                    "Reals in DEC VAX F_Floating (the convention FCSTC001 of CCSDS 646.0-G-1) and integers in "
                            + "ones' complement, as written on a VAX: little-endian, bit fields least significant "
                            + "bit first. The EAST interpreter reads every convention of CCSDS 646.0-G-1.",
                    "east-vax.east"));

    /** A new drb-python add-in: every hook, documented, none doing anything yet. */
    private static final String DRB_PYTHON_SKELETON = """
            \"\"\"
            Add-in for the generated drb-python driver. Every function is optional;
            delete the ones you don't need.
            \"\"\"


            def prepare(data):
                \"\"\"The file's bytes, before the element tree reads them: decrypt or
                decompress them here. Returns the bytes to read.\"\"\"
                return data


            def check(root):
                \"\"\"After decoding: checksums, CRCs, cross-checks. root.original_bytes
                are the file's bytes, root.decoded_bytes what prepare() returned, and
                root's children the decoded elements (child.name, child.value).
                Returns a list of problems; empty if there are none.\"\"\"
                return []


            def metadata(root):
                \"\"\"More metadata, merged into the metadata add-on's result.\"\"\"
                return {}


            def restore(data):
                \"\"\"When writing the file back: undo prepare() (e.g. encrypt again), so the
                written file matches the original. Returns the bytes to write.\"\"\"
                return data
            """;

    public static List<Example> examples(DescriptionLanguage language) {
        return EXAMPLES.stream().filter(e -> e.language() == language).toList();
    }

    public static List<Example> examples() {
        return EXAMPLES;
    }

    /** Whether what's written by hand for this language is an add-in to the generated driver (drb-python). */
    public static boolean isAddIn(DescriptionLanguage language) {
        return language == DescriptionLanguage.DRB_PYTHON;
    }

    /**
     * @return what's wrong with the text, in plain words; empty if it can be used. For a drb-python add-in
     *         only its size: whether it's valid Python is checked by {@code DrbPythonSampleRunner#checkAddIn}.
     */
    public static List<String> check(DescriptionLanguage language, String text) {
        List<String> problems = new ArrayList<>();
        if (text == null || text.isBlank()) {
            problems.add("The description is empty.");
            return problems;
        }
        if (text.length() > MAX_LENGTH) {
            problems.add("The description is longer than " + MAX_LENGTH + " characters.");
            return problems;
        }
        if (language == DescriptionLanguage.KAITAI) {
            checkKaitai(text, problems);
        } else if (language == DescriptionLanguage.EAST) {
            try {
                // EAST describes data and nothing else: whatever the interpreter accepts can be used.
                new info.oais.infomodel.structure.east.EastStructureRepInfo(
                        info.oais.infomodel.structure.east.EastFormatSpecification.ofText(text));
            } catch (info.oais.infomodel.structure.east.EastException e) {
                problems.add(e.getMessage());
            }
        } else if (language != DescriptionLanguage.DRB_PYTHON) {
            checkXml(language, text, problems);
        }
        return problems;
    }

    /** The {@code meta/id} of a {@code .ksy}, which names its class; null if there isn't a valid one. */
    public static String kaitaiId(String ksy) {
        try {
            Object doc = yaml().load(ksy);
            if (doc instanceof Map<?, ?> top && top.get("meta") instanceof Map<?, ?> meta
                    && meta.get("id") instanceof String id && KAITAI_ID.matcher(id).matches()) {
                return id;
            }
        } catch (RuntimeException e) {
            // Not valid YAML: no id.
        }
        return null;
    }

    private static void checkKaitai(String text, List<String> problems) {
        Object doc;
        try {
            doc = yaml().load(text);
        } catch (RuntimeException e) {
            problems.add("This isn't valid YAML: " + e.getMessage());
            return;
        }
        if (!(doc instanceof Map<?, ?> top) || !(top.get("meta") instanceof Map<?, ?> meta)) {
            problems.add("A Kaitai Struct description needs a 'meta' section with an 'id'.");
            return;
        }
        if (kaitaiId(text) == null) {
            problems.add("'meta/id' must be lower-case letters, digits and underscores, starting with a letter.");
        }
        if (meta.containsKey("imports")) {
            problems.add("'meta/imports' reads other files, which isn't allowed here; put everything in one description.");
        }
        if (Boolean.TRUE.equals(meta.get("ks-opaque-types"))) {
            problems.add("'ks-opaque-types' lets a description use Java classes directly, which isn't allowed here.");
        }
        walkYaml(doc, problems);
    }

    private static void walkYaml(Object node, List<String> problems) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                checkYamlString(entry.getKey(), problems);
                if ("process".equals(entry.getKey()) && entry.getValue() instanceof String process
                        && !BUILT_IN_PROCESS.matcher(process.strip()).matches()) {
                    problems.add("'process: " + process + "' names a custom routine, which runs Java code; only the "
                            + "built-in zlib, xor(...), rol(...) and ror(...) are allowed here.");
                }
                walkYaml(entry.getValue(), problems);
            }
        } else if (node instanceof List<?> list) {
            list.forEach(item -> walkYaml(item, problems));
        } else {
            checkYamlString(node, problems);
        }
    }

    private static void checkYamlString(Object value, List<String> problems) {
        if (value instanceof String s && s.contains("*/")) {
            String problem = "'*/' can't appear in a Kaitai Struct description here (the compiler copies text into "
                    + "Java comments, which it would end): write '* /'.";
            if (!problems.contains(problem)) {
                problems.add(problem);
            }
        }
    }

    private static Yaml yaml() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(50);
        return new Yaml(new SafeConstructor(options));
    }

    private static void checkXml(DescriptionLanguage language, String text, List<String> problems) {
        Document doc;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(null);
            doc = builder.parse(new InputSource(new StringReader(text)));
        } catch (Exception e) {
            problems.add("This isn't well-formed XML (a DOCTYPE isn't allowed either): " + e.getMessage());
            return;
        }
        Element root = doc.getDocumentElement();
        if (!XSD.equals(root.getNamespaceURI()) || !"schema".equals(root.getLocalName())) {
            problems.add("A " + language.label() + " description is an XML Schema: its root element must be xs:schema.");
        }
        walkXml(language, root, problems);
    }

    private static void walkXml(DescriptionLanguage language, Node node, List<String> problems) {
        if (node instanceof Element e) {
            String name = e.getLocalName();
            if (XSD.equals(e.getNamespaceURI())
                    && ("include".equals(name) || "import".equals(name) || "redefine".equals(name) || "override".equals(name))
                    && e.hasAttribute("schemaLocation")) {
                String location = e.getAttribute("schemaLocation").strip();
                boolean builtIn = language == DescriptionLanguage.DFDL && location.startsWith("/org/apache/daffodil/")
                        && !location.contains("..");
                if (!builtIn) {
                    problems.add("xs:" + name + " of '" + location + "' isn't allowed here"
                            + (language == DescriptionLanguage.DFDL
                            ? ": only Daffodil's built-in schemas (/org/apache/daffodil/...) can be included."
                            : "; put everything in one schema."));
                }
            }
            NamedNodeMap attributes = e.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                Attr a = (Attr) attributes.item(i);
                if (language == DescriptionLanguage.DRB) {
                    checkDrbText(a.getValue(), problems);
                }
            }
        } else if (node.getNodeType() == Node.TEXT_NODE || node.getNodeType() == Node.CDATA_SECTION_NODE) {
            if (language == DescriptionLanguage.DRB) {
                checkDrbText(node.getNodeValue(), problems);
            }
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            walkXml(language, child, problems);
        }
    }

    /** DRB queries: no Java calls, no reading files or URLs. */
    private static void checkDrbText(String value, List<String> problems) {
        String normalised = decodeCharacterReferences(value).replaceAll("\\s+", "").toLowerCase();
        if (normalised.contains("java:")) {
            add(problems, "DRB queries can't call Java ('java:' namespaces) here.");
        }
        if (FILE_FUNCTION.matcher(normalised).find()) {
            add(problems, "DRB queries can't read files, URLs or the environment (doc(), collection(), "
                    + "unparsed-text(), ...) here.");
        }
    }

    /** XQuery string literals may spell characters as references, e.g. {@code &#106;} for j. */
    private static String decodeCharacterReferences(String s) {
        String decoded = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'");
        Matcher m = Pattern.compile("&#(x[0-9a-fA-F]+|[0-9]+);").matcher(decoded);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String ref = m.group(1);
            int code;
            try {
                code = ref.startsWith("x") ? Integer.parseInt(ref.substring(1), 16) : Integer.parseInt(ref);
            } catch (NumberFormatException e) {
                code = '?';
            }
            if (code < 0 || code > Character.MAX_CODE_POINT) {
                code = '?';
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(new String(Character.toChars(code))));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static void add(List<String> problems, String problem) {
        if (!problems.contains(problem)) {
            problems.add(problem);
        }
    }

    /** A starting point for a new description in {@code language}, when there's nothing generated yet. */
    public static String skeleton(DescriptionLanguage language) {
        if (language == DescriptionLanguage.DRB_PYTHON) {
            return DRB_PYTHON_SKELETON;
        }
        List<Example> examples = examples(language);
        return examples.isEmpty() ? "" : examples.get(0).text();
    }
}
