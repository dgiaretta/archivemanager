package info.oais.archive.manager;

import info.oais.archive.manager.service.format.DfdlGenerator;
import info.oais.archive.manager.service.format.DrbGenerator;
import info.oais.archive.manager.service.format.DrbPythonSampleRunner;
import info.oais.archive.manager.service.format.FormatTemplates;
import info.oais.archive.manager.service.format.KaitaiGenerator;
import info.oais.archive.manager.service.format.SampleDecodeResult;
import info.oais.infomodel.implementation.DigitalObjectRefImpl;
import info.oais.infomodel.structure.DefaultStructureNode;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.StructureNodeKind;
import info.oais.infomodel.structure.description.ByteOrder;
import info.oais.infomodel.structure.description.ChoiceDescription;
import info.oais.infomodel.structure.description.DescriptionValidator;
import info.oais.infomodel.structure.description.ElementDescription;
import info.oais.infomodel.structure.description.Expression;
import info.oais.infomodel.structure.description.FieldDescription;
import info.oais.infomodel.structure.description.FormatDescription;
import info.oais.infomodel.structure.description.Occurrence;
import info.oais.infomodel.structure.description.PrimitiveType;
import info.oais.infomodel.structure.description.RecordDescription;
import info.oais.infomodel.structure.description.Semantics;
import info.oais.infomodel.structure.description.StructureAligner;
import info.oais.infomodel.structure.dfdl.DfdlFormatSpecification;
import info.oais.infomodel.structure.dfdl.DfdlStructureRepInfo;
import info.oais.infomodel.structure.drb.DrbFormatSpecification;
import info.oais.infomodel.structure.drb.DrbStructureRepInfo;
import info.oais.infomodel.structure.kaitai.KaitaiFormatSpecification;
import info.oais.infomodel.structure.kaitai.KaitaiStructureRepInfo;
import io.kaitai.struct.KaitaiStruct;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.tools.ToolProvider;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The consistency check across engines: each reference format is generated
 * for every engine, the same sample bytes are decoded by each, and the
 * results - lined up against the description by {@link StructureAligner} -
 * must be identical. Daffodil (DFDL) and DRB always run; drb-python runs
 * when a Python with drb is available ({@code DRB_PYTHON}), and Kaitai Struct
 * when its compiler is ({@code KAITAI_COMPILER}, or on the usual install
 * path / PATH).
 */
class GeneratedDescriptionsMatrixTest {

    private static final DfdlGenerator DFDL = new DfdlGenerator();
    private static final DrbGenerator DRB = new DrbGenerator();
    private static final KaitaiGenerator KAITAI = new KaitaiGenerator();

    @TempDir
    Path tmp;

    /**
     * @param limitations engines known to decode this case differently, with why - asserted
     *                    to still differ, so a fix in the engine shows up here
     */
    record Case(String title, FormatDescription format, byte[] sample, List<String> expected,
                Map<String, String> limitations) {
        Case(String title, FormatDescription format, byte[] sample, List<String> expected) {
            this(title, format, sample, expected, Map.of());
        }

        @Override
        public String toString() {
            return title;
        }
    }

    static Stream<Case> cases() {
        return Stream.of(
                new Case("packet: counts, lengths, arithmetic, choice, optional, until end", packet(), packetBytes(),
                        List.of("packet.version = 3", "packet.n = 2", "packet.name_len = 2", "packet.name = ab",
                                "packet.record[0].kind = 1", "packet.record[0].body -> temp_body",
                                "packet.record[0].body.temp_body.temp = 21.5", "packet.record[0].flags = 1",
                                "packet.record[0].extra = 300", "packet.record[1].kind = 2",
                                "packet.record[1].body -> label_body", "packet.record[1].body.label_body.label_len = 2",
                                "packet.record[1].body.label_body.label = hi", "packet.record[1].flags = 0",
                                "packet.record[1].extra = <absent>", "packet.pad = 0xaabbcc",
                                "packet.trailer[0] = 7", "packet.trailer[1] = 8", "packet.trailer[2] = 9")),
                new Case("CSV rows", csv(), "42,-7,hi\n7,13,demo\n".getBytes(StandardCharsets.US_ASCII), csvLines()),
                new Case("CSV rows, no final newline", csv(), "42,-7,hi\n7,13,demo".getBytes(StandardCharsets.US_ASCII),
                        csvLines(), Map.of("DRB (Java)", "DRB's sdf:delimiter is a single character with no "
                        + "end-of-data alternative, so a last line without its newline is dropped")),
                new Case("every field type, little-endian", allTypes(), allTypesBytes(),
                        List.of("all_types.int8 = -5", "all_types.uint8 = 250", "all_types.int16 = -300",
                                "all_types.uint16 = 65000", "all_types.int32 = -70000", "all_types.uint32 = 4000000000",
                                "all_types.int64 = -5000000000", "all_types.uint64 = 18446744073709551615",
                                "all_types.float32 = 1.5", "all_types.float64 = -2.25", "all_types.label = abcd",
                                "all_types.raw = 0xdead")),
                new Case("FITS template", FormatTemplates.fits().toFormatDescription(), fitsBytes(), null),
                new Case("telemetry template", FormatTemplates.telemetry().toFormatDescription(), telemetryBytes(),
                        telemetryLines()),
                new Case("CSV template", FormatTemplates.csv().toFormatDescription(),
                        "AB12,32,-45\nXY9,33,215\n".getBytes(StandardCharsets.US_ASCII),
                        List.of("weather_station_readings_csv_example.reading[0].station = AB12",
                                "weather_station_readings_csv_example.reading[0].day = 32",
                                "weather_station_readings_csv_example.reading[0].temperature = -45",
                                "weather_station_readings_csv_example.reading[1].station = XY9",
                                "weather_station_readings_csv_example.reading[1].day = 33",
                                "weather_station_readings_csv_example.reading[1].temperature = 215")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void everyEngineDecodesTheSameDataTheSameWay(Case c) throws Exception {
        assertThat(DescriptionValidator.validate(c.format())).as("the description is valid").isEmpty();

        Map<String, List<String>> results = new LinkedHashMap<>();
        results.put("DFDL (Daffodil)", align(c, decodeDfdl(c)));
        results.put("DRB (Java)", align(c, decodeDrb(c)));
        StructureNode python = decodeDrbPython(c);
        if (python != null) {
            results.put("drb-python", align(c, python));
        }
        StructureNode kaitai = decodeKaitai(c);
        if (kaitai != null) {
            results.put("Kaitai Struct", align(c, kaitai));
        }

        List<String> expected = c.expected() != null ? c.expected() : results.get("DFDL (Daffodil)");
        results.forEach((engine, lines) -> {
            if (c.limitations().containsKey(engine)) {
                assertThat(lines).as(engine + " (known limitation: " + c.limitations().get(engine) + ")")
                        .isNotEqualTo(expected);
            } else {
                assertThat(lines).as(engine).containsExactlyElementsOf(expected);
            }
        });
    }

    private static List<String> align(Case c, StructureNode decoded) {
        return StructureAligner.align(c.format(), decoded).canonicalLines().stream()
                // Fixed-width text keeps its padding in every engine; compare without it.
                .map(line -> line.replaceAll("\\s+$", "")).toList();
    }

    private StructureNode decodeDfdl(Case c) throws Exception {
        Path schema = Files.writeString(tmp.resolve("format.dfdl.xsd"), DFDL.generate(c.format()));
        return new DfdlStructureRepInfo(new DfdlFormatSpecification(schema.toUri())).apply(sample(c));
    }

    private StructureNode decodeDrb(Case c) throws Exception {
        Path schema = Files.writeString(tmp.resolve("format.drb.xsd"), DRB.generateSdfSchema(c.format()));
        return new DrbStructureRepInfo(new DrbFormatSpecification(schema.toUri())).apply(sample(c));
    }

    private static StructureNode decodeDrbPython(Case c) {
        String configured = System.getenv("DRB_PYTHON");
        DrbPythonSampleRunner runner = new DrbPythonSampleRunner(configured == null ? "" : configured);
        if (runner.drbVersion().isEmpty()) {
            return null;
        }
        SampleDecodeResult result = runner.run(DRB.pythonModule(c.format(), "MatrixFactory"), "MatrixFactory", c.sample());
        assertThat(result.error()).as("drb-python").isNull();
        return fromRows(result.rows());
    }

    /** Rebuilds a node tree from drb-python's flattened rows (depth-first, with depths). */
    private static StructureNode fromRows(List<SampleDecodeResult.TreeRow> rows) {
        record Pending(SampleDecodeResult.TreeRow row, List<StructureNode> children) {
        }
        Deque<Pending> stack = new ArrayDeque<>();
        StructureNode[] root = new StructureNode[1];
        java.util.function.Consumer<Pending> close = p -> {
            StructureNode node = p.row().kind().equals("LEAF")
                    ? DefaultStructureNode.leaf(p.row().name(), p.row().value())
                    : DefaultStructureNode.builder(p.row().name(), StructureNodeKind.COMPOSITE).addChildren(p.children()).build();
            if (stack.isEmpty()) {
                root[0] = node;
            } else {
                stack.peek().children().add(node);
            }
        };
        for (SampleDecodeResult.TreeRow row : rows) {
            while (stack.size() > row.depth()) {
                close.accept(stack.pop());
            }
            stack.push(new Pending(row, new ArrayList<>()));
        }
        while (!stack.isEmpty()) {
            close.accept(stack.pop());
        }
        return root[0];
    }

    private StructureNode decodeKaitai(Case c) throws Exception {
        List<String> compiler = kaitaiCompiler();
        if (compiler == null) {
            return null;
        }
        Path ksy = Files.writeString(tmp.resolve(c.format().root().name() + ".ksy"), KAITAI.generate(c.format()));
        Path src = Files.createDirectories(tmp.resolve("kaitai-src"));
        List<String> command = new ArrayList<>(compiler);
        command.addAll(List.of("-t", "java", "--java-package", "matrix", "--outdir", src.toString(), ksy.toString()));
        Process ksc = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(ksc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(ksc.waitFor(120, TimeUnit.SECONDS)).isTrue();
        assertThat(ksc.exitValue()).as("kaitai-struct-compiler: " + output).isZero();

        Path classes = Files.createDirectories(tmp.resolve("kaitai-classes"));
        List<String> sources;
        try (Stream<Path> files = Files.walk(src)) {
            sources = files.filter(p -> p.toString().endsWith(".java")).map(Path::toString).toList();
        }
        List<String> javac = new ArrayList<>(List.of("-nowarn", "-d", classes.toString(),
                "-cp", System.getProperty("java.class.path")));
        javac.addAll(sources);
        ByteArrayOutputStream javacErrors = new ByteArrayOutputStream();
        int status = ToolProvider.getSystemJavaCompiler().run(null, null, javacErrors, javac.toArray(String[]::new));
        assertThat(status).as("javac: " + javacErrors).isZero();

        String className = "matrix." + pascal(c.format().root().name());
        try (URLClassLoader loader = new URLClassLoader(new URL[] {classes.toUri().toURL()}, getClass().getClassLoader())) {
            Class<? extends KaitaiStruct> type = loader.loadClass(className).asSubclass(KaitaiStruct.class);
            return new KaitaiStructureRepInfo(new KaitaiFormatSpecification(type)).apply(sample(c));
        }
    }

    /** The Kaitai Struct compiler command, or null when it isn't installed. */
    private static List<String> kaitaiCompiler() {
        String configured = System.getenv("KAITAI_COMPILER");
        List<Path> candidates = new ArrayList<>();
        if (configured != null && !configured.isBlank()) {
            candidates.add(Path.of(configured));
        }
        candidates.add(Path.of("C:\\Program Files (x86)\\kaitai-struct-compiler\\bin\\kaitai-struct-compiler.bat"));
        candidates.add(Path.of("/usr/bin/kaitai-struct-compiler"));
        candidates.add(Path.of("/usr/local/bin/kaitai-struct-compiler"));
        for (Path p : candidates) {
            if (Files.isRegularFile(p)) {
                return p.toString().endsWith(".bat") ? List.of("cmd", "/c", p.toString()) : List.of(p.toString());
            }
        }
        return null;
    }

    private static DigitalObjectRefImpl sample(Case c) {
        return new DigitalObjectRefImpl(new ByteArrayInputStream(c.sample()));
    }

    private static String pascal(String snake) {
        StringBuilder sb = new StringBuilder();
        for (String part : snake.split("_")) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
            }
        }
        return sb.toString();
    }

    // ---- reference formats and samples ----

    static FormatDescription packet() {
        RecordDescription temp = RecordDescription.of("temp_body", List.of(
                new FieldDescription("temp", "temp", PrimitiveType.FLOAT32, null, ByteOrder.LITTLE_ENDIAN,
                        Occurrence.ONCE, Semantics.of("Temperature", "Sensor temperature", "degC"))));
        RecordDescription label = RecordDescription.of("label_body", List.of(
                field("label_len", PrimitiveType.UINT8, null),
                field("label", PrimitiveType.STRING, Expression.parse("label_len"))));
        ChoiceDescription body = new ChoiceDescription("body", "body", Expression.parse("kind"),
                List.of(new ChoiceDescription.Branch("1", temp), new ChoiceDescription.Branch("2", label)),
                Occurrence.ONCE, Semantics.NONE);
        RecordDescription record = new RecordDescription("record", "record", List.of(
                field("kind", PrimitiveType.UINT8, null), body, field("flags", PrimitiveType.UINT8, null),
                field("extra", PrimitiveType.UINT16, null).withOccurrence(new Occurrence.Optional(Expression.parse("flags = 1")))),
                null, new Occurrence.Repeated(Expression.parse("n")), Semantics.NONE);
        RecordDescription root = new RecordDescription("packet", "packet", List.of(
                field("version", PrimitiveType.UINT8, null), field("n", PrimitiveType.UINT16, null),
                field("name_len", PrimitiveType.UINT8, null), field("name", PrimitiveType.STRING, Expression.parse("name_len")),
                record, field("pad", PrimitiveType.BYTES, Expression.parse("n * 2 - 1")),
                field("trailer", PrimitiveType.UINT8, null).withOccurrence(new Occurrence.UntilEnd())),
                null, Occurrence.ONCE, Semantics.NONE);
        return new FormatDescription("packet", "", ByteOrder.BIG_ENDIAN, List.of("pkt"), root);
    }

    static byte[] packetBytes() {
        ByteBuffer b = ByteBuffer.allocate(25);
        b.put((byte) 3).putShort((short) 2).put((byte) 2).put("ab".getBytes(StandardCharsets.US_ASCII));
        b.put((byte) 1).order(java.nio.ByteOrder.LITTLE_ENDIAN).putFloat(21.5f).order(java.nio.ByteOrder.BIG_ENDIAN)
                .put((byte) 1).putShort((short) 300);
        b.put((byte) 2).put((byte) 2).put("hi".getBytes(StandardCharsets.US_ASCII)).put((byte) 0);
        b.put((byte) 0xAA).put((byte) 0xBB).put((byte) 0xCC).put((byte) 7).put((byte) 8).put((byte) 9);
        return b.array();
    }

    /** Two packets: housekeeping (28 V, nominal, "OK"), then science (300 K, one sample pair, checksum). */
    static byte[] telemetryBytes() {
        return new byte[] {0, 2,
                1, 0x6D, 0x60, 0, 2, 'O', 'K',
                2, 0x75, 0x30, 1, (byte) 0xFF, (byte) 0xFF, 0, 5, (byte) 0xBE, (byte) 0xEF};
    }

    static List<String> telemetryLines() {
        String p = "telemetry_packets_example.packet";
        return List.of("telemetry_packets_example.packet_count = 2",
                p + "[0].packet_type = 1", p + "[0].body -> housekeeping", p + "[0].body.housekeeping.bus_voltage = 28000",
                p + "[0].body.housekeeping.status = 0", p + "[0].body.housekeeping.message_length = 2",
                p + "[0].body.housekeeping.message = OK", p + "[0].checksum = <absent>",
                p + "[1].packet_type = 2", p + "[1].body -> science", p + "[1].body.science.temperature = 30000",
                p + "[1].body.science.sample_pairs = 1", p + "[1].body.science.samples[0] = -1",
                p + "[1].body.science.samples[1] = 5", p + "[1].checksum = 48879");
    }

    static FormatDescription csv() {
        RecordDescription row = RecordDescription.of("row", List.of(
                field("x", PrimitiveType.INT32, null), field("y", PrimitiveType.INT32, null),
                field("label", PrimitiveType.STRING, null)))
                .withText(RecordDescription.TextLayout.CSV).withOccurrence(new Occurrence.UntilEnd());
        return new FormatDescription("points", "", ByteOrder.BIG_ENDIAN, List.of("csv"),
                RecordDescription.of("points", List.of(row)));
    }

    static List<String> csvLines() {
        return List.of("points.row[0].x = 42", "points.row[0].y = -7", "points.row[0].label = hi",
                "points.row[1].x = 7", "points.row[1].y = 13", "points.row[1].label = demo");
    }

    static FormatDescription allTypes() {
        List<ElementDescription> fields = new ArrayList<>();
        for (PrimitiveType t : List.of(PrimitiveType.INT8, PrimitiveType.UINT8, PrimitiveType.INT16, PrimitiveType.UINT16,
                PrimitiveType.INT32, PrimitiveType.UINT32, PrimitiveType.INT64, PrimitiveType.UINT64,
                PrimitiveType.FLOAT32, PrimitiveType.FLOAT64)) {
            fields.add(field(t.name().toLowerCase(), t, null));
        }
        fields.add(field("label", PrimitiveType.STRING, new Expression.IntLiteral(4)));
        fields.add(field("raw", PrimitiveType.BYTES, new Expression.IntLiteral(2)));
        return new FormatDescription("all types", "", ByteOrder.LITTLE_ENDIAN, List.of(),
                RecordDescription.of("all_types", fields));
    }

    static byte[] allTypesBytes() {
        return ByteBuffer.allocate(48).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .put((byte) -5).put((byte) 250).putShort((short) -300).putShort((short) 65000).putInt(-70000)
                .putInt((int) 4_000_000_000L).putLong(-5_000_000_000L).putLong(-1L).putFloat(1.5f).putDouble(-2.25)
                .put("abcd".getBytes(StandardCharsets.US_ASCII)).put((byte) 0xDE).put((byte) 0xAD).array();
    }

    static byte[] fitsBytes() {
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        for (String card : List.of("SIMPLE  =                    T", "BITPIX  =                   16",
                "NAXIS   =                    2", "NAXIS1  =                  100", "NAXIS2  =                  200",
                "EXTEND  =                    T", "BSCALE  =                  1.0", "BZERO   =                  0.0",
                "BUNIT   = 'JY/BEAM '", "END")) {
            header.writeBytes(String.format("%-80s", card).getBytes(StandardCharsets.US_ASCII));
        }
        return header.toByteArray();
    }

    private static FieldDescription field(String name, PrimitiveType type, Expression length) {
        return new FieldDescription(name, name, type, length, null, Occurrence.ONCE, Semantics.NONE);
    }
}
