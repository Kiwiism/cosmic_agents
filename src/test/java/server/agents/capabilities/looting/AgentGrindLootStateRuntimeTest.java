package server.agents.capabilities.looting;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.maps.MapItem;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentGrindLootStateRuntimeTest {
    @Test
    void adaptsCachedGrindLootTarget() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        MapItem loot = mock(MapItem.class);

        assertFalse(AgentGrindLootStateRuntime.hasGrindLootTarget(entry));

        AgentGrindLootStateRuntime.setGrindLootTarget(entry, loot);

        assertTrue(AgentGrindLootStateRuntime.hasGrindLootTarget(entry));
        assertSame(loot, AgentGrindLootStateRuntime.grindLootTarget(entry));

        AgentGrindLootStateRuntime.clearGrindLootTarget(entry);

        assertFalse(AgentGrindLootStateRuntime.hasGrindLootTarget(entry));
        assertNull(AgentGrindLootStateRuntime.grindLootTarget(entry));
    }

    @Test
    void adaptsRetrySuppressionAndClearsAfterExpiry() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        MapItem loot = mock(MapItem.class);
        when(loot.getObjectId()).thenReturn(123);

        AgentGrindLootStateRuntime.suppressRetry(entry, loot, 2_000L);

        assertTrue(AgentGrindLootStateRuntime.isRetrySuppressed(entry, loot, 1_999L));
        assertFalse(AgentGrindLootStateRuntime.isRetrySuppressed(entry, loot, 2_000L));
        assertFalse(AgentGrindLootStateRuntime.isRetrySuppressed(entry, loot, 2_001L));
    }
}
