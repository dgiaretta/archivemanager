package info.oais.infomodel.structure.semantic;

import java.net.URI;
import java.util.Objects;

/**
 * A reference to an external XML file describing how to view some
 * {@link info.oais.infomodel.structure.StructureNode} subtree as an
 * {@link info.oais.infomodel.interfaces.utility.OaisIfTable} - row selection,
 * and each column's name, declared type and how to read its value - without
 * hard-coding any of that in Java source. See
 * {@link TableSemanticRepInfo#TableSemanticRepInfo(TableViewSpecification)}
 * and {@link TableViewSpecificationReader} for how it is read, and that
 * class's Javadoc for the file format itself, with an example.
 *
 * <p>Deliberately mirrors
 * {@link info.oais.infomodel.structure.dfdl.DfdlFormatSpecification}'s own
 * {@code URI schemaLocation}: the point of this class is that "how to view
 * this data as a table" is described the same externalised way "how these
 * bytes are structured" already is by a DFDL schema - as a file an analyst
 * can read, write and version without touching Java code, rather than as a
 * {@link TableMapping} built by hand in application source (which remains
 * available too, for callers that do want to build one programmatically).</p>
 *
 * @param location the external XML file's location
 */
public record TableViewSpecification(URI location) {

	public TableViewSpecification {
		Objects.requireNonNull(location, "location");
	}
}
