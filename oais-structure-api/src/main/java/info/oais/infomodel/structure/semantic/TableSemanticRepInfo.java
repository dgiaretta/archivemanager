package info.oais.infomodel.structure.semantic;

import java.util.Objects;

import info.oais.infomodel.interfaces.utility.OaisIfTable;
import info.oais.infomodel.structure.StructureNode;

/**
 * An {@link ExecutableSemanticRepInfo} that views a {@link StructureNode}
 * subtree as an {@link OaisIfTable}, driven entirely by a declarative
 * {@link TableMapping} - see that class and the package Javadoc for why this
 * is the interoperability layer this project adds on top of Structure
 * Representation Information.
 *
 * <p>{@link #apply} returns a thin, lazy view ({@link StructureNodeBackedTable})
 * over the given {@code StructureNode}, not a copy. The returned
 * {@code OaisIfTable}'s mutating methods ({@code addColumn}/{@code setValueAt})
 * throw {@link UnsupportedOperationException}, since a {@code StructureNode}
 * tree is itself read-only navigation over an already-decoded Digital
 * Object.</p>
 *
 * <p>A {@link TableMapping} can be built by hand in Java, or - preferred,
 * so "how to view this data as a table" lives outside Java source the same
 * way a DFDL schema externalises "how these bytes are structured" - read
 * from an external XML file via {@link #TableSemanticRepInfo(TableViewSpecification)}.
 * See {@link TableViewSpecification} and {@link TableViewSpecificationReader}.</p>
 */
public class TableSemanticRepInfo extends AbstractExecutableSemanticRepInfo<OaisIfTable> {

	private final TableMapping mapping;

	public TableSemanticRepInfo(TableMapping mapping) {
		super();
		this.mapping = Objects.requireNonNull(mapping, "mapping");
	}

	/**
	 * Builds this instance's {@link TableMapping} by reading {@code specification}'s
	 * external XML file - see {@link TableViewSpecificationReader} for the file
	 * format - rather than requiring one to be constructed programmatically.
	 * Reads the file immediately (table view descriptions are small, unlike a
	 * DFDL schema's compilation), so a malformed or unreadable specification
	 * fails fast, at construction, rather than on first {@link #apply}.
	 *
	 * @param specification the external file describing this table's rows and columns
	 * @throws ViewSpecificationException if the file cannot be read or does
	 *                                    not match the expected format
	 */
	public TableSemanticRepInfo(TableViewSpecification specification) {
		this(TableViewSpecificationReader.read(specification));
	}

	public TableMapping getMapping() {
		return mapping;
	}

	@Override
	protected OaisIfTable doApply(StructureNode root) {
		return new StructureNodeBackedTable(mapping.rowSelector().apply(root), mapping.columns());
	}
}
