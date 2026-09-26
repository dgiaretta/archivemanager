package info.oais.infomodel.structure.semantic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import info.oais.infomodel.interfaces.utility.OaisIfImage;
import info.oais.infomodel.structure.DefaultStructureNode;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.StructureNodeKind;

class ImageSemanticRepInfoTest {

	private static StructureNode row(String name, int... pixelValues) {
		DefaultStructureNode.Builder b = DefaultStructureNode.builder(name, StructureNodeKind.COMPOSITE);
		for (int v : pixelValues) {
			b.addChild(DefaultStructureNode.leaf("pixel", v));
		}
		return b.build();
	}

	@Test
	void pixelGridIsReadFromRowsOfPixelChildren() {
		StructureNode image = DefaultStructureNode.builder("image", StructureNodeKind.COMPOSITE)
				.addChild(row("row", 1, 2, 3))
				.addChild(row("row", 4, 5, 6))
				.build();

		ImageMapping mapping = new ImageMapping(
				root -> root.childrenNamed("row"),
				r -> r.childrenNamed("pixel"),
				ImageMapping::leafValue,
				Integer.class);

		OaisIfImage view = new ImageSemanticRepInfo(mapping).apply(image);

		assertEquals(2, view.getRowCount());
		assertEquals(3, view.getColumnCount());
		assertEquals(Integer.class, view.getPixelClass());
		assertArrayEquals(new Object[] { 1, 2, 3 }, view.getPixelValues()[0]);
		assertArrayEquals(new Object[] { 4, 5, 6 }, view.getPixelValues()[1]);
	}
}
