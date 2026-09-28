package info.oais.archive.manager.service.format;

import info.oais.infomodel.implementation.DigitalObjectRefImpl;
import info.oais.infomodel.structure.ElementPath;
import info.oais.infomodel.structure.description.FormatDescription;
import info.oais.infomodel.structure.drb.DrbFormatSpecification;
import info.oais.infomodel.structure.drb.DrbStructureRepInfo;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

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

    /** Why an identical DRB round trip says less than the other engines' do. */
    public static final String IN_PLACE_NOTE = "DRB writes each value back in place, into a copy of the sample, so "
            + "bytes the schema doesn't describe are kept as they were: identical bytes show every value is written "
            + "back as stored, not that the schema covers every byte (the decode test above shows that).";

    /**
     * Writes {@code sample} back with the SDF schema, in place: decodes it,
     * applies {@code changes}, and writes every value again at its own offset
     * and length (see {@code DrbStructureRepInfo#write}).
     */
    public WriteBackResult write(String sdfSchema, byte[] sample, Map<ElementPath, String> changes) {
        Path schemaFile = null;
        try {
            schemaFile = Files.createTempFile("repinfo-tools-", ".drb.xsd");
            Files.writeString(schemaFile, sdfSchema, StandardCharsets.UTF_8);
            DrbStructureRepInfo repInfo = new DrbStructureRepInfo(new DrbFormatSpecification(schemaFile.toUri()));
            byte[] written = repInfo.write(new DigitalObjectRefImpl(new ByteArrayInputStream(sample)), changes);
            return WriteBackResult.of(sample, written, IN_PLACE_NOTE);
        } catch (IOException e) {
            return WriteBackResult.failure("Could not write the schema to a temporary file: " + e.getMessage());
        } catch (RuntimeException e) {
            return WriteBackResult.failure(WriteBackResult.message(e));
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
