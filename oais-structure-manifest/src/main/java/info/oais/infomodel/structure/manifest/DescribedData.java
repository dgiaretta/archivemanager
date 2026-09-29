package info.oais.infomodel.structure.manifest;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * One Data Object in a {@link RepInfoManifest}: where its bits are, and the
 * Representation Information needed to interpret them.
 *
 * @param iri        the Data Object's IRI in the manifest
 * @param name       what to call it (its label, else the last part of its IRI)
 * @param data       where its bits are (a file or a URL)
 * @param structures its structure descriptions: equivalent alternatives, any one of which will do
 * @param views      how to view it (as a table, a time series, ...)
 * @param meanings   what its elements mean, by structural path
 */
public record DescribedData(String iri, String name, URI data, List<StructureDescription> structures,
		List<ViewDescription> views, List<ElementMeaning> meanings) {

	public DescribedData {
		structures = List.copyOf(structures);
		views = List.copyOf(views);
		meanings = List.copyOf(meanings);
	}

	/**
	 * A structure description, in the given order of preference for its
	 * language: the first alternative in a preferred language that
	 * {@code usable} accepts (e.g. whose engine is installed).
	 */
	public Optional<StructureDescription> structure(List<String> preferredLanguages,
			java.util.function.Predicate<StructureDescription> usable) {
		return structures.stream().filter(usable)
				.min(Comparator.comparingInt(s -> {
					int i = preferredLanguages.indexOf(s.language());
					return i < 0 ? Integer.MAX_VALUE : i;
				}));
	}

	/** The first view of this kind, e.g. {@link ViewDescription#TABLE}. */
	public Optional<ViewDescription> view(String kind) {
		return views.stream().filter(v -> v.kind().equals(kind)).findFirst();
	}

	/**
	 * What the element at the end of a row-relative name means: the meaning
	 * whose structural path ends with {@code rowName.childName} (for a row
	 * record called {@code rowName}), else the one whose path's last part is
	 * {@code childName}, if there's only one such.
	 */
	public Optional<ElementMeaning> meaningOf(String rowName, String childName) {
		if (rowName != null) {
			Optional<ElementMeaning> exact = meanings.stream()
					.filter(m -> m.path().equals(rowName + "." + childName) || m.path().endsWith("." + rowName + "." + childName))
					.findFirst();
			if (exact.isPresent()) {
				return exact;
			}
		}
		List<ElementMeaning> byName = meanings.stream().filter(m -> m.lastPart().equals(childName)).toList();
		return byName.size() == 1 ? Optional.of(byName.get(0)) : Optional.empty();
	}
}
