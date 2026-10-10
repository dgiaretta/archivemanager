package info.oais.archive.manager;

import com.sun.net.httpserver.HttpServer;
import info.oais.archive.manager.i18n.LanguagePreference;
import info.oais.archive.manager.rdf.Ns;
import info.oais.archive.manager.rdf.RdfStore;
import info.oais.archive.manager.security.EditAuthInterceptor;
import info.oais.archive.manager.service.AipBuilder;
import info.oais.archive.manager.service.AipComponents;
import info.oais.archive.manager.service.ArchiveService;
import info.oais.archive.manager.service.EditService;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Making an Archival Information Package around a Data Object, from its page (see {@link AipBuilder}). */
@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = "archive.fetch.allow-private-addresses=true")
@SuppressWarnings("null")
class AipBuilderTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private RdfStore store;
    @Autowired
    private EditService edit;
    @Autowired
    private ArchiveService archive;
    @Autowired
    private AipBuilder builder;
    @Autowired
    private AipComponents components;

    private <T> T inTransaction(ReadWrite mode, Supplier<T> work) {
        store.beginTransaction(mode);
        boolean ok = false;
        try {
            T result = work.get();
            ok = true;
            return result;
        } finally {
            store.endTransaction(ok && mode == ReadWrite.WRITE);
        }
    }

    private static MockHttpSession loggedIn(String language) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(EditAuthInterceptor.SESSION_KEY, Boolean.TRUE);
        session.setAttribute(LanguagePreference.SESSION_KEY, language);
        return session;
    }

    @Test
    void makesAnAipWithEverythingTheArchiveKnowsAndSaysWhatIsLeft() throws Exception {
        byte[] bits = "The bits of a Data Object.\n".getBytes(StandardCharsets.US_ASCII);
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bits));
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(200, bits.length);
            exchange.getResponseBody().write(bits);
            exchange.close();
        });
        server.start();
        String[] made = inTransaction(ReadWrite.WRITE, () -> {
            String ri = edit.createEntity(Ns.IM + "StructureRepresentationInformation");
            edit.addLiteral(ri, Ns.RDFS + "label", "Plain text, ASCII");
            String object = edit.createEntity(Ns.IM + "DigitalObject");
            edit.addLiteral(object, Ns.RDFS + "label", "A test Data Object");
            edit.addLiteral(object, Ns.DC + "identifier", "TEST-0001");
            edit.addRelationship(object, Ns.IM + "hasStorageLocation",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/data.txt");
            edit.addRelationship(object, Ns.IM + "interpretedUsing", ri);
            String other = edit.createEntity(Ns.IM + "DigitalObject");
            edit.addLiteral(other, Ns.RDFS + "label", "A Data Object with no storage location");
            return new String[] {object, ri, other};
        });
        String object = made[0];
        String id = archive.encodeId(object);
        Set<String> toRemove = new LinkedHashSet<>(List.of(made));
        try {
            // Its page offers to make one, in either language.
            mockMvc.perform(get("/resource/{id}", id).session(loggedIn("en")))
                    .andExpect(content().string(containsString("Create the AIP")));
            mockMvc.perform(get("/resource/{id}", id).session(loggedIn("pt")))
                    .andExpect(content().string(containsString("Criar o AIP")));
            // Only with the edit password.
            mockMvc.perform(post("/data-objects/{id}/aip", id)).andExpect(status().isForbidden());

            String location = mockMvc.perform(post("/data-objects/{id}/aip", id).session(loggedIn("en"))
                            .param("designatedCommunity", "").param("newCommunity", "Test readers")
                            .param("communityDescription", "Read English text")
                            .param("preservationObjective", "Read the text").param("accessRights", "Open to all")
                            .param("context", "Made for a test").param("recordedBy", "A tester"))
                    .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
            String aip = inTransaction(ReadWrite.READ, () -> builder.aipsOf(object).get(0));
            assertThat(location).isEqualTo("/resource/" + archive.encodeId(aip));

            inTransaction(ReadWrite.READ, () -> {
                Model m = store.dataModel();
                collect(m, aip, toRemove);
                List<AipComponents.Part> parts = components.check(aip, Map.of(), null);
                // Everything OAIS requires is there; Packaging Information comes with the bag.
                org.apache.jena.rdf.model.Statement why = m.getResource(aip)
                        .getProperty(m.createProperty(Ns.RDFS + "comment"));
                assertThat(parts).as(why == null ? "" : why.getString()).filteredOn(AipComponents.Part::missing)
                        .isEmpty();
                assertThat(parts).filteredOn(p -> p.name().equals("Packaging Information"))
                        .extracting(AipComponents.Part::status).containsExactly(AipComponents.Status.PROVIDED_BY_BAG);
                String fixity = parts.stream().filter(p -> p.name().equals("Fixity Information")).findFirst()
                        .orElseThrow().iris().get(0);
                assertThat(m.getResource(fixity).getProperty(m.createProperty(Ns.RDFS + "comment")).getString())
                        .contains("SHA-256: " + sha256).contains("(" + bits.length + " bytes)");
                String reference = parts.stream().filter(p -> p.name().equals("Reference Information")).findFirst()
                        .orElseThrow().iris().get(0);
                assertThat(m.getResource(reference).getProperty(m.createProperty(Ns.RDFS + "comment")).getString())
                        .contains("TEST-0001").contains(object);
                Resource community = m.getResource(aip).getPropertyResourceValue(
                        m.createProperty(Ns.IM + "hasDesignatedCommunity"));
                assertThat(archive.label(community.getURI())).isEqualTo("Test readers");
                assertThat(community.getPropertyResourceValue(m.createProperty(Ns.IM + "hasPreservationObjective")))
                        .isNotNull();
                return null;
            });

            // Its page now says which AIP it's in; asking again shows that AIP rather than making another.
            mockMvc.perform(get("/resource/{id}", id).session(loggedIn("en")))
                    .andExpect(content().string(containsString("It is the Content Data Object of:")));
            mockMvc.perform(post("/data-objects/{id}/aip", id).session(loggedIn("en")))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl(location));
            assertThat(inTransaction(ReadWrite.READ, () -> builder.aipsOf(object))).hasSize(1);

            // With no bits to digest, the AIP is still made, and says it has no Fixity Information.
            mockMvc.perform(post("/data-objects/{id}/aip", archive.encodeId(made[2])).session(loggedIn("en")))
                    .andExpect(status().is3xxRedirection());
            inTransaction(ReadWrite.READ, () -> {
                String second = builder.aipsOf(made[2]).get(0);
                collect(store.dataModel(), second, toRemove);
                assertThat(components.check(second, Map.of(), null)).filteredOn(AipComponents.Part::missing)
                        .extracting(AipComponents.Part::name)
                        .contains("Bits of the Content Data Object", "Fixity Information", "Access Rights Information");
                return null;
            });
        } finally {
            server.stop(0);
            inTransaction(ReadWrite.WRITE, () -> {
                toRemove.forEach(edit::deleteResource);
                return null;
            });
        }
    }

    /** Everything {@code from} leads to that this test made: the AIP's components. */
    private static void collect(Model m, String from, Set<String> into) {
        Deque<String> todo = new ArrayDeque<>(List.of(from));
        while (!todo.isEmpty()) {
            String iri = todo.pop();
            if (!iri.startsWith(Ns.EX) || !into.add(iri) && !iri.equals(from)) {
                continue;
            }
            m.getResource(iri).listProperties().forEachRemaining(s -> {
                if (s.getObject().isURIResource()) {
                    todo.push(s.getObject().asResource().getURI());
                }
            });
        }
        // The Package Description points at the AIP, not the other way round only.
        m.listSubjectsWithProperty(m.createProperty(Ns.IM + "derivedFrom"), m.getResource(from))
                .forEachRemaining(r -> into.add(r.getURI()));
    }
}
