package info.oais.infomodel.structure.kaitai;

import java.util.Objects;

import io.kaitai.struct.KaitaiStream;
import io.kaitai.struct.KaitaiStruct;

import info.oais.infomodel.structure.FormatSpecification;
import info.oais.infomodel.structure.SpecificationLanguage;

/**
 * Points a {@link KaitaiStructureRepInfo} at the generated Kaitai Struct
 * parser class to use.
 *
 * <p>Unlike DFDL or DRB, a Kaitai Struct format's "specification" is not
 * consumed at run time as bytes: the {@code .ksy} source (see
 * {@code src/main/ksy/point2d.ksy} for this module's demo format) is
 * compiled <em>ahead of time</em>, by the separate {@code ksc} compiler,
 * into a plain Java class. This specification is therefore just that class -
 * it must extend {@link KaitaiStruct} and declare a public constructor
 * taking a single {@link KaitaiStream} argument, which is exactly what
 * every class Kaitai's Java target generates looks like.</p>
 */
public final class KaitaiFormatSpecification implements FormatSpecification {

	private final Class<? extends KaitaiStruct> generatedType;

	public KaitaiFormatSpecification(Class<? extends KaitaiStruct> generatedType) {
		this.generatedType = Objects.requireNonNull(generatedType, "generatedType");
		try {
			generatedType.getConstructor(KaitaiStream.class);
		} catch (NoSuchMethodException e) {
			throw new IllegalArgumentException(
					generatedType + " has no public constructor(KaitaiStream) - is it really a "
							+ "Kaitai-Struct-generated (or generated-shaped) class?",
					e);
		}
	}

	/**
	 * @return the generated parser class to instantiate for each Digital Object
	 */
	public Class<? extends KaitaiStruct> getGeneratedType() {
		return generatedType;
	}

	@Override
	public SpecificationLanguage getSpecificationLanguage() {
		return SpecificationLanguage.KAITAI_STRUCT;
	}

	@Override
	public String toString() {
		return "KaitaiFormatSpecification{generatedType=" + generatedType.getName() + "}";
	}
}
