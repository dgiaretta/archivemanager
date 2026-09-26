package info.oais.infomodel.structure.topcat;

import java.awt.datatransfer.DataFlavor;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import io.kaitai.struct.KaitaiStruct;

import info.oais.infomodel.implementation.DigitalObjectRefImpl;
import info.oais.infomodel.interfaces.DigitalObject;
import info.oais.infomodel.interfaces.utility.OaisIfTable;
import info.oais.infomodel.structure.ExecutableStructureRepInfo;
import info.oais.infomodel.structure.FormatSpecification;
import info.oais.infomodel.structure.StructureInterpretationException;
import info.oais.infomodel.structure.StructureInterpreterFactory;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.dfdl.DfdlFormatSpecification;
import info.oais.infomodel.structure.drb.DrbFormatSpecification;
import info.oais.infomodel.structure.kaitai.KaitaiFormatSpecification;
import info.oais.infomodel.structure.semantic.TableSemanticRepInfo;
import info.oais.infomodel.structure.semantic.TableViewSpecification;
import info.oais.infomodel.structure.semantic.ViewSpecificationException;

import uk.ac.starlink.table.ColumnInfo;
import uk.ac.starlink.table.RowListStarTable;
import uk.ac.starlink.table.StarTable;
import uk.ac.starlink.table.StoragePolicy;
import uk.ac.starlink.table.TableBuilder;
import uk.ac.starlink.table.TableFormatException;
import uk.ac.starlink.table.TableSink;
import uk.ac.starlink.util.DataSource;

/**
 * A {@link TableBuilder} that opens a data file described by a DFDL schema,
 * a Kaitai Struct generated class, or a DRB descriptor, by running it
 * through {@code oais-structure-api}'s existing engine-agnostic pipeline
 * ({@link StructureInterpreterFactory} -&gt; {@link StructureNode} -&gt;
 * {@link TableSemanticRepInfo} -&gt; {@link OaisIfTable}) rather than parsing
 * anything itself.
 *
 * <h2>Sidecar files</h2>
 * <p>DFDL/Kaitai/DRB descriptions are inherently external to the raw data
 * bytes, unlike a self-describing format such as FITS or VOTable, so this
 * builder needs a convention for finding them. For a data file at
 * {@code some/dir/foo.ext}, it looks for these siblings (same directory,
 * {@code foo} being the data file's name with its own last extension
 * stripped) -- the same {@code point.bin} + {@code point.dfdl.xsd} +
 * {@code point-table-view.xml} naming already used by this project's own
 * demo fixtures, not a new convention invented for this module:</p>
 * <ul>
 *   <li>{@code foo.dfdl.xsd} -- a DFDL schema; used via
 *       {@link DfdlFormatSpecification}.</li>
 *   <li>{@code foo.ksy.classname} -- a one-line text file naming an
 *       already-compiled, already-on-classpath Kaitai Struct generated class
 *       (see {@link KaitaiFormatSpecification}'s own Javadoc for why a
 *       runtime {@code .ksy} path alone is not enough).</li>
 *   <li>{@code foo.drb.properties} -- optional {@code factoryResolverClassName}/
 *       {@code protocolHint} properties for {@link DrbFormatSpecification}'s
 *       3-argument constructor; or {@code foo.drb} (present, even empty) to
 *       opt in to DRB's own auto-detecting no-argument constructor instead.</li>
 *   <li>{@code foo-table-view.xml} -- required alongside any of the above:
 *       the {@link TableViewSpecification} describing how to view the
 *       resulting {@link StructureNode} tree as rows and columns.</li>
 * </ul>
 * <p>Requiring an explicit sidecar for every engine -- including DRB, whose
 * underlying library can auto-detect a format with no hint at all -- keeps
 * {@link #looksLikeFile} predictable: this builder only ever claims a file
 * it has direct sidecar evidence for, so it can't out-compete some other
 * {@code TableBuilder} on an unrelated file via DRB's own content-sniffing.</p>
 *
 * <h2>Registering with TOPCAT</h2>
 * <p>Put this module's jar (and whichever of {@code oais-structure-dfdl}/
 * {@code oais-structure-kaitai}/{@code oais-structure-drb} you need) on
 * TOPCAT's classpath, then launch it with
 * {@code -Dstartable.readers=info.oais.infomodel.structure.topcat.OaisStructureTableBuilder}
 * (STIL's {@code StarTableFactory.KNOWN_BUILDERS_PROPERTY}) -- no fork or
 * patch of starjava itself is needed.</p>
 *
 * <p>Only file-backed {@link DataSource}s are supported, since sidecar
 * resolution works by resolving sibling paths next to the data file's own
 * location; a {@link DataSource} with no resolvable local path (e.g. one
 * backed directly by an in-memory stream) is declined via
 * {@link TableFormatException}.</p>
 */
public class OaisStructureTableBuilder implements TableBuilder {

    private static final String DFDL_SUFFIX = ".dfdl.xsd";
    private static final String KAITAI_CLASSNAME_SUFFIX = ".ksy.classname";
    private static final String DRB_PROPERTIES_SUFFIX = ".drb.properties";
    private static final String DRB_MARKER_SUFFIX = ".drb";
    private static final String VIEW_SUFFIX = "-table-view.xml";

    @Override
    public StarTable makeStarTable(DataSource datsrc, boolean wantRandom, StoragePolicy storagePolicy)
            throws IOException {
        Path dataPath = toPath(datsrc);
        if (!hasFormatSidecar(dataPath)) {
            throw new TableFormatException(
                    "No " + DFDL_SUFFIX + " / " + KAITAI_CLASSNAME_SUFFIX + " / " + DRB_PROPERTIES_SUFFIX
                            + " / " + DRB_MARKER_SUFFIX + " sidecar found next to " + dataPath);
        }
        Path viewPath = siblingOf(dataPath, VIEW_SUFFIX);
        if (!Files.exists(viewPath)) {
            throw new TableFormatException("No " + VIEW_SUFFIX + " sidecar (" + viewPath + ") found next to "
                    + dataPath + " -- a format sidecar alone isn't enough, this builder also needs to know "
                    + "how to view the result as rows and columns");
        }

        FormatSpecification spec = resolveFormatSpecification(dataPath);
        OaisIfTable table;
        try (InputStream in = datsrc.getInputStream()) {
            DigitalObject digitalObject = new DigitalObjectRefImpl(in);
            ExecutableStructureRepInfo structureRepInfo = new StructureInterpreterFactory().create(spec);
            StructureNode tree = structureRepInfo.apply(digitalObject);
            table = new TableSemanticRepInfo(new TableViewSpecification(viewPath.toUri())).apply(tree);
        } catch (StructureInterpretationException | ViewSpecificationException | IllegalStateException e) {
            throw new IOException("Failed to interpret " + dataPath + " via " + spec, e);
        }

        return toStarTable(table);
    }

    @Override
    public void streamStarTable(InputStream istrm, TableSink sink, String pos) throws IOException {
        throw new TableFormatException(
                "OaisStructureTableBuilder needs random access to locate its sidecar files, so it can't "
                        + "read from a bare InputStream");
    }

    @Override
    public boolean canImport(DataFlavor flavor) {
        // No drag-and-drop MIME-type story for this format -- format detection
        // here is entirely sidecar-file-based (see looksLikeFile), which drag
        // and drop can't supply.
        return false;
    }

    @Override
    public boolean looksLikeFile(String location) {
        try {
            Path path = Path.of(location);
            return hasFormatSidecar(path) && Files.exists(siblingOf(path, VIEW_SUFFIX));
        } catch (Exception e) {
            // Not a resolvable local path (e.g. a remote URL) -- sidecar
            // resolution can't work, so this isn't a file we recognise.
            return false;
        }
    }

    @Override
    public String getFormatName() {
        return "OAIS-Structure";
    }

    private static boolean hasFormatSidecar(Path dataPath) {
        return Files.exists(siblingOf(dataPath, DFDL_SUFFIX))
                || Files.exists(siblingOf(dataPath, KAITAI_CLASSNAME_SUFFIX))
                || Files.exists(siblingOf(dataPath, DRB_PROPERTIES_SUFFIX))
                || Files.exists(siblingOf(dataPath, DRB_MARKER_SUFFIX));
    }

    /** Constructs the {@link FormatSpecification} for whichever sidecar {@link #hasFormatSidecar} found, checked in this order. */
    private static FormatSpecification resolveFormatSpecification(Path dataPath) throws IOException {
        Path dfdlPath = siblingOf(dataPath, DFDL_SUFFIX);
        if (Files.exists(dfdlPath)) {
            return new DfdlFormatSpecification(dfdlPath.toUri());
        }

        Path ksyClassnamePath = siblingOf(dataPath, KAITAI_CLASSNAME_SUFFIX);
        if (Files.exists(ksyClassnamePath)) {
            String className = Files.readString(ksyClassnamePath, StandardCharsets.UTF_8).strip();
            try {
                return new KaitaiFormatSpecification(Class.forName(className).asSubclass(KaitaiStruct.class));
            } catch (ClassNotFoundException e) {
                throw new IOException("Kaitai class '" + className + "' named in " + ksyClassnamePath
                        + " is not on the classpath", e);
            } catch (ClassCastException e) {
                throw new IOException("Class '" + className + "' named in " + ksyClassnamePath
                        + " does not extend KaitaiStruct", e);
            }
        }

        Path drbPropertiesPath = siblingOf(dataPath, DRB_PROPERTIES_SUFFIX);
        if (Files.exists(drbPropertiesPath)) {
            Properties props = new Properties();
            try (InputStream in = Files.newInputStream(drbPropertiesPath)) {
                props.load(in);
            }
            String resolverClassName = props.getProperty("factoryResolverClassName",
                    "fr.gael.drb.DrbFactoryResolver");
            String protocolHint = props.getProperty("protocolHint");
            return new DrbFormatSpecification(resolverClassName, protocolHint, dataPath.toUri());
        }

        Path drbMarkerPath = siblingOf(dataPath, DRB_MARKER_SUFFIX);
        if (Files.exists(drbMarkerPath)) {
            return new DrbFormatSpecification();
        }

        // hasFormatSidecar() is always checked by makeStarTable before this is
        // called, so reaching here would mean the two disagree -- a bug here,
        // not a normal "unrecognised file" outcome.
        throw new IllegalStateException("No format sidecar found next to " + dataPath
                + " despite hasFormatSidecar() reporting one -- resolveFormatSpecification() is out of sync "
                + "with it");
    }

    private static StarTable toStarTable(OaisIfTable table) {
        int columnCount = table.getColumnCount();
        ColumnInfo[] columnInfos = new ColumnInfo[columnCount];
        for (int col = 0; col < columnCount; col++) {
            columnInfos[col] = new ColumnInfo(table.getColumnName(col), table.getColumnClass(col), null);
        }

        RowListStarTable starTable = new RowListStarTable(columnInfos);
        long rowCount = table.getRowCount();
        for (long row = 0; row < rowCount; row++) {
            Object[] values = new Object[columnCount];
            for (int col = 0; col < columnCount; col++) {
                values[col] = table.getValueAt(row, col);
            }
            starTable.addRow(values);
        }
        return starTable;
    }

    private static Path toPath(DataSource datsrc) throws IOException {
        URL url = datsrc.getURL();
        if (url == null) {
            throw new TableFormatException(
                    "OaisStructureTableBuilder needs a file-backed DataSource to locate sidecar files ("
                            + datsrc.getName() + " has none)");
        }
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException | FileSystemNotFoundException | IllegalArgumentException e) {
            throw new TableFormatException(
                    "OaisStructureTableBuilder only supports local, file-backed data sources; got " + url, e);
        }
    }

    /** {@code some/dir/foo.ext} + {@code suffix} -&gt; {@code some/dir/foo<suffix>} (the data file's own last extension is dropped first). */
    private static Path siblingOf(Path dataPath, String suffix) {
        String fileName = dataPath.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        String baseName = dot > 0 ? fileName.substring(0, dot) : fileName;
        Path parent = dataPath.getParent();
        return parent != null ? parent.resolve(baseName + suffix) : Path.of(baseName + suffix);
    }
}
