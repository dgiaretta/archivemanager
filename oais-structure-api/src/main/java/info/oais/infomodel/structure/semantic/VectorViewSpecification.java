package info.oais.infomodel.structure.semantic;

import java.net.URI;
import java.util.Objects;

/**
 * A reference to an external XML file describing how to view some
 * {@link info.oais.infomodel.structure.StructureNode} subtree as an
 * {@link info.oais.infomodel.interfaces.utility.OaisIfVector} - row (feature)
 * selection, the geometry column (kind, dimension, and where its coordinate
 * tuples come from), and any further attribute columns - without
 * hard-coding any of that in Java source. See {@link VectorSemanticRepInfo#VectorSemanticRepInfo(VectorViewSpecification)}
 * and {@link VectorViewSpecificationReader} for how it is read, and that
 * class's Javadoc for the file format itself, with an example. Mirrors
 * {@link TableViewSpecification} exactly - see its Javadoc for why this is
 * an external file rather than a {@link VectorMapping} built by hand.
 *
 * @param location the external XML file's location
 */
public record VectorViewSpecification(URI location) {

	public VectorViewSpecification {
		Objects.requireNonNull(location, "location");
	}
}
