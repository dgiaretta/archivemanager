package info.oais.infomodel.structure.dfdl;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

import info.oais.infomodel.structure.FormatSpecification;
import info.oais.infomodel.structure.SpecificationLanguage;

/**
 * Points a {@link DfdlStructureRepInfo} at the DFDL schema it should compile
 * and use.
 *
 * <p>Only the schema's default root element is currently supported (see
 * {@link DfdlStructureRepInfo} for why picking an alternate root is left to
 * you): if your schema defines a single top-level element, or you always
 * want the first one, this is all you need.</p>
 */
public final class DfdlFormatSpecification implements FormatSpecification {

	private final URI schemaLocation;

	public DfdlFormatSpecification(URI schemaLocation) {
		this.schemaLocation = Objects.requireNonNull(schemaLocation, "schemaLocation");
	}

	/**
	 * @return where the {@code .dfdl.xsd} schema can be read from (a
	 *         {@code classpath:}, {@code file:} or {@code http(s):} URI,
	 *         anything {@code org.apache.daffodil.japi.Compiler#compileSource}
	 *         accepts)
	 */
	public URI getSchemaLocation() {
		return schemaLocation;
	}

	@Override
	public SpecificationLanguage getSpecificationLanguage() {
		return SpecificationLanguage.DFDL;
	}

	@Override
	public Optional<URI> getSpecificationLocation() {
		return Optional.of(schemaLocation);
	}

	@Override
	public String toString() {
		return "DfdlFormatSpecification{schemaLocation=" + schemaLocation + "}";
	}
}
