package info.oais.infomodel.structure;

/**
 * Thrown when an {@link ExecutableRepresentationInformation} fails to apply
 * itself to a Data Object - a schema failed to compile, the bytes did not
 * match the expected format, the underlying engine threw, and so on. Wraps
 * whatever engine-specific exception (a Daffodil diagnostic, a Kaitai
 * runtime exception, a DRB exception) actually caused the failure, via
 * {@link #getCause()}, so nothing engine-specific needs to be caught by
 * calling code that only knows about the {@code oais-structure-api} module.
 *
 * <p>Unchecked, consistent with the rest of the oaisCore interfaces, whose
 * getters/setters/factory methods do not declare checked exceptions.</p>
 */
public class StructureInterpretationException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public StructureInterpretationException(String message) {
		super(message);
	}

	public StructureInterpretationException(String message, Throwable cause) {
		super(message, cause);
	}
}
