package server.agents.capabilities.partyquest.lpq;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentLpqPortalMazeStateTest {
    @Test
    void observationsAreSessionLocalAndResettable() {
        AgentLpqPortalMazeState first = new AgentLpqPortalMazeState();
        AgentLpqPortalMazeState second = new AgentLpqPortalMazeState();
        first.recordSuccess(0, 3);
        first.recordSuccess(1, 7);
        second.recordFailure(0);

        assertEquals(3, first.successfulPortal(0));
        assertEquals(2, first.currentRow());
        assertNull(second.successfulPortal(0));
        assertEquals(1, second.nextCandidateOffset(0));

        first.reset();
        assertEquals(0, first.currentRow());
        assertNull(first.successfulPortal(0));
        assertEquals(0, first.nextCandidateOffset(0));
    }
}
