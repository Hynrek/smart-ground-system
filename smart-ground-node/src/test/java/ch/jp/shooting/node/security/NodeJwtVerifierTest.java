package ch.jp.shooting.node.security;

import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class NodeJwtVerifierTest {

    // Same Base64 dev secret the Hub signs with (application.properties jwt.secret default).
    private static final String SECRET = "bWVpbi1zZWNyZXQta2V5LWZ1ZXItc21hcnQtZ3JvdW5kLWlzdC1sYW5n";
    private final NodeJwtVerifier verifier = new NodeJwtVerifier(SECRET);

    private static String jwt(long expEpochSeconds, String secretBase64) throws Exception {
        Base64.Encoder url = Base64.getUrlEncoder().withoutPadding();
        String header = url.encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = url.encodeToString(
                ("{\"sub\":\"admin@smartground.local\",\"exp\":" + expEpochSeconds + "}").getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(Base64.getDecoder().decode(secretBase64), "HmacSHA256"));
        String sig = url.encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII)));
        return signingInput + "." + sig;
    }

    @Test
    void validSignatureAndFutureExp_isValid() throws Exception {
        String token = jwt(Instant.now().getEpochSecond() + 3600, SECRET);
        assertThat(verifier.isValid(token)).isTrue();
    }

    @Test
    void expiredToken_isInvalid() throws Exception {
        String token = jwt(Instant.now().getEpochSecond() - 10, SECRET);
        assertThat(verifier.isValid(token)).isFalse();
    }

    @Test
    void wrongSecret_isInvalid() throws Exception {
        String token = jwt(Instant.now().getEpochSecond() + 3600,
                Base64.getEncoder().encodeToString("some-other-key-that-is-32-bytes!".getBytes(StandardCharsets.UTF_8)));
        assertThat(verifier.isValid(token)).isFalse();
    }

    @Test
    void garbage_isInvalidNotThrown() {
        assertThat(verifier.isValid("not-a-jwt")).isFalse();
        assertThat(verifier.isValid("a.b")).isFalse();
    }
}
