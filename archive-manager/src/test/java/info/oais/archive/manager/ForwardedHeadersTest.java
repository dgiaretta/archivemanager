package info.oais.archive.manager;

import info.oais.archive.manager.rdf.Ns;
import info.oais.archive.manager.rdf.RdfStore;
import info.oais.archive.manager.service.ArchiveService;
import info.oais.archive.manager.service.EditService;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behind a reverse proxy, the links the archive writes use the address the
 * proxy was reached at ({@code server.forward-headers-strategy: native}); used
 * directly, its own. Needs a real server: the setting acts in Tomcat itself.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ForwardedHeadersTest {

    @LocalServerPort
    private int port;
    @Autowired
    private RdfStore store;
    @Autowired
    private EditService edit;
    @Autowired
    private ArchiveService archive;

    @Test
    void writesLinksWithTheAddressTheProxyWasReachedAt() throws Exception {
        Set<Resource> before = read(() -> new HashSet<>(store.dataModel().listSubjects().toList()));
        try {
            String dataObject = write(() -> {
                String d = edit.createEntity(Ns.IM + "DigitalObject");
                edit.addRelationship(d, Ns.IM + "hasStorageLocation", "https://example.org/data.bin");
                String structure = edit.createEntity(Ns.IM + "StructureRepresentationInformation");
                edit.addLiteral(structure, Ns.IM + "specificationLanguage", "DFDL");
                edit.addLiteral(structure, Ns.IM + "specificationText", "<xs:schema/>");
                edit.addRelationship(d, Ns.IM + "interpretedUsing", structure);
                return d;
            });
            URI manifest = URI.create("http://localhost:" + port + "/api/data-objects/"
                    + archive.encodeId(dataObject) + "/repinfo.ttl");
            HttpClient client = HttpClient.newHttpClient();

            String proxied = client.send(HttpRequest.newBuilder(manifest)
                    .header("X-Forwarded-Proto", "https").header("X-Forwarded-Host", "archive.example.org")
                    .header("X-Forwarded-Port", "443").build(), HttpResponse.BodyHandlers.ofString()).body();
            assertThat(proxied).contains("<https://archive.example.org/api/specifications/")
                    .doesNotContain("localhost");

            String direct = client.send(HttpRequest.newBuilder(manifest).build(),
                    HttpResponse.BodyHandlers.ofString()).body();
            assertThat(direct).contains("<http://localhost:" + port + "/api/specifications/");
        } finally {
            write(() -> {
                Model m = store.dataModel();
                for (Resource r : m.listSubjects().toList()) {
                    if (!before.contains(r)) {
                        m.removeAll(r, null, null);
                        m.removeAll(null, null, r);
                    }
                }
                return null;
            });
        }
    }

    private <T> T read(Supplier<T> work) {
        store.beginTransaction(ReadWrite.READ);
        try {
            return work.get();
        } finally {
            store.endTransaction(true);
        }
    }

    private <T> T write(Supplier<T> work) {
        store.beginTransaction(ReadWrite.WRITE);
        boolean ok = false;
        try {
            T result = work.get();
            ok = true;
            return result;
        } finally {
            store.endTransaction(ok);
        }
    }
}
