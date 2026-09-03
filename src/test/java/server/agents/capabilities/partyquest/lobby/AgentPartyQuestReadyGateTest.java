package server.agents.capabilities.partyquest.lobby;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPartyQuestReadyGateTest {
    private static final String LOBBY = "ready-gate-test";

    @AfterEach
    void release() {
        AgentPartyQuestReadyGate.release(LOBBY);
    }

    @Test
    void rosterChangeRestartsTheStablePartyCountdown() {
        assertFalse(AgentPartyQuestReadyGate.ready(LOBBY, 1L, 7L, 1_000L));
        assertTrue(AgentPartyQuestReadyGate.ready(LOBBY, 1L, 7L, 100_000L));

        assertFalse(AgentPartyQuestReadyGate.ready(LOBBY, 2L, 7L, 100_000L));
        assertTrue(AgentPartyQuestReadyGate.ready(LOBBY, 2L, 7L, 200_000L));
    }
}
