package info.oais.infomodel.structure.drb;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import info.oais.infomodel.interfaces.DigitalObject;
import info.oais.infomodel.structure.AbstractExecutableStructureRepInfo;
import info.oais.infomodel.structure.ElementPath;
import info.oais.infomodel.structure.StructureInterpretationException;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.WritableStructureRepInfo;

/**
 * {@link info.oais.infomodel.structure.ExecutableStructureRepInfo} backed by
 * GAEL's Java DRB (Data Request Broker), reached through {@link DrbApi} -
 * see this module's {@code README-DRB.md}.
 *
 * <p>DRB opens data by file path, so the Digital Object's bytes are written
 * to a temporary file first. DRB then either recognises the format itself or
 * applies the {@link DrbFormatSpecification}'s SDF schema, and the resulting
 * node tree is copied into plain {@link StructureNode}s before the temporary
 * file is deleted - so, like the DFDL and Kaitai adapters, the returned tree
 * is fully in memory and the Digital Object's stream is closed on return.
 * {@link DrbApi#MAX_NODES} bounds that copy.</p>
 *
 * <p><b>Writing back</b> ({@link #write}) needs an SDF schema: DRB's SDF
 * blocks write each value where it was read from, into a copy of the data,
 * which is returned; bytes the schema doesn't describe are kept as they
 * were. A delimited value can change length (DRB moves what follows); a
 * value of fixed length can't, so a change that doesn't come back as asked
 * when the result is read again is refused.</p>
 */
public final class DrbStructureRepInfo extends AbstractExecutableStructureRepInfo implements WritableStructureRepInfo {

	public DrbStructureRepInfo(DrbFormatSpecification formatSpecification) {
		super(formatSpecification);
	}

	@Override
	public DrbFormatSpecification getFormatSpecification() {
		return (DrbFormatSpecification) super.getFormatSpecification();
	}

	@Override
	public byte[] write(DigitalObject original, Map<ElementPath, String> changes) {
		if (!DrbApi.isAvailable()) {
			throw new StructureInterpretationException(
					"The DRB library (fr.gael.drb) is not on the classpath - see README-DRB.md");
		}
		Path schema = getFormatSpecification().sdfSchemaLocation().map(Path::of).orElseThrow(
				() -> new StructureInterpretationException("Writing back with DRB needs an SDF schema"));
		Path data = null;
		try {
			data = Files.createTempFile("oais-drb-", "." + getFormatSpecification().fileExtension().orElse("dat"));
			try (InputStream in = original.getObject()) {
				Files.copy(in, data, StandardCopyOption.REPLACE_EXISTING);
			}
			byte[] written = DrbApi.write(data, schema, changes);
			if (!changes.isEmpty()) {
				checkChanges(written, data, schema, changes);
			}
			return written;
		} catch (StructureInterpretationException e) {
			throw e;
		} catch (Exception e) {
			throw new StructureInterpretationException("DRB couldn't write the data back: " + e, e);
		} finally {
			if (data != null) {
				try {
					Files.deleteIfExists(data);
				} catch (IOException e) {
					data.toFile().deleteOnExit();
				}
			}
		}
	}

	/**
	 * DRB pads a short value to its element's fixed length and cuts a long
	 * one, without complaint; reading the written data back shows whether
	 * each change came out as asked (ignoring padding).
	 */
	private static void checkChanges(byte[] written, Path data, Path schema, Map<ElementPath, String> changes)
			throws Exception {
		Files.write(data, written);
		StructureNode root = DrbApi.decode(data, schema);
		for (Map.Entry<ElementPath, String> change : changes.entrySet()) {
			String got = change.getKey().find(root).getValue().map(String::valueOf).orElse("").strip();
			String wanted = change.getValue().strip();
			if (!got.equals(wanted) && !sameNumber(got, wanted)) {
				throw new StructureInterpretationException(change.getKey() + ": DRB wrote '" + got + "' rather than '"
						+ wanted + "' - the value doesn't fit the element's length or type");
			}
		}
	}

	private static boolean sameNumber(String a, String b) {
		try {
			return new java.math.BigDecimal(a).compareTo(new java.math.BigDecimal(b)) == 0;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	@Override
	protected StructureNode doApply(DigitalObject digitalObject) throws Exception {
		if (!DrbApi.isAvailable()) {
			throw new StructureInterpretationException(
					"The DRB library (fr.gael.drb) is not on the classpath - see README-DRB.md");
		}
		// DRB recognises built-in formats by file extension, so the temp file carries the expected one.
		Path data = Files.createTempFile("oais-drb-", "." + getFormatSpecification().fileExtension().orElse("dat"));
		try {
			try (InputStream in = digitalObject.getObject()) {
				Files.copy(in, data, StandardCopyOption.REPLACE_EXISTING);
			}
			Path schema = getFormatSpecification().sdfSchemaLocation().map(Path::of).orElse(null);
			return DrbApi.decode(data, schema);
		} finally {
			try {
				Files.deleteIfExists(data);
			} catch (IOException e) {
				// Still held open by DRB (possible on Windows): don't fail the decode over it.
				data.toFile().deleteOnExit();
			}
		}
	}
}
