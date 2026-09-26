package info.oais.infomodel.structure.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import info.oais.infomodel.interfaces.utility.OaisIfTable;
import info.oais.infomodel.structure.DefaultStructureNode;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.StructureNodeKind;

/**
 * Exercises {@link TableSemanticRepInfo} against hand-built
 * {@link DefaultStructureNode} fixtures standing in for independently
 * "parsed" Digital Objects, proving the interoperability claim this package
 * exists for: the same declarative mapping (or, where repetition is
 * represented differently, at least the same {@link ColumnMapping} list)
 * reads correctly regardless of which engine's conventions produced the
 * tree.
 */
class TableSemanticRepInfoTest {

	private static StructureNode pointRow(String name, int x, int y) {
		return DefaultStructureNode.builder(name, StructureNodeKind.COMPOSITE)
				.addChild(DefaultStructureNode.leaf("x", x))
				.addChild(DefaultStructureNode.leaf("y", y))
				.build();
	}

	private static final TableMapping POINT_TABLE_MAPPING = new TableMapping(
			root -> root.childrenNamed("point"),
			List.of(ColumnMapping.ofChild("x", Integer.class), ColumnMapping.ofChild("y", Integer.class)));

	@Test
	void sameMappingInstanceReadsCorrectlyFromTwoIndependentlyBuiltTrees() {
		StructureNode treeA = DefaultStructureNode.builder("points", StructureNodeKind.COMPOSITE)
				.addChild(pointRow("point", 1, 2))
				.addChild(pointRow("point", 3, 4))
				.build();
		StructureNode treeB = DefaultStructureNode.builder("points", StructureNodeKind.COMPOSITE)
				.addChild(pointRow("point", 10, 20))
				.build();

		TableSemanticRepInfo semanticRepInfo = new TableSemanticRepInfo(POINT_TABLE_MAPPING);

		OaisIfTable tableA = semanticRepInfo.apply(treeA);
		assertEquals(2, tableA.getRowCount());
		assertEquals(2, tableA.getColumnCount());
		assertEquals("x", tableA.getColumnName(0));
		assertEquals("y", tableA.getColumnName(1));
		assertEquals(1, tableA.getValueAt(0, 0));
		assertEquals(2, tableA.getValueAt(0, 1));
		assertEquals(3, tableA.getValueAt(1, 0));
		assertEquals(4, tableA.getValueAt(1, 1));

		OaisIfTable tableB = semanticRepInfo.apply(treeB);
		assertEquals(1, tableB.getRowCount());
		assertEquals(10, tableB.getValueAt(0, 0));
		assertEquals(20, tableB.getValueAt(0, 1));
	}

	@Test
	void columnMappingsAreReusableAcrossDifferentRowRepetitionConventions() {
		// "DFDL/DRB style": repetition as same-named siblings directly under the row parent.
		StructureNode siblingsTree = DefaultStructureNode.builder("points", StructureNodeKind.COMPOSITE)
				.addChild(pointRow("point", 5, 6))
				.addChild(pointRow("point", 7, 8))
				.build();

		// "Kaitai style": repetition as a single ARRAY node wrapping the rows.
		StructureNode arrayTree = DefaultStructureNode.builder("root", StructureNodeKind.COMPOSITE)
				.addChild(DefaultStructureNode.builder("points", StructureNodeKind.ARRAY)
						.addChild(pointRow("0", 5, 6))
						.addChild(pointRow("1", 7, 8))
						.build())
				.build();

		List<ColumnMapping> columns = List.of(
				ColumnMapping.ofChild("x", Integer.class), ColumnMapping.ofChild("y", Integer.class));

		TableSemanticRepInfo siblingsRepInfo = new TableSemanticRepInfo(
				new TableMapping(root -> root.childrenNamed("point"), columns));
		TableSemanticRepInfo arrayRepInfo = new TableSemanticRepInfo(
				new TableMapping(root -> root.child("points").map(StructureNode::getChildren).orElse(List.of()),
						columns));

		OaisIfTable fromSiblings = siblingsRepInfo.apply(siblingsTree);
		OaisIfTable fromArray = arrayRepInfo.apply(arrayTree);

		assertEquals(fromSiblings.getRowCount(), fromArray.getRowCount());
		for (long row = 0; row < fromSiblings.getRowCount(); row++) {
			for (int col = 0; col < fromSiblings.getColumnCount(); col++) {
				assertEquals(fromSiblings.getValueAt(row, col), fromArray.getValueAt(row, col));
			}
		}
	}

	@Test
	void viewIsReadOnly() {
		StructureNode tree = DefaultStructureNode.builder("points", StructureNodeKind.COMPOSITE)
				.addChild(pointRow("point", 1, 2))
				.build();
		OaisIfTable table = new TableSemanticRepInfo(POINT_TABLE_MAPPING).apply(tree);

		assertThrows(UnsupportedOperationException.class, () -> table.addColumn("z", Integer.class));
		assertThrows(UnsupportedOperationException.class, () -> table.setValueAt(99, 0, 0));
	}
}
