package info.oais.archive.manager;

import info.oais.infomodel.structure.description.ByteOrder;
import info.oais.infomodel.structure.description.PrimitiveType;
import info.oais.archive.manager.model.format.FormatDefinition;
import info.oais.archive.manager.model.format.FormatDefinitionKind;
import info.oais.archive.manager.service.format.DfdlGenerator;
import info.oais.archive.manager.service.format.DfdlSampleRunner;
import info.oais.archive.manager.service.format.FormatTemplates;
import info.oais.archive.manager.service.format.SampleDecodeResult;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trips {@link DfdlGenerator}'s output through real Apache Daffodil
 * (via {@code oais-structure-dfdl}), so a generated schema is checked for
 * actually decoding bytes, not just for being well-formed XML.
 */
class DfdlSampleRunnerTest {

    private final DfdlGenerator generator = new DfdlGenerator();
    private final DfdlSampleRunner runner = new DfdlSampleRunner();

    @Test
    void decodesALittleEndianBinaryRecord() {
        FormatDefinition def = new FormatDefinition();
        def.setName("point record");
        def.setKind(FormatDefinitionKind.BYTE_LAYOUT);
        def.setDefaultByteOrder(ByteOrder.LITTLE_ENDIAN);
        TestFormats.addField(def, "x", PrimitiveType.INT32, null, null, null, "x ordinate", null);
        TestFormats.addField(def, "y", PrimitiveType.INT32, null, null, null, "y ordinate", null);
        TestFormats.addField(def, "label", PrimitiveType.STRING, 4, null, null, "short label", null);

        byte[] bytes = ByteBuffer.allocate(12).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                .putInt(42).putInt(-7).put("abcd".getBytes(StandardCharsets.US_ASCII)).array();

        SampleDecodeResult result = runner.run(generator.generate(def), bytes);

        assertThat(result.error()).isNull();
        assertThat(leafValues(result)).containsExactly("x=42", "y=-7", "label=abcd");
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

        SampleDecodeResult result = runner.run(generator.generate(FormatTemplates.fits()), header.toByteArray());

        assertThat(result.error()).isNull();
        assertThat(leafValues(result)).hasSize(10);
        assertThat(leafValues(result).get(1)).startsWith("bitpix=BITPIX  =                   16");
    }

    @Test
    void reportsDaffodilDiagnosticsWhenTheSampleIsTooShort() {
        SampleDecodeResult result = runner.run(generator.generate(FormatTemplates.fits()), new byte[10]);

        assertThat(result.ok()).isFalse();
        assertThat(result.rows()).isEmpty();
        assertThat(result.error()).isNotBlank();
    }

    private static List<String> leafValues(SampleDecodeResult result) {
        return result.rows().stream()
                .filter(r -> r.kind().equals("LEAF"))
                .map(r -> r.name() + "=" + r.value().strip())
                .toList();
    }
}
