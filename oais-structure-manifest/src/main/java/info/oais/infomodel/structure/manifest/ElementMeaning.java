package info.oais.infomodel.structure.manifest;

/**
 * What one element of the data means: its Semantic Representation
 * Information, as far as a viewer needs it.
 *
 * @param path       where the element is, e.g. {@code reading.temperature}
 * @param label      its semantic name, or null
 * @param definition its definition, or null
 * @param units      its units, or null
 * @param concept    the IRI of the concept it represents, or null
 */
public record ElementMeaning(String path, String label, String definition, String units, String concept) {

	/** The last part of {@link #path}, e.g. {@code temperature}. */
	public String lastPart() {
		int dot = path.lastIndexOf('.');
		String last = dot < 0 ? path : path.substring(dot + 1);
		int bang = last.lastIndexOf('!');
		return bang < 0 ? last : last.substring(bang + 1).replace("\"", "");
	}
}
