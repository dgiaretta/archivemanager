package info.oais.infomodel.structure.drb;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

import info.oais.infomodel.structure.FormatSpecification;
import info.oais.infomodel.structure.SpecificationLanguage;

/**
 * Points a {@link DrbStructureRepInfo} at the DRB factory resolver class to
 * use and, optionally, a protocol/format hint.
 *
 * <p>DRB is usually able to auto-detect a Digital Object's format from its
 * content (that is much of the point of it), so most callers only need
 * {@link #DrbFormatSpecification()}, which uses DRB's default factory
 * resolver. {@link #protocolHint()} is provided for cases where you already
 * know the format and want to bypass or confirm auto-detection; whether/how
 * a given DRB version honours it is left to {@link DrbStructureRepInfo} to
 * attempt on a best-effort basis.</p>
 */
public final class DrbFormatSpecification implements FormatSpecification {

	private final String factoryResolverClassName;
	private final String protocolHint;
	private final URI specificationLocation;

	public DrbFormatSpecification() {
		this("fr.gael.drb.DrbFactoryResolver", null, null);
	}

	public DrbFormatSpecification(String factoryResolverClassName, String protocolHint, URI specificationLocation) {
		this.factoryResolverClassName = Objects.requireNonNull(factoryResolverClassName, "factoryResolverClassName");
		this.protocolHint = protocolHint;
		this.specificationLocation = specificationLocation;
	}

	/**
	 * @return the fully-qualified class name of the DRB factory resolver to
	 *         look up via reflection, e.g. {@code fr.gael.drb.DrbFactoryResolver}
	 *         for classic DRB, or whatever the equivalent is in the DRB
	 *         Cortex distribution you are using
	 */
	public String getFactoryResolverClassName() {
		return factoryResolverClassName;
	}

	/**
	 * @return an optional protocol/format identifier to pass through to DRB,
	 *         bypassing content-sniffing auto-detection where the installed
	 *         DRB version supports doing so
	 */
	public Optional<String> protocolHint() {
		return Optional.ofNullable(protocolHint);
	}

	@Override
	public SpecificationLanguage getSpecificationLanguage() {
		return SpecificationLanguage.DRB;
	}

	@Override
	public Optional<URI> getSpecificationLocation() {
		return Optional.ofNullable(specificationLocation);
	}

	@Override
	public String toString() {
		return "DrbFormatSpecification{factoryResolverClassName=" + factoryResolverClassName
				+ ", protocolHint=" + protocolHint + "}";
	}
}
