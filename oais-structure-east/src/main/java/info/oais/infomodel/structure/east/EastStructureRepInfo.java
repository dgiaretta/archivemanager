package info.oais.infomodel.structure.east;

import java.io.InputStream;

import info.oais.infomodel.interfaces.DigitalObject;
import info.oais.infomodel.structure.AbstractExecutableStructureRepInfo;
import info.oais.infomodel.structure.StructureNode;

/**
 * Structure Representation Information in EAST (CCSDS 644.0-B-3), executed
 * by {@link EastInterpreter}: the description is checked when this is
 * created, and each Data Object it's applied to is read into a tree of
 * values. It reads data only; it doesn't write it back.
 */
public final class EastStructureRepInfo extends AbstractExecutableStructureRepInfo {

	private final EastInterpreter interpreter;

	/**
	 * @throws EastException if the description isn't EAST, or uses what the interpreter doesn't know
	 */
	public EastStructureRepInfo(EastFormatSpecification formatSpecification) {
		super(formatSpecification);
		this.interpreter = new EastInterpreter(formatSpecification.text());
	}

	@Override
	public EastFormatSpecification getFormatSpecification() {
		return (EastFormatSpecification) super.getFormatSpecification();
	}

	/** Reads data already in memory. */
	public StructureNode apply(byte[] data) {
		return interpreter.decode(data);
	}

	@Override
	protected StructureNode doApply(DigitalObject digitalObject) throws Exception {
		try (InputStream in = digitalObject.getObject()) {
			return interpreter.decode(in.readAllBytes());
		}
	}
}
