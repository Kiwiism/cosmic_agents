package server.agents.capabilities.partyquest.hpq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
class AgentHpqBonusPolicyTest {
    @Test
    void deploymentDefaultIsAnExplicitAgentChoice() {
        assertEquals(AgentHpqSession.BonusMode.SKIP, AgentHpqBonusPolicy.defaultMode());
    }
}
