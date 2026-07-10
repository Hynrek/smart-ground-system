package ch.jp.shooting.node.box;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/** Erzeugt zufällige 32-Byte K_Box-Schlüssel (ADR-003). */
@Component
public class KBoxGenerator {

    private final SecureRandom random = new SecureRandom();

    public byte[] generate() {
        byte[] kBox = new byte[32];
        random.nextBytes(kBox);
        return kBox;
    }
}
