package info.oais.archive.manager.service.transform;

/** Why a Transformation couldn't be done, in words for the person doing it. */
public class TransformationException extends RuntimeException {

    public TransformationException(String message) {
        super(message);
    }

    public TransformationException(String message, Throwable cause) {
        super(message, cause);
    }
}
