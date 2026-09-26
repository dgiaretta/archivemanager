package info.oais.infomodel.structure.semantic;

/**
 * Thrown when a view specification's external XML file - a
 * {@link TableViewSpecification}, {@link TimeSeriesViewSpecification},
 * {@link VectorViewSpecification} or {@link ImageViewSpecification} - cannot
 * be read, or does not match the format its reader ({@link TableViewSpecificationReader},
 * {@link TimeSeriesViewSpecificationReader}, {@link VectorViewSpecificationReader}
 * or {@link ImageViewSpecificationReader}) expects.
 */
public class ViewSpecificationException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public ViewSpecificationException(String message) {
		super(message);
	}

	public ViewSpecificationException(String message, Throwable cause) {
		super(message, cause);
	}
}
