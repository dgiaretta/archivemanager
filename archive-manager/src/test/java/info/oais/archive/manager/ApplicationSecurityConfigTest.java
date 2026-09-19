package info.oais.archive.manager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ApplicationSecurityConfigTest {

    @Value("${archive.edit-password}")
    private String editPassword;

    @Test
    void passwordIsConfiguredForDeployment() {
        assertThat(editPassword)
                .isNotBlank();
    }
}
