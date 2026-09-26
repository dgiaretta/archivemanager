package info.oais.infomodel.structure;

/**
 * Service Provider Interface implemented once per engine adapter module
 * (DFDL, Kaitai Struct, DRB, ...) and discovered via {@link java.util.ServiceLoader}
 * by {@link StructureInterpreterFactory}. This is what lets a new engine be
 * added later as a self-contained module - drop it on the classpath with a
 * {@code META-INF/services/info.oais.infomodel.structure.StructureInterpreterProvider}
 * entry - without changing {@code oais-structure-api}, oaisCore, or any of
 * the other adapter modules.
 */
public interface StructureInterpreterProvider {

	/**
	 * @return the {@link SpecificationLanguage} this provider builds interpreters for
	 */
	SpecificationLanguage getSpecificationLanguage();

	/**
	 * Whether the engine this provider wraps is actually usable right now -
	 * typically implemented as a cheap {@code Class.forName(...)} probe for
	 * the engine's own main class, so that (for example) the DRB provider
	 * quietly declines to register itself when no DRB jar has been added to
	 * the classpath, rather than failing at class-loading time.
	 *
	 * @return true if {@link #create} can be expected to succeed
	 */
	boolean isAvailable();

	/**
	 * Build an {@link ExecutableStructureRepInfo} configured from the given
	 * specification.
	 *
	 * @param formatSpecification a specification for this provider's
	 *                             {@link #getSpecificationLanguage()} (i.e. of the concrete type the
	 *                             corresponding adapter module defines)
	 * @return a new, ready-to-use executable Structure RepInfo
	 * @throws IllegalArgumentException if {@code formatSpecification} is not
	 *                                   of the type this provider expects
	 */
	ExecutableStructureRepInfo create(FormatSpecification formatSpecification);
}
