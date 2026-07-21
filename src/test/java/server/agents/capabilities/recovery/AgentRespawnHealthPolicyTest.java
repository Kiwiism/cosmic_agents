package server.agents.capabilities.recovery;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentRespawnHealthPolicyTest {
    @Test
    void zeroPercentUsesFiftyHpDefault() {
        assertEquals(50, AgentRespawnHealthPolicy.restoredHp(500, 0));
    }

    @Test
    void positivePercentRoundsUpAndCapsAtMaxHp() {
        assertEquals(31, AgentRespawnHealthPolicy.restoredHp(123, 25));
        assertEquals(123, AgentRespawnHealthPolicy.restoredHp(123, 150));
    }

    @Test
    void defaultDoesNotExceedSmallMaxHp() {
        assertEquals(20, AgentRespawnHealthPolicy.restoredHp(20, 0));
    }
}
