package info.oais.archive.manager;

import info.oais.archive.manager.model.format.ByteOrder;
import info.oais.archive.manager.model.format.DrbTarget;
import info.oais.archive.manager.model.format.FieldType;
import info.oais.archive.manager.model.format.FormatDefinition;
import info.oais.archive.manager.model.format.FormatDefinitionKind;
import info.oais.archive.manager.model.format.FormatField;
import info.oais.archive.manager.service.format.DrbGenerator;
import info.oais.archive.manager.service.format.DrbPythonSampleRunner;
import info.oais.archive.manager.service.format.FormatTemplates;
import info.oais.archive.manager.service.format.SampleDecodeResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

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

    @BeforeAll
    static void findDrbPython() {
        String configured = System.getenv("DRB_PYTHON");
        runner = new DrbPythonSampleRunner(configured == null ? "" : configured);
        assumeTrue(runner.drbVersion().isPresent(), "drb-python not available: " + runner.notAvailableMessage());
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
        def.addField(new FormatField("x", FieldType.INT32, null, null, "X", xDefinition, "m"));
        def.addField(new FormatField("y", FieldType.INT16, null, null, null, null, null));
        def.addField(new FormatField("z", FieldType.FLOAT64, null, null, null, null, null));
        def.addField(new FormatField("label", FieldType.ASCII_STRING, 4, null, null, null, null));
        return def;
    }

    private static List<String> leafValues(SampleDecodeResult result) {
        return result.rows().stream()
                .filter(r -> r.kind().equals("LEAF"))
                .map(r -> r.name() + "=" + r.value())
                .toList();
    }
}
