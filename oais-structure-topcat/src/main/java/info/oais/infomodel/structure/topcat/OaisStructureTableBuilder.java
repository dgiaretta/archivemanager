package info.oais.infomodel.structure.topcat;

import java.awt.datatransfer.DataFlavor;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import info.oais.infomodel.interfaces.utility.OaisIfTable;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.manifest.DescribedData;
import info.oais.infomodel.structure.manifest.ElementMeaning;
import info.oais.infomodel.structure.manifest.RepInfoManifest;
import info.oais.infomodel.structure.manifest.ViewDescription;
import info.oais.infomodel.structure.semantic.ColumnMapping;
import info.oais.infomodel.structure.semantic.TableMapping;
import info.oais.infomodel.structure.semantic.TableSemanticRepInfo;
import info.oais.infomodel.structure.semantic.TableViewSpecification;
import info.oais.infomodel.structure.semantic.TableViewSpecificationReader;
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
 * A {@link TableBuilder} for TOPCAT/STIL that opens data through its OAIS
 * Representation Information: a Representation Information manifest (see
 * oais-structure-manifest's {@link RepInfoManifest}) -- a Turtle excerpt
 * naming the data file, its structure descriptions (DFDL, Kaitai Struct, DRB
 * SDF: equivalent alternatives, any one of which will do), a table view
 * specification, and what its elements mean. Nothing is found by file name:
 * every file is named in the manifest, relative to it or as a URL, so a
 * manifest can be opened from disk or straight from the archive's web
 * address for a Data Object.
 *
 * <p>The first structure alternative whose engine is available is used, in
 * the order DFDL, DRB SDF, Kaitai Struct (whose generated class must be on the
 * classpath), DRB's own format recognition. The data is decoded with it,
 * viewed as rows and columns by the table view, and each column's units and
 * description come from the view, else from the element semantics in the
 * manifest (matched by structural path).</p>
 *
 * <p>A manifest describing several Data Objects is opened with the one to
 * use after a {@code #}, e.g. {@code station.ttl#readings}.</p>
 *
 * <h2>Registering with TOPCAT</h2>
 * <p>Put this module's jar (and its dependencies) on TOPCAT's classpath, then
 * launch it with
 * {@code -Dstartable.readers=info.oais.infomodel.structure.topcat.OaisStructureTableBuilder}
 * (STIL's {@code StarTableFactory.KNOWN_BUILDERS_PROPERTY}) -- no fork or
 * patch of starjava itself is needed. Choose the format {@code OAIS-RepInfo}
 * when loading a manifest by URL; a manifest file on disk is also recognised
 * by its content.</p>
 */
public class OaisStructureTableBuilder implements TableBuilder {

	/** How much of a file {@link #looksLikeFile} reads to recognise a manifest. */
	private static final int SNIFF_BYTES = 64 * 1024;

	/** A manifest is a small text file; anything larger is something else. */
	private static final int MAX_MANIFEST_BYTES = 4 * 1024 * 1024;

	@Override
	public StarTable makeStarTable(DataSource datsrc, boolean wantRandom, StoragePolicy storagePolicy)
			throws IOException {
		byte[] content;
		try (InputStream in = datsrc.getInputStream()) {
			content = in.readNBytes(MAX_MANIFEST_BYTES + 1);
		}
		if (content.length > MAX_MANIFEST_BYTES
				|| !RepInfoManifest.looksLikeManifest(new String(content, StandardCharsets.UTF_8))) {
			throw new TableFormatException(datsrc.getName() + " isn't a Representation Information manifest");
		}
		URL url = datsrc.getURL();
		if (url == null) {
			throw new TableFormatException("A Representation Information manifest names its files relative to its "
					+ "own location, so it has to be opened from a file or URL (" + datsrc.getName() + " has none)");
		}
		DescribedData data;
		try {
			URI location = url.toURI();
			URI base = new URI(location.getScheme(), location.getSchemeSpecificPart(), null);
			data = RepInfoManifest.read(new java.io.ByteArrayInputStream(content), base).select(datsrc.getPosition());
		} catch (URISyntaxException | RepInfoManifest.ManifestException e) {
			throw new TableFormatException(e.getMessage(), e);
		}
		return open(data);
	}

	/**
	 * Decodes the data {@code data} describes and views it as a table, with
	 * its columns' units and descriptions -- the whole pipeline, for callers
	 * other than TOPCAT (e.g. SPLAT, or a server writing VOTable).
	 */
	public static StarTable open(DescribedData data) throws IOException {
		List<Path> temporary = new ArrayList<>();
		try {
			ViewDescription view = data.view(ViewDescription.TABLE).orElseThrow(() -> new TableFormatException(
					"The manifest gives no table view for " + data.name() + ": it needs an im:ViewSpecification with "
							+ "im:viewKind \"table\" to know how to show the data as rows and columns"));
			StructureNode tree = RepInfoDecoder.decode(data, temporary);
			Path viewFile = RepInfoDecoder.local(view.location(), ".xml", temporary);
			TableMapping mapping = withMeanings(
					TableViewSpecificationReader.read(new TableViewSpecification(viewFile.toUri())), rowName(viewFile), data);
			OaisIfTable table;
			try {
				table = new TableSemanticRepInfo(mapping).apply(tree);
			} catch (ViewSpecificationException | IllegalStateException e) {
				throw new IOException("Failed to view " + data.data() + " as a table: " + e.getMessage(), e);
			}
			return toStarTable(table, mapping.columns());
		} finally {
			RepInfoDecoder.deleteAll(temporary);
		}
	}

	/** The table view's row element name ({@code <rows name="...">}), which qualifies column meanings; or null. */
	private static String rowName(Path view) {
		try {
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			NodeList rows = factory.newDocumentBuilder().parse(view.toFile()).getElementsByTagName("rows");
			String name = rows.getLength() == 0 ? "" : ((Element) rows.item(0)).getAttribute("name");
			return name.isBlank() ? null : name;
		} catch (Exception e) {
			return null;
		}
	}

	/** Each column's units and description from the manifest's element semantics, where the view gives none. */
	private static TableMapping withMeanings(TableMapping mapping, String rowName, DescribedData data) {
		List<ColumnMapping> columns = mapping.columns().stream().map(c -> data.meaningOf(rowName, c.name())
				.map(m -> c.withMetadataDefaults(m.units(), description(m), null)).orElse(c)).toList();
		return new TableMapping(mapping.rowSelector(), columns);
	}

	private static String description(ElementMeaning m) {
		if (m.label() != null && m.definition() != null) {
			return m.label() + ": " + m.definition();
		}
		return m.label() != null ? m.label() : m.definition();
	}

	private static StarTable toStarTable(OaisIfTable table, List<ColumnMapping> columns) {
		int columnCount = table.getColumnCount();
		ColumnInfo[] columnInfos = new ColumnInfo[columnCount];
		for (int col = 0; col < columnCount; col++) {
			ColumnMapping mapping = col < columns.size() ? columns.get(col) : null;
			columnInfos[col] = new ColumnInfo(table.getColumnName(col), table.getColumnClass(col),
					mapping == null ? null : mapping.description());
			if (mapping != null && mapping.unit() != null) {
				columnInfos[col].setUnitString(mapping.unit());
			}
			if (mapping != null && mapping.ucd() != null) {
				columnInfos[col].setUCD(mapping.ucd());
			}
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

	@Override
	public void streamStarTable(InputStream istrm, TableSink sink, String pos) throws IOException {
		throw new TableFormatException("A Representation Information manifest names its files relative to its own "
				+ "location, so it can't be read from a bare stream");
	}

	@Override
	public boolean canImport(DataFlavor flavor) {
		return false;
	}

	/** A local file whose content is a Representation Information manifest, whatever it's called. */
	@Override
	public boolean looksLikeFile(String location) {
		try {
			Path path = Path.of(location.replaceFirst("#.*$", ""));
			if (!Files.isRegularFile(path)) {
				return false;
			}
			try (InputStream in = Files.newInputStream(path)) {
				return RepInfoManifest.looksLikeManifest(new String(in.readNBytes(SNIFF_BYTES), StandardCharsets.UTF_8));
			}
		} catch (Exception e) {
			return false;
		}
	}

	@Override
	public String getFormatName() {
		return "OAIS-RepInfo";
	}
}
