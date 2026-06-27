package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotPotionStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotPotionStateRuntimeTest {
    @Test
    void adaptsPotionShareRequestedStateByPotionType() {
        BotEntry entry = new BotEntry(null, null, null);

        AgentBotPotionStateRuntime.setPotShareRequested(entry, true, true);
        AgentBotPotionStateRuntime.setPotShareRequested(entry, false, true);

        assertTrue(AgentBotPotionStateRuntime.potShareRequested(entry, true));
        assertTrue(AgentBotPotionStateRuntime.potShareRequested(entry, false));

        AgentBotPotionStateRuntime.clearPotShareRequested(entry, true);
        assertFalse(AgentBotPotionStateRuntime.potShareRequested(entry, true));
        assertTrue(AgentBotPotionStateRuntime.potShareRequested(entry, false));

        AgentBotPotionStateRuntime.clearAllPotShareRequests(entry);
        assertFalse(AgentBotPotionStateRuntime.potShareRequested(entry, true));
        assertFalse(AgentBotPotionStateRuntime.potShareRequested(entry, false));
    }

    @Test
    void adaptsPotionCheckTimerState() {
        BotEntry entry = new BotEntry(null, null, null);

        assertFalse(AgentBotPotionStateRuntime.hasPotCheckDelay(entry));

        AgentBotPotionStateRuntime.setPotCheckTimerMs(entry, 5_000);

        assertTrue(AgentBotPotionStateRuntime.hasPotCheckDelay(entry));
        assertEquals(5_000, AgentBotPotionStateRuntime.potCheckTimerMs(entry));

        AgentBotPotionStateRuntime.tickPotCheckDelay(entry, value -> value - 750);

        assertEquals(4_250, AgentBotPotionStateRuntime.potCheckTimerMs(entry));

        AgentBotPotionStateRuntime.requestPotionCheckSoon(entry, 250);

        assertEquals(250, AgentBotPotionStateRuntime.potCheckTimerMs(entry));

        AgentBotPotionStateRuntime.requestPotionCheckSoon(entry, 1_000);

        assertEquals(250, AgentBotPotionStateRuntime.potCheckTimerMs(entry));
    }

    @Test
    void adaptsMpRecoveryTimerState() {
        BotEntry entry = new BotEntry(null, null, null);

        assertFalse(AgentBotPotionStateRuntime.hasMpRecoveryDelay(entry));

        AgentBotPotionStateRuntime.setMpRecoveryTimerMs(entry, 10_000);

        assertTrue(AgentBotPotionStateRuntime.hasMpRecoveryDelay(entry));
        assertEquals(10_000, AgentBotPotionStateRuntime.mpRecoveryTimerMs(entry));

        AgentBotPotionStateRuntime.tickMpRecoveryDelay(entry, value -> value - 1_000);

        assertEquals(9_000, AgentBotPotionStateRuntime.mpRecoveryTimerMs(entry));

        AgentBotPotionStateRuntime.clearMpRecoveryTimer(entry);

        assertEquals(0, AgentBotPotionStateRuntime.mpRecoveryTimerMs(entry));
        assertFalse(AgentBotPotionStateRuntime.hasMpRecoveryDelay(entry));
    }
}
