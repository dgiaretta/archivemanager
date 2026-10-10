package info.oais.archive.manager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@SuppressWarnings("null")
class ArchiveApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void countsEndpointReturnsSummaryPayload() throws Exception {
        mockMvc.perform(get("/api/counts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records").exists())
                .andExpect(jsonPath("$.triples").exists());
    }

    @Test
    void recordsEndpointReturnsPagedResults() throws Exception {
        mockMvc.perform(get("/api/records").param("page", "1").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").exists())
                .andExpect(jsonPath("$.page").value(1));
    }

    @Test
    void readsOneEntitysPropertiesExactlyAsStored() throws Exception {
        org.springframework.mock.web.MockHttpSession session = new org.springframework.mock.web.MockHttpSession();
        session.setAttribute(info.oais.archive.manager.security.EditAuthInterceptor.SESSION_KEY, Boolean.TRUE);
        String created = mockMvc.perform(post("/api/entities").session(session)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"classIri\": \"http://ontology.oais.info/im/DigitalObject\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.read(created, "$.id");
        String iri = com.jayway.jsonpath.JsonPath.read(created, "$.iri");
        String comment = "  Two lines,\n  with spaces kept.  ";
        try {
            mockMvc.perform(post("/api/entities/{id}/properties", id).session(session)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("{\"property\": \"http://www.w3.org/2000/01/rdf-schema#comment\", \"value\": "
                            + "\"  Two lines,\\n  with spaces kept.  \"}"))
                    .andExpect(status().isNoContent());
            mockMvc.perform(post("/api/entities/{id}/relationships", id).session(session)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("{\"property\": \"http://ontology.oais.info/im/hasStorageLocation\", "
                            + "\"customTarget\": \"https://example.org/data.bin\"}"))
                    .andExpect(status().isNoContent());

            // Open: no login needed to read.
            mockMvc.perform(get("/api/entities/{id}", id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.iri").value(iri))
                    .andExpect(jsonPath("$.types[0]").value("http://ontology.oais.info/im/DigitalObject"))
                    .andExpect(jsonPath("$.properties[?(@.property == 'http://www.w3.org/2000/01/rdf-schema#comment')]"
                            + ".value").value(org.hamcrest.Matchers.contains(comment)))
                    .andExpect(jsonPath("$.properties[?(@.resource == true)].value")
                            .value(org.hamcrest.Matchers.contains("https://example.org/data.bin")));
            mockMvc.perform(get("/api/entities/{id}", id).param("property", "rdfs:comment"))
                    .andExpect(jsonPath("$.properties.length()").value(1))
                    .andExpect(jsonPath("$.properties[0].resource").value(false));

            // The value read is the value to delete it by.
            mockMvc.perform(delete("/api/entities/{id}/properties", id).session(session)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("{\"propertyIri\": \"http://www.w3.org/2000/01/rdf-schema#comment\", \"value\": "
                            + "\"  Two lines,\\n  with spaces kept.  \"}"))
                    .andExpect(status().isNoContent());
            mockMvc.perform(get("/api/entities/{id}", id).param("property", "rdfs:comment"))
                    .andExpect(jsonPath("$.properties.length()").value(0));
        } finally {
            mockMvc.perform(delete("/api/entities/{id}", id).session(session)).andExpect(status().isNoContent());
        }
        mockMvc.perform(get("/api/entities/{id}", id)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/entities/{id}", "not*base64")).andExpect(status().isBadRequest());
    }
}
