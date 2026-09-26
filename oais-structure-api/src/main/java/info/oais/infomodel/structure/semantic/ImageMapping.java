package info.oais.infomodel.structure.semantic;

import java.util.List;
import java.util.function.Function;

import info.oais.infomodel.structure.StructureNode;

/**
 * Declares how to see some {@link StructureNode} subtree as an image: which
 * nodes are the rows, which nodes within a row are the pixels of that row,
 * and how to read one pixel's value. See {@link ImageSemanticRepInfo}.
 *
 * @param rowSelector    given the StructureNode passed to
 *                       {@link ExecutableSemanticRepInfo#apply}, returns the
 *                       ordered list of row nodes (see
 *                       {@link TableMapping#rowSelector()} for the same
 *                       choice in table form)
 * @param pixelSelector  given one row node, returns the ordered list of that
 *                       row's pixel nodes
 * @param pixelExtractor given one pixel node, returns its value
 * @param pixelClass     the declared class of a pixel's value
 */
public record ImageMapping(Function<StructureNode, List<StructureNode>> rowSelector,
		Function<StructureNode, List<StructureNode>> pixelSelector,
		Function<StructureNode, Object> pixelExtractor,
		Class<?> pixelClass) {

	/**
	 * Convenience {@code pixelExtractor}: a pixel node's own leaf value.
	 *
	 * @param pixel the pixel node
	 * @return the pixel node's leaf value, or {@code null}
	 */
	public static Object leafValue(StructureNode pixel) {
		return pixel.getValue().orElse(null);
	}
}
