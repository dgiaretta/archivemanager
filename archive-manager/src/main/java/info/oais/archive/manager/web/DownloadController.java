package info.oais.archive.manager.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Read-only, ungated downloads of the ontology source files this app
 * ships with -- these are schema/documentation, not archive data, so
 * they're open the same way browsing is (see {@code EditAuthInterceptor}:
 * only writes require login). Each endpoint streams the classpath resource
 * directly, byte-for-byte, rather than re-serializing from the triple
 * store -- that guarantees the download is exactly the file
 * {@code RdfStore.init()} actually loaded (comments and formatting
 * included), not a Jena-regenerated approximation of it.
 */
@Controller
public class DownloadController {

    @GetMapping("/download/bridge-ontology")
    public ResponseEntity<Resource> downloadBridge() {
        return download("rdf/oais-ric-bridge.ttl", "oais-ric-bridge.ttl");
    }

    @GetMapping("/download/oais-ontology")
    public ResponseEntity<Resource> downloadOais() {
        return download("rdf/oais_im_schema-sh-v5.ttl", "oais_im_schema-sh-v5.ttl");
    }

    private ResponseEntity<Resource> download(String classpath, String filename) {
        Resource resource = new ClassPathResource(classpath);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/turtle"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(resource);
    }
}
