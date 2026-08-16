package server.agents.runtime.activity.world;

import org.junit.jupiter.api.Test;
import server.agents.runtime.activity.session.AgentActivityKind;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentWorldDirectorTest {
    @Test
    void choosesAcrossAllFourPrimarySystems() {
        AgentWorldDirector director = new AgentWorldDirector(20L);
        List<AgentWorldActivityProposal> proposals = List.of(
                proposal("town", AgentActivityKind.TOWN_LIFE, 10, 80),
                proposal("hunt", AgentActivityKind.HUNTING, 10, 100),
                proposal("quest", AgentActivityKind.QUESTING, 20, 40),
                proposal("shop", AgentActivityKind.COMMERCE, 30, 10));

        AgentWorldActivityDecision decision = director.select(
                AgentActivityKind.TOWN_LIFE, proposals);

        assertEquals(AgentActivityKind.COMMERCE, decision.kind());
        assertEquals("shop", decision.proposalId());
        assertTrue(decision.switchRequired());
    }

    @Test
    void retentionUtilityPreventsEqualPriorityThrashing() {
        AgentWorldDirector director = new AgentWorldDirector(25L);

        AgentWorldActivityDecision decision = director.select(AgentActivityKind.HUNTING,
                List.of(proposal("hunt", AgentActivityKind.HUNTING, 10, 90),
                        proposal("quest", AgentActivityKind.QUESTING, 10, 100)));

        assertEquals(AgentActivityKind.HUNTING, decision.kind());
        assertEquals(false, decision.switchRequired());
    }

    private static AgentWorldActivityProposal proposal(
            String id, AgentActivityKind kind, int priority, long utility) {
        return new AgentWorldActivityProposal(id, kind, priority, utility, true, id + " ready");
    }
}
