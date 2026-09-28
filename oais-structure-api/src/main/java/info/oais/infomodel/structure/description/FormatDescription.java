package info.oais.infomodel.structure.description;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * An engine-neutral description of a data format: what its data can
 * contain, and what it means - the model RepInfo Tools edits, every
 * generator (Kaitai Struct, DFDL, DRB, drb-python) is driven from, and
 * {@code StructureAligner} lines decoded data up against.
 *
 * @param defaultByteOrder the byte order of multi-byte numbers unless a field overrides it
 * @param fileExtensions   extensions the format's files usually have, lower-case, no dot
 * @param root             the whole file, as one record
 */
public record FormatDescription(String name, String notes, ByteOrder defaultByteOrder, List<String> fileExtensions,
		RecordDescription root) implements Serializable {

	public FormatDescription {
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(root, "root");
		notes = notes == null ? "" : notes;
		defaultByteOrder = defaultByteOrder == null ? ByteOrder.BIG_ENDIAN : defaultByteOrder;
		fileExtensions = fileExtensions == null ? List.of() : List.copyOf(fileExtensions);
	}

	public FormatDescription withRoot(RecordDescription root) {
		return new FormatDescription(name, notes, defaultByteOrder, fileExtensions, root);
	}
}
