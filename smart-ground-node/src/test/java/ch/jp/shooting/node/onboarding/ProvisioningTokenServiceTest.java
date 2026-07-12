package ch.jp.shooting.node.onboarding;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class ProvisioningTokenServiceTest {

    @Autowired
    private ProvisioningTokenService service;

    @Test
    void mint_thenValidateAndConsume_succeedsOnce() {
        ProvisioningTokenService.MintedToken token = service.mint("AA:BB:CC:DD:EE:20");
        assertThat(token.raw()).hasSize(16);
        assertThat(token.hex()).hasSize(32);
        assertThat(token.expiresAt()).isAfter(java.time.Instant.now());

        // first consume succeeds
        service.validateAndConsume(token.hex(), "AA:BB:CC:DD:EE:20");

        // second consume fails: single-use
        assertThatThrownBy(() -> service.validateAndConsume(token.hex(), "AA:BB:CC:DD:EE:20"))
                .isInstanceOf(ErrorResponseException.class);
    }

    @Test
    void validateAndConsume_wrongMac_isRejected() {
        ProvisioningTokenService.MintedToken token = service.mint("AA:BB:CC:DD:EE:21");
        assertThatThrownBy(() -> service.validateAndConsume(token.hex(), "AA:BB:CC:DD:EE:99"))
                .isInstanceOf(ErrorResponseException.class);
    }

    @Test
    void validateAndConsume_unknownToken_isRejected() {
        assertThatThrownBy(() -> service.validateAndConsume("deadbeef", "AA:BB:CC:DD:EE:22"))
                .isInstanceOf(ErrorResponseException.class);
    }
}
