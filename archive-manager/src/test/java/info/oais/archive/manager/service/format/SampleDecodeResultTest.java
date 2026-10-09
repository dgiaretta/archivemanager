package info.oais.archive.manager.service.format;

import info.oais.infomodel.structure.DefaultStructureNode;
import info.oais.infomodel.structure.StructureNodeKind;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SampleDecodeResultTest {

    @Test
    void showsRealsWithoutTrailingZerosAndNotANumberAsItIs() {
        assertThat(SampleDecodeResult.describe(2.50)).isEqualTo("2.5");
        assertThat(SampleDecodeResult.describe(0.73671484f)).isEqualTo("0.73671484");
        assertThat(SampleDecodeResult.describe(Float.NaN)).isEqualTo("NaN");
        assertThat(SampleDecodeResult.describe(Double.NEGATIVE_INFINITY)).isEqualTo("-Infinity");
        SampleDecodeResult result = SampleDecodeResult.of(DefaultStructureNode.builder("r", StructureNodeKind.COMPOSITE)
                .addChild(DefaultStructureNode.leaf("density", Float.NaN)).build());
        assertThat(result.ok()).isTrue();
        assertThat(result.rows().get(1).value()).isEqualTo("NaN");
    }
}
