package info.oais.archive.manager;

import info.oais.archive.manager.service.format.DfdlGenerator;
import info.oais.archive.manager.service.format.DrbGenerator;
import info.oais.archive.manager.service.format.DrbPythonSampleRunner;
import info.oais.archive.manager.service.format.FormatTemplates;
import info.oais.archive.manager.service.format.KaitaiGenerator;
import info.oais.archive.manager.service.format.KaitaiSampleRunner;
import info.oais.archive.manager.service.format.SampleDecodeResult;
import info.oais.archive.manager.service.format.WriteBackResult;
import info.oais.infomodel.implementation.DigitalObjectRefImpl;
import info.oais.infomodel.structure.DefaultStructureNode;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.StructureNodeKind;
import info.oais.infomodel.structure.description.ByteOrder;
import info.oais.infomodel.structure.description.ChoiceDescription;
import info.oais.infomodel.structure.description.DescriptionLanguage;
import info.oais.infomodel.structure.description.DescriptionValidator;
import info.oais.infomodel.structure.description.EastReader;
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
     * @param writeLimitations engines known not to write this case's sample back byte for byte, with why
     */
    record Case(String title, FormatDescription format, byte[] sample, List<String> expected,
                Map<String, String> limitations, Map<String, String> writeLimitations) {
        Case(String title, FormatDescription format, byte[] sample, List<String> expected) {
            this(title, format, sample, expected, Map.of());
        }

        Case(String title, FormatDescription format, byte[] sample, List<String> expected,
             Map<String, String> limitations) {
            this(title, format, sample, expected, limitations, Map.of());
        }

        @Override
        public String toString() {
            return title;
        }
    }

    /** Why a record of a stated size with unused bytes at its end can't be written back byte for byte. */
    private static final String UNUSED_BYTES = "the block's last 2 bytes are unused, so they aren't among the decoded "
            + "values, and are written as zeros";

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
                        + "end-of-data alternative, so a last line without its newline is dropped"),
                        Map.of("DFDL (Daffodil)", "the infoset doesn't record that the last line had no newline, "
                                + "and Daffodil's unparser writes one")),
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
                                "bits.pad = 0", "bits.trailer = 9"), Map.of(),
                        Map.of("DFDL (Daffodil)", UNUSED_BYTES, "Kaitai Struct", UNUSED_BYTES)),
                new Case("quoted CSV, nil values and number formats (DFDL)", quotedCsv(),
                        ("\"Smith, J\",\"1,234.50\",NA\n\"Say \"\"hi\"\"\",-2.00,ok\n").getBytes(StandardCharsets.US_ASCII),
                        List.of("ledger.row[0].name = Smith, J", "ledger.row[0].amount = 1234.5", "ledger.row[0].note = <nil>",
                                "ledger.row[1].name = Say \"hi\"", "ledger.row[1].amount = -2", "ledger.row[1].note = ok")),
                new Case("offsets and compression (Kaitai)", archive(), archiveBytes(), archiveLines()),
                new Case("arithmetic on 8-bit fields in a condition and a count", smallArithmetic(),
                        new byte[] {1, 2, 7, 5, 6, 7, 8, 9},
                        List.of("small.a = 1", "small.b = 2", "small.extra = 7", "small.items[0] = 5",
                                "small.items[1] = 6", "small.items[2] = 7", "small.items[3] = 8", "small.items[4] = 9")),
                new Case("EAST: record representation clauses and overlapping alternatives", eastRecords(),
                        eastRecordsBytes(), List.of(
                                "records.set[0].second.the_number = 2",
                                "records.set[0].second.the_year = 2024",
                                "records.set[0].second.the_measurement[0] = 2.5",
                                "records.set[0].second.the_measurement[1] = -1",
                                "records.set[0].second.the_month = 6",
                                "records.set[0].fourth.the_day_of_month = 0",
                                "records.set[0].fourth.the_month = 7",
                                "records.set[0].fourth.when_mon.the_measurement = 3.25",
                                "records.set[0].fourth.when_tue_to_thu_or_sat = <absent>",
                                "records.set[0].fourth.when_others = <absent>",
                                "records.set[0].fourth.the_year = 2000",
                                "records.set[1].second.the_number = 1",
                                "records.set[1].second.the_year = 1999",
                                "records.set[1].second.the_measurement[0] = 4",
                                "records.set[1].second.the_month = 12",
                                "records.set[1].fourth.the_day_of_month = 1",
                                "records.set[1].fourth.the_month = 8",
                                "records.set[1].fourth.when_mon = <absent>",
                                "records.set[1].fourth.when_tue_to_thu_or_sat.the_alpha_value = 3",
                                "records.set[1].fourth.when_tue_to_thu_or_sat.the_beta_value = 4",
                                "records.set[1].fourth.when_tue_to_thu_or_sat.spare_1 = 0x0000",
                                "records.set[1].fourth.when_others = <absent>",
                                "records.set[1].fourth.the_year = 1999",
                                "records.set[2].second.the_number = 1",
                                "records.set[2].second.the_year = 1998",
                                "records.set[2].second.the_measurement[0] = 0.5",
                                "records.set[2].second.the_month = 1",
                                "records.set[2].fourth.the_day_of_month = 6",
                                "records.set[2].fourth.the_month = 9",
                                "records.set[2].fourth.when_mon = <absent>",
                                "records.set[2].fourth.when_tue_to_thu_or_sat = <absent>",
                                "records.set[2].fourth.when_others.spare_1 = 0x00000000",
                                "records.set[2].fourth.the_year = 1998")),
                new Case("EAST: packet format with virtual discriminants and bit fields", eastPacket(),
                        eastPacketBytes(), List.of(
                                "packet_description.set[0].flag = 1",
                                "packet_description.set[0].length = 3",
                                "packet_description.set[0].packet.primary_header.packet_identification.version_number = 0",
                                "packet_description.set[0].packet.primary_header.packet_identification.type_id = 0",
                                "packet_description.set[0].packet.primary_header.packet_identification.secondary_header_flag = 1",
                                "packet_description.set[0].packet.primary_header.packet_identification.application_process_id = 291",
                                "packet_description.set[0].packet.primary_header.packet_sequence_control.segmentation_flag = 3",
                                "packet_description.set[0].packet.primary_header.packet_sequence_control.source_sequence_count = 5",
                                "packet_description.set[0].packet.primary_header.source_data_length = 3",
                                "packet_description.set[0].packet.case_secondary_header_flag -> when_present_flag",
                                "packet_description.set[0].packet.case_secondary_header_flag.when_present_flag.secondary_header[0] = 10",
                                "packet_description.set[0].packet.case_secondary_header_flag.when_present_flag.secondary_header[1] = 11",
                                "packet_description.set[0].packet.case_secondary_header_flag.when_present_flag.secondary_header[2] = 12",
                                "packet_description.set[0].packet.case_secondary_header_flag.when_present_flag.secondary_header[3] = 13",
                                "packet_description.set[0].packet.case_secondary_header_flag.when_present_flag.source_data_1[0] = 1",
                                "packet_description.set[0].packet.case_secondary_header_flag.when_present_flag.source_data_1[1] = 2",
                                "packet_description.set[0].packet.case_secondary_header_flag.when_present_flag.source_data_1[2] = 3",
                                "packet_description.set[1].flag = 0",
                                "packet_description.set[1].length = 1",
                                "packet_description.set[1].packet.primary_header.packet_identification.version_number = 0",
                                "packet_description.set[1].packet.primary_header.packet_identification.type_id = 1",
                                "packet_description.set[1].packet.primary_header.packet_identification.secondary_header_flag = 0",
                                "packet_description.set[1].packet.primary_header.packet_identification.application_process_id = 1",
                                "packet_description.set[1].packet.primary_header.packet_sequence_control.segmentation_flag = 1",
                                "packet_description.set[1].packet.primary_header.packet_sequence_control.source_sequence_count = 6",
                                "packet_description.set[1].packet.primary_header.source_data_length = 1",
                                "packet_description.set[1].packet.case_secondary_header_flag -> when_absent_flag",
                                "packet_description.set[1].packet.case_secondary_header_flag.when_absent_flag.source_data_0[0] = 9")),
                new Case("EAST: calculated presence condition and EOF marker", eastWeeks(),
                        new byte[] {5, 10, 20, 7, 10, 12}, List.of(
                                "weeks.previous_week = 5",
                                "weeks.this_week[0].result_1 = 10",
                                "weeks.this_week[0].result_2 = 20",
                                "weeks.this_week[0].when_true.bonus = 7",
                                "weeks.this_week[1].result_1 = 10",
                                "weeks.this_week[1].result_2 = 12",
                                "weeks.this_week[1].when_true = <absent>")),
                new Case("EAST: codes, ASCII representations and little-endian numbers", eastLittleEndian(),
                        eastLittleEndianBytes(), List.of(
                                "readings.set[0].name = SPACECRA",
                                "readings.set[0].operation = 12",
                                "readings.set[0].process = IDLE",
                                "readings.set[0].count =   123",
                                "readings.set[0].distance = 1.5",
                                "readings.set[0].height = -2",
                                "readings.set[0].heights[0].element[0] = -200",
                                "readings.set[0].heights[0].element[1] = -100",
                                "readings.set[0].heights[1].element[0] = 0",
                                "readings.set[0].heights[1].element[1] = 100",
                                "readings.set[0].heights[2].element[0] = 200",
                                "readings.set[0].heights[2].element[1] = 300")),
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

    /**
     * Write-back: every engine that can decode a case writes its sample back
     * unchanged, byte for byte -- the round trip that shows the description
     * is complete and encodes values the way the sample stores them.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void everyEngineWritesTheSampleBackUnchanged(Case c) throws Exception {
        Map<String, info.oais.infomodel.structure.RoundTrip> results = new LinkedHashMap<>();
        if (supports(c, DescriptionLanguage.DFDL, () -> DFDL.generate(c.format()))) {
            Path schema = Files.writeString(tmp.resolve("format.dfdl.xsd"), DFDL.generate(c.format()));
            results.put("DFDL (Daffodil)",
                    new DfdlStructureRepInfo(new DfdlFormatSpecification(schema.toUri())).roundTrip(sample(c)));
        }
        if (supports(c, DescriptionLanguage.DRB, () -> DRB.generateSdfSchema(c.format()))) {
            Path schema = Files.writeString(tmp.resolve("format.drb.xsd"), DRB.generateSdfSchema(c.format()));
            results.put("DRB (Java)",
                    new DrbStructureRepInfo(new DrbFormatSpecification(schema.toUri())).roundTrip(sample(c)));
        }
        if (supports(c, DescriptionLanguage.DRB_PYTHON, () -> DRB.pythonModule(c.format(), "MatrixFactory"))) {
            String configured = System.getenv("DRB_PYTHON");
            DrbPythonSampleRunner runner = new DrbPythonSampleRunner(configured == null ? "" : configured);
            if (runner.drbVersion().isPresent()) {
                DrbPythonSampleRunner.WriteOutcome outcome = runner.write(DRB.pythonModule(c.format(), "MatrixFactory"),
                        null, "MatrixFactory", DrbGenerator.driverId(c.format().name()), c.sample(), Map.of());
                assertThat(outcome.error()).as("drb-python").isNull();
                results.put("drb-python", info.oais.infomodel.structure.RoundTrip.compare(c.sample(), outcome.written()));
            }
        }
        if (supports(c, DescriptionLanguage.KAITAI, () -> KAITAI.generate(c.format()))) {
            WriteBackResult written = KAITAI_RUNNER.write(KAITAI.generate(c.format()), c.format().root().name(),
                    c.sample(), Map.of());
            assertThat(written.error()).as("Kaitai Struct").isNull();
            results.put("Kaitai Struct", written.roundTrip());
        }
        results.forEach((engine, roundTrip) -> {
            String limitation = c.writeLimitations().get(engine);
            if (limitation != null) {
                assertThat(roundTrip.identical()).as(engine + " (known limitation: " + limitation + ")").isFalse();
            } else {
                assertThat(roundTrip.describe()).as(engine).startsWith("Identical");
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

    // ---- EAST descriptions (CCSDS 644.0-B-3), read by EastReader ----

    static FormatDescription eastRecords() {
        return EastReader.read("""
                package RECORDS is
                   type DAY is (MON, TUE, WED, THU, FRI, SAT, SUN);
                   for DAY'size use 8;
                   type MONTH is range 1 .. 12;
                   for MONTH'size use 8;
                   type YEAR is range 1900 .. 2100;
                   for YEAR'size use 16;
                   type NUMBER is range 1 .. 10;
                   for NUMBER'size use 8;
                   type SMALL is range 1 .. 10;
                   for SMALL'size use 8;
                   type VALUE is digits 5;
                   for VALUE'size use 32;
                   type VECTOR is array(NUMBER range <>) of VALUE;
                   type SECOND_RECORD(THE_NUMBER: NUMBER := 1) is record
                      THE_YEAR: YEAR;
                      THE_MEASUREMENT: VECTOR(1 .. THE_NUMBER);
                      THE_MONTH: MONTH;
                   end record;
                   for SECOND_RECORD use record
                      THE_NUMBER at 0 range 0 .. 7;
                      THE_YEAR at 0 range 8 .. 23;
                   end record;
                   type FOURTH_RECORD (THE_DAY_OF_MONTH: DAY := MON) is record
                      THE_MONTH: MONTH;
                      THE_YEAR: YEAR;
                      case THE_DAY_OF_MONTH is
                         when MON =>
                            THE_MEASUREMENT: VALUE;
                         when TUE .. THU | SAT =>
                            THE_ALPHA_VALUE: SMALL;
                            THE_BETA_VALUE: SMALL;
                         when others =>
                            null;
                      end case;
                   end record;
                   for FOURTH_RECORD use record
                      THE_DAY_OF_MONTH at 0 range 0 .. 7;
                      THE_MONTH at 0 range 8 .. 15;
                      THE_MEASUREMENT at 0 range 16 .. 47;
                      THE_ALPHA_VALUE at 0 range 16 .. 23;
                      THE_BETA_VALUE at 0 range 24 .. 31;
                      THE_YEAR at 0 range 48 .. 63;
                   end record;
                   for FOURTH_RECORD'size use 64;
                   SECOND : SECOND_RECORD;
                   FOURTH : FOURTH_RECORD;
                end RECORDS;
                package RECORDS_PHYSICAL is
                end RECORDS_PHYSICAL;
                """);
    }

    /** Three sets: a Monday measurement, a Tuesday's alpha and beta values, and a Sunday with nothing. */
    static byte[] eastRecordsBytes() {
        ByteBuffer b = ByteBuffer.allocate(3 * 8 + 12 + 8 + 8);
        b.put((byte) 2).putShort((short) 2024).putFloat(2.5f).putFloat(-1f).put((byte) 6);
        b.put((byte) 0).put((byte) 7).putFloat(3.25f).putShort((short) 2000);
        b.put((byte) 1).putShort((short) 1999).putFloat(4f).put((byte) 12);
        b.put((byte) 1).put((byte) 8).put((byte) 3).put((byte) 4).putShort((short) 0).putShort((short) 1999);
        b.put((byte) 1).putShort((short) 1998).putFloat(0.5f).put((byte) 1);
        b.put((byte) 6).put((byte) 9).putInt(0).putShort((short) 1998);
        return b.array();
    }

    static FormatDescription eastPacket() {
        return EastReader.read("""
                package PACKET_DESCRIPTION is
                   type VERSION is (VERSION_1, VERSION_2);
                   for VERSION'size use 3;
                   type PACKET_TYPE is (TELEMETRY , TELECOMMAND);
                   for PACKET_TYPE'size use 1;
                   type PRESENCE_FLAG is (ABSENT , PRESENT);
                   for PRESENCE_FLAG'size use 1;
                   type FLAG_OCTET is (ABSENT_FLAG, PRESENT_FLAG);
                   for FLAG_OCTET'size use 8;
                   type PROCESS_IDENTIFICATION is range 0 .. 2047;
                   for PROCESS_IDENTIFICATION'size use 11;
                   type PACKET_IDENTIFICATION_TYPE is record
                      VERSION_NUMBER: VERSION;
                      TYPE_ID: PACKET_TYPE;
                      SECONDARY_HEADER_FLAG: PRESENCE_FLAG;
                      APPLICATION_PROCESS_ID: PROCESS_IDENTIFICATION;
                   end record;
                   type STATUS is (CONTINUATION_SEGMENT, FIRST_SEGMENT, LAST_SEGMENT, UNSEGMENTED_PACKET);
                   for STATUS'size use 2;
                   type COUNTER is range 0 .. 16383;
                   for COUNTER'size use 14;
                   type PACKET_SEQUENCE_CONTROL_TYPE is record
                      SEGMENTATION_FLAG: STATUS;
                      SOURCE_SEQUENCE_COUNT: COUNTER;
                   end record;
                   type NUMBER is range 0 .. 65535;
                   for NUMBER'size use 16;
                   type OCTET is range 0 .. 255;
                   for OCTET'size use 8;
                   type DATA_ARRAY is array (NUMBER range <>) of OCTET;
                   type SECONDARY_HEADER_TYPE is array (1 .. 4) of OCTET;
                   type PRIMARY_HEADER_TYPE is record
                      PACKET_IDENTIFICATION: PACKET_IDENTIFICATION_TYPE;
                      PACKET_SEQUENCE_CONTROL: PACKET_SEQUENCE_CONTROL_TYPE;
                      SOURCE_DATA_LENGTH: NUMBER;
                   end record;
                   type PACKET_FORMAT_TYPE(
                      VIRTUAL_SECONDARY_HEADER_FLAG: FLAG_OCTET := PRESENT_FLAG;
                      VIRTUAL_SOURCE_DATA_LENGTH: NUMBER := 256)
                   is record
                      PRIMARY_HEADER: PRIMARY_HEADER_TYPE;
                      case VIRTUAL_SECONDARY_HEADER_FLAG is
                         when ABSENT_FLAG =>
                            SOURCE_DATA_0: DATA_ARRAY (1 .. VIRTUAL_SOURCE_DATA_LENGTH);
                         when PRESENT_FLAG =>
                            SECONDARY_HEADER: SECONDARY_HEADER_TYPE;
                            SOURCE_DATA_1: DATA_ARRAY (1 .. VIRTUAL_SOURCE_DATA_LENGTH);
                      end case;
                   end record;
                   FLAG : FLAG_OCTET;
                   LENGTH : NUMBER;
                   PACKET : PACKET_FORMAT_TYPE;
                   PACKET.VIRTUAL_SECONDARY_HEADER_FLAG : virtual FLAG_OCTET := FLAG;
                   PACKET.VIRTUAL_SOURCE_DATA_LENGTH : virtual NUMBER := LENGTH;
                end PACKET_DESCRIPTION;
                package PACKET_PHYSICAL is
                   type BIT_ORDER is (HIGH_ORDER_FIRST, LOW_ORDER_FIRST);
                   OCTET_STORAGE: constant BIT_ORDER := HIGH_ORDER_FIRST;
                end PACKET_PHYSICAL;
                """);
    }

    /** A packet with a secondary header and 3 bytes of data, then one without and with 1 byte. */
    static byte[] eastPacketBytes() {
        return new byte[] {1, 0, 3, 0x09, 0x23, (byte) 0xC0, 0x05, 0, 3, 0x0A, 0x0B, 0x0C, 0x0D, 1, 2, 3,
                0, 0, 1, 0x10, 0x01, 0x40, 0x06, 0, 1, 9};
    }

    static FormatDescription eastWeeks() {
        return EastReader.read("""
                package WEEKS is
                   type A_RESULT is range 0 .. 100;
                   for A_RESULT'size use 8;
                   type RESULTS (VIRTUAL_BONUS_FLAG : BOOLEAN := TRUE) is record
                      RESULT_1 : A_RESULT;
                      RESULT_2 : A_RESULT;
                      case VIRTUAL_BONUS_FLAG is
                         when TRUE => BONUS : A_RESULT;
                         when FALSE => null;
                      end case;
                   end record;
                   PREVIOUS_WEEK : A_RESULT;
                   THIS_WEEK : RESULTS;
                   END_OF_WEEKS : constant EOF;
                   THIS_WEEK.VIRTUAL_BONUS_FLAG : virtual BOOLEAN
                      := (THIS_WEEK.RESULT_2 - THIS_WEEK.RESULT_1) > PREVIOUS_WEEK;
                end WEEKS;
                package WEEKS_PHYSICAL is
                end WEEKS_PHYSICAL;
                """);
    }

    static FormatDescription eastLittleEndian() {
        return EastReader.read("""
                package READINGS is
                   type CODE is (ADD , SUB , MUL);
                   for CODE use (ADD => 2#1#, SUB => 2#10#, MUL => 16#0C#);
                   for CODE'size use 8;
                   type PROCESS_IDENTIFICATION is (WORKING, IDLE);
                   for PROCESS_IDENTIFICATION'size use 56;
                   type COUNTER is range -1 .. 16383;
                   for COUNTER'size use 40;
                   type KILOMETERS is digits 5;
                   for KILOMETERS'size use 32;
                   type LEVEL is range -32768 .. 32767;
                   for LEVEL'size use 16;
                   type OCTET is range 0 .. 255;
                   for OCTET'size use 8;
                   type GRID is array (OCTET range <>, OCTET range <>) of LEVEL;
                   NAME : STRING (1 .. 8);
                   OPERATION : CODE;
                   PROCESS : PROCESS_IDENTIFICATION;
                   COUNT : COUNTER;
                   DISTANCE : KILOMETERS;
                   HEIGHT : LEVEL;
                   HEIGHTS : GRID (1 .. 2, 1 .. 3);
                end READINGS;
                package READINGS_PHYSICAL is
                   type BIT_ORDER is ( HIGH_ORDER_FIRST, LOW_ORDER_FIRST);
                   OCTET_STORAGE: constant BIT_ORDER := LOW_ORDER_FIRST;
                   Binary_Representation_01: constant INTEGER_PHYSICAL_DESCRIPTION :=
                      (NUMBER_OF_SUBFIELDS => 1, COMPLEMENT => TWOS_COMPLEMENT, LOCATION => (1 => (0,15)));
                   Real_Representation: constant REAL_PHYSICAL_DESCRIPTION :=
                      (NUMBER_OF_SUBFIELDS_IN_EXPONENT => 2, NUMBER_OF_SUBFIELDS_IN_MANTISSA => 3,
                       CONVENTION_USED => FCSTC000, SIGN_BIT_NUMBER => 24, COMPLEMENT => SIGN_AND_MAGNITUDE,
                       EXPONENT_BASE => 2, BIAS => 127,
                       LOCATION_OF_EXPONENT => ( 1 => (25,31), 2 => (16,16)),
                       LOCATION_OF_MANTISSA => ( 1 => (17,23), 2 => (8,15), 3 => (0,7)));
                   ASCII_Rep_01: constant ASCII_ENUMERATION_PHYSICAL_DESCRIPTION :=
                      (NUMBER_OF_OCCURRENCES => 2, NUMBER_OF_CHARACTERS => 7, REPRESENTATION => ("WORKING" , "IDLE"));
                   ASCII_Rep_02: constant ASCII_NUMERIC_PHYSICAL_DESCRIPTION := (NUMBER_OF_CHARACTERS => 5);
                   type BASIC_TYPE_NAMES is (USER_TYPE_LEVEL, USER_TYPE_KILOMETERS,
                                             USER_TYPE_PROCESS_IDENTIFICATION, USER_TYPE_COUNTER);
                   type RELATION(choice: BASIC_TYPE_NAMES) is record
                      case choice is
                         when USER_TYPE_LEVEL => PHYS_1: INTEGER_PHYSICAL_DESCRIPTION := Binary_Representation_01;
                         when USER_TYPE_KILOMETERS => PHYS_2: REAL_PHYSICAL_DESCRIPTION := Real_Representation;
                         when USER_TYPE_PROCESS_IDENTIFICATION =>
                            PHYS_3: ASCII_ENUMERATION_PHYSICAL_DESCRIPTION := ASCII_Rep_01;
                         when USER_TYPE_COUNTER => PHYS_4: ASCII_NUMERIC_PHYSICAL_DESCRIPTION := ASCII_Rep_02;
                      end case;
                   end record;
                end READINGS_PHYSICAL;
                """);
    }

    static byte[] eastLittleEndianBytes() {
        ByteBuffer b = ByteBuffer.allocate(8 + 1 + 7 + 5 + 4 + 2 + 12).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        b.put("SPACECRA".getBytes(StandardCharsets.US_ASCII)).put((byte) 12)
                .put("IDLE   ".getBytes(StandardCharsets.US_ASCII)).put("  123".getBytes(StandardCharsets.US_ASCII))
                .putFloat(1.5f).putShort((short) -2);
        for (int i = 1; i <= 6; i++) {
            b.putShort((short) (i * 100 - 300));
        }
        return b.array();
    }

    private static FieldDescription field(String name, PrimitiveType type, Expression length) {
        return new FieldDescription(name, name, type, length, null, Occurrence.ONCE, Semantics.NONE);
    }
}
