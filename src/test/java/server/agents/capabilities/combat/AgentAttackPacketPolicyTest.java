package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentAttackPacketPolicyTest {
    @Test
    void packsCountsWithoutDamageNibbleCorruptingTargetCount() {
        assertEquals(0xFE, AgentAttackPacketPolicy.packCounts(15, 14));
        assertEquals(0xFF, AgentAttackPacketPolicy.packCounts(99, 99));
        assertEquals(0, AgentAttackPacketPolicy.packCounts(-1, -1));
    }
}
