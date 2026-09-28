package info.oais.archive.manager;

import info.oais.infomodel.structure.description.ByteOrder;
import info.oais.archive.manager.model.format.DrbTarget;
import info.oais.infomodel.structure.description.PrimitiveType;
import info.oais.archive.manager.model.format.FormatDefinition;
import info.oais.archive.manager.model.format.FormatDefinitionKind;
import info.oais.archive.manager.service.format.DrbGenerator;
import info.oais.archive.manager.service.format.DrbPythonSampleRunner;
import info.oais.archive.manager.service.format.FormatTemplates;
import info.oais.archive.manager.service.format.HandWrittenDescriptions;
import info.oais.archive.manager.service.format.SampleDecodeResult;
import info.oais.infomodel.structure.description.Semantics;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs {@link DrbGenerator}'s generated drb-python driver through real
 * drb-python. Needs a Python with drb installed ({@code pip install drb}):
 * point the {@code DRB_PYTHON} environment variable at its interpreter, or
 * have one on the PATH as {@code python3}/{@code python}. Skipped otherwise.
 */
class DrbPythonSampleRunnerTest {

    private static final DrbGenerator GENERATOR = new DrbGenerator();
    private static DrbPythonSampleRunner runner;
    /** Runs hand-written add-ins, as a server with archive.drb-python.run-hand-written-add-ins would. */
    private static DrbPythonSampleRunner addInRunner;

    @BeforeAll
    static void findDrbPython() {
        String configured = System.getenv("DRB_PYTHON");
        runner = new DrbPythonSampleRunner(configured == null ? "" : configured);
        addInRunner = new DrbPythonSampleRunner(configured == null ? "" : configured, true);
        assumeTrue(runner.drbVersion().isPresent(), "drb-python not available: " + runner.notAvailableMessage());
    }

    @Test
    void addOnsGiveMetadataAndFindValuesOutsideTheirRangeOrCodeList() {
        FormatDefinition def = new FormatDefinition();
        def.setName("reading");
        def.setKind(FormatDefinitionKind.BYTE_LAYOUT);
        TestFormats.addField(def, "mode", PrimitiveType.UINT8, null, new Semantics("instrument mode", null, null, null,
                null, Map.of("1", "science", "2", "calibration"), null, null, null, null, null));
        TestFormats.addField(def, "temp", PrimitiveType.UINT16, null, new Semantics("temperature", null, "K", null, null,
                Map.of(), new BigDecimal("0.5"), null, "65535", BigDecimal.ZERO, new BigDecimal("400")));

        DrbPythonSampleRunner.Outcome good = run(def, null, new byte[] {1, 0x01, (byte) 0x90});
        assertThat(good.decoded().error()).isNull();
        assertThat(good.addOns().error()).isNull();
        assertThat(good.addOns().metadata()).containsEntry("instrument mode", "science").containsEntry("temperature", "200");
        assertThat(good.addOns().checks()).isEmpty();

        DrbPythonSampleRunner.Outcome bad = run(def, null, new byte[] {7, 0x06, 0x40, 9});
        assertThat(bad.addOns().checks()).containsExactly("/mode = 7 isn't in its code list",
                "/temp = 800 is outside its valid range (0 to 400)", "1 bytes at the end aren't covered by the description");

        DrbPythonSampleRunner.Outcome fill = run(def, null, new byte[] {2, (byte) 0xFF, (byte) 0xFF});
        assertThat(fill.addOns().metadata()).containsEntry("instrument mode", "calibration").containsEntry("temperature", "");
        assertThat(fill.addOns().checks()).isEmpty();
    }

    @Test
    void crcAddInChecksTheFileAndAddsMetadata() {
        FormatDefinition def = new FormatDefinition();
        def.setName("with crc");
        def.setKind(FormatDefinitionKind.BYTE_LAYOUT);
        TestFormats.addField(def, "payload", PrimitiveType.STRING, 5, null, null, null, null);
        TestFormats.addField(def, "crc", PrimitiveType.UINT32, null, null, null, null, null);
        CRC32 crc = new CRC32();
        crc.update("hello".getBytes(StandardCharsets.US_ASCII));
        byte[] file = ByteBuffer.allocate(9).put("hello".getBytes(StandardCharsets.US_ASCII)).putInt((int) crc.getValue())
                .array();
        String addIn = example("drb-python-crc32");

        DrbPythonSampleRunner.Outcome ok = run(def, addIn, file);
        assertThat(ok.addOns().checks()).isEmpty();
        assertThat(ok.addOns().metadata()).containsEntry("crc32", String.format("%08x", crc.getValue()))
                .containsEntry("crc32_ok", "True");

        file[0] = 'j';
        assertThat(run(def, addIn, file).addOns().checks()).singleElement().asString().startsWith("CRC-32 mismatch");
    }

    @Test
    void decompressAddInReadsXzAndBzip2Files() {
        FormatDefinition def = textRecord();
        String addIn = example("drb-python-decompress");
        for (String compressed : List.of("/Td6WFoAAATm1rRGAgAhARYAAAB0L+WjAQAEaGVsbG8AAAAAsTe52+XaHpsAAR0FuC2Arx+2830BAAAAAARZWg==",
                "QlpoOTFBWSZTWRkxZT0AAACBAAJEoAAhmmgzTQczi7kinChIDJiynoA=")) {
            DrbPythonSampleRunner.Outcome outcome = run(def, addIn, Base64.getDecoder().decode(compressed));
            assertThat(leafValues(outcome.decoded())).containsExactly("text=hello");
            assertThat(outcome.addOns().checks()).isEmpty();
        }
    }

    @Test
    void aesAddInDecryptsTheFile() throws Exception {
        byte[] iv = new byte[16];
        iv[15] = 7;
        Cipher aes = Cipher.getInstance("AES/CTR/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(HexFormat.of().parseHex(
                "000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"), "AES"), new IvParameterSpec(iv));
        byte[] encrypted = aes.doFinal("hello".getBytes(StandardCharsets.US_ASCII));
        byte[] file = ByteBuffer.allocate(16 + encrypted.length).put(iv).put(encrypted).array();

        DrbPythonSampleRunner.Outcome outcome = run(textRecord(), example("drb-python-aes"), file);
        String error = String.valueOf(outcome.decoded().error());
        assumeTrue(!error.contains("No module named 'cryptography'"), "the cryptography package isn't installed");
        assertThat(leafValues(outcome.decoded())).containsExactly("text=hello");
    }

    @Test
    void addInsOnlyRunWhereTheServerAllowsThem() {
        DrbPythonSampleRunner.Outcome outcome = runner.run(GENERATOR.generate(textRecord(), DrbTarget.PYTHON),
                "def prepare(data):\n    return data\n", GENERATOR.pythonFactoryClassName(textRecord()), null,
                "hello".getBytes(StandardCharsets.US_ASCII), null);
        assertThat(outcome.decoded().error()).isEqualTo(DrbPythonSampleRunner.ADD_INS_NOT_RUN);
    }

    @Test
    void addInsAreParsedWithoutBeingRun() {
        assertThat(runner.checkAddIn("def prepare(data):\n    return data\n")).isEmpty();
        assertThat(runner.checkAddIn("def prepare(data)\n    return data\n"))
                .singleElement().asString().startsWith("This isn't valid Python (line 1");
        assertThat(runner.checkAddIn("import os\nos.system('exit 3')\n"))
                .singleElement().asString().startsWith("The add-in doesn't define any of the functions");
        for (HandWrittenDescriptions.Example e : HandWrittenDescriptions.examples(
                info.oais.infomodel.structure.description.DescriptionLanguage.DRB_PYTHON)) {
            assertThat(runner.checkAddIn(e.text())).as(e.id()).isEmpty();
        }
    }

    private static DrbPythonSampleRunner.Outcome run(FormatDefinition def, String addIn, byte[] bytes) {
        return addInRunner.run(GENERATOR.generate(def, DrbTarget.PYTHON), addIn, GENERATOR.pythonFactoryClassName(def),
                GENERATOR.pythonDriverId(def), bytes, null);
    }

    private static FormatDefinition textRecord() {
        FormatDefinition def = new FormatDefinition();
        def.setName("text record");
        def.setKind(FormatDefinitionKind.BYTE_LAYOUT);
        TestFormats.addField(def, "text", PrimitiveType.STRING, 5, null, null, null, null);
        return def;
    }

    private static String example(String id) {
        return HandWrittenDescriptions.examples().stream().filter(e -> e.id().equals(id)).findFirst().orElseThrow().text();
    }

    @Test
    void decodesALittleEndianBinaryRecord() {
        FormatDefinition def = pointRecord("x ordinate");
        byte[] bytes = ByteBuffer.allocate(20).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putInt(42).putShort((short) -7).putDouble(2.5).put("ab".getBytes(StandardCharsets.US_ASCII))
                .putShort((short) 0).array();

        SampleDecodeResult result = run(def, bytes);

        assertThat(result.error()).isNull();
        assertThat(leafValues(result)).containsExactly("x=42", "y=-7", "z=2.5", "label=ab");
        assertThat(result.rows().get(3).byteRange()).isEqualTo("bytes 6–13");
    }

    @Test
    void decodesAFitsPrimaryHeaderWithTheBuiltInTemplate() {
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        for (String card : List.of("SIMPLE  =                    T", "BITPIX  =                   16",
                "NAXIS   =                    2", "NAXIS1  =                  100", "NAXIS2  =                  200",
                "EXTEND  =                    T", "BSCALE  =                  1.0", "BZERO   =                  0.0",
                "BUNIT   = 'JY/BEAM '", "END")) {
            header.writeBytes(String.format("%-80s", card).getBytes(StandardCharsets.US_ASCII));
        }

        SampleDecodeResult result = run(FormatTemplates.fits(), header.toByteArray());

        assertThat(result.error()).isNull();
        assertThat(leafValues(result)).hasSize(10).contains("bitpix=BITPIX  =                   16", "end=END");
    }

    @Test
    void reportsWhichFieldTheSampleIsTooShortFor() {
        SampleDecodeResult result = run(FormatTemplates.fits(), new byte[100]);

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("bitpix").contains("offset 80");
    }

    @Test
    void userTextStaysDataEvenWhenItLooksLikePythonCode() {
        // Would be a syntax error, or run as code, if the generator didn't escape it.
        FormatDefinition def = pointRecord("\"\"\"); import os; os.system('echo pwned') #\né–😀 \\");
        def.setNotes("'''\"\"\" end");
        byte[] bytes = ByteBuffer.allocate(20).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putInt(1).putShort((short) 2).putDouble(3).array();

        SampleDecodeResult result = run(def, bytes);

        assertThat(result.error()).isNull();
        assertThat(leafValues(result)).startsWith("x=1");
    }

    @Test
    void namesTheRootAfterTheUploadedFileWithoutTrustingItAsAPath() {
        byte[] bytes = ByteBuffer.allocate(20).putInt(1).array();

        SampleDecodeResult result = runner.run(GENERATOR.generate(pointRecord(null), DrbTarget.PYTHON),
                GENERATOR.pythonFactoryClassName(pointRecord(null)), bytes, "..\\..\\evil dir/../my file.dat");

        assertThat(result.rows().get(0).name()).isEqualTo("my_file.dat");
    }

    private static SampleDecodeResult run(FormatDefinition def, byte[] bytes) {
        return runner.run(GENERATOR.generate(def, DrbTarget.PYTHON), GENERATOR.pythonFactoryClassName(def), bytes);
    }

    private static FormatDefinition pointRecord(String xDefinition) {
        FormatDefinition def = new FormatDefinition();
        def.setName("point record");
        def.setKind(FormatDefinitionKind.BYTE_LAYOUT);
        def.setDefaultByteOrder(ByteOrder.LITTLE_ENDIAN);
        TestFormats.addField(def, "x", PrimitiveType.INT32, null, null, "X", xDefinition, "m");
        TestFormats.addField(def, "y", PrimitiveType.INT16, null, null, null, null, null);
        TestFormats.addField(def, "z", PrimitiveType.FLOAT64, null, null, null, null, null);
        TestFormats.addField(def, "label", PrimitiveType.STRING, 4, null, null, null, null);
        return def;
    }

    private static List<String> leafValues(SampleDecodeResult result) {
        return result.rows().stream()
                .filter(r -> r.kind().equals("LEAF"))
                .map(r -> r.name() + "=" + r.value().trim())
                .toList();
    }
}
