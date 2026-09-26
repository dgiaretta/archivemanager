package info.oais.infomodel.structure.semantic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;

import info.oais.infomodel.interfaces.utility.GeometryKind;
import info.oais.infomodel.interfaces.utility.OaisIfGeometry;
import info.oais.infomodel.interfaces.utility.OaisIfVector;
import info.oais.infomodel.structure.DefaultStructureNode;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.StructureNodeKind;

class VectorViewSpecificationReaderTest {

	@Test
	void readsRepeatedVertexChildrenAsALineStringPlusAttributeColumns() {
		StructureNode tree = DefaultStructureNode.builder("features", StructureNodeKind.COMPOSITE)
				.addChild(feature("north", vertex(0, 0), vertex(10, 0), vertex(10, 5)))
				.build();

		VectorSemanticRepInfo repInfo = new VectorSemanticRepInfo(
				new VectorViewSpecification(resource("/vector-view/features.xml")));
		OaisIfVector vector = repInfo.apply(tree);

		assertEquals(1, vector.getRowCount());
		assertEquals(2, vector.getColumnCount());
		assertEquals(OaisIfGeometry.class, vector.getColumnClass(0));

		OaisIfGeometry geometry = (OaisIfGeometry) vector.getValueAt(0, 0);
		assertEquals(GeometryKind.LINE_STRING, geometry.getKind());
		assertEquals(2, geometry.getDimension());
		assertArrayEquals(new double[][] { { 0, 0 }, { 10, 0 }, { 10, 5 } }, geometry.getCoordinates());
		assertEquals("north", vector.getValueAt(0, 1));
	}

	@Test
	void selectSelfGivesASingleCoordinateTupleForAPointGeometry() {
		StructureNode tree = DefaultStructureNode.builder("features", StructureNodeKind.COMPOSITE)
				.addChild(DefaultStructureNode.builder("feature", StructureNodeKind.COMPOSITE)
						.addChild(DefaultStructureNode.leaf("x", 3))
						.addChild(DefaultStructureNode.leaf("y", 4))
						.build())
				.build();

		VectorSemanticRepInfo repInfo = new VectorSemanticRepInfo(
				new VectorViewSpecification(resource("/vector-view/point-geometry.xml")));
		OaisIfVector vector = repInfo.apply(tree);

		OaisIfGeometry geometry = (OaisIfGeometry) vector.getValueAt(0, 0);
		assertEquals(GeometryKind.POINT, geometry.getKind());
		assertArrayEquals(new double[][] { { 3, 4 } }, geometry.getCoordinates());
	}

	@Test
	void ordinateCountNotMatchingDimensionIsRejected() {
		VectorViewSpecification specification = new VectorViewSpecification(
				resource("/vector-view/dimension-mismatch.xml"));
		assertThrows(ViewSpecificationException.class, () -> VectorViewSpecificationReader.read(specification));
	}

	private static StructureNode feature(String label, StructureNode... vertices) {
		DefaultStructureNode.Builder builder = DefaultStructureNode.builder("feature", StructureNodeKind.COMPOSITE)
				.addChild(DefaultStructureNode.leaf("label", label));
		for (StructureNode vertex : vertices) {
			builder.addChild(vertex);
		}
		return builder.build();
	}

	private static StructureNode vertex(double x, double y) {
		return DefaultStructureNode.builder("vertex", StructureNodeKind.COMPOSITE)
				.addChild(DefaultStructureNode.leaf("x", x))
				.addChild(DefaultStructureNode.leaf("y", y))
				.build();
	}

	private static URI resource(String name) {
		try {
			return VectorViewSpecificationReaderTest.class.getResource(name).toURI();
		} catch (URISyntaxException e) {
			throw new IllegalStateException("Malformed resource URI for " + name, e);
		}
	}
}
