package info.oais.infomodel.structure.semantic;

import java.net.URI;
import java.util.List;
import java.util.function.Function;

import org.w3c.dom.Element;

import info.oais.infomodel.structure.StructureNode;

/**
 * Reads a {@link TimeSeriesViewSpecification}'s external XML file into a
 * runtime {@link TimeSeriesMapping}. {@link TimeSeriesSemanticRepInfo#TimeSeriesSemanticRepInfo(TimeSeriesViewSpecification)}
 * is the entry point applications actually use; this class does the file
 * reading and is not usually called directly.
 *
 * <p><b>File format</b> - a series of instantaneous events, each with a
 * label:
 *
 * <pre>{@code
 * <?xml version="1.0" encoding="UTF-8"?>
 * <timeSeriesView>
 *     <rows select="children" name="event"/>
 *     <eventStart name="when"/>
 *     <columns>
 *         <column name="label" type="string"/>
 *     </columns>
 * </timeSeriesView>
 * }</pre>
 *
 * <p>{@code <rows>} follows exactly the {@code select="self"} /
 * {@code select="children" name="..."} convention {@link TableViewSpecificationReader}
 * documents in full.
 *
 * <p>{@code <eventStart>} (required) and {@code <eventEnd>} (optional - only
 * for events with a duration, not an instant) each declare a {@code name}
 * attribute: the column's name, and the row node's child name to read an
 * epoch-millisecond value from - see {@link TimeSeriesMapping#epochMillisColumn(String)},
 * which is exactly what backs both. Per {@link info.oais.infomodel.interfaces.utility.OaisIfTimeSeries}'s
 * documented contract, {@code eventStart} becomes column zero and, if
 * present, {@code eventEnd} becomes column one.
 *
 * <p>The optional trailing {@code <columns>} block, if present, declares any
 * further attribute columns exactly as {@link TableViewSpecificationReader}
 * documents for a {@code <tableView>}'s {@code <columns>}.
 */
public final class TimeSeriesViewSpecificationReader {

	private TimeSeriesViewSpecificationReader() {
	}

	/**
	 * Reads {@code specification}'s external XML file into a {@link TimeSeriesMapping}.
	 *
	 * @param specification the external file to read
	 * @return the mapping it describes
	 * @throws ViewSpecificationException if the file cannot be read or does
	 *                                    not match the expected format
	 */
	public static TimeSeriesMapping read(TimeSeriesViewSpecification specification) {
		URI location = specification.location();
		Element view = ViewSpecificationXml.parseRoot(location);

		Element rowsElement = ViewSpecificationXml.requiredSingleChild(view, "rows", location);
		Function<StructureNode, List<StructureNode>> rowSelector = ViewSpecificationXml.rowSelectorFor(rowsElement,
				location);

		Element eventStartElement = ViewSpecificationXml.requiredSingleChild(view, "eventStart", location);
		ColumnMapping eventStart = TimeSeriesMapping
				.epochMillisColumn(ViewSpecificationXml.requiredAttribute(eventStartElement, "name", location));

		List<Element> eventEndElements = ViewSpecificationXml.directChildElements(view, "eventEnd");
		ColumnMapping eventEnd = eventEndElements.isEmpty() ? null
				: TimeSeriesMapping.epochMillisColumn(
						ViewSpecificationXml.requiredAttribute(eventEndElements.get(0), "name", location));

		List<ColumnMapping> otherColumns = ViewSpecificationXml.directChildElements(view, "columns").isEmpty()
				? List.of()
				: TableViewSpecificationReader.readColumns(view, location);

		return new TimeSeriesMapping(rowSelector, eventStart, eventEnd, otherColumns);
	}
}
