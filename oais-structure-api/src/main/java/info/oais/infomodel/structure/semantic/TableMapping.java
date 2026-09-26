package info.oais.infomodel.structure.semantic;

import java.util.List;
import java.util.function.Function;

import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.StructureNodeKind;

/**
 * Declares how to see some {@link StructureNode} subtree as a table: which
 * nodes are the rows, and, for each column, how to read that column's value
 * out of a row node. See {@link TableSemanticRepInfo}.
 *
 * @param rowSelector given the StructureNode passed to
 *                    {@link ExecutableSemanticRepInfo#apply}, returns the
 *                    ordered list of row nodes - typically
 *                    {@code root -> root.childrenNamed("point")} or
 *                    {@code root -> root.child("points").map(StructureNode::getChildren).orElse(List.of())},
 *                    depending on whether the tree represents repetition as
 *                    same-named siblings or as a single ARRAY node (see
 *                    {@link StructureNodeKind})
 * @param columns     the columns, in order; never empty
 */
public record TableMapping(Function<StructureNode, List<StructureNode>> rowSelector, List<ColumnMapping> columns) {

	public TableMapping {
		if (columns == null || columns.isEmpty()) {
			throw new IllegalArgumentException("columns must not be empty");
		}
		columns = List.copyOf(columns);
	}
}
