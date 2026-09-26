package info.oais.infomodel.structure.semantic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.net.URISyntaxException;

import org.junit.jupiter.api.Test;

import info.oais.infomodel.interfaces.utility.OaisIfImage;
import info.oais.infomodel.structure.DefaultStructureNode;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.StructureNodeKind;

class ImageViewSpecificationReaderTest {

	@Test
	void readsRepeatedRowAndPixelChildrenAsAPixelGrid() {
		int[][] pixelValues = { { 0, 64 }, { 128, 192 } };
		DefaultStructureNode.Builder image = DefaultStructureNode.builder("image", StructureNodeKind.COMPOSITE);
		for (int[] rowPixels : pixelValues) {
			DefaultStructureNode.Builder row = DefaultStructureNode.builder("row", StructureNodeKind.COMPOSITE);
			for (int pixel : rowPixels) {
				row.addChild(DefaultStructureNode.leaf("pixel", pixel));
			}
			image.addChild(row.build());
		}
		StructureNode tree = image.build();

		ImageSemanticRepInfo repInfo = new ImageSemanticRepInfo(
				new ImageViewSpecification(resource("/image-view/pixels.xml")));
		OaisIfImage decodedImage = repInfo.apply(tree);

		assertEquals(2, decodedImage.getRowCount());
		assertEquals(2, decodedImage.getColumnCount());
		assertEquals(Integer.class, decodedImage.getPixelClass());
		assertEquals(0, decodedImage.getPixelValues()[0][0]);
		assertEquals(64, decodedImage.getPixelValues()[0][1]);
		assertEquals(128, decodedImage.getPixelValues()[1][0]);
		assertEquals(192, decodedImage.getPixelValues()[1][1]);
	}

	@Test
	void missingPixelTypeIsRejected() {
		ImageViewSpecification specification = new ImageViewSpecification(
				resource("/image-view/missing-pixel-type.xml"));
		assertThrows(ViewSpecificationException.class, () -> ImageViewSpecificationReader.read(specification));
	}

	private static URI resource(String name) {
		try {
			return ImageViewSpecificationReaderTest.class.getResource(name).toURI();
		} catch (URISyntaxException e) {
			throw new IllegalStateException("Malformed resource URI for " + name, e);
		}
	}
}
