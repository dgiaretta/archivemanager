package info.oais.infomodel.structure.semantic;

import java.net.URI;
import java.util.Objects;

/**
 * A reference to an external XML file describing how to view some
 * {@link info.oais.infomodel.structure.StructureNode} subtree as an
 * {@link info.oais.infomodel.interfaces.utility.OaisIfTimeSeries} - row
 * selection, which column is the event start (and, optionally, event end),
 * and any further attribute columns - without hard-coding any of that in
 * Java source. See {@link TimeSeriesSemanticRepInfo#TimeSeriesSemanticRepInfo(TimeSeriesViewSpecification)}
 * and {@link TimeSeriesViewSpecificationReader} for how it is read, and that
 * class's Javadoc for the file format itself, with an example. Mirrors
 * {@link TableViewSpecification} exactly - see its Javadoc for why this is
 * an external file rather than a {@link TimeSeriesMapping} built by hand.
 *
 * @param location the external XML file's location
 */
public record TimeSeriesViewSpecification(URI location) {

	public TimeSeriesViewSpecification {
		Objects.requireNonNull(location, "location");
	}
}
