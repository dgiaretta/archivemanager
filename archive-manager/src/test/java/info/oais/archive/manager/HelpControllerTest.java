package info.oais.archive.manager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class HelpControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void helpPageLinksToEachDataDescriptionLanguagesNotes() throws Exception {
        mockMvc.perform(get("/help"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("href=\"/help/engines/dfdl\""),
                        containsString("href=\"/help/engines/kaitai\""), containsString("href=\"/help/engines/drb\""))));
    }

    @Test
    void helpPageExplainsTheThreePreservationTechniques() throws Exception {
        mockMvc.perform(get("/help"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("id=\"preservation\""),
                        containsString("1. Add Representation Information"), containsString("2. Transformation"),
                        containsString("3. Hand over complete AIPs to another archive"),
                        containsString("href=\"#editing\""), containsString("id=\"editing\""))));
    }

    @Test
    void helpPageStartsWithWhatTheApplicationIsAndFollowsTheLifeOfTheInformation() throws Exception {
        String page = mockMvc.perform(get("/help")).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString();
        int about = page.indexOf("What this application is");
        int working = page.indexOf("id=\"working\"");
        int aip = page.indexOf("The components of an OAIS Archival Information Package");
        int preservation = page.indexOf("id=\"preservation\"");
        int viewing = page.indexOf("id=\"viewing\"");
        org.assertj.core.api.Assertions.assertThat(about).isPositive().isLessThan(working);
        org.assertj.core.api.Assertions.assertThat(working).isLessThan(aip);
        org.assertj.core.api.Assertions.assertThat(aip).isLessThan(preservation);
        org.assertj.core.api.Assertions.assertThat(preservation).isLessThan(viewing);
        org.assertj.core.api.Assertions.assertThat(page).contains("prototype", "Records in Contexts",
                "Why astronomical applications?", "other applications could be used in their place");
    }

    @Test
    void rendersEachBundledReadmeAsHtml() throws Exception {
        mockMvc.perform(get("/help/engines/dfdl"))
                .andExpect(status().isOk())
                .andExpect(content().string(allOf(containsString("<h1>oais-structure-dfdl notes</h1>"),
                        containsString("<code>GeneralFormat</code>"), not(containsString("## How")))));
        mockMvc.perform(get("/help/engines/kaitai"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>oais-structure-kaitai notes</h1>")));
        mockMvc.perform(get("/help/engines/drb"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<h1>oais-structure-drb notes</h1>")));
    }

    @Test
    void unknownEngineIsNotFound() throws Exception {
        mockMvc.perform(get("/help/engines/other")).andExpect(status().isNotFound());
    }
}
