package info.oais.infomodel.structure.kaitai;

import java.io.InputStream;
import java.lang.reflect.Constructor;

import io.kaitai.struct.ByteBufferKaitaiStream;
import io.kaitai.struct.KaitaiStream;
import io.kaitai.struct.KaitaiStruct;

import info.oais.infomodel.structure.AbstractExecutableStructureRepInfo;
import info.oais.infomodel.structure.StructureInterpretationException;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.interfaces.DigitalObject;

/**
 * {@link info.oais.infomodel.structure.ExecutableStructureRepInfo} backed by
 * a Kaitai-Struct-generated Java parser class (see
 * {@link KaitaiFormatSpecification}), bridged to {@link StructureNode}
 * generically via {@link KaitaiReflectiveStructureNode}.
 *
 * <p>The Kaitai Java runtime parses from an in-memory buffer
 * ({@link ByteBufferKaitaiStream}) rather than streaming, so - unlike the
 * DRB adapter - this reads the whole {@link DigitalObject} into memory
 * up front. For the very large objects Kaitai Struct is sometimes used on,
 * consider {@code io.kaitai.struct.RandomAccessFileKaitaiStream} instead if
 * your {@link DigitalObject} is (or can cheaply be materialised as) a local
 * file; that is a straightforward variant of {@link #doApply} left out here
 * to keep this reference adapter simple.</p>
 */
public final class KaitaiStructureRepInfo extends AbstractExecutableStructureRepInfo {

	public KaitaiStructureRepInfo(KaitaiFormatSpecification formatSpecification) {
		super(formatSpecification);
	}

	@Override
	public KaitaiFormatSpecification getFormatSpecification() {
		return (KaitaiFormatSpecification) super.getFormatSpecification();
	}

	@Override
	protected StructureNode doApply(DigitalObject digitalObject) throws Exception {
		byte[] bytes;
		try (InputStream in = digitalObject.getObject()) {
			bytes = in.readAllBytes();
		}

		Class<? extends KaitaiStruct> generatedType = getFormatSpecification().getGeneratedType();
		Constructor<? extends KaitaiStruct> constructor = generatedType.getConstructor(KaitaiStream.class);

		KaitaiStruct parsed;
		try {
			parsed = constructor.newInstance(new ByteBufferKaitaiStream(bytes));
		} catch (java.lang.reflect.InvocationTargetException e) {
			throw new StructureInterpretationException(
					"Kaitai Struct parser " + generatedType.getName() + " rejected the DigitalObject's bytes",
					e.getCause() != null ? e.getCause() : e);
		}

		return KaitaiReflectiveStructureNode.ofRoot("root", parsed);
	}
}
