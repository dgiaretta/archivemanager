package info.oais.archive.manager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
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
}
