package info.oais.archive.manager.service.format;

import info.oais.infomodel.implementation.DigitalObjectRefImpl;
import info.oais.infomodel.structure.description.FormatDescription;
import info.oais.infomodel.structure.drb.DrbFormatSpecification;
import info.oais.infomodel.structure.drb.DrbStructureRepInfo;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Runs a generated DRB SDF schema (see {@link DrbGenerator}, Java target)
 * against a sample data file with GAEL's Java DRB, in-process, through
 * {@code oais-structure-drb}'s {@link DrbStructureRepInfo} -- the DRB
 * counterpart of {@link DfdlSampleRunner}. DRB reports each field's byte
 * offset and length, so the result carries positions.
 */
@Component
public class DrbSampleRunner {

    public SampleDecodeResult run(String sdfSchema, byte[] sample) {
        return run(null, sdfSchema, sample);
    }

    /** @param format the description the schema was generated from, to line the result up against (null: don't) */
    public SampleDecodeResult run(FormatDescription format, String sdfSchema, byte[] sample) {
        Path schemaFile = null;
        try {
            schemaFile = Files.createTempFile("repinfo-tools-", ".drb.xsd");
            Files.writeString(schemaFile, sdfSchema, StandardCharsets.UTF_8);
            DrbStructureRepInfo repInfo = new DrbStructureRepInfo(new DrbFormatSpecification(schemaFile.toUri()));
            var root = repInfo.apply(new DigitalObjectRefImpl(new ByteArrayInputStream(sample)));
            return format == null ? SampleDecodeResult.of(root) : SampleDecodeResult.of(format, root);
        } catch (IOException e) {
            return SampleDecodeResult.failure("Could not write the schema to a temporary file: " + e.getMessage());
        } catch (RuntimeException e) {
            // Includes StructureInterpretationException; its cause, if any, is DRB's own error.
            String message = e.getMessage() != null ? e.getMessage() : e.toString();
            if (e.getCause() != null && e.getCause().getMessage() != null && !message.contains(e.getCause().getMessage())) {
                message += "\n" + e.getCause().getMessage();
            }
            return SampleDecodeResult.failure(message);
        } finally {
            if (schemaFile != null) {
                try {
                    Files.deleteIfExists(schemaFile);
                } catch (IOException ignored) {
                    // A leftover temp file isn't worth failing the request over.
                }
            }
        }
    }
}
