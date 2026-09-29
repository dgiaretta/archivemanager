package info.oais.archive.manager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * A client that goes away before its response is written (a browser leaving
 * the page, a cancelled download) makes the next write fail with "Broken
 * pipe". That's routine, and shouldn't be logged as a warning.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@Import(ClientDisconnectTest.DisconnectingController.class)
class ClientDisconnectTest {

    @RestController
    static class DisconnectingController {
        @GetMapping("/test-only/client-goes-away")
        String write() throws AsyncRequestNotUsableException {
            throw new AsyncRequestNotUsableException("ServletOutputStream failed to write: java.io.IOException: Broken pipe");
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aClientGoingAwayIsNotAWarning(CapturedOutput output) throws Exception {
        mockMvc.perform(get("/test-only/client-goes-away"));

        assertThat(output.getAll()).doesNotContain("AsyncRequestNotUsableException");
    }
}
