package info.oais.infomodel.structure.semantic;

/**
 * @deprecated superseded by the more general {@link ViewSpecificationException},
 *             which {@link TableViewSpecificationReader} now throws (along
 *             with the new {@link TimeSeriesViewSpecificationReader},
 *             {@link VectorViewSpecificationReader} and
 *             {@link ImageViewSpecificationReader}). No longer thrown
 *             anywhere in this package; kept only so any code outside this
 *             module that still references this type by name continues to
 *             compile.
 */
@Deprecated
public class TableViewSpecificationException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public TableViewSpecificationException(String message) {
		super(message);
	}

	public TableViewSpecificationException(String message, Throwable cause) {
		super(message, cause);
	}
}
