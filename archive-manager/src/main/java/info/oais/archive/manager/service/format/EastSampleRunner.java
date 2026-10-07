package info.oais.archive.manager.service.format;

import info.oais.infomodel.structure.east.EastException;
import info.oais.infomodel.structure.east.EastFormatSpecification;
import info.oais.infomodel.structure.east.EastStructureRepInfo;
import org.springframework.stereotype.Component;

/**
 * Runs an EAST description against a sample file with the EAST interpreter
 * ({@code oais-structure-east}), in-process, for RepInfo Tools' "Test
 * against a sample file". The decoded tree is shown as the interpreter gives
 * it - EAST's own records, arrays and values - not lined up with the element
 * tree, since an EAST description can say more than the tree.
 */
@Component
public class EastSampleRunner {

    public SampleDecodeResult run(String east, byte[] sample) {
        try {
            EastStructureRepInfo repInfo = new EastStructureRepInfo(EastFormatSpecification.ofText(east));
            return SampleDecodeResult.of(repInfo.apply(sample));
        } catch (EastException e) {
            return SampleDecodeResult.failure("The EAST description can't be used: " + e.getMessage());
        } catch (RuntimeException e) {
            return SampleDecodeResult.failure(e.getMessage() != null ? e.getMessage() : e.toString());
        }
    }
}
