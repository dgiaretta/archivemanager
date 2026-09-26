package info.oais.infomodel.structure.semantic;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import org.w3c.dom.Element;

import info.oais.infomodel.interfaces.utility.GeometryKind;
import info.oais.infomodel.structure.StructureNode;

/**
 * Reads a {@link VectorViewSpecification}'s external XML file into a
 * runtime {@link VectorMapping}. {@link VectorSemanticRepInfo#VectorSemanticRepInfo(VectorViewSpecification)}
 * is the entry point applications actually use; this class does the file
 * reading and is not usually called directly.
 *
 * <p><b>File format</b> - one row per feature, each a two-vertex line
 * (a {@code LINE_STRING}) plus a label attribute:
 *
 * <pre>{@code
 * <?xml version="1.0" encoding="UTF-8"?>
 * <vectorView>
 *     <rows select="children" name="feature"/>
 *     <geometry name="shape" kind="LINE_STRING" dimension="2">
 *         <coordinates select="children" name="vertex">
 *             <ordinate child="x"/>
 *             <ordinate child="y"/>
 *         </coordinates>
 *     </geometry>
 *     <columns>
 *         <column name="label" type="string"/>
 *     </columns>
 * </vectorView>
 * }</pre>
 *
 * <p>{@code <rows>} follows exactly the {@code select="self"} /
 * {@code select="children" name="..."} convention {@link TableViewSpecificationReader}
 * documents in full - here, one row per feature.
 *
 * <p>{@code <geometry>} (required) becomes column zero, per
 * {@link info.oais.infomodel.interfaces.utility.OaisIfVector}'s documented
 * contract, and declares:
 * <ul>
 * <li>{@code name} (required) - the geometry column's name;</li>
 * <li>{@code kind} (required) - one of {@link GeometryKind}'s constants
 * (e.g. {@code POINT}, {@code LINE_STRING}, {@code POLYGON}), case-insensitive;</li>
 * <li>{@code dimension} (required) - {@code 2} or {@code 3}, the number of
 * ordinates in each coordinate tuple.</li>
 * </ul>
 *
 * <p>Its required {@code <coordinates>} child selects, per feature row, the
 * ordered vertex nodes contributing one coordinate tuple each - again the
 * {@code select="self"}/{@code select="children" name="..."} convention, so
 * {@code select="self"} gives a single-tuple geometry (a {@code POINT}
 * feature whose own children are its ordinates) while
 * {@code select="children" name="vertex"} gives one tuple per repeated
 * {@code vertex} child (a {@code LINE_STRING}/{@code POLYGON} feature). Its
 * {@code <ordinate child="...">} children, in document order, name each
 * vertex node's child to read one ordinate's numeric value from; there must
 * be exactly {@code dimension} of them.
 *
 * <p>The optional trailing {@code <columns>} block, if present, declares any
 * further feature-attribute columns exactly as {@link TableViewSpecificationReader}
 * documents for a {@code <tableView>}'s {@code <columns>}.
 */
public final class VectorViewSpecificationReader {

	private VectorViewSpecificationReader() {
	}

	/**
	 * Reads {@code specification}'s external XML file into a {@link VectorMapping}.
	 *
	 * @param specification the external file to read
	 * @return the mapping it describes
	 * @throws ViewSpecificationException if the file cannot be read or does
	 *                                    not match the expected format
	 */
	public static VectorMapping read(VectorViewSpecification specification) {
		URI location = specification.location();
		Element view = ViewSpecificationXml.parseRoot(location);

		Element rowsElement = ViewSpecificationXml.requiredSingleChild(view, "rows", location);
		Function<StructureNode, List<StructureNode>> rowSelector = ViewSpecificationXml.rowSelectorFor(rowsElement,
				location);

		Element geometryElement = ViewSpecificationXml.requiredSingleChild(view, "geometry", location);
		ColumnMapping geometryColumn = geometryColumnFor(geometryElement, location);

		List<ColumnMapping> attributeColumns = ViewSpecificationXml.directChildElements(view, "columns").isEmpty()
				? List.of()
				: TableViewSpecificationReader.readColumns(view, location);

		return new VectorMapping(rowSelector, geometryColumn, attributeColumns);
	}

	private static ColumnMapping geometryColumnFor(Element geometryElement, URI location) {
		String name = ViewSpecificationXml.requiredAttribute(geometryElement, "name", location);
		GeometryKind kind = geometryKindFor(ViewSpecificationXml.requiredAttribute(geometryElement, "kind", location),
				location);
		int dimension = dimensionFor(ViewSpecificationXml.requiredAttribute(geometryElement, "dimension", location),
				location);

		Element coordinatesElement = ViewSpecificationXml.requiredSingleChild(geometryElement, "coordinates",
				location);
		Function<StructureNode, List<StructureNode>> vertexSelector = ViewSpecificationXml
				.rowSelectorFor(coordinatesElement, location);

		List<Element> ordinateElements = ViewSpecificationXml.directChildElements(coordinatesElement, "ordinate");
		if (ordinateElements.isEmpty()) {
			throw new ViewSpecificationException("<coordinates> must declare at least one <ordinate> in " + location);
		}
		List<String> ordinateChildNames = new ArrayList<>();
		for (Element ordinateElement : ordinateElements) {
			ordinateChildNames.add(ViewSpecificationXml.requiredAttribute(ordinateElement, "child", location));
		}
		if (ordinateChildNames.size() != dimension) {
			throw new ViewSpecificationException("<geometry dimension=\"" + dimension + "\"> must have exactly "
					+ dimension + " <ordinate> children in <coordinates>, but found " + ordinateChildNames.size()
					+ " in " + location);
		}

		Function<StructureNode, double[][]> coordinateSelector = row -> {
			List<StructureNode> vertices = vertexSelector.apply(row);
			double[][] tuples = new double[vertices.size()][];
			for (int i = 0; i < vertices.size(); i++) {
				tuples[i] = ordinatesFor(vertices.get(i), ordinateChildNames, location);
			}
			return tuples;
		};

		return VectorMapping.geometryColumn(name, kind, coordinateSelector, dimension);
	}

	private static double[] ordinatesFor(StructureNode vertex, List<String> ordinateChildNames, URI location) {
		double[] ordinates = new double[ordinateChildNames.size()];
		for (int i = 0; i < ordinateChildNames.size(); i++) {
			String childName = ordinateChildNames.get(i);
			Object value = vertex.child(childName).flatMap(StructureNode::getValue).orElse(null);
			if (!(value instanceof Number number)) {
				throw new ViewSpecificationException("<ordinate child=\"" + childName
						+ "\"> did not produce a numeric value in " + location);
			}
			ordinates[i] = number.doubleValue();
		}
		return ordinates;
	}

	private static GeometryKind geometryKindFor(String kindName, URI location) {
		try {
			return GeometryKind.valueOf(kindName.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new ViewSpecificationException(
					"Unknown geometry kind \"" + kindName + "\" in " + location + " (expected one of "
							+ List.of(GeometryKind.values()) + ")",
					e);
		}
	}

	private static int dimensionFor(String dimensionText, URI location) {
		try {
			int dimension = Integer.parseInt(dimensionText);
			if (dimension != 2 && dimension != 3) {
				throw new NumberFormatException(dimensionText);
			}
			return dimension;
		} catch (NumberFormatException e) {
			throw new ViewSpecificationException(
					"<geometry dimension=\"" + dimensionText + "\"> must be \"2\" or \"3\" in " + location, e);
		}
	}
}
