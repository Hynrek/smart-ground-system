package ch.jp.shooting.node.uart;

import ch.jp.shooting.node.frame.PairingTestVectors;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UartFrameDecoderTest {

    private static final byte[] SET_CHANNEL_FRAME = PairingTestVectors.hex("7e03000101065ba8");
    private static final byte[] SET_CHANNEL_FRAME_CORRUPTED = PairingTestVectors.hex("7e03000101065ba9");
    private static final byte[] ADD_PEER_FRAME = PairingTestVectors.hex("7e08000202aabbccddeeff8e8d");
    private static final byte[] SEND_FRAME_WITH_EMBEDDED_START_BYTE =
            PairingTestVectors.hex("7e0b000604aabbccddeeff7e01021eb3");

    @Test
    void feed_decodesSingleCompleteFrame() {
        UartFrameDecoder decoder = new UartFrameDecoder();

        List<UartCodec.DecodedFrame> frames = decoder.feed(SET_CHANNEL_FRAME);

        assertThat(frames).hasSize(1);
        assertThat(frames.get(0).cmdId()).isEqualTo(0x01);
        assertThat(frames.get(0).cmd()).isEqualTo(UartCodec.CMD_SET_CHANNEL);
        assertThat(frames.get(0).body()).isEqualTo(new byte[]{0x06});
    }

    @Test
    void feed_decodesMultipleFramesInOneChunk() {
        UartFrameDecoder decoder = new UartFrameDecoder();
        byte[] chunk = concat(SET_CHANNEL_FRAME, ADD_PEER_FRAME);

        List<UartCodec.DecodedFrame> frames = decoder.feed(chunk);

        assertThat(frames).hasSize(2);
        assertThat(frames.get(0).cmd()).isEqualTo(UartCodec.CMD_SET_CHANNEL);
        assertThat(frames.get(1).cmd()).isEqualTo(UartCodec.CMD_ADD_PEER);
        assertThat(frames.get(1).body()).isEqualTo(PairingTestVectors.hex("aabbccddeeff"));
    }

    @Test
    void feed_reassemblesFrameSplitAcrossTwoChunks() {
        UartFrameDecoder decoder = new UartFrameDecoder();
        byte[] firstHalf = new byte[]{SET_CHANNEL_FRAME[0], SET_CHANNEL_FRAME[1], SET_CHANNEL_FRAME[2], SET_CHANNEL_FRAME[3]};
        byte[] secondHalf = new byte[]{SET_CHANNEL_FRAME[4], SET_CHANNEL_FRAME[5], SET_CHANNEL_FRAME[6], SET_CHANNEL_FRAME[7]};

        List<UartCodec.DecodedFrame> afterFirst = decoder.feed(firstHalf);
        List<UartCodec.DecodedFrame> afterSecond = decoder.feed(secondHalf);

        assertThat(afterFirst).isEmpty();
        assertThat(afterSecond).hasSize(1);
        assertThat(afterSecond.get(0).cmd()).isEqualTo(UartCodec.CMD_SET_CHANNEL);
    }

    @Test
    void feed_discardsGarbageBytesBeforeValidFrame() {
        UartFrameDecoder decoder = new UartFrameDecoder();
        byte[] chunk = concat(new byte[]{0x00, (byte) 0xFF}, SET_CHANNEL_FRAME);

        List<UartCodec.DecodedFrame> frames = decoder.feed(chunk);

        assertThat(frames).hasSize(1);
        assertThat(frames.get(0).cmd()).isEqualTo(UartCodec.CMD_SET_CHANNEL);
    }

    @Test
    void feed_resyncsPastCrcCorruptedFrameToNextValidFrame() {
        UartFrameDecoder decoder = new UartFrameDecoder();
        byte[] chunk = concat(SET_CHANNEL_FRAME_CORRUPTED, ADD_PEER_FRAME);

        List<UartCodec.DecodedFrame> frames = decoder.feed(chunk);

        assertThat(frames).hasSize(1);
        assertThat(frames.get(0).cmd()).isEqualTo(UartCodec.CMD_ADD_PEER);
    }

    @Test
    void feed_resyncsPastImplausibleLengthField() {
        UartFrameDecoder decoder = new UartFrameDecoder();
        byte[] fakeHeader = new byte[]{0x7E, (byte) 0xFF, (byte) 0xFF, 0x00, 0x01, 0x02};
        byte[] chunk = concat(fakeHeader, SET_CHANNEL_FRAME);

        List<UartCodec.DecodedFrame> frames = decoder.feed(chunk);

        assertThat(frames).hasSize(1);
        assertThat(frames.get(0).cmd()).isEqualTo(UartCodec.CMD_SET_CHANNEL);
    }

    @Test
    void feed_doesNotMisinterpretStartByteEmbeddedInBody() {
        UartFrameDecoder decoder = new UartFrameDecoder();

        List<UartCodec.DecodedFrame> frames = decoder.feed(SEND_FRAME_WITH_EMBEDDED_START_BYTE);

        assertThat(frames).hasSize(1);
        assertThat(frames.get(0).cmd()).isEqualTo(UartCodec.CMD_SEND);
        assertThat(frames.get(0).body()).isEqualTo(PairingTestVectors.hex("aabbccddeeff7e0102"));
    }

    private static byte[] concat(byte[]... parts) {
        int total = 0;
        for (byte[] part : parts) {
            total += part.length;
        }
        byte[] out = new byte[total];
        int pos = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, out, pos, part.length);
            pos += part.length;
        }
        return out;
    }
}
