package server.agents.diagnostics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MovementPacketDecoderTest {
    @Test
    void decodesAbsoluteRopeMovement() {
        byte[] data = {
                1, 0,
                100, 0,
                56, (byte) 0xFF,
                3, 0,
                (byte) 0xFC, (byte) 0xFF,
                7, 0,
                16,
                120, 0
        };

        List<String> decoded = MovementPacketDecoder.decode(data);

        assertEquals("fragmentCount=1", decoded.get(0));
        assertEquals("fragment[0] cmd=0 absolute x=100 y=-200 xWobble=3 yWobble=-4 fh=7 "
                + "stance=16(rope-right) duration=120", decoded.get(1));
        assertEquals("01 00 64 00 38 FF 03 00 FC FF 07 00 10 78 00", MovementPacketDecoder.hex(data));
    }

    @Test
    void decodesMultipleFragmentsIncludingRelativeMovement() {
        byte[] data = {
                2,
                1,
                (byte) 0xFC, (byte) 0xFF,
                9, 0,
                15,
                33, 0,
                10, 5
        };

        List<String> decoded = MovementPacketDecoder.decode(data);

        assertEquals(List.of(
                "fragmentCount=2",
                "fragment[0] cmd=1 relative x=-4 y=9 stance=15(ladder-left) duration=33",
                "fragment[1] cmd=10 changeEquip value=5"
        ), decoded);
    }

    @Test
    void reportsTruncatedFragmentWithoutThrowing() {
        byte[] data = {1, 0, 1, 0};

        List<String> decoded = MovementPacketDecoder.decode(data);

        assertEquals("fragmentCount=1", decoded.get(0));
        assertTrue(decoded.get(1).contains("fragment[0] cmd=0 truncated"));
        assertTrue(decoded.get(1).contains("need 13 payload bytes, have 2"));
    }
}
