package info.oais.infomodel.structure.splat;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

import javax.swing.SwingUtilities;

import info.oais.infomodel.structure.manifest.DescribedData;
import info.oais.infomodel.structure.manifest.RepInfoManifest;
import info.oais.infomodel.structure.topcat.OaisStructureTableBuilder;

import uk.ac.starlink.splat.data.SpecData;
import uk.ac.starlink.splat.data.SpecDataFactory;
import uk.ac.starlink.splat.iface.SplatBrowser;
import uk.ac.starlink.splat.util.SplatException;
import uk.ac.starlink.table.StarTable;

/**
 * Opens data described by a Representation Information manifest (see
 * oais-structure-manifest's {@link RepInfoManifest}: a Turtle excerpt naming
 * the data file, its structure descriptions, its table view and what its
 * elements mean, every file explicitly) as a spectrum in a live SPLAT window.
 * The manifest can be a file or a URL -- e.g. the archive's address for a
 * Data Object's manifest -- with {@code #dataObject} after it when it
 * describes more than one.
 *
 * <p>Unlike TOPCAT, SPLAT has no plugin-registration hook equivalent to
 * STIL's {@code startable.readers} system property: its own format dispatch
 * is a hard-coded switch over known formats
 * ({@code uk.ac.starlink.splat.data.NameParser}), so it can't be told about
 * an arbitrary new {@code TableBuilder} at the command line. Instead this
 * class builds the {@link StarTable} itself with
 * {@link OaisStructureTableBuilder#open} (the same pipeline TOPCAT uses:
 * the first usable structure description, the table view, and each column's
 * units from the manifest), wraps it as a {@link SpecData} via
 * {@link SpecDataFactory#get(StarTable, String, String)}, and adds it to a
 * running {@link SplatBrowser} via {@link SplatBrowser#addSpectrum(SpecData)}.
 * The table view has to give numeric columns only (a spectrum's X and Y):
 * SPLAT's table spectra don't accept text columns.</p>
 */
public final class OaisStructureSpectrumLauncher {

    private OaisStructureSpectrumLauncher() {
    }

    /**
     * Builds a {@link SpecData} from the data a manifest describes.
     *
     * @param manifestLocation a manifest file's path or URL, optionally followed by {@code #dataObject}
     */
    public static SpecData toSpecData(String manifestLocation) throws IOException, SplatException {
        int hash = manifestLocation.indexOf('#');
        String where = hash < 0 ? manifestLocation : manifestLocation.substring(0, hash);
        String which = hash < 0 ? null : manifestLocation.substring(hash + 1);
        DescribedData data;
        try {
            data = RepInfoManifest.read(toUri(where)).select(which);
        } catch (RepInfoManifest.ManifestException e) {
            throw new IOException(e.getMessage(), e);
        }
        StarTable table = OaisStructureTableBuilder.open(data);
        return SpecDataFactory.getInstance().get(table, data.name(), manifestLocation);
    }

    /** A URL as it is; anything else (including a Windows path like {@code C:\...}) as a local file. */
    static URI toUri(String location) {
        int colon = location.indexOf(':');
        if (colon > 1 && location.substring(0, colon).matches("[A-Za-z][A-Za-z0-9+.-]*")) {
            return URI.create(location);
        }
        return Path.of(location).toAbsolutePath().toUri();
    }

    /** Opens a SPLAT window with the data a manifest describes already loaded as a spectrum. */
    public static void main(String[] args) throws IOException, SplatException {
        if (args.length != 1) {
            System.err.println("usage: OaisStructureSpectrumLauncher <manifest file or URL>[#dataObject]");
            System.exit(2);
        }
        SpecData spectrum = toSpecData(args[0]);
        SwingUtilities.invokeLater(() -> {
            SplatBrowser browser = new SplatBrowser();
            browser.setVisible(true);
            browser.addSpectrum(spectrum);
        });
    }
}
