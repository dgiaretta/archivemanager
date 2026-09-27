package info.oais.infomodel.structure.drb;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import info.oais.infomodel.interfaces.DigitalObject;
import info.oais.infomodel.structure.AbstractExecutableStructureRepInfo;
import info.oais.infomodel.structure.StructureInterpretationException;
import info.oais.infomodel.structure.StructureNode;

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
 */
public final class DrbStructureRepInfo extends AbstractExecutableStructureRepInfo {

	public DrbStructureRepInfo(DrbFormatSpecification formatSpecification) {
		super(formatSpecification);
	}

	@Override
	public DrbFormatSpecification getFormatSpecification() {
		return (DrbFormatSpecification) super.getFormatSpecification();
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
