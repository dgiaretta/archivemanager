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
 * <p>{@link #isAvailable()} is {@code true} only when the DRB library itself
 * is on the classpath (see {@link DrbApi}).</p>
 */
public final class DrbStructureInterpreterProvider implements StructureInterpreterProvider {

	@Override
	public SpecificationLanguage getSpecificationLanguage() {
		return SpecificationLanguage.DRB;
	}

	@Override
	public boolean isAvailable() {
		return DrbApi.isAvailable();
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
