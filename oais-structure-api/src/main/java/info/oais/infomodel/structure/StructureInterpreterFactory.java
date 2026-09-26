package info.oais.infomodel.structure;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import info.oais.infomodel.interfaces.AbstractOaisFactory;

/**
 * Locates whichever {@link StructureInterpreterProvider}s are on the
 * classpath - normally one per adapter module actually depended on, of the
 * three provided ({@code oais-structure-dfdl}, {@code oais-structure-kaitai},
 * {@code oais-structure-drb}), but any number of further ones can register
 * themselves the same way - and uses them to build {@link ExecutableStructureRepInfo}
 * instances on request, realising oaisCore's existing {@link AbstractOaisFactory}
 * pattern for this project's structure-interpretation engines.
 *
 * <p>Typical use:</p>
 *
 * <pre>{@code
 * StructureInterpreterFactory factory = new StructureInterpreterFactory();
 * FormatSpecification spec = new DfdlFormatSpecification(schemaUri);
 * ExecutableStructureRepInfo structureRepInfo = factory.create(spec);
 * StructureNode tree = structureRepInfo.apply(someDigitalObject);
 * }</pre>
 */
public final class StructureInterpreterFactory implements AbstractOaisFactory<ExecutableStructureRepInfo> {

	private final Map<SpecificationLanguage, StructureInterpreterProvider> providersByLanguage =
			new EnumMap<>(SpecificationLanguage.class);

	/**
	 * Discovers providers via {@link ServiceLoader#load(Class)} using this
	 * class's own classloader. This is what most applications should use.
	 */
	public StructureInterpreterFactory() {
		this(ServiceLoader.load(StructureInterpreterProvider.class));
	}

	/**
	 * Builds a factory from an explicit set of providers - useful in tests,
	 * or in an OSGi/modular runtime where {@link ServiceLoader}'s default
	 * classloader lookup is not appropriate.
	 *
	 * @param discovered the providers to make available, regardless of
	 *                    {@link StructureInterpreterProvider#isAvailable()} elsewhere on the classpath
	 */
	public StructureInterpreterFactory(Iterable<StructureInterpreterProvider> discovered) {
		for (StructureInterpreterProvider provider : discovered) {
			if (provider.isAvailable()) {
				providersByLanguage.putIfAbsent(provider.getSpecificationLanguage(), provider);
			}
		}
	}

	/**
	 * Satisfies {@link AbstractOaisFactory}'s generic entry point. A bare
	 * type name is not enough to build a working interpreter (an actual
	 * schema location, generated class, or protocol id is also required),
	 * so this always fails; use {@link #create(FormatSpecification)}
	 * instead.
	 *
	 * @param infoType ignored
	 * @throws UnsupportedOperationException always
	 */
	@Override
	public ExecutableStructureRepInfo create(String infoType) {
		throw new UnsupportedOperationException(
				"An ExecutableStructureRepInfo needs a FormatSpecification (schema location, generated "
						+ "parser class, protocol id, ...), not just a type name '" + infoType
						+ "'. Use create(FormatSpecification) instead.");
	}

	/**
	 * Build an {@link ExecutableStructureRepInfo} for the given specification,
	 * dispatching to whichever provider is registered for its
	 * {@link FormatSpecification#getSpecificationLanguage()}.
	 *
	 * @param formatSpecification the specification to build an interpreter for
	 * @return a ready-to-use executable Structure RepInfo
	 * @throws IllegalStateException if no provider is registered for that language
	 */
	public ExecutableStructureRepInfo create(FormatSpecification formatSpecification) {
		SpecificationLanguage language = formatSpecification.getSpecificationLanguage();
		StructureInterpreterProvider provider = providersByLanguage.get(language);
		if (provider == null) {
			throw new IllegalStateException("No StructureInterpreterProvider is available for " + language
					+ " - available: " + providersByLanguage.keySet()
					+ ". Is the corresponding adapter module (" + adapterModuleNameFor(language)
					+ ") on the classpath, and is its underlying engine "
					+ "(Daffodil / Kaitai runtime / DRB) resolvable?");
		}
		return provider.create(formatSpecification);
	}

	private static String adapterModuleNameFor(SpecificationLanguage language) {
		return switch (language) {
			case DFDL -> "oais-structure-dfdl";
			case KAITAI_STRUCT -> "oais-structure-kaitai";
			case DRB -> "oais-structure-drb";
			case OTHER -> "a custom adapter module";
		};
	}

	/**
	 * @return the languages for which a provider is currently registered and available
	 */
	public Set<SpecificationLanguage> availableLanguages() {
		return Collections.unmodifiableSet(providersByLanguage.keySet());
	}
}
