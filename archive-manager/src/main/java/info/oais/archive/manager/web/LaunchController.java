package info.oais.archive.manager.web;

import info.oais.archive.manager.service.ArchiveService;
import info.oais.archive.manager.service.LaunchService;
import info.oais.archive.manager.service.format.DataObjectViewService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * OpenWebStart launch files (see {@link LaunchService}), open to anyone like
 * the data they open:
 * <ul>
 *   <li>{@code /launch/{id}/topcat.jnlp}, {@code /launch/{id}/splat.jnlp} --
 *       launch TOPCAT or SPLAT with a Data Object's data, as VOTable;</li>
 *   <li>{@code /launch/files/...} -- the applications' jars, and SPLAT's
 *       native libraries packed per platform, which the JNLP files name, all
 *       signed with the archive's self-signed certificate;</li>
 *   <li>{@code /launch/certificate.cer} -- that certificate.</li>
 * </ul>
 */
@RestController
public class LaunchController {

    private static final MediaType JNLP = MediaType.parseMediaType("application/x-java-jnlp-file");
    private static final MediaType JAR = MediaType.parseMediaType("application/java-archive");

    private final LaunchService launch;
    private final ArchiveService archive;
    private final DataObjectViewService views;
    private final info.oais.archive.manager.service.JarSigning signing;

    public LaunchController(LaunchService launch, ArchiveService archive, DataObjectViewService views,
                            info.oais.archive.manager.service.JarSigning signing) {
        this.launch = launch;
        this.archive = archive;
        this.views = views;
        this.signing = signing;
    }

    /** The self-signed certificate the launched applications' jars are signed with, to check or trust it. */
    @GetMapping("/launch/certificate.cer")
    public ResponseEntity<byte[]> certificate() {
        return signing.certificate().map(c -> {
            try {
                return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/pkix-cert"))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"archive-launch.cer\"")
                        .body(c.getEncoded());
            } catch (java.security.cert.CertificateEncodingException e) {
                throw new IllegalStateException(e);
            }
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/launch/{id}/topcat.jnlp")
    public ResponseEntity<String> topcat(@PathVariable String id) {
        String iri = archive.decodeId(id);
        // TOPCAT is given the data decoded as VOTable, or a FITS file as it is.
        java.util.Optional<DataObjectViewService.Viewer> topcat = views.viewers(iri).stream()
                .filter(v -> v.id().equals("topcat")).findFirst();
        if (!launch.topcat() || topcat.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return jnlp("topcat", launch.topcatJnlp(codebase(), id + "/topcat.jnlp", archive.label(iri),
                data(id, topcat.get().format())));
    }

    @GetMapping("/launch/{id}/splat.jnlp")
    public ResponseEntity<String> splat(@PathVariable String id) throws IOException {
        String iri = archive.decodeId(id);
        if (!launch.splat() || !views.viewers(iri).contains(DataObjectViewService.SPLAT)) {
            return ResponseEntity.notFound().build();
        }
        return jnlp("splat", launch.splatJnlp(codebase(), id + "/splat.jnlp", archive.label(iri),
                data(id, "data.vot")));
    }

    @GetMapping("/launch/files/topcat-full.jar")
    public ResponseEntity<Resource> topcatJar() throws IOException {
        return launch.topcatJarFile().map(f -> ResponseEntity.ok().contentType(JAR)
                .<Resource>body(new FileSystemResource(f))).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/launch/files/splat/**")
    public ResponseEntity<Resource> splatJar(HttpServletRequest request) throws IOException {
        String relative = request.getRequestURI().substring(request.getRequestURI().indexOf("/launch/files/splat/")
                + "/launch/files/splat/".length());
        return launch.splatFile(relative).map(f -> ResponseEntity.ok().contentType(JAR)
                .<Resource>body(new FileSystemResource(f))).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/launch/files/splat-native/{key}.jar")
    public ResponseEntity<byte[]> splatNative(@PathVariable String key) throws IOException {
        return launch.splatNativeJar(key).map(bytes -> ResponseEntity.ok().contentType(JAR).body(bytes))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static ResponseEntity<String> jnlp(String application, String text) {
        return ResponseEntity.ok().contentType(new MediaType(JNLP, StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + application + ".jnlp\"")
                .body(text);
    }

    private static String codebase() {
        return ServletUriComponentsBuilder.fromCurrentContextPath().path("/launch/").toUriString();
    }

    private static String data(String id, String what) {
        return ServletUriComponentsBuilder.fromCurrentContextPath().path("/api/data-objects/{id}/{what}")
                .buildAndExpand(id, what).toUriString();
    }

    /** What the page offers: which launches are available for a Data Object with these viewers. */
    public static List<String> available(LaunchService launch, List<DataObjectViewService.Viewer> viewers) {
        List<String> found = new java.util.ArrayList<>();
        if (launch.topcat() && viewers.stream().anyMatch(v -> v.id().equals("topcat"))) {
            found.add("topcat");
        }
        if (launch.splat() && viewers.contains(DataObjectViewService.SPLAT)) {
            found.add("splat");
        }
        return found;
    }
}
