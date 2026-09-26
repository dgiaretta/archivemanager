package info.oais.infomodel.structure.semantic;

import java.util.List;

import info.oais.infomodel.interfaces.utility.OaisIfVector;
import info.oais.infomodel.structure.StructureNode;

/**
 * {@link StructureNodeBackedTable} typed as an {@link OaisIfVector} - see
 * {@link VectorSemanticRepInfo}. Adds nothing over its base class: the
 * caller-supplied {@link ColumnMapping}s (assembled by
 * {@link VectorMapping#toTableMapping()}) are responsible for column zero
 * producing {@link info.oais.infomodel.interfaces.utility.OaisIfGeometry}
 * values, per {@code OaisIfVector}'s documented contract.
 */
final class StructureNodeBackedVector extends StructureNodeBackedTable implements OaisIfVector {

	StructureNodeBackedVector(List<StructureNode> rows, List<ColumnMapping> columns) {
		super(rows, columns);
	}
}
