package server.agents.capabilities.supplies;

import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPotionStateRuntimeTest {
    @Test
    void adaptsPotionShareRequestedStateByPotionType() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentPotionStateRuntime.setPotShareRequested(entry, true, true);
        AgentPotionStateRuntime.setPotShareRequested(entry, false, true);

        assertTrue(AgentPotionStateRuntime.potShareRequested(entry, true));
        assertTrue(AgentPotionStateRuntime.potShareRequested(entry, false));

        AgentPotionStateRuntime.clearPotShareRequested(entry, true);
        assertFalse(AgentPotionStateRuntime.potShareRequested(entry, true));
        assertTrue(AgentPotionStateRuntime.potShareRequested(entry, false));

        AgentPotionStateRuntime.clearAllPotShareRequests(entry);
        assertFalse(AgentPotionStateRuntime.potShareRequested(entry, true));
        assertFalse(AgentPotionStateRuntime.potShareRequested(entry, false));
    }

    @Test
    void adaptsPotionCheckTimerState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentPotionStateRuntime.hasPotCheckDelay(entry));

        AgentPotionStateRuntime.setPotCheckTimerMs(entry, 5_000);

        assertTrue(AgentPotionStateRuntime.hasPotCheckDelay(entry));
        assertEquals(5_000, AgentPotionStateRuntime.potCheckTimerMs(entry));

        AgentPotionStateRuntime.tickPotCheckDelay(entry, value -> value - 750);

        assertEquals(4_250, AgentPotionStateRuntime.potCheckTimerMs(entry));

        AgentPotionStateRuntime.requestPotionCheckSoon(entry, 250);

        assertEquals(250, AgentPotionStateRuntime.potCheckTimerMs(entry));

        AgentPotionStateRuntime.requestPotionCheckSoon(entry, 1_000);

        assertEquals(250, AgentPotionStateRuntime.potCheckTimerMs(entry));
    }

    @Test
    void adaptsMpRecoveryTimerState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentPotionStateRuntime.hasMpRecoveryDelay(entry));

        AgentPotionStateRuntime.setMpRecoveryTimerMs(entry, 10_000);

        assertTrue(AgentPotionStateRuntime.hasMpRecoveryDelay(entry));
        assertEquals(10_000, AgentPotionStateRuntime.mpRecoveryTimerMs(entry));

        AgentPotionStateRuntime.tickMpRecoveryDelay(entry, value -> value - 1_000);

        assertEquals(9_000, AgentPotionStateRuntime.mpRecoveryTimerMs(entry));

        AgentPotionStateRuntime.clearMpRecoveryTimer(entry);

        assertEquals(0, AgentPotionStateRuntime.mpRecoveryTimerMs(entry));
        assertFalse(AgentPotionStateRuntime.hasMpRecoveryDelay(entry));
    }
}
