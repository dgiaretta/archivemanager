package info.oais.archive.manager;

import info.oais.archive.manager.service.format.DfdlGenerator;
import info.oais.archive.manager.service.format.DrbGenerator;
import info.oais.archive.manager.service.format.DrbPythonSampleRunner;
import info.oais.archive.manager.service.format.FormatTemplates;
import info.oais.archive.manager.service.format.KaitaiGenerator;
import info.oais.archive.manager.service.format.KaitaiSampleRunner;
import info.oais.archive.manager.service.format.SampleDecodeResult;
import info.oais.infomodel.implementation.DigitalObjectRefImpl;
import info.oais.infomodel.structure.DefaultStructureNode;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.StructureNodeKind;
import info.oais.infomodel.structure.description.ByteOrder;
import info.oais.infomodel.structure.description.ChoiceDescription;
import info.oais.infomodel.structure.description.DescriptionLanguage;
import info.oais.infomodel.structure.description.DescriptionValidator;
import info.oais.infomodel.structure.description.ElementDescription;
import info.oais.infomodel.structure.description.Expression;
import info.oais.infomodel.structure.description.Feature;
import info.oais.infomodel.structure.description.FieldDescription;
import info.oais.infomodel.structure.description.FormatDescription;
import info.oais.infomodel.structure.description.NumberFormat;
import info.oais.infomodel.structure.description.Occurrence;
import info.oais.infomodel.structure.description.PrimitiveType;
import info.oais.infomodel.structure.description.RecordDescription;
import info.oais.infomodel.structure.description.Semantics;
import info.oais.infomodel.structure.description.StructureAligner;
import info.oais.infomodel.structure.dfdl.DfdlFormatSpecification;
import info.oais.infomodel.structure.dfdl.DfdlStructureRepInfo;
import info.oais.infomodel.structure.drb.DrbFormatSpecification;
import info.oais.infomodel.structure.drb.DrbStructureRepInfo;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;
import java.util.zip.Deflater;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The consistency check across engines: each reference format is generated
 * for every engine, the same sample bytes are decoded by each, and the
 * results - lined up against the description by {@link StructureAligner} -
 * must be identical. Daffodil (DFDL), DRB and Kaitai Struct (through the
 * bundled compiler, as RepInfo Tools runs it) always run; drb-python runs
 * when a Python with drb is available ({@code DRB_PYTHON}). An engine whose
 * language can't express a case's {@link Feature}s is skipped for that case,
 * after checking that its generator refuses it.
 */
class GeneratedDescriptionsMatrixTest {

    private static final DfdlGenerator DFDL = new DfdlGenerator();
    private static final DrbGenerator DRB = new DrbGenerator();
    private static final KaitaiGenerator KAITAI = new KaitaiGenerator();
    private static final KaitaiSampleRunner KAITAI_RUNNER = new KaitaiSampleRunner(120);

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
                new Case("bit fields and a record of a stated size (DFDL, Kaitai)", bits(), bitsBytes(),
                        List.of("bits.version = 5", "bits.flags = 19", "bits.count = 7", "bits.block.value = 258",
                                "bits.block.label = hi", "bits.wide = 2748", "bits.rest = 13", "bits.on = 1",
                                "bits.pad = 0", "bits.trailer = 9")),
                new Case("quoted CSV, nil values and number formats (DFDL)", quotedCsv(),
                        ("\"Smith, J\",\"1,234.50\",NA\n\"Say \"\"hi\"\"\",-2.00,ok\n").getBytes(StandardCharsets.US_ASCII),
                        List.of("ledger.row[0].name = Smith, J", "ledger.row[0].amount = 1234.5", "ledger.row[0].note = <nil>",
                                "ledger.row[1].name = Say \"hi\"", "ledger.row[1].amount = -2", "ledger.row[1].note = ok")),
                new Case("offsets and compression (Kaitai)", archive(), archiveBytes(), archiveLines()),
                new Case("arithmetic on 8-bit fields in a condition and a count", smallArithmetic(),
                        new byte[] {1, 2, 7, 5, 6, 7, 8, 9},
                        List.of("small.a = 1", "small.b = 2", "small.extra = 7", "small.items[0] = 5",
                                "small.items[1] = 6", "small.items[2] = 7", "small.items[3] = 8", "small.items[4] = 9")),
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
        if (supports(c, DescriptionLanguage.DFDL, () -> DFDL.generate(c.format()))) {
            results.put("DFDL (Daffodil)", align(c, decodeDfdl(c)));
        }
        if (supports(c, DescriptionLanguage.DRB, () -> DRB.generateSdfSchema(c.format()))) {
            results.put("DRB (Java)", align(c, decodeDrb(c)));
        }
        if (supports(c, DescriptionLanguage.DRB_PYTHON, () -> DRB.pythonModule(c.format(), "MatrixFactory"))) {
            StructureNode python = decodeDrbPython(c);
            if (python != null) {
                results.put("drb-python", align(c, python));
            }
        }
        if (supports(c, DescriptionLanguage.KAITAI, () -> KAITAI.generate(c.format()))) {
            results.put("Kaitai Struct", decodeKaitai(c));
        }
        assertThat(results).as("engines that can decode this case").isNotEmpty();

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

    /** Through the bundled Kaitai Struct compiler, as RepInfo Tools' sample test runs it. */
    private List<String> decodeKaitai(Case c) throws Exception {
        return KAITAI_RUNNER.withDecoded(KAITAI.generate(c.format()), c.format().root().name(), c.sample(),
                node -> align(c, node));
    }

    /**
     * Whether {@code language} can express everything the case uses; if it
     * can't, checks that its generator refuses the description rather than
     * quietly leaving something out.
     */
    private static boolean supports(Case c, DescriptionLanguage language, Runnable generate) {
        if (Feature.unsupported(c.format(), language).isEmpty()) {
            return true;
        }
        try {
            generate.run();
        } catch (Feature.UnsupportedFeatureException expected) {
            return false;
        }
        throw new AssertionError(language.label() + " generated a description using features it can't express");
    }

    private static DigitalObjectRefImpl sample(Case c) {
        return new DigitalObjectRefImpl(new ByteArrayInputStream(c.sample()));
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

    /**
     * Bit fields (3 + 5 bits in one byte, 12 + 4 across two, a one-bit flag,
     * which Kaitai reads as a boolean) and a 6-byte record whose elements use 4.
     */
    static FormatDescription bits() {
        RecordDescription block = RecordDescription.of("block", List.of(field("value", PrimitiveType.UINT16, null),
                field("label", PrimitiveType.STRING, new Expression.IntLiteral(2)))).withSize(Expression.parse("count - 1"));
        return new FormatDescription("bits", "", ByteOrder.BIG_ENDIAN, List.of(), RecordDescription.of("bits", List.of(
                bitField("version", 3), bitField("flags", 5), field("count", PrimitiveType.UINT8, null), block,
                bitField("wide", 12), bitField("rest", 4), bitField("on", 1), bitField("pad", 7),
                field("trailer", PrimitiveType.UINT8, null))));
    }

    static byte[] bitsBytes() {
        return new byte[] {(byte) 0b1011_0011, 7, 1, 2, 'h', 'i', (byte) 0xEE, (byte) 0xEE,
                (byte) 0xAB, (byte) 0xCD, (byte) 0x80, 9};
    }

    private static FieldDescription bitField(String name, int bits) {
        return field(name, PrimitiveType.BITS, new Expression.IntLiteral(bits));
    }

    /** RFC 4180-style quoting, a nil value, and numbers with grouping separators. */
    static FormatDescription quotedCsv() {
        RecordDescription row = RecordDescription.of("row", List.of(
                        field("name", PrimitiveType.STRING, null),
                        field("amount", PrimitiveType.FLOAT64, null).withNumberFormat(new NumberFormat("#,##0.00", ".", ",")),
                        field("note", PrimitiveType.STRING, null).withNilValue("NA")))
                .withText(RecordDescription.TextLayout.CSV.withQuote("\"")).withOccurrence(new Occurrence.UntilEnd());
        return new FormatDescription("ledger", "", ByteOrder.BIG_ENDIAN, List.of("csv"),
                RecordDescription.of("ledger", List.of(row)));
    }

    /** A header, a zlib-compressed record of a stated size, and an index found by its offset. */
    static FormatDescription archive() {
        RecordDescription data = RecordDescription.of("data", List.of(field("count", PrimitiveType.UINT8, null),
                        field("values", PrimitiveType.UINT16, null).withOccurrence(new Occurrence.Repeated(Expression.parse("count")))))
                .withSize(Expression.parse("compressed_size")).withCompression(RecordDescription.Compression.ZLIB);
        RecordDescription index = RecordDescription.of("index", List.of(
                field("magic", PrimitiveType.STRING, new Expression.IntLiteral(4)), field("entries", PrimitiveType.UINT8, null)))
                .withOffset(Expression.parse("index_offset"));
        return new FormatDescription("archive", "", ByteOrder.BIG_ENDIAN, List.of(), RecordDescription.of("archive", List.of(
                field("index_offset", PrimitiveType.UINT32, null), field("compressed_size", PrimitiveType.UINT16, null),
                data, index)));
    }

    static byte[] archiveBytes() {
        byte[] compressed = zlib(new byte[] {3, 0, 1, 0, 2, 0, 3});
        int indexOffset = 6 + compressed.length + 2;
        ByteBuffer b = ByteBuffer.allocate(indexOffset + 5);
        b.putInt(indexOffset).putShort((short) compressed.length).put(compressed).put(new byte[2])
                .put("IDX!".getBytes(StandardCharsets.US_ASCII)).put((byte) 5);
        return b.array();
    }

    static List<String> archiveLines() {
        byte[] compressed = zlib(new byte[] {3, 0, 1, 0, 2, 0, 3});
        return List.of("archive.index_offset = " + (6 + compressed.length + 2),
                "archive.compressed_size = " + compressed.length, "archive.data.count = 3",
                "archive.data.values[0] = 1", "archive.data.values[1] = 2", "archive.data.values[2] = 3",
                "archive.index.magic = IDX!", "archive.index.entries = 5");
    }

    static byte[] zlib(byte[] data) {
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();
        byte[] buffer = new byte[256];
        int n = deflater.deflate(buffer);
        deflater.end();
        return java.util.Arrays.copyOf(buffer, n);
    }

    /** Daffodil 3.11 fails adding xs:unsignedByte values directly; the generator must avoid that. */
    static FormatDescription smallArithmetic() {
        return new FormatDescription("small", "", ByteOrder.BIG_ENDIAN, List.of(), RecordDescription.of("small", List.of(
                field("a", PrimitiveType.UINT8, null), field("b", PrimitiveType.UINT8, null),
                field("extra", PrimitiveType.UINT8, null).withOccurrence(new Occurrence.Optional(Expression.parse("a + b = 3"))),
                field("items", PrimitiveType.UINT8, null).withOccurrence(new Occurrence.Repeated(Expression.parse("a + b * 2"))))));
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
