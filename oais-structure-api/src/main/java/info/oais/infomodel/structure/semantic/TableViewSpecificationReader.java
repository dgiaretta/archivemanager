package info.oais.infomodel.structure.semantic;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.w3c.dom.Element;

import info.oais.infomodel.structure.StructureNode;

/**
 * Reads a {@link TableViewSpecification}'s external XML file into a runtime
 * {@link TableMapping} - the same {@link ColumnMapping}-list-plus-row-selector
 * shape a caller could otherwise build by hand in Java, just described
 * outside Java source instead. {@link TableSemanticRepInfo#TableSemanticRepInfo(TableViewSpecification)}
 * is the entry point applications actually use; this class does the file
 * reading and is not usually called directly.
 *
 * <p><b>File format</b> - a minimal example, for a single, non-repeated
 * record (its one row is the root node itself):
 *
 * <pre>{@code
 * <?xml version="1.0" encoding="UTF-8"?>
 * <tableView>
 *     <rows select="self"/>
 *     <columns>
 *         <column name="x" type="int"/>
 *         <column name="y" type="int"/>
 *         <column name="label" type="string"/>
 *     </columns>
 * </tableView>
 * }</pre>
 *
 * <p>For a tree that represents a repeated element as several same-named
 * siblings (the DFDL/DRB convention - see
 * {@link info.oais.infomodel.structure.StructureNodeKind}'s Javadoc on ARRAY
 * vs. repeated COMPOSITE siblings), use {@code select="children"} with a
 * {@code name} attribute naming the repeated child:
 *
 * <pre>{@code
 * <rows select="children" name="point"/>
 * }</pre>
 *
 * <p>For a tree that represents repetition as a single {@code ARRAY} node
 * instead (the Kaitai Struct convention - a {@code repeat:} field), use
 * {@code select="array"} with a {@code name} attribute naming that field;
 * its indexed elements become the rows:
 *
 * <pre>{@code
 * <rows select="array" name="points"/>
 * }</pre>
 *
 * <p>Each {@code <column>} declares:
 * <ul>
 * <li>{@code name} (required) - the column's name, and, unless {@code child}
 * is also given, the row node's child name to read the value from;</li>
 * <li>{@code type} (required) - the column's declared value class: one of the
 * short names {@code int}, {@code long}, {@code short}, {@code byte},
 * {@code double}, {@code float}, {@code boolean}, {@code string},
 * {@code biginteger}, {@code bigdecimal}, {@code object} (case-insensitive),
 * or a fully-qualified Java class name resolved via
 * {@link Class#forName(String)};</li>
 * <li>{@code child} (optional) - the row node's child name to read the value
 * from, when it differs from {@code name}.</li>
 * </ul>
 *
 * <p>Every value is read from a row via {@link StructureNode#child(String)}
 * {@code .flatMap(}{@link StructureNode#getValue()}{@code )} - the same
 * convention {@link ColumnMapping#ofChild} uses - so, like the rest of this
 * package, an external table view description only ever refers to
 * {@code StructureNode} names, never to DFDL, Kaitai Struct or DRB directly.
 * Coercing a mismatched runtime value (e.g. raw text where {@code int} was
 * declared) to the declared type happens later, in
 * {@link StructureNodeBackedTable}, not here.</p>
 *
 * <p>The underlying XML-reading mechanics (parsing the file, the
 * {@code <rows select="...">} convention, resolving a {@code type} name to a
 * {@link Class}) live in the shared, package-private {@link ViewSpecificationXml},
 * along with this class's {@code readTableMapping}/{@code readColumns}
 * helpers below, which {@link TimeSeriesViewSpecificationReader} and
 * {@link VectorViewSpecificationReader} also call, since a time series or
 * vector view's optional trailing {@code <columns>} block is read exactly
 * the same way a table view's is.</p>
 */
public final class TableViewSpecificationReader {

	private TableViewSpecificationReader() {
	}

	/**
	 * Reads {@code specification}'s external XML file into a {@link TableMapping}.
	 *
	 * @param specification the external file to read
	 * @return the mapping it describes
	 * @throws ViewSpecificationException if the file cannot be read or does
	 *                                    not match the expected format
	 */
	public static TableMapping read(TableViewSpecification specification) {
		Element tableView = ViewSpecificationXml.parseRoot(specification.location());
		return readTableMapping(tableView, specification.location());
	}

	/**
	 * Reads a {@code <tableView>}-shaped element - the {@code <tableView>}
	 * itself, or any other element with the same {@code <rows>}/{@code <columns>}
	 * children - into a {@link TableMapping}. Package-private: shared with the
	 * other view specification readers so a {@code <rows>} plus optional
	 * {@code <columns>} block is only ever parsed in one place.
	 */
	static TableMapping readTableMapping(Element viewElement, URI location) {
		Element rowsElement = ViewSpecificationXml.requiredSingleChild(viewElement, "rows", location);
		Function<StructureNode, List<StructureNode>> rowSelector = ViewSpecificationXml.rowSelectorFor(rowsElement,
				location);
		List<ColumnMapping> columns = readColumns(viewElement, location);
		return new TableMapping(rowSelector, columns);
	}

	/**
	 * Reads {@code viewElement}'s {@code <columns>} child (which must declare
	 * at least one {@code <column>}) into an ordered list of
	 * {@link ColumnMapping}s.
	 */
	static List<ColumnMapping> readColumns(Element viewElement, URI location) {
		Element columnsElement = ViewSpecificationXml.requiredSingleChild(viewElement, "columns", location);
		List<Element> columnElements = ViewSpecificationXml.directChildElements(columnsElement, "column");
		if (columnElements.isEmpty()) {
			throw new ViewSpecificationException("<columns> must declare at least one <column> in " + location);
		}
		List<ColumnMapping> columns = new ArrayList<>();
		for (Element columnElement : columnElements) {
			columns.add(ViewSpecificationXml.columnMappingFor(columnElement, location));
		}
		return columns;
	}
}
