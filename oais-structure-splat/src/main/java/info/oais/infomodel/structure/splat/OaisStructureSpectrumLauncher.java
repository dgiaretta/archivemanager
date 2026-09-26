package info.oais.infomodel.structure.splat;

import java.io.IOException;
import java.nio.file.Path;

import javax.swing.SwingUtilities;

import info.oais.infomodel.structure.topcat.OaisStructureTableBuilder;

import uk.ac.starlink.splat.data.SpecData;
import uk.ac.starlink.splat.data.SpecDataFactory;
import uk.ac.starlink.splat.iface.SplatBrowser;
import uk.ac.starlink.splat.util.SplatException;
import uk.ac.starlink.table.StarTable;
import uk.ac.starlink.table.StoragePolicy;
import uk.ac.starlink.util.FileDataSource;

/**
 * Opens a DFDL/Kaitai/DRB-described data file -- using the same sidecar
 * convention as {@link OaisStructureTableBuilder}, oais-structure-topcat's
 * TOPCAT plugin -- as a spectrum in a live SPLAT window.
 *
 * <p>Unlike TOPCAT, SPLAT has no plugin-registration hook equivalent to
 * STIL's {@code startable.readers} system property: its own format dispatch
 * is a hard-coded switch over known formats
 * ({@code uk.ac.starlink.splat.data.NameParser}), so it can't be told about
 * an arbitrary new {@code TableBuilder} at the command line. Instead this
 * class builds the {@link StarTable} itself, reusing
 * {@link OaisStructureTableBuilder#makeStarTable} directly (the same engine
 * dispatch TOPCAT uses), wraps it as a {@link SpecData} via
 * {@link SpecDataFactory#get(StarTable, String, String)}, and adds it to a
 * running {@link SplatBrowser} via {@link SplatBrowser#addSpectrum(SpecData)}
 * -- confirmed against SplatBrowser's own source as the supported way to
 * hand it a programmatically-built spectrum.</p>
 */
public final class OaisStructureSpectrumLauncher {

    private OaisStructureSpectrumLauncher() {
    }

    /**
     * Builds a {@link SpecData} from a sidecar-described data file, via the
     * same {@link OaisStructureTableBuilder} pipeline oais-structure-topcat
     * uses for TOPCAT.
     */
    public static SpecData toSpecData(Path dataPath) throws IOException, SplatException {
        StarTable table = new OaisStructureTableBuilder()
                .makeStarTable(new FileDataSource(dataPath.toFile()), false, StoragePolicy.PREFER_MEMORY);
        String name = dataPath.getFileName().toString();
        return SpecDataFactory.getInstance().get(table, name, dataPath.toString());
    }

    /** Opens a SPLAT window with the given sidecar-described data file already loaded as a spectrum. */
    public static void main(String[] args) throws IOException, SplatException {
        if (args.length != 1) {
            System.err.println("usage: OaisStructureSpectrumLauncher <sidecar-described-data-file>");
            System.exit(2);
        }
        SpecData spectrum = toSpecData(Path.of(args[0]));
        SwingUtilities.invokeLater(() -> {
            SplatBrowser browser = new SplatBrowser();
            browser.setVisible(true);
            browser.addSpectrum(spectrum);
        });
    }
}
