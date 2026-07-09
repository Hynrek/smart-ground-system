package ch.jp.shooting.node.crypto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HkdfTest {

    @Test
    void extractAndExpand_matchRfc5869TestCase1() {
        HkdfVector v = CryptoTestVectors.load().hkdf_sha256_rfc5869().get(0);

        byte[] prk = Hkdf.extract(CryptoTestVectors.hex(v.salt()), CryptoTestVectors.hex(v.ikm()));
        assertThat(prk).isEqualTo(CryptoTestVectors.hex(v.prk()));

        byte[] okm = Hkdf.expand(prk, CryptoTestVectors.hex(v.info()), v.l());
        assertThat(okm).isEqualTo(CryptoTestVectors.hex(v.okm()));
    }
}
