package server.agents.behavior;

import org.junit.jupiter.api.Test;
import server.agents.runtime.activity.session.AgentActivityKind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBehaviorAdaptationStateTest {
    @Test
    void catchesUpDrainAndRecoveryFromObservedHighLevelActivity() {
        AgentBehaviorAdaptationState state = new AgentBehaviorAdaptationState();
        int initial = state.observe(AgentActivityKind.HUNTING, 1_000L).energyPercent();
        int afterHunting = state.observe(AgentActivityKind.TOWN_LIFE, 121_000L).energyPercent();
        int afterTown = state.observe(null, 241_000L).energyPercent();
        int afterIdle = state.observe(null, 301_000L).energyPercent();

        assertEquals(initial - 2, afterHunting);
        assertEquals(afterHunting + 2, afterTown);
        assertEquals(afterTown + 2, afterIdle);
        assertTrue(state.snapshot(301_000L).energyPercent() <= 100);
    }

    @Test
    void offlineRecoveryIsFastAndBounded() {
        AgentBehaviorAdaptationState state = new AgentBehaviorAdaptationState();
        state.restoreOffline(new AgentBehaviorAdaptationSnapshot(
                20, 50, 30, 80, 2, 1_000L), 20 * 60_000L + 1_000L);

        AgentBehaviorAdaptationSnapshot restored = state.snapshot(20 * 60_000L + 1_000L);
        assertEquals(80, restored.energyPercent());
        assertEquals(20, restored.restDebtPercent());
        assertEquals(0, restored.frustrationPercent());
    }
}
