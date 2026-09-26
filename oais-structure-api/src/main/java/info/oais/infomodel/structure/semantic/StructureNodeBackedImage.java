package info.oais.infomodel.structure.semantic;

import java.util.List;
import java.util.function.Function;

import info.oais.infomodel.interfaces.utility.OaisIfImage;
import info.oais.infomodel.structure.StructureNode;

/**
 * A read-only {@link OaisIfImage} view over a list of row {@link StructureNode}s
 * and an {@link ImageMapping} - see {@link ImageSemanticRepInfo}. Pixel
 * values are computed on demand, not copied out up front.
 */
class StructureNodeBackedImage implements OaisIfImage {

	private final List<StructureNode> rows;
	private final Function<StructureNode, List<StructureNode>> pixelSelector;
	private final Function<StructureNode, Object> pixelExtractor;
	private final Class<?> pixelClass;

	StructureNodeBackedImage(List<StructureNode> rows, Function<StructureNode, List<StructureNode>> pixelSelector,
			Function<StructureNode, Object> pixelExtractor, Class<?> pixelClass) {
		this.rows = List.copyOf(rows);
		this.pixelSelector = pixelSelector;
		this.pixelExtractor = pixelExtractor;
		this.pixelClass = pixelClass;
	}

	@Override
	public long getRowCount() {
		return rows.size();
	}

	@Override
	public int getColumnCount() {
		return rows.isEmpty() ? 0 : pixelSelector.apply(rows.get(0)).size();
	}

	@Override
	public Class<?> getPixelClass() {
		return pixelClass;
	}

	@Override
	public Object[][] getPixelValues() {
		Object[][] pixels = new Object[rows.size()][];
		for (int r = 0; r < rows.size(); r++) {
			List<StructureNode> rowPixels = pixelSelector.apply(rows.get(r));
			Object[] row = new Object[rowPixels.size()];
			for (int c = 0; c < rowPixels.size(); c++) {
				row[c] = pixelExtractor.apply(rowPixels.get(c));
			}
			pixels[r] = row;
		}
		return pixels;
	}

	@Override
	public void setRowCount(long rowNum) {
		throw new UnsupportedOperationException(
				"This image is a read-only view over a StructureNode tree - its extent is fixed by its ImageMapping");
	}

	@Override
	public void setColumnCount(int colCount) {
		throw new UnsupportedOperationException(
				"This image is a read-only view over a StructureNode tree - its extent is fixed by its ImageMapping");
	}

	@Override
	public void setPixelClass(Class<?> pixClass) {
		throw new UnsupportedOperationException(
				"This image is a read-only view over a StructureNode tree - its pixel class is fixed by its ImageMapping");
	}

	@Override
	public void setValueAt(long rowIndex, int columnIndex, Object obj) {
		throw new UnsupportedOperationException(
				"This image is a read-only view over a StructureNode tree, which is itself read-only");
	}

	@Override
	public void setPixelValues(Object[][] pix) {
		throw new UnsupportedOperationException(
				"This image is a read-only view over a StructureNode tree, which is itself read-only");
	}
}
