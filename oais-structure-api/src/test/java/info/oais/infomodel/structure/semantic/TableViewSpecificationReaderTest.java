package info.oais.infomodel.structure.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;

import org.junit.jupiter.api.Test;

import info.oais.infomodel.interfaces.utility.OaisIfTable;
import info.oais.infomodel.structure.DefaultStructureNode;
import info.oais.infomodel.structure.StructureInterpretationException;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.StructureNodeKind;

/**
 * Exercises {@link TableViewSpecificationReader} (and, through it,
 * {@link TableSemanticRepInfo#TableSemanticRepInfo(TableViewSpecification)})
 * against the small XML files under {@code src/test/resources/table-view/} -
 * see {@link TableViewSpecificationReader}'s Javadoc for the file format, and
 * {@link TableViewSpecification}'s Javadoc for why this externalisation
 * matters: the whole point is that a mapping like this can be written and
 * changed without touching Java source, the same way a DFDL schema
 * externalises "how these bytes are structured".
 */
class TableViewSpecificationReaderTest {

	@Test
	void selfRowReadsOneRowFromTheRootItself() {
		StructureNode point = DefaultStructureNode.builder("point", StructureNodeKind.COMPOSITE)
				.addChild(DefaultStructureNode.leaf("x", 42))
				.addChild(DefaultStructureNode.leaf("y", -7))
				.addChild(DefaultStructureNode.leaf("label", "hi"))
				.build();

		OaisIfTable table = new TableSemanticRepInfo(specification("self-row.xml")).apply(point);

		assertEquals(1, table.getRowCount());
		assertEquals(3, table.getColumnCount());
		assertEquals("x", table.getColumnName(0));
		assertEquals(Integer.class, table.getColumnClass(0));
		assertEquals(42, table.getValueAt(0, 0));
		assertEquals(-7, table.getValueAt(0, 1));
		assertEquals("hi", table.getValueAt(0, 2));
	}

	@Test
	void childrenRowReadsOneRowPerSameNamedSibling() {
		StructureNode root = DefaultStructureNode.builder("points", StructureNodeKind.COMPOSITE)
				.addChild(pointNamed(1, 2))
				.addChild(pointNamed(3, 4))
				.build();

		OaisIfTable table = new TableSemanticRepInfo(specification("children-row.xml")).apply(root);

		assertEquals(2, table.getRowCount());
		assertEquals(1, table.getValueAt(0, 0));
		assertEquals(2, table.getValueAt(0, 1));
		assertEquals(3, table.getValueAt(1, 0));
		assertEquals(4, table.getValueAt(1, 1));
	}

	@Test
	void arrayRowReadsOneRowPerArrayElement() {
		// The Kaitai Struct convention (StructureNodeKind.ARRAY) rather than
		// childrenRowReadsOneRowPerSameNamedSibling's DFDL/DRB convention: one
		// named field holding the repeated elements, indexed rather than named.
		StructureNode root = DefaultStructureNode.builder("csv", StructureNodeKind.COMPOSITE)
				.addChild(DefaultStructureNode.builder("points", StructureNodeKind.ARRAY)
						.addChild(pointNamed(1, 2))
						.addChild(pointNamed(3, 4))
						.build())
				.build();

		OaisIfTable table = new TableSemanticRepInfo(specification("array-row.xml")).apply(root);

		assertEquals(2, table.getRowCount());
		assertEquals(1, table.getValueAt(0, 0));
		assertEquals(2, table.getValueAt(0, 1));
		assertEquals(3, table.getValueAt(1, 0));
		assertEquals(4, table.getValueAt(1, 1));
	}

	@Test
	void arraySelectFailsWithAClearMessageWhenTheNamedChildIsNotAnArray() {
		StructureNode root = DefaultStructureNode.builder("csv", StructureNodeKind.COMPOSITE)
				.addChild(DefaultStructureNode.builder("points", StructureNodeKind.COMPOSITE)
						.addChild(DefaultStructureNode.leaf("x", 1))
						.build())
				.build();

		// AbstractExecutableSemanticRepInfo#apply wraps this in a
		// StructureInterpretationException; the ViewSpecificationException with
		// the clear message is its cause.
		StructureInterpretationException e = assertThrows(StructureInterpretationException.class,
				() -> new TableSemanticRepInfo(specification("array-row.xml")).apply(root));
		assertTrue(e.getCause().getMessage().contains("ARRAY"),
				() -> "message should mention ARRAY: " + e.getCause().getMessage());
	}

	@Test
	void coercesTextValuesToTheDeclaredColumnClass() {
		// A leaf value that is text (e.g. from an engine with no typed-value
		// information available, like a bare DOM parse) should still come back
		// as a real Integer, via StructureNodeBackedTable's coercion - see that
		// class's Javadoc on "Coercion".
		StructureNode point = DefaultStructureNode.builder("point", StructureNodeKind.COMPOSITE)
				.addChild(DefaultStructureNode.leaf("x", "42"))
				.addChild(DefaultStructureNode.leaf("y", "-7"))
				.addChild(DefaultStructureNode.leaf("label", "hi"))
				.build();

		OaisIfTable table = new TableSemanticRepInfo(specification("self-row.xml")).apply(point);

		assertEquals(42, table.getValueAt(0, 0));
		assertTrue(table.getValueAt(0, 0) instanceof Integer);
	}

	@Test
	void missingColumnsElementFailsWithAClearMessage() {
		ViewSpecificationException e = assertThrows(ViewSpecificationException.class,
				() -> TableViewSpecificationReader.read(specification("missing-columns.xml")));
		assertTrue(e.getMessage().contains("columns"),
				() -> "message should mention the missing <columns>: " + e.getMessage());
	}

	private static StructureNode pointNamed(int x, int y) {
		return DefaultStructureNode.builder("point", StructureNodeKind.COMPOSITE)
				.addChild(DefaultStructureNode.leaf("x", x))
				.addChild(DefaultStructureNode.leaf("y", y))
				.build();
	}

	private static TableViewSpecification specification(String resourceName) {
		try {
			URI uri = TableViewSpecificationReaderTest.class.getResource("/table-view/" + resourceName).toURI();
			return new TableViewSpecification(uri);
		} catch (Exception e) {
			throw new IllegalStateException("Malformed test resource URI for " + resourceName, e);
		}
	}
}
