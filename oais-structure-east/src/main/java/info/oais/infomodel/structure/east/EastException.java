package info.oais.infomodel.structure.east;

/** Why an EAST description can't be read, interpreted or written, and where. */
public final class EastException extends IllegalArgumentException {
	private static final long serialVersionUID = 1L;
	private final int line;

	public EastException(int line, String message) {
		super(line > 0 ? "Line " + line + ": " + message : message);
		this.line = line;
	}

	/** @return the line of the description the problem is on; 0 if it isn't on one line */
	public int line() {
		return line;
	}
}
