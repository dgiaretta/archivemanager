package info.oais.archive.manager.web;

import info.oais.archive.manager.service.ArchiveService;
import info.oais.archive.manager.service.PackageExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Writing out what the archive holds as files (see {@link PackageExportService}),
 * open to anyone like browsing is:
 * <ul>
 *   <li>{@code /api/descriptions/{id}/description.zip} -- the data description
 *       of a Data Object, or of Representation Information;</li>
 *   <li>{@code /api/packages/{id}/bagit.zip} -- an Archival Information
 *       Package as a BagIt bag; with {@code ?bits=refer}, its Data Objects'
 *       bits are referred to in {@code fetch.txt} rather than included.</li>
 * </ul>
 */
@RestController
public class PackageExportController {

    private static final MediaType ZIP = MediaType.parseMediaType("application/zip");

    private final ArchiveService archive;
    private final PackageExportService exports;

    public PackageExportController(ArchiveService archive, PackageExportService exports) {
        this.archive = archive;
        this.exports = exports;
    }

    @GetMapping("/api/descriptions/{id}/description.zip")
    public ResponseEntity<byte[]> description(@PathVariable String id) {
        String iri = archive.decodeId(id);
        if (!exports.hasDescription(iri)) {
            return problem(404, "This is neither Representation Information nor a Data Object interpreted using some.");
        }
        try {
            return download(exports.dataDescription(iri));
        } catch (IOException | RuntimeException e) {
            return problem(422, "Couldn't write out its data description: " + e.getMessage());
        }
    }

    @GetMapping("/api/packages/{id}/bagit.zip")
    public ResponseEntity<byte[]> bag(@PathVariable String id,
                                      @RequestParam(defaultValue = "include") String bits) {
        String iri = archive.decodeId(id);
        if (!exports.isPackage(iri)) {
            return problem(404, "This isn't an Archival Information Package.");
        }
        try {
            return download(exports.bag(iri, "refer".equals(bits)));
        } catch (IOException | RuntimeException e) {
            return problem(422, e.getMessage());
        }
    }

    private static ResponseEntity<byte[]> download(PackageExportService.Export export) {
        return ResponseEntity.ok().contentType(ZIP)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + export.fileName() + "\"")
                .body(export.bytes());
    }

    private static ResponseEntity<byte[]> problem(int status, String message) {
        return ResponseEntity.status(status).contentType(MediaType.TEXT_PLAIN)
                .body(message.getBytes(StandardCharsets.UTF_8));
    }
}
