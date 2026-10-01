package info.oais.archive.manager.web;

import info.oais.archive.manager.service.ArchiveService;
import info.oais.archive.manager.service.format.DataObjectViewService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * A Data Object's Representation Information and data for viewers, open to
 * anyone like browsing is:
 * <ul>
 *   <li>{@code /api/data-objects/{id}/repinfo.ttl} -- its Representation
 *       Information manifest (Turtle), naming its bits' storage location and
 *       each structure description and view at the URL below; TOPCAT (with
 *       the oais-structure-topcat reader) and SPLAT open it by this URL;</li>
 *   <li>{@code /api/specifications/{id}} -- the text of one structure
 *       description or view specification;</li>
 *   <li>{@code /api/data-objects/{id}/votable} -- its data, decoded on the
 *       server, as VOTable, which TOPCAT opens with no plugin at all;</li>
 *   <li>{@code /api/data-objects/{id}/fits} -- an image, decoded on the server
 *       and viewed with its image view, as FITS, which SAOImage DS9, Aladin
 *       and Fiji/ImageJ open.</li>
 * </ul>
 */
@RestController
public class DataObjectExportController {

    private static final MediaType VOTABLE = MediaType.parseMediaType("application/x-votable+xml");
    private static final MediaType FITS = MediaType.parseMediaType("application/fits");

    private final ArchiveService archive;
    private final DataObjectViewService views;

    public DataObjectExportController(ArchiveService archive, DataObjectViewService views) {
        this.archive = archive;
        this.views = views;
    }

    @GetMapping("/api/data-objects/{id}/repinfo.ttl")
    public ResponseEntity<String> manifest(@PathVariable String id) {
        String iri = archive.decodeId(id);
        return views.describe(iri, this::specificationUrl)
                .map(d -> ResponseEntity.ok().contentType(MediaType.parseMediaType("text/turtle;charset=UTF-8"))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"repinfo.ttl\"")
                        .body(DataObjectViewService.manifest(d)))
                .orElseGet(() -> ResponseEntity.status(404).contentType(MediaType.TEXT_PLAIN).body(
                        "This isn't a Data Object with a storage location for its bits and Representation Information."));
    }

    @GetMapping("/api/specifications/{id}")
    public ResponseEntity<String> specification(@PathVariable String id) {
        return views.specification(archive.decodeId(id))
                .map(s -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(s.mediaType() + ";charset=UTF-8"))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"specification" + s.extension() + "\"")
                        .body(s.text()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/api/data-objects/{id}/votable")
    public ResponseEntity<byte[]> votable(@PathVariable String id) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            views.writeVotable(archive.decodeId(id), out);
        } catch (IOException | RuntimeException e) {
            return ResponseEntity.unprocessableEntity().contentType(MediaType.TEXT_PLAIN)
                    .body(("Couldn't make a VOTable of this Data Object: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
        return ResponseEntity.ok().contentType(VOTABLE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"data.vot\"")
                .body(out.toByteArray());
    }

    @GetMapping("/api/data-objects/{id}/fits")
    public ResponseEntity<byte[]> fits(@PathVariable String id) {
        String iri = archive.decodeId(id);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            views.writeFits(iri, out);
        } catch (IOException | RuntimeException e) {
            return ResponseEntity.unprocessableEntity().contentType(MediaType.TEXT_PLAIN)
                    .body(("Couldn't make a FITS image of this Data Object: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
        return ResponseEntity.ok().contentType(FITS)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName(archive.label(iri)) + ".fits\"")
                .body(out.toByteArray());
    }

    /** {@code label} as a file name: letters, digits, dots, dashes and underscores only. */
    static String fileName(String label) {
        String name = label == null ? "" : label.strip().replaceAll("[^A-Za-z0-9._-]+", "-").replaceAll("^[-.]+|-+$", "");
        return name.isEmpty() ? "image" : name;
    }

    private URI specificationUrl(String iri) {
        return ServletUriComponentsBuilder.fromCurrentContextPath().path("/api/specifications/{id}")
                .buildAndExpand(archive.encodeId(iri)).toUri();
    }
}
