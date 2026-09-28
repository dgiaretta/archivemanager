package info.oais.archive.manager.service.format;

import info.oais.infomodel.implementation.DigitalObjectRefImpl;
import info.oais.infomodel.structure.ElementPath;
import info.oais.infomodel.structure.StructureNode;
import info.oais.infomodel.structure.description.FormatDescription;
import info.oais.infomodel.structure.dfdl.DfdlFormatSpecification;
import info.oais.infomodel.structure.dfdl.DfdlStructureRepInfo;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Runs a generated DFDL schema (see {@link DfdlGenerator}) against a sample
 * data file through {@code oais-structure-dfdl}'s {@link DfdlStructureRepInfo}
 * -- the same Apache Daffodil-backed executable Structure Representation
 * Information the adapter modules use -- so RepInfo Tools can show whether
 * the description actually decodes real bytes before it's saved.
 *
 * <p>Only DFDL is run, not Kaitai or DRB: Daffodil compiles a
 * {@code .dfdl.xsd} at runtime, whereas {@code oais-structure-kaitai} needs a
 * Java class generated ahead of time by the Kaitai Struct compiler, and
 * {@code oais-structure-drb} needs the (non-Maven-Central) DRB library.
 */
@Component
public class DfdlSampleRunner {

    /**
     * Byte positions in the result are best-effort in {@code oais-structure-dfdl}
     * (see its {@code PositionTrackingInfosetOutputter}): they depend on internal
     * Daffodil accessors that not every Daffodil version exposes.
     */
    public SampleDecodeResult run(String dfdlSchema, byte[] sample) {
        return run(null, dfdlSchema, sample);
    }

    /** @param format the description the schema was generated from, to line the result up against (null: don't) */
    public SampleDecodeResult run(FormatDescription format, String dfdlSchema, byte[] sample) {
        Path schemaFile = null;
        try {
            // Daffodil compiles from a URI, not a string.
            schemaFile = Files.createTempFile("repinfo-tools-", ".dfdl.xsd");
            Files.writeString(schemaFile, dfdlSchema, StandardCharsets.UTF_8);
            DfdlStructureRepInfo repInfo = new DfdlStructureRepInfo(new DfdlFormatSpecification(schemaFile.toUri()));
            StructureNode root = repInfo.apply(new DigitalObjectRefImpl(new ByteArrayInputStream(sample)));
            return format == null ? SampleDecodeResult.of(root) : SampleDecodeResult.of(format, root);
        } catch (IOException e) {
            return SampleDecodeResult.failure("Could not write the schema to a temporary file: " + e.getMessage());
        } catch (RuntimeException e) {
            // StructureInterpretationException carries Daffodil's own diagnostics in its message,
            // or, for an error Daffodil didn't report as a diagnostic, in its cause.
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

    /**
     * Writes {@code sample} back with the schema: decodes it, applies
     * {@code changes} and encodes it again (see {@code DfdlStructureRepInfo#write}).
     */
    public WriteBackResult write(String schema, byte[] sample, Map<ElementPath, String> changes) {
        Path schemaFile = null;
        try {
            schemaFile = Files.createTempFile("repinfo-tools-", ".dfdl.xsd");
            Files.writeString(schemaFile, schema, StandardCharsets.UTF_8);
            DfdlStructureRepInfo repInfo = new DfdlStructureRepInfo(new DfdlFormatSpecification(schemaFile.toUri()));
            byte[] written = repInfo.write(new DigitalObjectRefImpl(new ByteArrayInputStream(sample)), changes);
            return WriteBackResult.of(sample, written, null);
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
