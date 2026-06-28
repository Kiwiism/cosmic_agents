package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotBuffStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotBuffStateRuntimeTest {
    @Test
    void storesBuffConsumableToggleAndMode() {
        BotEntry entry = new BotEntry(null, null, null);

        AgentBotBuffStateRuntime.setEnabled(entry, true);
        AgentBotBuffStateRuntime.setCheapMode(entry, false);

        assertTrue(AgentBotBuffStateRuntime.enabled(entry));
        assertFalse(AgentBotBuffStateRuntime.cheapMode(entry));

        AgentBotBuffStateRuntime.disable(entry);

        assertFalse(AgentBotBuffStateRuntime.enabled(entry));
    }

    @Test
    void resetScanMakesScanDueImmediately() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentBotBuffStateRuntime.markScanned(entry, 10_000L);

        assertFalse(AgentBotBuffStateRuntime.scanDue(entry, 10_500L, 3_000L));

        AgentBotBuffStateRuntime.resetScan(entry);

        assertTrue(AgentBotBuffStateRuntime.scanDue(entry, 10_500L, 3_000L));
    }

    @Test
    void adaptsConsumableBuffScanAndDecisionState() {
        BotEntry entry = new BotEntry(null, null, null);

        assertTrue(AgentBotBuffStateRuntime.scanDue(entry, 1_000, 750));

        AgentBotBuffStateRuntime.markScanned(entry, 1_000);
        assertFalse(AgentBotBuffStateRuntime.scanDue(entry, 1_500, 750));
        assertTrue(AgentBotBuffStateRuntime.scanDue(entry, 1_750, 750));

        AgentBotBuffStateRuntime.noteDecision(entry, 2_000, "used buff");
        assertEquals(2_000, AgentBotBuffStateRuntime.lastActionAtMs(entry));
        assertEquals("used buff", AgentBotBuffStateRuntime.lastActionSummary(entry));
    }
}
