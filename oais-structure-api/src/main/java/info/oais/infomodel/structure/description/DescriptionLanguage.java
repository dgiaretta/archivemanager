package info.oais.infomodel.structure.description;

/** A data description language a {@link FormatDescription} can be generated in. */
public enum DescriptionLanguage {
	KAITAI("Kaitai Struct"),
	DFDL("DFDL"),
	DRB("DRB (Java)"),
	DRB_PYTHON("drb-python"),
	/** The CCSDS data description language EAST (CCSDS 644.0-B-3). */
	EAST("EAST");

	private final String label;

	DescriptionLanguage(String label) {
		this.label = label;
	}

	public String label() {
		return label;
	}
}
