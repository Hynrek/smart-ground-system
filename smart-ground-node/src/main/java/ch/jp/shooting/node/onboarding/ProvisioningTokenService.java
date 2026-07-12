package ch.jp.shooting.node.onboarding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

/**
 * Vergibt und prüft einmalige Provisioning-Token. Das Token wandert im Klartext-
 * ONBOARD_OFFER zur Box und kommt bei der box-api-Discovery zurück; hier wird es
 * gegen unbenutzt / nicht-abgelaufen / MAC-gebunden geprüft und dabei verbraucht.
 */
@Service
public class ProvisioningTokenService {

    /** hex = Kleinbuchstaben-Hex von raw (16 Byte); raw wandert roh in den ONBOARD_OFFER-Frame. */
    public record MintedToken(String hex, byte[] raw, Instant expiresAt) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ProvisioningTokenRepository repository;
    private final Duration ttl;

    public ProvisioningTokenService(ProvisioningTokenRepository repository,
                                    @Value("${onboarding.token-ttl-seconds:300}") long ttlSeconds) {
        this.repository = repository;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    @Transactional
    public MintedToken mint(String mac) {
        byte[] raw = new byte[16];
        RANDOM.nextBytes(raw);
        String hex = toHex(raw);
        Instant expiresAt = Instant.now().plus(ttl);

        ProvisioningTokenRecord record = new ProvisioningTokenRecord();
        record.setToken(hex);
        record.setMacAddress(mac);
        record.setExpiresAt(expiresAt);
        record.setUsed(false);
        repository.save(record);

        return new MintedToken(hex, raw, expiresAt);
    }

    @Transactional
    public void validateAndConsume(String tokenHex, String mac) {
        ProvisioningTokenRecord record = repository.findByToken(tokenHex).orElseThrow(() -> reject("unbekannt"));
        if (record.isUsed()) {
            throw reject("bereits benutzt");
        }
        if (record.getExpiresAt().isBefore(Instant.now())) {
            throw reject("abgelaufen");
        }
        if (!record.getMacAddress().equals(mac)) {
            throw reject("MAC passt nicht");
        }
        record.setUsed(true);
        repository.save(record);
    }

    private static ErrorResponseException reject(String reason) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Provisioning-Token ungültig: " + reason + ".");
        detail.setType(URI.create("/errors/invalid-provisioning-token"));
        return new ErrorResponseException(HttpStatus.BAD_REQUEST, detail, null);
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
