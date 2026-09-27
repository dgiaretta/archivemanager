package info.oais.archive.manager;

import info.oais.archive.manager.model.format.ByteOrder;
import info.oais.archive.manager.model.format.DrbTarget;
import info.oais.archive.manager.model.format.FieldType;
import info.oais.archive.manager.model.format.FormatDefinition;
import info.oais.archive.manager.model.format.FormatDefinitionKind;
import info.oais.archive.manager.model.format.FormatField;
import info.oais.archive.manager.service.format.DrbGenerator;
import info.oais.archive.manager.service.format.DrbSampleRunner;
import info.oais.archive.manager.service.format.FormatTemplates;
import info.oais.archive.manager.service.format.SampleDecodeResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trips {@link DrbGenerator}'s generated DRB SDF schemas through GAEL's
 * real Java DRB 2.5.13, via {@code oais-structure-drb}.
 */
class DrbSampleRunnerTest {

    private final DrbGenerator generator = new DrbGenerator();
    private final DrbSampleRunner runner = new DrbSampleRunner();

    @ParameterizedTest
    @EnumSource(ByteOrder.class)
    void decodesEveryFieldTypeInEitherByteOrder(ByteOrder order) {
        FormatDefinition def = new FormatDefinition();
        def.setName("all types");
        def.setKind(FormatDefinitionKind.BYTE_LAYOUT);
        def.setDefaultByteOrder(order);
        for (FieldType type : List.of(FieldType.INT8, FieldType.UINT8, FieldType.INT16, FieldType.UINT16,
                FieldType.INT32, FieldType.UINT32, FieldType.INT64, FieldType.UINT64, FieldType.FLOAT32, FieldType.FLOAT64)) {
            def.addField(new FormatField(type.name().toLowerCase(), type, null, null, null, null, null));
        }
        def.addField(new FormatField("label", FieldType.ASCII_STRING, 4, null, null, null, null));
        def.addField(new FormatField("raw", FieldType.BYTES, 2, null, null, null, null));

        ByteBuffer bytes = ByteBuffer.allocate(48).order(order == ByteOrder.LITTLE_ENDIAN
                ? java.nio.ByteOrder.LITTLE_ENDIAN : java.nio.ByteOrder.BIG_ENDIAN);
        bytes.put((byte) -5).put((byte) 250).putShort((short) -300).putShort((short) 65000).putInt(-70000)
                .putInt((int) 4_000_000_000L).putLong(-5_000_000_000L).putLong(-1L).putFloat(1.5f).putDouble(-2.25)
                .put("abcd".getBytes(StandardCharsets.US_ASCII)).put((byte) 0xde).put((byte) 0xad);

        SampleDecodeResult result = runner.run(generator.generate(def, DrbTarget.JAVA), bytes.array());

        assertThat(result.error()).isNull();
        assertThat(leafValues(result)).containsExactly("int8=-5", "uint8=250", "int16=-300", "uint16=65000",
                "int32=-70000", "uint32=4000000000", "int64=-5000000000", "uint64=18446744073709551615",
                "float32=1.5", "float64=-2.25", "label=abcd", "raw=222", "raw=173");
        assertThat(result.rows().get(5).byteRange()).isEqualTo("bytes 6–9");
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

        SampleDecodeResult result = runner.run(generator.generate(FormatTemplates.fits(), DrbTarget.JAVA),
                header.toByteArray());

        assertThat(result.error()).isNull();
        assertThat(leafValues(result)).hasSize(10);
        assertThat(leafValues(result).get(1)).startsWith("bitpix=BITPIX  =                   16");
    }

    @Test
    void reportsWhereTheSampleIsTooShort() {
        SampleDecodeResult result = runner.run(generator.generate(FormatTemplates.fits(), DrbTarget.JAVA), new byte[100]);

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).contains("The data is 100 bytes");
    }

    @Test
    void keepsXmlSpecialCharactersInDefinitionsAsText() {
        FormatDefinition def = new FormatDefinition();
        def.setName("escaping -- check");
        def.setKind(FormatDefinitionKind.BYTE_LAYOUT);
        def.setNotes("notes with <markup> & \"quotes\"");
        def.addField(new FormatField("x", FieldType.UINT8, null, null, null, "</xs:documentation><evil/> & more", null));

        SampleDecodeResult result = runner.run(generator.generate(def, DrbTarget.JAVA), new byte[] {7});

        assertThat(result.error()).isNull();
        assertThat(leafValues(result)).containsExactly("x=7");
    }

    private static List<String> leafValues(SampleDecodeResult result) {
        return result.rows().stream()
                .filter(r -> r.kind().equals("LEAF"))
                .map(r -> r.name() + "=" + r.value().strip())
                .toList();
    }
}
