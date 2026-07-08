package server.agents.capabilities.combat;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBuffStateRuntimeTest {
    @Test
    void storesBuffConsumableToggleAndMode() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentBuffStateRuntime.setEnabled(entry, true);
        AgentBuffStateRuntime.setCheapMode(entry, false);

        assertTrue(AgentBuffStateRuntime.enabled(entry));
        assertFalse(AgentBuffStateRuntime.cheapMode(entry));

        AgentBuffStateRuntime.disable(entry);

        assertFalse(AgentBuffStateRuntime.enabled(entry));
    }

    @Test
    void resetScanMakesScanDueImmediately() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentBuffStateRuntime.markScanned(entry, 10_000L);

        assertFalse(AgentBuffStateRuntime.scanDue(entry, 10_500L, 3_000L));

        AgentBuffStateRuntime.resetScan(entry);

        assertTrue(AgentBuffStateRuntime.scanDue(entry, 10_500L, 3_000L));
    }

    @Test
    void adaptsConsumableBuffScanAndDecisionState() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertTrue(AgentBuffStateRuntime.scanDue(entry, 1_000, 750));

        AgentBuffStateRuntime.markScanned(entry, 1_000);
        assertFalse(AgentBuffStateRuntime.scanDue(entry, 1_500, 750));
        assertTrue(AgentBuffStateRuntime.scanDue(entry, 1_750, 750));

        AgentBuffStateRuntime.noteDecision(entry, 2_000, "used buff");
        assertEquals(2_000, AgentBuffStateRuntime.lastActionAtMs(entry));
        assertEquals("used buff", AgentBuffStateRuntime.lastActionSummary(entry));
    }
}
