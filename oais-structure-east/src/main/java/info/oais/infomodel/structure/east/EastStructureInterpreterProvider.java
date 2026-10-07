package info.oais.infomodel.structure.east;

import info.oais.infomodel.structure.ExecutableStructureRepInfo;
import info.oais.infomodel.structure.FormatSpecification;
import info.oais.infomodel.structure.SpecificationLanguage;
import info.oais.infomodel.structure.StructureInterpreterProvider;

/** Registers {@link EastStructureRepInfo} for {@link SpecificationLanguage#EAST}; always available. */
public final class EastStructureInterpreterProvider implements StructureInterpreterProvider {

	@Override
	public SpecificationLanguage getSpecificationLanguage() {
		return SpecificationLanguage.EAST;
	}

	@Override
	public boolean isAvailable() {
		return true;
	}

	@Override
	public ExecutableStructureRepInfo create(FormatSpecification formatSpecification) {
		if (!(formatSpecification instanceof EastFormatSpecification east)) {
			throw new IllegalArgumentException("EastStructureInterpreterProvider expects an EastFormatSpecification, got "
					+ (formatSpecification == null ? "null" : formatSpecification.getClass()));
		}
		return new EastStructureRepInfo(east);
	}
}
