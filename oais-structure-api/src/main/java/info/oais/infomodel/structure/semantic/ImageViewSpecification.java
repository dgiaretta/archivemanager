package info.oais.infomodel.structure.semantic;

import java.net.URI;
import java.util.Objects;

/**
 * A reference to an external XML file describing how to view some
 * {@link info.oais.infomodel.structure.StructureNode} subtree as an
 * {@link info.oais.infomodel.interfaces.utility.OaisIfImage} - which nodes
 * are the rows, which nodes within a row are that row's pixels, and the
 * pixels' declared value class - without hard-coding any of that in Java
 * source. See {@link ImageSemanticRepInfo#ImageSemanticRepInfo(ImageViewSpecification)}
 * and {@link ImageViewSpecificationReader} for how it is read, and that
 * class's Javadoc for the file format itself, with an example. Mirrors
 * {@link TableViewSpecification} exactly - see its Javadoc for why this is
 * an external file rather than an {@link ImageMapping} built by hand.
 *
 * @param location the external XML file's location
 */
public record ImageViewSpecification(URI location) {

	public ImageViewSpecification {
		Objects.requireNonNull(location, "location");
	}
}
