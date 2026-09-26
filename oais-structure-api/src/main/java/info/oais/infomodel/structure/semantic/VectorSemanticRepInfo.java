package info.oais.infomodel.structure.semantic;

import java.util.Objects;

import info.oais.infomodel.interfaces.utility.OaisIfVector;
import info.oais.infomodel.structure.StructureNode;

/**
 * An {@link ExecutableSemanticRepInfo} that views a {@link StructureNode}
 * subtree as an {@link OaisIfVector} - see {@link VectorMapping} and
 * {@link TableSemanticRepInfo}, which this otherwise mirrors exactly,
 * including its external-XML-file constructor.
 */
public class VectorSemanticRepInfo extends AbstractExecutableSemanticRepInfo<OaisIfVector> {

	private final VectorMapping mapping;

	public VectorSemanticRepInfo(VectorMapping mapping) {
		super();
		this.mapping = Objects.requireNonNull(mapping, "mapping");
	}

	/**
	 * Builds this instance's {@link VectorMapping} by reading
	 * {@code specification}'s external XML file - see
	 * {@link VectorViewSpecificationReader} for the file format - rather
	 * than requiring one to be constructed programmatically. Reads the file
	 * immediately, so a malformed or unreadable specification fails fast, at
	 * construction, rather than on first {@link #apply}.
	 *
	 * @param specification the external file describing this vector's
	 *                       features (rows), geometry column and attribute
	 *                       columns
	 * @throws ViewSpecificationException if the file cannot be read or does
	 *                                    not match the expected format
	 */
	public VectorSemanticRepInfo(VectorViewSpecification specification) {
		this(VectorViewSpecificationReader.read(specification));
	}

	public VectorMapping getMapping() {
		return mapping;
	}

	@Override
	protected OaisIfVector doApply(StructureNode root) {
		TableMapping tableMapping = mapping.toTableMapping();
		return new StructureNodeBackedVector(tableMapping.rowSelector().apply(root), tableMapping.columns());
	}
}
