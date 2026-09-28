package info.oais.archive.manager;

import info.oais.archive.manager.service.format.KaitaiGenerator;
import info.oais.archive.manager.service.format.KaitaiSampleRunner;
import info.oais.archive.manager.service.format.SampleDecodeResult;
import info.oais.infomodel.structure.description.ByteOrder;
import info.oais.infomodel.structure.description.Expression;
import info.oais.infomodel.structure.description.FieldDescription;
import info.oais.infomodel.structure.description.FormatDescription;
import info.oais.infomodel.structure.description.Occurrence;
import info.oais.infomodel.structure.description.PrimitiveType;
import info.oais.infomodel.structure.description.RecordDescription;
import info.oais.infomodel.structure.description.Semantics;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The in-app Kaitai sample test: the bundled Kaitai Struct compiler, run as
 * a separate process, then the generated Java compiled in-process.
 */
class KaitaiSampleRunnerTest {

    private final KaitaiGenerator generator = new KaitaiGenerator();

    /** Names YAML would read as booleans, and a doc that tries to end the Java comment. */
    private static FormatDescription awkwardNames() {
        return new FormatDescription("awkward", "", ByteOrder.BIG_ENDIAN, List.of(), RecordDescription.of("awkward", List.of(
                field("no"), field("on"), field("off"), field("yes"),
                new FieldDescription("text", "text", PrimitiveType.STRING, new Expression.IntLiteral(2), null,
                        Occurrence.ONCE, Semantics.of("Label */ class X {", "Ends */ here", null)))));
    }

    private static FieldDescription field(String name) {
        return new FieldDescription(name, name, PrimitiveType.UINT8, null, null, Occurrence.ONCE, Semantics.NONE);
    }

    @Test
    void decodesWithTheBundledCompilerEvenWithAwkwardNames() {
        FormatDescription format = awkwardNames();
        SampleDecodeResult result = new KaitaiSampleRunner(120)
                .run(format, generator.generate(format), new byte[] {1, 2, 3, 4, 'o', 'k', 9});

        assertThat(result.error()).isNull();
        assertThat(result.rows()).extracting(SampleDecodeResult.TreeRow::name)
                .contains("no", "on", "off", "yes", "text");
        assertThat(result.rows()).extracting(SampleDecodeResult.TreeRow::value).contains("1", "2", "3", "4", "ok");
        assertThat(result.trailingBytes()).isEqualTo(1L);
        assertThat(result.hasPositions()).as("compiled with --debug").isTrue();
    }

    @Test
    void compilesWithTheBundledEclipseCompilerForServersWithOnlyAJre() {
        FormatDescription format = awkwardNames();
        SampleDecodeResult result = new KaitaiSampleRunner(120, true)
                .run(format, generator.generate(format), new byte[] {1, 2, 3, 4, 'o', 'k'});

        assertThat(result.error()).isNull();
        assertThat(result.rows()).extracting(SampleDecodeResult.TreeRow::value).contains("ok");
    }

    @Test
    void explainsDataThatEndsTooEarly() {
        FormatDescription format = awkwardNames();
        SampleDecodeResult result = new KaitaiSampleRunner(120).run(format, generator.generate(format), new byte[] {1, 2});

        assertThat(result.ok()).isFalse();
        assertThat(result.error()).isNotBlank();
    }
}
