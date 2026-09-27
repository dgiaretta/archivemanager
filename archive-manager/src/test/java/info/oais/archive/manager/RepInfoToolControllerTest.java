package info.oais.archive.manager;

import info.oais.archive.manager.security.EditAuthInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Test
    void downloadsTheDrbPythonDriverAsAnInstallablePackage() throws Exception {
        MockHttpSession session = loggedInSessionWithFitsDraft();

        byte[] zip = mockMvc.perform(get("/repinfo-tools/download/drb-python").session(session))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/zip"))
                .andReturn().getResponse().getContentAsByteArray();

        List<String> entries = new ArrayList<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e = in.getNextEntry(); e != null; e = in.getNextEntry()) {
                entries.add(e.getName());
            }
        }
        assertThat(entries).contains("drb-driver-am-fits-primary-header/pyproject.toml",
                "drb-driver-am-fits-primary-header/drb/topics/am_fits_primary_header/cortex.ttl");
    }

    private MockHttpSession loggedInSessionWithFitsDraft() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(EditAuthInterceptor.SESSION_KEY, Boolean.TRUE);
        mockMvc.perform(post("/repinfo-tools/start").param("template", "fits").session(session))
                .andExpect(status().is3xxRedirection());
        return session;
    }
}
