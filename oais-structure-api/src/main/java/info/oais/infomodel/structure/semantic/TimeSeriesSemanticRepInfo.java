package info.oais.infomodel.structure.semantic;

import java.util.Objects;

import info.oais.infomodel.interfaces.utility.OaisIfTimeSeries;
import info.oais.infomodel.structure.StructureNode;

/**
 * An {@link ExecutableSemanticRepInfo} that views a {@link StructureNode}
 * subtree as an {@link OaisIfTimeSeries} - see {@link TimeSeriesMapping} and
 * {@link TableSemanticRepInfo}, which this otherwise mirrors exactly,
 * including its external-XML-file constructor.
 */
public class TimeSeriesSemanticRepInfo extends AbstractExecutableSemanticRepInfo<OaisIfTimeSeries> {

	private final TimeSeriesMapping mapping;

	public TimeSeriesSemanticRepInfo(TimeSeriesMapping mapping) {
		super();
		this.mapping = Objects.requireNonNull(mapping, "mapping");
	}

	/**
	 * Builds this instance's {@link TimeSeriesMapping} by reading
	 * {@code specification}'s external XML file - see
	 * {@link TimeSeriesViewSpecificationReader} for the file format - rather
	 * than requiring one to be constructed programmatically. Reads the file
	 * immediately, so a malformed or unreadable specification fails fast, at
	 * construction, rather than on first {@link #apply}.
	 *
	 * @param specification the external file describing this time series'
	 *                       rows and columns
	 * @throws ViewSpecificationException if the file cannot be read or does
	 *                                    not match the expected format
	 */
	public TimeSeriesSemanticRepInfo(TimeSeriesViewSpecification specification) {
		this(TimeSeriesViewSpecificationReader.read(specification));
	}

	public TimeSeriesMapping getMapping() {
		return mapping;
	}

	@Override
	protected OaisIfTimeSeries doApply(StructureNode root) {
		TableMapping tableMapping = mapping.toTableMapping();
		return new StructureNodeBackedTimeSeries(tableMapping.rowSelector().apply(root), tableMapping.columns());
	}
}
