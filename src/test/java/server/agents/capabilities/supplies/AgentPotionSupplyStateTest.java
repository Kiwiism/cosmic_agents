package server.agents.capabilities.supplies;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPotionSupplyStateTest {
    @Test
    void storesPotionTimersAndShareFlags() {
        AgentPotionSupplyState state = new AgentPotionSupplyState();

        assertEquals(0, state.potCheckTimerMs());
        assertEquals(0, state.mpRecoveryTimerMs());
        assertEquals(0L, state.diseaseSignature());
        assertEquals(0L, state.diseaseCureDueAtMs());
        assertFalse(state.shareRequested(true));
        assertFalse(state.shareRequested(false));

        state.setPotCheckTimerMs(5_000);
        state.setMpRecoveryTimerMs(10_000);
        state.scheduleDiseaseCure(123L, 2_500L);
        state.setShareRequested(true, true);
        state.setShareRequested(false, true);

        assertEquals(5_000, state.potCheckTimerMs());
        assertEquals(10_000, state.mpRecoveryTimerMs());
        assertEquals(123L, state.diseaseSignature());
        assertEquals(2_500L, state.diseaseCureDueAtMs());
        assertTrue(state.shareRequested(true));
        assertTrue(state.shareRequested(false));

        state.clearAllShareRequests();
        state.clearDiseaseCure();

        assertFalse(state.shareRequested(true));
        assertFalse(state.shareRequested(false));
        assertEquals(0L, state.diseaseSignature());
        assertEquals(0L, state.diseaseCureDueAtMs());
    }
}
