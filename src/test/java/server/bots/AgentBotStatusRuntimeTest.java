package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.dialogue.AgentChatStatusRuntime;
import server.agents.capabilities.dialogue.AgentChatWelcomeBackFlow;
import server.agents.integration.AgentBotStatusRuntime;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotStatusRuntimeTest {
    @Test
    void statusStateAdaptsBotEntryAfkFields() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentChatStatusRuntime.StatusState state = AgentBotStatusRuntime.statusState(entry);
        Point position = new Point(10, 20);

        state.setOwnerAfkPosition(position);
        state.setOwnerAfkSinceMs(1234L);
        state.setOwnerWasAfk(true);

        assertEquals(position, entry.ownerAfkPosition());
        assertEquals(1234L, entry.ownerAfkSinceMs());
        assertTrue(state.ownerWasAfk());
        assertTrue(entry.ownerWasAfk());
    }

    @Test
    void afkStateAdaptsBotEntryAfkFields() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentChatWelcomeBackFlow.AfkState state = AgentBotStatusRuntime.afkState(entry);
        Point position = new Point(30, 40);

        state.setOwnerAfkPosition(position);
        state.setOwnerAfkSinceMs(5678L);
        state.setOwnerWasAfk(true);

        assertEquals(position, state.ownerAfkPosition());
        assertEquals(5678L, state.ownerAfkSinceMs());
        assertTrue(state.ownerWasAfk());
    }

    @Test
    void statusCheckStateAdaptsSpawnUpgradeFlag() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentChatStatusRuntime.StatusCheckState state = AgentBotStatusRuntime.statusCheckState(entry);

        assertFalse(state.spawnUpgradeCheckDone());

        state.setSpawnUpgradeCheckDone(true);

        assertTrue(entry.spawnUpgradeCheckDone());
        assertTrue(state.spawnUpgradeCheckDone());
    }

    @Test
    void gearSuggestionStateAdaptsCooldown() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentChatStatusRuntime.GearSuggestionState state = AgentBotStatusRuntime.gearSuggestionState(entry);

        state.setNextGearSuggestionAt(9000L);

        assertEquals(9000L, entry.nextGearSuggestionAt());
        assertEquals(9000L, state.nextGearSuggestionAt());
    }

    @Test
    void recommendedGearReportStateAdaptsCooldown() {
        BotEntry entry = new BotEntry(null, null, null);

        AgentBotStatusRuntime.recommendedGearReportState(entry).setNextGearSuggestionAt(12_000L);

        assertEquals(12_000L, entry.nextGearSuggestionAt());
    }

    @Test
    void offlineReturnActionsTreatNullAgentAsUnavailable() {
        assertFalse(AgentBotStatusRuntime.offlineReturnActions(null).hasAgent());
    }

    @Test
    void afkReturnActionsTreatNullAgentAsUnavailable() {
        BotEntry entry = new BotEntry(null, null, null);

        assertFalse(AgentBotStatusRuntime.afkReturnActions(entry).hasAgent());
    }
}
