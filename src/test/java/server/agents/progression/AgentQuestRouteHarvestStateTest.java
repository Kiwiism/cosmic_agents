package server.agents.progression;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentQuestRouteHarvestStateTest {
    @Test
    void harvestIsBoundedByProgressAndDoesNotRestartForTheSameDebtScope() {
        AgentQuestRouteHarvestState state = new AgentQuestRouteHarvestState();

        assertEquals(AgentQuestRouteHarvestState.Decision.STARTED,
                state.evaluate("pack", "pig|slime", 100, 4,
                        true, 1_000L, 20_000L, 5));
        assertEquals(AgentQuestRouteHarvestState.Decision.HARVEST,
                state.evaluate("pack", "pig|slime", 100, 8,
                        true, 2_000L, 20_000L, 5));
        assertEquals(AgentQuestRouteHarvestState.Decision.FINISHED,
                state.evaluate("pack", "pig|slime", 100, 9,
                        true, 3_000L, 20_000L, 5));
        assertEquals(AgentQuestRouteHarvestState.Decision.SKIP,
                state.evaluate("pack", "pig|slime", 100, 9,
                        true, 4_000L, 20_000L, 5));
        assertEquals(AgentQuestRouteHarvestState.Decision.STARTED,
                state.evaluate("pack", "slime", 100, 9,
                        true, 5_000L, 20_000L, 5));
    }
}
