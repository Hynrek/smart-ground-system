package ch.jp.shooting.node.security;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

/**
 * Minimale, JDK-only JWT-Verifikation für node-api: prüft nur HS256-Signatur + Ablauf gegen
 * das geteilte {@code jwt.secret} (dieselbe Basis wie der Hub). Bewusst KEINE feingranulare
 * Permission-Auflösung — das Bediener-Gate bleibt der offene Punkt der Coupling-Spec.
 * Keine io.jsonwebtoken-Abhängigkeit (offline nicht verfügbar). Nutzt {@code tools.jackson.databind}
 * (Jackson 3, von spring-boot-starter-web transitiv im compile-Scope) statt {@code com.fasterxml.jackson}
 * (Jackson 2, hier nur test-scoped) — API-Oberfläche ist identisch.
 */
@Component
public class NodeJwtVerifier {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final byte[] keyBytes;

    public NodeJwtVerifier(@Value("${jwt.secret}") String secretBase64) {
        this.keyBytes = Base64.getDecoder().decode(secretBase64);
    }

    public boolean isValid(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return false;
            }
            String signingInput = parts[0] + "." + parts[1];
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
            byte[] expected = mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII));
            byte[] actual = Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(expected, actual)) {
                return false;
            }
            JsonNode claims = MAPPER.readTree(Base64.getUrlDecoder().decode(parts[1]));
            if (claims.has("exp") && Instant.now().getEpochSecond() >= claims.get("exp").asLong()) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
