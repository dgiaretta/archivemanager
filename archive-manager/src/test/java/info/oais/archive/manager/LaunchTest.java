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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Launching TOPCAT and SPLAT with OpenWebStart, against a TOPCAT jar and a SPLAT installation made for the test. */
@SpringBootTest
@AutoConfigureMockMvc
class LaunchTest {

    private static final Path INSTALLED;

    static {
        try {
            INSTALLED = Files.createTempDirectory("launch-test-");
            jar(INSTALLED.resolve("topcat-full.jar"), "uk.ac.starlink.topcat.Driver");
            Path splat = INSTALLED.resolve("splat-vo");
            jar(splat.resolve("lib/splat/splat.jar"), "uk.ac.starlink.splat.SplatBrowserMain");
            jar(splat.resolve("lib/jniast/jniast.jar"), null);
            Files.createDirectories(splat.resolve("lib/amd64"));
            Files.writeString(splat.resolve("lib/amd64/jniast.dll"), "windows library");
            Files.writeString(splat.resolve("lib/amd64/libjniast.so"), "linux library");
            Files.writeString(splat.resolve("lib/amd64/README.txt"), "not a library");
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void installed(DynamicPropertyRegistry registry) {
        registry.add("archive.launch.topcat-jar", () -> INSTALLED.resolve("topcat-full.jar").toString());
        registry.add("archive.launch.splat-home", () -> INSTALLED.resolve("splat-vo").toString());
        registry.add("archive.launch.signing-location", () -> INSTALLED.resolve("signing").toString());
    }

    private static void jar(Path file, String mainClass) throws IOException {
        Files.createDirectories(file.getParent());
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (mainClass != null) {
            manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, mainClass);
        }
        try (OutputStream out = Files.newOutputStream(file); JarOutputStream jar = new JarOutputStream(out, manifest)) {
            jar.putNextEntry(new ZipEntry("placeholder.txt"));
            jar.write("test".getBytes(StandardCharsets.UTF_8));
        }
    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RdfStore store;
    @Autowired
    private EditService edit;
    @Autowired
    private ArchiveService archive;

    @Test
    void launchesTopcatAndSplatWithTheDataLoaded() throws Exception {
        Set<Resource> before = read(() -> new HashSet<>(store.dataModel().listSubjects().toList()));
        try {
            String spectrum = write(this::spectrum);
            String id = archive.encodeId(spectrum);

            mockMvc.perform(get("/resource/{id}", id))
                    .andExpect(content().string(containsString("/launch/" + id + "/topcat.jnlp")))
                    .andExpect(content().string(containsString("/launch/" + id + "/splat.jnlp")))
                    .andExpect(content().string(containsString("OpenWebStart")));

            Document topcat = xml(mockMvc.perform(get("/launch/{id}/topcat.jnlp", id)).andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith("application/x-java-jnlp-file"))
                    .andReturn().getResponse().getContentAsString());
            Element jnlp = topcat.getDocumentElement();
            assertThat(jnlp.getAttribute("codebase")).isEqualTo("http://localhost/launch/");
            assertThat(jnlp.getAttribute("href")).isEqualTo(id + "/topcat.jnlp");
            assertThat(attributes(topcat, "jar", "href")).containsExactly("files/topcat-full.jar");
            assertThat(attributes(topcat, "application-desc", "main-class"))
                    .containsExactly("uk.ac.starlink.topcat.Driver");
            assertThat(texts(topcat, "argument")).containsExactly("-f", "votable",
                    "http://localhost/api/data-objects/" + id + "/votable");
            assertThat(topcat.getElementsByTagName("all-permissions").getLength()).isEqualTo(1);
            // Every jar is signed with the archive's own certificate, which can be downloaded.
            java.security.cert.Certificate certificate = java.security.cert.CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(mockMvc.perform(get("/launch/certificate.cer"))
                            .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray()));
            assertThat(((java.security.cert.X509Certificate) certificate).getSubjectX500Principal().getName())
                    .contains("OAIS Archive Manager (self-signed)");
            assertSignedBy(certificate, mockMvc.perform(get("/launch/files/topcat-full.jar"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());

            Document splat = xml(mockMvc.perform(get("/launch/{id}/splat.jnlp", id)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString());
            assertThat(attributes(splat, "jar", "href")).containsExactly("files/splat/lib/splat/splat.jar",
                    "files/splat/lib/jniast/jniast.jar");
            assertThat(attributes(splat, "application-desc", "main-class"))
                    .containsExactly("uk.ac.starlink.splat.SplatBrowserMain");
            assertThat(texts(splat, "argument")).containsExactly("http://localhost/api/data-objects/" + id
                    + "/data.vot");
            assertThat(attributes(splat, "resources", "os")).contains("Linux", "Windows");
            assertThat(attributes(splat, "nativelib", "href")).containsExactlyInAnyOrder(
                    "files/splat-native/linux-amd64.jar", "files/splat-native/windows-amd64.jar");

            assertSignedBy(certificate, mockMvc.perform(get("/launch/files/splat/lib/jniast/jniast.jar"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
            mockMvc.perform(get("/launch/files/splat/lib/amd64/README.txt")).andExpect(status().isNotFound());
            byte[] windows = mockMvc.perform(get("/launch/files/splat-native/windows-amd64.jar"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
            assertThat(entries(windows)).contains("jniast.dll");
            assertSignedBy(certificate, windows);
            mockMvc.perform(get("/launch/files/splat-native/plan9-amd64.jar")).andExpect(status().isNotFound());

            // Something that isn't a table can't be launched in TOPCAT.
            String unviewable = write(() -> edit.createEntity(Ns.IM + "DigitalObject"));
            mockMvc.perform(get("/launch/{id}/topcat.jnlp", archive.encodeId(unviewable)))
                    .andExpect(status().isNotFound());
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

    /** A spectrum: a table view whose columns are all numbers, so offered to TOPCAT and SPLAT. */
    private String spectrum() {
        String spectrum = edit.createEntity(Ns.IM + "DigitalObject");
        edit.addLiteral(spectrum, Ns.RDFS + "label", "Test spectrum");
        edit.addRelationship(spectrum, Ns.IM + "hasStorageLocation", "https://example.org/spectrum.csv");
        String top = edit.createEntity(Ns.IM + "RepInfoAndGroup");
        edit.addRelationship(spectrum, Ns.IM + "interpretedUsing", top);
        String dfdl = edit.createEntity(Ns.IM + "StructureRepresentationInformation");
        edit.addLiteral(dfdl, Ns.IM + "specificationLanguage", "DFDL");
        edit.addLiteral(dfdl, Ns.IM + "specificationText", "<xs:schema/>");
        String semantics = edit.createEntity(Ns.IM + "SemanticRepresentationInformation");
        String view = edit.createEntity(Ns.IM + "ViewSpecification");
        edit.addLiteral(view, Ns.IM + "viewKind", "table");
        edit.addLiteral(view, Ns.IM + "specificationText", "<tableView><rows select=\"children\" name=\"row\"/>"
                + "<columns><column name=\"wavelength\" type=\"double\"/><column name=\"flux\" type=\"double\"/>"
                + "</columns></tableView>");
        edit.addRelationship(top, Ns.IM + "hasGroupMember", dfdl);
        edit.addRelationship(top, Ns.IM + "hasGroupMember", semantics);
        edit.addRelationship(semantics, Ns.IM + "interpretedUsingRecurse", view);
        return spectrum;
    }

    private static Document xml(String text) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static List<String> attributes(Document d, String element, String attribute) {
        List<String> found = new ArrayList<>();
        NodeList nodes = d.getElementsByTagName(element);
        for (int i = 0; i < nodes.getLength(); i++) {
            String value = ((Element) nodes.item(i)).getAttribute(attribute);
            if (!value.isEmpty()) {
                found.add(value);
            }
        }
        return found;
    }

    private static List<String> texts(Document d, String element) {
        List<String> found = new ArrayList<>();
        NodeList nodes = d.getElementsByTagName(element);
        for (int i = 0; i < nodes.getLength(); i++) {
            found.add(nodes.item(i).getTextContent());
        }
        return found;
    }

    /**
     * Checks {@code jar} is signed by {@code certificate}: every file in it
     * verifies against its signature, and its manifest asks for
     * {@code all-permissions}, as OpenWebStart expects of a signed application.
     */
    private static void assertSignedBy(java.security.cert.Certificate certificate, byte[] jar) throws IOException {
        try (java.util.jar.JarInputStream in = new java.util.jar.JarInputStream(new ByteArrayInputStream(jar), true)) {
            assertThat(in.getManifest().getMainAttributes().getValue("Permissions")).isEqualTo("all-permissions");
            int files = 0;
            for (java.util.jar.JarEntry e; (e = in.getNextJarEntry()) != null; ) {
                in.readAllBytes(); // verifies the entry against its digest
                if (e.isDirectory() || e.getName().toUpperCase().startsWith("META-INF/")) {
                    continue;
                }
                files++;
                assertThat(e.getCertificates()).as(e.getName()).isNotNull().contains(certificate);
            }
            assertThat(files).isPositive();
        }
    }

    private static List<String> entries(byte[] zip) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                names.add(e.getName());
            }
        }
        return names;
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
