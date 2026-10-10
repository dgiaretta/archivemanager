package info.oais.archive.manager;

import info.oais.archive.manager.i18n.LanguagePreference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The interface in English (the default) and Brazilian Portuguese: the
 * language switcher's choice reaches the message bundles, every page has all
 * its messages in both, and the two bundles have the same keys.
 */
@SpringBootTest
@AutoConfigureMockMvc
class I18nTest {

    @Autowired
    private MockMvc mockMvc;

    /** Every page that needs no particular entity, open or behind the login. */
    static final String[] PAGES = {"/", "/accession-register", "/activities", "/agents", "/catalogue",
            "/catalogue/explore", "/catalogue/explore?creator=x", "/catalogue/upload", "/entities", "/entities/import",
            "/entities/new", "/graph", "/help", "/login", "/mandates", "/records", "/records/new", "/repinfo-tools",
            "/sparql", "/statistics"};

    /** A logged-in session showing the interface in {@code language}. */
    private static MockHttpSession in(String language) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(LanguagePreference.SESSION_KEY, language);
        session.setAttribute(info.oais.archive.manager.security.EditAuthInterceptor.SESSION_KEY, Boolean.TRUE);
        return session;
    }

    @Test
    void showsTheInterfaceInTheChosenLanguage() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk())
                .andExpect(content().string(containsString(">Catalogue<")));
        mockMvc.perform(get("/").session(in("pt"))).andExpect(status().isOk())
                .andExpect(content().string(containsString(">Catálogo<")));
        // A language with labels in the ontology but no interface text of its own: English.
        mockMvc.perform(get("/").session(in("fr"))).andExpect(content().string(containsString(">Catalogue<")));
    }

    @Test
    void bothBundlesHaveTheSameKeys() throws Exception {
        Properties en = load("/i18n/messages.properties");
        Properties pt = load("/i18n/messages_pt.properties");
        assertThat(pt.keySet()).containsExactlyInAnyOrderElementsOf(en.keySet());
    }

    @Test
    void everyPageHasAllItsMessagesInBothLanguages() throws Exception {
        for (String language : new String[] {"en", "pt"}) {
            for (String page : PAGES) {
                String html = mockMvc.perform(get(page).session(in(language))).andReturn().getResponse()
                        .getContentAsString();
                assertThat(html).as(language + " " + page).isNotEmpty().doesNotContain("??");
            }
        }
    }

    @Autowired
    private info.oais.archive.manager.service.ArchiveService archive;

    @Autowired
    private info.oais.archive.manager.rdf.RdfStore store;

    /** The pages of one entity of each kind in the sample data, in both languages. */
    @Test
    void everyEntitysPagesHaveAllTheirMessagesInBothLanguages() throws Exception {
        java.util.List<String> pages = new java.util.ArrayList<>();
        store.beginTransaction(org.apache.jena.query.ReadWrite.READ);
        try {
            archive.listRecordResources(1, 1).items().forEach(r -> pages.addAll(java.util.List.of(
                    "/records/" + r.id(), "/oais/" + r.id(), "/resource/" + r.id(), "/entities/" + r.id() + "/edit",
                    "/graph/" + r.id())));
            archive.listAgents(1, 1).items().forEach(a -> pages.add("/agents/" + a.id()));
            archive.listMandates(1, 1).items().forEach(m -> pages.add("/mandates/" + m.id()));
            archive.listActivities(1, 1).items().forEach(e -> pages.add("/activities/" + e.id()));
        } finally {
            store.endTransaction(false);
        }
        assertThat(pages).isNotEmpty();
        for (String language : new String[] {"en", "pt"}) {
            for (String page : pages) {
                String html = mockMvc.perform(get(page).session(in(language))).andExpect(status().isOk()).andReturn()
                        .getResponse().getContentAsString();
                assertThat(html).as(language + " " + page).doesNotContain("??");
            }
        }
        // The engines' notes in Portuguese, where there are some.
        mockMvc.perform(get("/help/engines/east").session(in("pt"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Lendo EAST para a árvore de elementos")));
        mockMvc.perform(get("/help").session(in("pt"))).andExpect(content().string(containsString("Ajuda")));
    }

    private static Properties load(String resource) throws Exception {
        Properties p = new Properties();
        try (InputStream in = I18nTest.class.getResourceAsStream(resource)) {
            p.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        }
        return p;
    }
}
