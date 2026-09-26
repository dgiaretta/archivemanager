package info.oais.infomodel.structure.splat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import uk.ac.starlink.splat.data.SpecData;

/**
 * Exercises {@link OaisStructureSpectrumLauncher#toSpecData} end to end --
 * real bytes, through the real DFDL adapter, into a real STIL
 * {@link uk.ac.starlink.table.StarTable}, wrapped as a real SPLAT
 * {@link SpecData} -- without opening any GUI window (see
 * {@code run-in-splat.bat}, alongside these fixtures, for actually opening
 * one by hand).
 *
 * <p>Uses its own {@code spectrum.csv}/{@code spectrum.dfdl.xsd} fixture
 * (ten wavelength/flux rows) rather than oais-structure-topcat's
 * {@code point.bin}: SPLAT's {@code TableSpecDataImpl} requires every column
 * to be numeric (a spectrum is X/Y data, not an arbitrary table), which
 * {@code point.bin}'s "label" string column would violate.</p>
 */
class OaisStructureSpectrumLauncherTest {

    private static final String SPECTRUM_CSV = "/spectrum.csv";
    private static final String SPECTRUM_DFDL_XSD = "/spectrum.dfdl.xsd";
    private static final String SPECTRUM_VIEW_XML = "/spectrum-table-view.xml";

    @TempDir
    Path tempDir;

    private Path dataPath;

    @BeforeEach
    void copyDataAndSidecars() throws IOException {
        dataPath = tempDir.resolve("spectrum.csv");
        copyResource(SPECTRUM_CSV, dataPath);
        copyResource(SPECTRUM_DFDL_XSD, tempDir.resolve("spectrum.dfdl.xsd"));
        copyResource(SPECTRUM_VIEW_XML, tempDir.resolve("spectrum-table-view.xml"));
    }

    @Test
    void wrapsADfdlDescribedSpectrumAsASpecData() throws Exception {
        SpecData spectrum = OaisStructureSpectrumLauncher.toSpecData(dataPath);

        assertEquals("spectrum.csv", spectrum.getShortName());
        assertEquals(10, spectrum.size());
        assertEquals(4000.0, spectrum.getXData()[0], 1e-9);
        assertEquals(1.2, spectrum.getYData()[0], 1e-9);
        assertEquals(8500.0, spectrum.getXData()[9], 1e-9);
        assertEquals(0.2, spectrum.getYData()[9], 1e-9);
    }

    private static void copyResource(String resourcePath, Path destination) throws IOException {
        try (InputStream in = OaisStructureSpectrumLauncherTest.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Test fixture not found on classpath: " + resourcePath);
            }
            Files.copy(in, destination);
        }
    }
}
