package info.oais.infomodel.structure.semantic;

import java.net.URI;
import java.util.List;
import java.util.function.Function;

import org.w3c.dom.Element;

import info.oais.infomodel.structure.StructureNode;

/**
 * Reads an {@link ImageViewSpecification}'s external XML file into a
 * runtime {@link ImageMapping}. {@link ImageSemanticRepInfo#ImageSemanticRepInfo(ImageViewSpecification)}
 * is the entry point applications actually use; this class does the file
 * reading and is not usually called directly.
 *
 * <p><b>File format</b> - a tree that represents an image as repeated
 * {@code row} children, each with repeated {@code pixel} children:
 *
 * <pre>{@code
 * <?xml version="1.0" encoding="UTF-8"?>
 * <imageView>
 *     <rows select="children" name="row"/>
 *     <pixels select="children" name="pixel"/>
 *     <pixelType type="int"/>
 * </imageView>
 * }</pre>
 *
 * <p>{@code <rows>} (required) selects the row nodes, and {@code <pixels>}
 * (required) selects, per row node, that row's ordered pixel nodes - both
 * follow exactly the {@code select="self"}/{@code select="children" name="..."}
 * convention {@link TableViewSpecificationReader} documents in full for its
 * own {@code <rows>}.
 *
 * <p>{@code <pixelType>} (required) declares the pixels' declared value
 * class via its {@code type} attribute, using the same short names or
 * fully-qualified class name {@link TableViewSpecificationReader} documents
 * for a {@code <column>}'s {@code type}.
 *
 * <p>A pixel's value is always its own leaf value ({@link ImageMapping#leafValue}) -
 * unlike a {@code <column>}, there is no {@code child}-name indirection,
 * since a pixel node has no further structure of its own to name a child of.
 */
public final class ImageViewSpecificationReader {

	private ImageViewSpecificationReader() {
	}

	/**
	 * Reads {@code specification}'s external XML file into an {@link ImageMapping}.
	 *
	 * @param specification the external file to read
	 * @return the mapping it describes
	 * @throws ViewSpecificationException if the file cannot be read or does
	 *                                    not match the expected format
	 */
	public static ImageMapping read(ImageViewSpecification specification) {
		URI location = specification.location();
		Element view = ViewSpecificationXml.parseRoot(location);

		Element rowsElement = ViewSpecificationXml.requiredSingleChild(view, "rows", location);
		Function<StructureNode, List<StructureNode>> rowSelector = ViewSpecificationXml.rowSelectorFor(rowsElement,
				location);

		Element pixelsElement = ViewSpecificationXml.requiredSingleChild(view, "pixels", location);
		Function<StructureNode, List<StructureNode>> pixelSelector = ViewSpecificationXml.rowSelectorFor(pixelsElement,
				location);

		Element pixelTypeElement = ViewSpecificationXml.requiredSingleChild(view, "pixelType", location);
		Class<?> pixelClass = ViewSpecificationXml
				.classFor(ViewSpecificationXml.requiredAttribute(pixelTypeElement, "type", location), location);

		return new ImageMapping(rowSelector, pixelSelector, ImageMapping::leafValue, pixelClass);
	}
}
