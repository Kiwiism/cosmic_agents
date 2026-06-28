package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotGrindLootStateRuntime;
import server.maps.MapItem;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentBotGrindLootStateRuntimeTest {
    @Test
    void adaptsCachedGrindLootTarget() {
        BotEntry entry = new BotEntry(null, null, null);
        MapItem loot = mock(MapItem.class);

        assertFalse(AgentBotGrindLootStateRuntime.hasGrindLootTarget(entry));

        AgentBotGrindLootStateRuntime.setGrindLootTarget(entry, loot);

        assertTrue(AgentBotGrindLootStateRuntime.hasGrindLootTarget(entry));
        assertSame(loot, AgentBotGrindLootStateRuntime.grindLootTarget(entry));

        AgentBotGrindLootStateRuntime.clearGrindLootTarget(entry);

        assertFalse(AgentBotGrindLootStateRuntime.hasGrindLootTarget(entry));
        assertNull(AgentBotGrindLootStateRuntime.grindLootTarget(entry));
    }

    @Test
    void adaptsRetrySuppressionAndClearsAfterExpiry() {
        BotEntry entry = new BotEntry(null, null, null);
        MapItem loot = mock(MapItem.class);
        when(loot.getObjectId()).thenReturn(123);

        AgentBotGrindLootStateRuntime.suppressRetry(entry, loot, 2_000L);

        assertTrue(AgentBotGrindLootStateRuntime.isRetrySuppressed(entry, loot, 1_999L));
        assertFalse(AgentBotGrindLootStateRuntime.isRetrySuppressed(entry, loot, 2_000L));
        assertFalse(AgentBotGrindLootStateRuntime.isRetrySuppressed(entry, loot, 2_001L));
    }
}
