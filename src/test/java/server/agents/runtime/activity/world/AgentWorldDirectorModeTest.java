package server.agents.runtime.activity.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentWorldDirectorModeTest {
    @Test
    void authorityIsExplicitByMode() {
        assertTrue(AgentWorldDirectorMode.OBSERVE.isObservationOnly());
        assertFalse(AgentWorldDirectorMode.MANUAL.allowsAutomaticProposals());
        assertTrue(AgentWorldDirectorMode.MANUAL.acceptsOperatorDirectives());
        assertTrue(AgentWorldDirectorMode.ASSISTED.allowsAutomaticProposals());
        assertFalse(AgentWorldDirectorMode.EMERGENCY_HOLD.acceptsOperatorDirectives());
    }

    @Test
    void sessionModeTransitionStillDoesNotGrantLiveOwnership() {
        AgentWorldDirectorSession session = AgentWorldDirectorSession.shadow(27, 1_000L)
                .withMode(AgentWorldDirectorMode.MANUAL, "manual test", 1_001L);

        assertTrue(session.phase() == AgentWorldDirectorPhase.WAITING);
        assertFalse(session.mayOwnActivity());
    }
}
