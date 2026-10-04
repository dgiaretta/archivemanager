package info.oais.archive.manager.web;

import info.oais.archive.manager.service.ArchiveService;
import info.oais.archive.manager.service.BitStore;
import info.oais.archive.manager.service.format.DataObjectViewService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
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
 *       and Fiji/ImageJ open;</li>
 *   <li>{@code /api/data-objects/{id}/pixels.json} -- an image's pixels, for
 *       the archive's own image viewer in the browser;</li>
 *   <li>{@code /api/bits/{id}/{name}} -- bits in the archive's own
 *       {@link BitStore}, e.g. a transformed Data Object's.</li>
 * </ul>
 */
@RestController
public class DataObjectExportController {

    private static final MediaType VOTABLE = MediaType.parseMediaType("application/x-votable+xml");
    private static final MediaType FITS = MediaType.parseMediaType("application/fits");

    private final ArchiveService archive;
    private final DataObjectViewService views;
    private final BitStore bits;

    public DataObjectExportController(ArchiveService archive, DataObjectViewService views, BitStore bits) {
        this.archive = archive;
        this.views = views;
        this.bits = bits;
    }

    /** A Data Object's bits, fetched from its storage location: e.g. to transform with another application. */
    @GetMapping("/api/data-objects/{id}/bits")
    public ResponseEntity<byte[]> dataObjectBits(@PathVariable String id) {
        String iri = archive.decodeId(id);
        java.nio.file.Path dir = null;
        try {
            dir = java.nio.file.Files.createTempDirectory("archive-bits-");
            byte[] bytes = java.nio.file.Files.readAllBytes(views.fetchBits(iri, dir));
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + bitsFileName(iri) + "\"")
                    .body(bytes);
        } catch (IOException | RuntimeException e) {
            return ResponseEntity.unprocessableEntity().contentType(MediaType.TEXT_PLAIN)
                    .body(("Couldn't fetch this Data Object's bits: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        } finally {
            if (dir != null) {
                try (var files = java.nio.file.Files.walk(dir)) {
                    files.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
                } catch (IOException ignored) {
                    // a temporary folder; the system cleans it up eventually
                }
            }
        }
    }

    /** The file name of {@code dataObject}'s bits: its storage location's, else its label's. */
    private String bitsFileName(String dataObject) {
        String fromLocation = views.describe(dataObject, URI::create).map(d -> d.data().getPath())
                .map(path -> path.substring(path.lastIndexOf('/') + 1)).orElse("");
        return fromLocation.isBlank() ? fileName(archive.label(dataObject)) + ".bin" : BitStore.safeName(fromLocation);
    }

    @GetMapping("/api/bits/{id}/{name}")
    public ResponseEntity<Resource> bits(@PathVariable String id, @PathVariable String name) {
        return bits.file(id, name)
                .map(file -> ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                        .<Resource>body(new FileSystemResource(file)))
                .orElseGet(() -> ResponseEntity.notFound().build());
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

    /**
     * An image's pixels, decoded through its Representation Information, for
     * the archive's own viewer in the browser: its size, units and meaning, and
     * its rows, first row first; a missing or non-finite value is null.
     */
    @GetMapping("/api/data-objects/{id}/pixels.json")
    public ResponseEntity<?> pixels(@PathVariable String id) {
        try {
            info.oais.infomodel.structure.image.DecodedImage image = views.image(archive.decodeId(id));
            java.util.List<java.util.List<Double>> rows = new java.util.ArrayList<>(image.height());
            for (Number[] row : image.pixels()) {
                java.util.List<Double> values = new java.util.ArrayList<>(row.length);
                for (Number n : row) {
                    values.add(n == null || !Double.isFinite(n.doubleValue()) ? null : n.doubleValue());
                }
                rows.add(values);
            }
            java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("name", image.name());
            body.put("width", image.width());
            body.put("height", image.height());
            body.put("unit", image.unit());
            body.put("description", image.description());
            body.put("pixels", rows);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
        } catch (IOException | RuntimeException e) {
            return ResponseEntity.unprocessableEntity().contentType(MediaType.TEXT_PLAIN)
                    .body("Couldn't decode this Data Object as an image: " + e.getMessage());
        }
    }

    /** The same as {@link #votable}, at an address ending .vot, for applications that go by the extension (SPLAT). */
    @GetMapping("/api/data-objects/{id}/data.vot")
    public ResponseEntity<byte[]> votableFile(@PathVariable String id) {
        return votable(id);
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
