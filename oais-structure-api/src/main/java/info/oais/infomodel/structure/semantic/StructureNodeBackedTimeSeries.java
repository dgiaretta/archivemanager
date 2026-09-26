package info.oais.infomodel.structure.semantic;

import java.util.List;

import info.oais.infomodel.interfaces.utility.OaisIfTimeSeries;
import info.oais.infomodel.structure.StructureNode;

/**
 * {@link StructureNodeBackedTable} typed as an {@link OaisIfTimeSeries} - see
 * {@link TimeSeriesSemanticRepInfo}. Adds nothing over its base class: the
 * caller-supplied {@link ColumnMapping}s (assembled by
 * {@link TimeSeriesMapping#toTableMapping()}) are responsible for column
 * zero (and optionally column one) producing
 * {@link info.oais.infomodel.interfaces.utility.OaisIfTimeStamp} values, per
 * {@code OaisIfTimeSeries}'s documented contract.
 */
final class StructureNodeBackedTimeSeries extends StructureNodeBackedTable implements OaisIfTimeSeries {

	StructureNodeBackedTimeSeries(List<StructureNode> rows, List<ColumnMapping> columns) {
		super(rows, columns);
	}
}
