package info.oais.infomodel.structure;

import java.net.URI;
import java.util.Optional;

/**
 * Identifies, for one {@link ExecutableStructureRepInfo}, which format it
 * interprets and (loosely) where that format is defined - a DFDL schema
 * location, a Kaitai-generated parser class, a DRB protocol identifier.
 *
 * <p>Deliberately thin: what a "specification" actually consists of differs
 * enough between engines (DFDL and DRB consume a schema resource - bytes or
 * a URI - at run time; Kaitai Struct's schema is compiled ahead of time into
 * a Java class and is not consumed as bytes at all) that forcing one shape
 * on all three would either lose information or be misleading. Each adapter
 * module defines its own concrete {@code FormatSpecification}
 * ({@code DfdlFormatSpecification}, {@code KaitaiFormatSpecification},
 * {@code DrbFormatSpecification}) with whatever fields it actually needs;
 * this interface exists so generic code (in particular
 * {@link StructureInterpreterFactory}) can still dispatch on
 * {@link #getSpecificationLanguage()} without depending on any one adapter
 * module.</p>
 *
 * <p>Conceptually, the schema/parser a {@code FormatSpecification} points at
 * is itself Representation Information for the Structure Representation
 * Information's own Data Object (its bytes) - OAIS's Representation
 * Information recursion applies here too - but modelling that recursively
 * is left to whoever assembles the full
 * {@link info.oais.infomodel.interfaces.RepresentationInformationNetwork};
 * this interface only needs enough to let an engine find and run the format
 * definition.</p>
 */
public interface FormatSpecification {

	/**
	 * @return which engine/language this specification is for
	 */
	SpecificationLanguage getSpecificationLanguage();

	/**
	 * Where the format definition itself can be found (a DFDL schema file, a
	 * {@code .ksy} source, documentation for a DRB protocol), for provenance
	 * and logging. Not necessarily where an adapter actually reads the
	 * definition from at run time - see the note on {@link #getSpecificationLanguage()}.
	 *
	 * @return the location, if known
	 */
	default Optional<URI> getSpecificationLocation() {
		return Optional.empty();
	}
}
