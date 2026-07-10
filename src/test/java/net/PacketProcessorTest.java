package net;

import net.opcodes.RecvOpcode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketProcessorTest {
    @Test
    void shouldRejectNegativeAndPastEndOpcodes() {
        PacketProcessor processor = PacketProcessor.getLoginServerProcessor();
        short knownOpcode = 0;
        for (RecvOpcode opcode : RecvOpcode.values()) {
            if (opcode.getValue() >= 0 && opcode.getValue() <= Short.MAX_VALUE) {
                knownOpcode = (short) opcode.getValue();
            }
        }

        assertFalse(processor.isPacketIdInRange((short) -1));
        assertNull(processor.getHandler((short) -1));
        assertTrue(processor.isPacketIdInRange(knownOpcode));
    }
}
