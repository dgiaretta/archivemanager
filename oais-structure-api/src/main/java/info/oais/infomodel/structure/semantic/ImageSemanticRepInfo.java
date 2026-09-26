package info.oais.infomodel.structure.semantic;

import java.util.Objects;

import info.oais.infomodel.interfaces.utility.OaisIfImage;
import info.oais.infomodel.structure.StructureNode;

/**
 * An {@link ExecutableSemanticRepInfo} that views a {@link StructureNode}
 * subtree as an {@link OaisIfImage}, driven by a declarative
 * {@link ImageMapping} - see {@link TableSemanticRepInfo}'s Javadoc, which
 * this otherwise mirrors, including its external-XML-file constructor.
 */
public class ImageSemanticRepInfo extends AbstractExecutableSemanticRepInfo<OaisIfImage> {

	private final ImageMapping mapping;

	public ImageSemanticRepInfo(ImageMapping mapping) {
		super();
		this.mapping = Objects.requireNonNull(mapping, "mapping");
	}

	/**
	 * Builds this instance's {@link ImageMapping} by reading
	 * {@code specification}'s external XML file - see
	 * {@link ImageViewSpecificationReader} for the file format - rather than
	 * requiring one to be constructed programmatically. Reads the file
	 * immediately, so a malformed or unreadable specification fails fast, at
	 * construction, rather than on first {@link #apply}.
	 *
	 * @param specification the external file describing this image's rows,
	 *                       pixels and pixel type
	 * @throws ViewSpecificationException if the file cannot be read or does
	 *                                    not match the expected format
	 */
	public ImageSemanticRepInfo(ImageViewSpecification specification) {
		this(ImageViewSpecificationReader.read(specification));
	}

	public ImageMapping getMapping() {
		return mapping;
	}

	@Override
	protected OaisIfImage doApply(StructureNode root) {
		return new StructureNodeBackedImage(mapping.rowSelector().apply(root), mapping.pixelSelector(),
				mapping.pixelExtractor(), mapping.pixelClass());
	}
}
