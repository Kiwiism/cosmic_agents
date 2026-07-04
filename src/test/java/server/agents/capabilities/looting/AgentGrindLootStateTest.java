package server.agents.capabilities.looting;

import org.junit.jupiter.api.Test;
import server.maps.MapItem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentGrindLootStateTest {
    @Test
    void storesTargetAndRetrySuppression() {
        AgentGrindLootState state = new AgentGrindLootState();
        MapItem target = mock(MapItem.class);

        assertFalse(state.hasTarget());
        assertNull(state.target());
        assertEquals(0, state.ignoredObjectId());
        assertEquals(0L, state.ignoredUntilMs());

        state.setTarget(target);
        state.suppressRetry(123, 2_000L);

        assertTrue(state.hasTarget());
        assertSame(target, state.target());
        assertEquals(123, state.ignoredObjectId());
        assertEquals(2_000L, state.ignoredUntilMs());

        state.clearTarget();
        state.clearRetrySuppression();

        assertFalse(state.hasTarget());
        assertNull(state.target());
        assertEquals(0, state.ignoredObjectId());
        assertEquals(0L, state.ignoredUntilMs());
    }
}
