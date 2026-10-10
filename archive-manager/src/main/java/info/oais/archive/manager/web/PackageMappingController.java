package info.oais.archive.manager.web;

import info.oais.archive.manager.service.ArchiveService;
import info.oais.archive.manager.service.packages.PackageInspector;
import info.oais.archive.manager.service.packages.PackageMappingService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Where the components of an AIP are in the package it's stored as (see
 * {@link PackageMappingService}), open to anyone like browsing is:
 * {@code /packages/{id}/contents} shows the mapping, and
 * {@code /packages/{id}/contents.csv} gives it as CSV.
 */
@Controller
public class PackageMappingController {

    private final ArchiveService archive;
    private final PackageMappingService packages;

    private final info.oais.archive.manager.i18n.Messages messages;

    public PackageMappingController(ArchiveService archive, PackageMappingService packages, info.oais.archive.manager.i18n.Messages messages) {
        this.messages = messages;
        this.archive = archive;
        this.packages = packages;
    }

    @GetMapping("/packages/{id}/contents")
    public String contents(@PathVariable String id, Model model) {
        String iri = archive.decodeId(id);
        model.addAttribute("id", id);
        model.addAttribute("title", archive.label(iri));
        Optional<URI> location = packages.packageLocation(iri);
        model.addAttribute("location", location.map(URI::toString).orElse(null));
        if (location.isEmpty()) {
            model.addAttribute("error", messages.get("error.noPackage"));
            return "packages/contents";
        }
        try {
            model.addAttribute("inspection", packages.inspect(location.get()));
        } catch (IOException | RuntimeException e) {
            model.addAttribute("error", messages.get("error.packageUnreadable", e.getMessage()));
        }
        return "packages/contents";
    }

    @GetMapping("/packages/{id}/contents.csv")
    public ResponseEntity<byte[]> csv(@PathVariable String id) {
        String iri = archive.decodeId(id);
        Optional<URI> location = packages.packageLocation(iri);
        if (location.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        try {
            PackageInspector.Inspection inspection = packages.inspect(location.get());
            byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}; // so Excel reads it as UTF-8 (Thaana, etc.)
            byte[] body = PackageMappingService.csv(inspection).getBytes(StandardCharsets.UTF_8);
            byte[] bytes = new byte[bom.length + body.length];
            System.arraycopy(bom, 0, bytes, 0, bom.length);
            System.arraycopy(body, 0, bytes, bom.length, body.length);
            String name = (inspection.bagName() == null ? "package" : inspection.bagName()) + "-aip-components.csv";
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"").body(bytes);
        } catch (IOException | RuntimeException e) {
            return ResponseEntity.unprocessableEntity().contentType(MediaType.TEXT_PLAIN)
                    .body(("Couldn't look inside the package: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }
}
