package ch.jp.shooting.node.frame;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FrameHeaderTest {

    @Test
    void encode_matchesDiscoverHeaderFromFixture() {
        PairDiscoverVector v = PairingTestVectors.load().pair_discover();
        FrameHeader header = new FrameHeader(
                PairingTestVectors.hex(v.dest_mac()),
                PairingTestVectors.hex(v.src_mac()),
                v.frame_id(),
                v.ttl(),
                FrameType.fromCode((byte) v.type())
        );

        assertThat(header.encode()).isEqualTo(PairingTestVectors.hex(v.header()));
    }

    @Test
    void decode_roundTripsAllThreeFixtureHeaders() {
        PairDiscoverVector discover = PairingTestVectors.load().pair_discover();
        PairOfferVector offer = PairingTestVectors.load().pair_offer();
        PairConfirmVector confirm = PairingTestVectors.load().pair_confirm();

        FrameHeader decodedDiscover = FrameHeader.decode(PairingTestVectors.hex(discover.header()));
        assertThat(decodedDiscover.destMac()).isEqualTo(PairingTestVectors.hex(discover.dest_mac()));
        assertThat(decodedDiscover.srcMac()).isEqualTo(PairingTestVectors.hex(discover.src_mac()));
        assertThat(decodedDiscover.frameId()).isEqualTo(discover.frame_id());
        assertThat(decodedDiscover.ttl()).isEqualTo(discover.ttl());
        assertThat(decodedDiscover.type()).isEqualTo(FrameType.PAIR_DISCOVER);

        FrameHeader decodedOffer = FrameHeader.decode(PairingTestVectors.hex(offer.header()));
        assertThat(decodedOffer.type()).isEqualTo(FrameType.PAIR_OFFER);
        assertThat(decodedOffer.frameId()).isEqualTo(offer.frame_id());

        FrameHeader decodedConfirm = FrameHeader.decode(PairingTestVectors.hex(confirm.header()));
        assertThat(decodedConfirm.type()).isEqualTo(FrameType.PAIR_CONFIRM);
        assertThat(decodedConfirm.frameId()).isEqualTo(confirm.frame_id());
    }

    @Test
    void fromCode_rejectsUnknownType() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> FrameType.fromCode((byte) 0x00));
    }
}
