package ch.jp.shooting.node.onboarding;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.cert.Certificate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class NodeCertFingerprintTest {

    @Autowired
    private NodeCertFingerprint fingerprint;

    @Test
    void sha256_is32Bytes_andMatchesIndependentComputation() throws Exception {
        byte[] actual = fingerprint.sha256();
        assertThat(actual).hasSize(32);

        ResourceLoader loader = new DefaultResourceLoader();
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = loader.getResource("classpath:node-dev-keystore.p12").getInputStream()) {
            ks.load(in, "changeit".toCharArray());
        }
        Certificate cert = ks.getCertificate("smartground-node");
        byte[] expected = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void sha256_isStableAcrossCalls() {
        assertThat(fingerprint.sha256()).isEqualTo(fingerprint.sha256());
    }
}
