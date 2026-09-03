package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentHorizontalBoundaryStateRuntimeTest {
    @Test
    void clampsOnlyInsideTheConfiguredMap() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentHorizontalBoundaryStateRuntime.set(entry, 105100400, -100, 1068);

        assertEquals(-100, AgentHorizontalBoundaryStateRuntime.clampX(entry, 105100400, -140));
        assertEquals(300, AgentHorizontalBoundaryStateRuntime.clampX(entry, 105100400, 300));
        assertEquals(1068, AgentHorizontalBoundaryStateRuntime.clampX(entry, 105100400, 1100));
        assertEquals(-140, AgentHorizontalBoundaryStateRuntime.clampX(entry, 105100401, -140));
    }

    @Test
    void clearRemovesTheOptionalBoundary() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentHorizontalBoundaryStateRuntime.set(entry, 105100400, -100, 1068);

        AgentHorizontalBoundaryStateRuntime.clear(entry);

        assertEquals(-140, AgentHorizontalBoundaryStateRuntime.clampX(entry, 105100400, -140));
    }

    @Test
    void rejectsInvalidBounds() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertThrows(IllegalArgumentException.class,
                () -> AgentHorizontalBoundaryStateRuntime.set(entry, 105100400, 10, -10));
    }
}
