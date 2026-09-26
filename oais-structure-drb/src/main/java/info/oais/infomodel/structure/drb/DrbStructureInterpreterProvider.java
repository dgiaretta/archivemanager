package info.oais.infomodel.structure.drb;

import info.oais.infomodel.structure.ExecutableStructureRepInfo;
import info.oais.infomodel.structure.FormatSpecification;
import info.oais.infomodel.structure.SpecificationLanguage;
import info.oais.infomodel.structure.StructureInterpreterProvider;

/**
 * Registers {@link DrbStructureRepInfo} with {@link
 * info.oais.infomodel.structure.StructureInterpreterFactory} via
 * {@link java.util.ServiceLoader} - see
 * {@code META-INF/services/info.oais.infomodel.structure.StructureInterpreterProvider}
 * in this module's resources.
 *
 * <p>{@link #isAvailable()} probes for the default classic-DRB class name;
 * if you are using a DRB Cortex distribution with a different resolver
 * class, either add that class to the probe list here or simply rely on
 * {@link DrbStructureRepInfo} to fail with a clear message when it cannot
 * find the class named in your {@link DrbFormatSpecification}.</p>
 */
public final class DrbStructureInterpreterProvider implements StructureInterpreterProvider {

	private static final String[] KNOWN_RESOLVER_CLASSES = {
			"fr.gael.drb.DrbFactoryResolver"
	};

	@Override
	public SpecificationLanguage getSpecificationLanguage() {
		return SpecificationLanguage.DRB;
	}

	@Override
	public boolean isAvailable() {
		for (String className : KNOWN_RESOLVER_CLASSES) {
			try {
				Class.forName(className);
				return true;
			} catch (ClassNotFoundException | LinkageError ignored) {
				// try the next known class name
			}
		}
		return false;
	}

	@Override
	public ExecutableStructureRepInfo create(FormatSpecification formatSpecification) {
		if (!(formatSpecification instanceof DrbFormatSpecification drbSpec)) {
			throw new IllegalArgumentException(
					"DrbStructureInterpreterProvider expects a DrbFormatSpecification, got "
							+ (formatSpecification == null ? "null" : formatSpecification.getClass()));
		}
		return new DrbStructureRepInfo(drbSpec);
	}
}
