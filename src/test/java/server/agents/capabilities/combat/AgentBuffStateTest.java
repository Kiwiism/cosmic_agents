package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBuffStateTest {
    @Test
    void tracksConsumableBuffToggleModeAndScan() {
        AgentBuffState state = new AgentBuffState();

        assertFalse(state.consumablesEnabled());
        assertTrue(state.cheapMode());
        assertTrue(state.consumableScanDue(1_000L, 750L));

        state.setConsumablesEnabled(true);
        state.setCheapMode(false);
        state.setLastConsumableScanMs(1_000L);

        assertTrue(state.consumablesEnabled());
        assertFalse(state.cheapMode());
        assertFalse(state.consumableScanDue(1_500L, 750L));
        assertTrue(state.consumableScanDue(1_750L, 750L));

        state.resetLastConsumableScan();
        assertTrue(state.consumableScanDue(1_500L, 750L));
    }

    @Test
    void tracksConsumableAndSkillBuffActionSummaries() {
        AgentBuffState state = new AgentBuffState();

        assertEquals(0L, state.lastConsumableActionAtMs());
        assertEquals("no buff scans yet", state.lastConsumableActionSummary());
        assertEquals(0L, state.lastSkillActionAtMs());
        assertEquals("no skill buff checks yet", state.lastSkillActionSummary());
        assertEquals(-1L, state.lastSkillActionAgeMs(5_000L));

        state.rememberConsumableAction(2_000L, "used buff");
        state.rememberSkillAction(3_000L, "used haste");

        assertEquals(2_000L, state.lastConsumableActionAtMs());
        assertEquals("used buff", state.lastConsumableActionSummary());
        assertEquals(3_000L, state.lastSkillActionAtMs());
        assertEquals("used haste", state.lastSkillActionSummary());
        assertEquals(2_000L, state.lastSkillActionAgeMs(5_000L));
    }
}
