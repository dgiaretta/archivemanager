package info.oais.infomodel.structure.kaitai;

import info.oais.infomodel.structure.ExecutableStructureRepInfo;
import info.oais.infomodel.structure.FormatSpecification;
import info.oais.infomodel.structure.SpecificationLanguage;
import info.oais.infomodel.structure.StructureInterpreterProvider;

/**
 * Registers {@link KaitaiStructureRepInfo} with {@link
 * info.oais.infomodel.structure.StructureInterpreterFactory} via
 * {@link java.util.ServiceLoader} - see
 * {@code META-INF/services/info.oais.infomodel.structure.StructureInterpreterProvider}
 * in this module's resources.
 */
public final class KaitaiStructureInterpreterProvider implements StructureInterpreterProvider {

	@Override
	public SpecificationLanguage getSpecificationLanguage() {
		return SpecificationLanguage.KAITAI_STRUCT;
	}

	@Override
	public boolean isAvailable() {
		try {
			Class.forName("io.kaitai.struct.KaitaiStruct");
			return true;
		} catch (ClassNotFoundException | LinkageError e) {
			return false;
		}
	}

	@Override
	public ExecutableStructureRepInfo create(FormatSpecification formatSpecification) {
		if (!(formatSpecification instanceof KaitaiFormatSpecification kaitaiSpec)) {
			throw new IllegalArgumentException(
					"KaitaiStructureInterpreterProvider expects a KaitaiFormatSpecification, got "
							+ (formatSpecification == null ? "null" : formatSpecification.getClass()));
		}
		return new KaitaiStructureRepInfo(kaitaiSpec);
	}
}
