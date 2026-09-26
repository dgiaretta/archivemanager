package info.oais.infomodel.structure.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import info.oais.infomodel.interfaces.utility.GeometryKind;
import info.oais.infomodel.interfaces.utility.OaisIfGeometry;
import info.oais.infomodel.interfaces.utility.OaisIfVector;
import info.oais.infomodel.structure.DefaultStructureNode;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.StructureNodeKind;

class VectorSemanticRepInfoTest {

	@Test
	void columnZeroIsAnOaisIfGeometry() {
		StructureNode features = DefaultStructureNode.builder("features", StructureNodeKind.COMPOSITE)
				.addChild(DefaultStructureNode.builder("feature", StructureNodeKind.COMPOSITE)
						.addChild(DefaultStructureNode.leaf("x", 1.5))
						.addChild(DefaultStructureNode.leaf("y", 2.5))
						.addChild(DefaultStructureNode.leaf("label", "site-1"))
						.build())
				.build();

		VectorMapping mapping = new VectorMapping(
				root -> root.childrenNamed("feature"),
				VectorMapping.geometryColumn("geometry", GeometryKind.POINT,
						row -> new double[][] {
								{ (double) row.valueAt("x").orElse(0.0), (double) row.valueAt("y").orElse(0.0) } },
						2),
				List.of(ColumnMapping.ofChild("label", String.class)));

		OaisIfVector vector = new VectorSemanticRepInfo(mapping).apply(features);

		assertEquals(1, vector.getRowCount());
		assertEquals(2, vector.getColumnCount());
		assertEquals(OaisIfGeometry.class, vector.getColumnClass(0));

		OaisIfGeometry geometry = (OaisIfGeometry) vector.getValueAt(0, 0);
		assertEquals(GeometryKind.POINT, geometry.getKind());
		assertEquals(2, geometry.getDimension());
		assertEquals(1.5, geometry.getCoordinates()[0][0]);
		assertEquals(2.5, geometry.getCoordinates()[0][1]);
		assertEquals("site-1", vector.getValueAt(0, 1));
	}
}
