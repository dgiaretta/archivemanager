package info.oais.infomodel.structure.description;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A capability beyond the core model that only some description languages
 * can express. The core - fields, records, choices, counts, lengths and
 * conditions computed from earlier fields - generates for every language; a
 * description that uses a feature can only be generated for the languages
 * listed here.
 */
public enum Feature {
	BIT_FIELDS("bit fields", DescriptionLanguage.DFDL, DescriptionLanguage.KAITAI, DescriptionLanguage.EAST),
	SIZED_RECORDS("records of a stated size", DescriptionLanguage.DFDL, DescriptionLanguage.KAITAI,
			DescriptionLanguage.EAST),
	DELIMITED_TEXT("delimited text", DescriptionLanguage.DFDL, DescriptionLanguage.KAITAI, DescriptionLanguage.DRB,
			DescriptionLanguage.DRB_PYTHON),
	NIL_VALUES("nil values in delimited text", DescriptionLanguage.DFDL),
	QUOTED_TEXT("quoted values in delimited text", DescriptionLanguage.DFDL),
	NUMBER_FORMATS("number formats in delimited text", DescriptionLanguage.DFDL),
	ABSOLUTE_OFFSETS("elements at an absolute offset", DescriptionLanguage.KAITAI),
	COMPRESSION("zlib-compressed records", DescriptionLanguage.KAITAI);

	private final String label;
	private final Set<DescriptionLanguage> languages;

	Feature(String label, DescriptionLanguage first, DescriptionLanguage... rest) {
		this.label = label;
		this.languages = EnumSet.of(first, rest);
	}

	public String label() {
		return label;
	}

	public Set<DescriptionLanguage> languages() {
		return EnumSet.copyOf(languages);
	}

	public boolean supportedBy(DescriptionLanguage language) {
		return languages.contains(language);
	}

	/** Whether every one of {@code targets} supports this feature (false for no targets). */
	public boolean supportedByAll(Collection<DescriptionLanguage> targets) {
		return !targets.isEmpty() && languages.containsAll(targets);
	}

	/** The languages that support this feature, e.g. "DFDL and Kaitai Struct". */
	public String languagesText() {
		List<String> names = languages.stream().map(DescriptionLanguage::label).toList();
		return names.size() == 1 ? names.get(0)
				: String.join(", ", names.subList(0, names.size() - 1)) + " and " + names.get(names.size() - 1);
	}

	/** The features {@code e} itself uses (not its children's). */
	public static Set<Feature> usedBy(ElementDescription e) {
		Set<Feature> used = EnumSet.noneOf(Feature.class);
		if (e instanceof FieldDescription f) {
			if (f.type() == PrimitiveType.BITS) {
				used.add(BIT_FIELDS);
			}
			if (f.nilValue() != null) {
				used.add(NIL_VALUES);
			}
			if (f.numberFormat() != null) {
				used.add(NUMBER_FORMATS);
			}
			if (f.offset() != null) {
				used.add(ABSOLUTE_OFFSETS);
			}
		} else if (e instanceof RecordDescription r) {
			if (r.size() != null) {
				used.add(SIZED_RECORDS);
			}
			if (r.compression() != null) {
				used.add(COMPRESSION);
			}
			if (r.offset() != null) {
				used.add(ABSOLUTE_OFFSETS);
			}
			if (r.text() != null) {
				used.add(DELIMITED_TEXT);
			}
			if (r.text() != null && r.text().quote() != null) {
				used.add(QUOTED_TEXT);
			}
		}
		return used;
	}

	/** Every feature the format uses, with the elements that use it, in document order. */
	public static Map<Feature, List<ElementDescription>> used(FormatDescription format) {
		Map<Feature, List<ElementDescription>> used = new LinkedHashMap<>();
		for (ElementDescription e : Descriptions.all(format.root())) {
			for (Feature feature : usedBy(e)) {
				used.computeIfAbsent(feature, k -> new ArrayList<>()).add(e);
			}
		}
		return used;
	}

	/** The features {@code format} uses that {@code language} can't express. */
	public static Set<Feature> unsupported(FormatDescription format, DescriptionLanguage language) {
		return used(format).keySet().stream().filter(f -> !f.supportedBy(language))
				.collect(Collectors.toCollection(() -> EnumSet.noneOf(Feature.class)));
	}

	/**
	 * For generators: fails, naming the features, if {@code language} can't
	 * express everything {@code format} uses.
	 */
	public static void requireSupported(FormatDescription format, DescriptionLanguage language) {
		Set<Feature> missing = unsupported(format, language);
		if (!missing.isEmpty()) {
			throw new UnsupportedFeatureException(language.label() + " can't express "
					+ missing.stream().map(f -> f.label() + " (" + f.languagesText() + " only)")
							.collect(Collectors.joining(", "))
					+ ", which this description uses.");
		}
	}

	/** Thrown by a generator asked for a language that can't express the description. */
	public static final class UnsupportedFeatureException extends IllegalArgumentException {
		private static final long serialVersionUID = 1L;

		public UnsupportedFeatureException(String message) {
			super(message);
		}
	}
}
