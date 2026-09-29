package info.oais.infomodel.structure.splat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URISyntaxException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import uk.ac.starlink.splat.data.SpecData;

/**
 * Exercises {@link OaisStructureSpectrumLauncher#toSpecData} end to end --
 * real bytes, through the real DFDL adapter, into a real STIL
 * {@link uk.ac.starlink.table.StarTable}, wrapped as a real SPLAT
 * {@link SpecData} -- without opening any GUI window (see
 * {@code run-in-splat.bat}, alongside these fixtures, for actually opening
 * one by hand).
 *
 * <p>Uses its own fixture -- {@code spectrum-manifest.ttl} naming
 * {@code spectrum.csv} (ten wavelength/flux rows), its DFDL schema and its
 * table view -- rather than oais-structure-topcat's
 * {@code point.bin}: SPLAT's {@code TableSpecDataImpl} requires every column
 * to be numeric (a spectrum is X/Y data, not an arbitrary table), which
 * {@code point.bin}'s "label" string column would violate.</p>
 */
class OaisStructureSpectrumLauncherTest {

    @Test
    void wrapsADfdlDescribedSpectrumAsASpecData() throws Exception {
        SpecData spectrum = OaisStructureSpectrumLauncher.toSpecData(fixture("spectrum-manifest.ttl").toString());

        assertEquals("Test spectrum", spectrum.getShortName());
        assertEquals(10, spectrum.size());
        assertEquals(4000.0, spectrum.getXData()[0], 1e-9);
        assertEquals(1.2, spectrum.getYData()[0], 1e-9);
        assertEquals(8500.0, spectrum.getXData()[9], 1e-9);
        assertEquals(0.2, spectrum.getYData()[9], 1e-9);
    }

    @Test
    void takesPathsAndUrls() {
        assertEquals("https", OaisStructureSpectrumLauncher.toUri("https://example.org/m.ttl").getScheme());
        assertEquals("file", OaisStructureSpectrumLauncher.toUri("C:\data\m.ttl").getScheme());
        assertEquals("file", OaisStructureSpectrumLauncher.toUri("data/m.ttl").getScheme());
    }

    private static Path fixture(String name) throws URISyntaxException {
        return Path.of(OaisStructureSpectrumLauncherTest.class.getResource("/" + name).toURI());
    }
}
