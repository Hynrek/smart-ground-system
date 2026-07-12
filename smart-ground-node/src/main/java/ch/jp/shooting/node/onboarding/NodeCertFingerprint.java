package ch.jp.shooting.node.onboarding;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;

/**
 * SHA-256-Fingerprint des Node-Server-Zertifikats. Wandert im ONBOARD_OFFER zur Box, die
 * genau diesen Fingerprint für die Provisioning-TLS-Sitzung pinnt (Vertrauen über den
 * ohnehin vertrauten ESP-NOW-Kanal gebootstrappt — keine PKI). Einmal beim Start berechnet.
 */
@Component
public class NodeCertFingerprint {

    private final byte[] fingerprint;

    public NodeCertFingerprint(
            ResourceLoader resourceLoader,
            @Value("${server.ssl.key-store}") String keyStoreLocation,
            @Value("${server.ssl.key-store-password}") String password,
            @Value("${server.ssl.key-store-type:PKCS12}") String type,
            @Value("${server.ssl.key-alias}") String alias) {
        try {
            KeyStore keyStore = KeyStore.getInstance(type);
            try (InputStream in = resourceLoader.getResource(keyStoreLocation).getInputStream()) {
                keyStore.load(in, password.toCharArray());
            }
            Certificate cert = keyStore.getCertificate(alias);
            if (cert == null) {
                throw new IllegalStateException("Kein Zertifikat für Alias " + alias + " im Keystore.");
            }
            this.fingerprint = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());
        } catch (Exception e) {
            throw new IllegalStateException("Zert-Fingerprint konnte nicht berechnet werden", e);
        }
    }

    public byte[] sha256() {
        return fingerprint.clone();
    }
}
