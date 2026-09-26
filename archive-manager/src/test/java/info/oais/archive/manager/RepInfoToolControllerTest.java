package info.oais.archive.manager;

import info.oais.archive.manager.security.EditAuthInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RepInfoToolControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void decodesAnUploadedSampleWithTheDraftsDfdl() throws Exception {
        MockHttpSession session = loggedInSessionWithFitsDraft();
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        for (int i = 0; i < 10; i++) {
            header.writeBytes(String.format("%-80s", i == 9 ? "END" : "KEY" + i + "    = 1")
                    .getBytes(StandardCharsets.US_ASCII));
        }

        mockMvc.perform(multipart("/repinfo-tools/test-dfdl")
                        .file(new MockMultipartFile("sample", "header.fits", "application/octet-stream",
                                header.toByteArray()))
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("decoded successfully")))
                .andExpect(content().string(containsString("KEY1    = 1")));
    }

    @Test
    void showsDiagnosticsForASampleThatDoesNotMatch() throws Exception {
        MockHttpSession session = loggedInSessionWithFitsDraft();

        mockMvc.perform(multipart("/repinfo-tools/test-dfdl")
                        .file(new MockMultipartFile("sample", "short.bin", "application/octet-stream", new byte[5]))
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("could not be decoded")));
    }

    private MockHttpSession loggedInSessionWithFitsDraft() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(EditAuthInterceptor.SESSION_KEY, Boolean.TRUE);
        mockMvc.perform(post("/repinfo-tools/start").param("template", "fits").session(session))
                .andExpect(status().is3xxRedirection());
        return session;
    }
}
