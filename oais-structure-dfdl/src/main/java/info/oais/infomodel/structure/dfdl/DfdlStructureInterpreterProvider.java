package info.oais.infomodel.structure.dfdl;

import info.oais.infomodel.structure.ExecutableStructureRepInfo;
import info.oais.infomodel.structure.FormatSpecification;
import info.oais.infomodel.structure.SpecificationLanguage;
import info.oais.infomodel.structure.StructureInterpreterProvider;

/**
 * Registers {@link DfdlStructureRepInfo} with {@link
 * info.oais.infomodel.structure.StructureInterpreterFactory} via
 * {@link java.util.ServiceLoader} - see
 * {@code META-INF/services/info.oais.infomodel.structure.StructureInterpreterProvider}
 * in this module's resources.
 */
public final class DfdlStructureInterpreterProvider implements StructureInterpreterProvider {

	@Override
	public SpecificationLanguage getSpecificationLanguage() {
		return SpecificationLanguage.DFDL;
	}

	@Override
	public boolean isAvailable() {
		try {
			Class.forName("org.apache.daffodil.japi.Daffodil");
			return true;
		} catch (ClassNotFoundException | LinkageError e) {
			return false;
		}
	}

	@Override
	public ExecutableStructureRepInfo create(FormatSpecification formatSpecification) {
		if (!(formatSpecification instanceof DfdlFormatSpecification dfdlSpec)) {
			throw new IllegalArgumentException(
					"DfdlStructureInterpreterProvider expects a DfdlFormatSpecification, got "
							+ (formatSpecification == null ? "null" : formatSpecification.getClass()));
		}
		return new DfdlStructureRepInfo(dfdlSpec);
	}
}
