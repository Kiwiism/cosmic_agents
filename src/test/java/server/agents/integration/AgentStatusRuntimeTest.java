package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatStatusRuntime;
import server.agents.capabilities.dialogue.AgentChatWelcomeBackFlow;
import server.agents.runtime.AgentActivityStateRuntime;
import server.agents.capabilities.trade.AgentOfferStateRuntime;
import server.agents.integration.AgentReplyRuntime;
import server.agents.runtime.AgentSchedulerRuntime;
import server.agents.integration.AgentStatusRuntime;
import server.agents.runtime.AgentStatusStateRuntime;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentStatusRuntimeTest {
    @Test
    void statusStateAdaptsAgentRuntimeEntryAfkFields() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentChatStatusRuntime.StatusState state = AgentStatusStateRuntime.statusState(entry);
        Point position = new Point(10, 20);

        state.setOwnerAfkPosition(position);
        state.setOwnerAfkSinceMs(1234L);
        state.setOwnerWasAfk(true);

        assertEquals(position, AgentActivityStateRuntime.ownerAfkPosition(entry));
        assertEquals(1234L, AgentActivityStateRuntime.ownerAfkSinceMs(entry));
        assertTrue(state.ownerWasAfk());
        assertTrue(AgentActivityStateRuntime.ownerWasAfk(entry));
    }

    @Test
    void afkStateAdaptsAgentRuntimeEntryAfkFields() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentChatWelcomeBackFlow.AfkState state = AgentStatusStateRuntime.afkState(entry);
        Point position = new Point(30, 40);

        state.setOwnerAfkPosition(position);
        state.setOwnerAfkSinceMs(5678L);
        state.setOwnerWasAfk(true);

        assertEquals(position, state.ownerAfkPosition());
        assertEquals(5678L, state.ownerAfkSinceMs());
        assertTrue(state.ownerWasAfk());
    }

    @Test
    void activityStateAdapterBacksAfkFields() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Point position = new Point(50, 60);

        AgentActivityStateRuntime.setOwnerAfkPosition(entry, position);
        AgentActivityStateRuntime.setOwnerAfkSinceMs(entry, 4321L);
        AgentActivityStateRuntime.setOwnerWasAfk(entry, true);

        assertEquals(position, AgentActivityStateRuntime.ownerAfkPosition(entry));
        assertEquals(4321L, AgentActivityStateRuntime.ownerAfkSinceMs(entry));
        assertTrue(AgentActivityStateRuntime.ownerWasAfk(entry));
    }

    @Test
    void statusCheckStateAdaptsSpawnUpgradeFlag() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentChatStatusRuntime.StatusCheckState state = AgentStatusStateRuntime.statusCheckState(entry);

        assertFalse(state.spawnUpgradeCheckDone());

        state.setSpawnUpgradeCheckDone(true);

        assertTrue(AgentOfferStateRuntime.spawnUpgradeCheckDone(entry));
        assertTrue(state.spawnUpgradeCheckDone());
    }

    @Test
    void gearSuggestionStateAdaptsCooldown() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentChatStatusRuntime.GearSuggestionState state = AgentStatusStateRuntime.gearSuggestionState(entry);

        state.setNextGearSuggestionAt(9000L);

        assertEquals(9000L, AgentOfferStateRuntime.nextGearSuggestionAt(entry));
        assertEquals(9000L, state.nextGearSuggestionAt());
    }

    @Test
    void recommendedGearReportStateAdaptsCooldown() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        AgentStatusStateRuntime.recommendedGearReportState(entry).setNextGearSuggestionAt(12_000L);

        assertEquals(12_000L, AgentOfferStateRuntime.nextGearSuggestionAt(entry));
    }

    @Test
    void offlineReturnActionsTreatNullAgentAsUnavailable() {
        assertFalse(AgentStatusRuntime.offlineReturnActions(null).hasAgent());
    }

    @Test
    void afkReturnActionsTreatNullAgentAsUnavailable() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentStatusRuntime.afkReturnActions(entry).hasAgent());
    }

    @Test
    void offlineReturnActionsUseReplyAndSchedulerRuntimes() {
        Character bot = mock(Character.class);
        Runnable action = () -> {
        };

        try (MockedStatic<AgentSchedulerRuntime> scheduler =
                     mockStatic(AgentSchedulerRuntime.class);
             MockedStatic<AgentReplyRuntime> replies =
                     mockStatic(AgentReplyRuntime.class)) {
            AgentChatStatusRuntime.OfflineReturnActions actions =
                    AgentStatusRuntime.offlineReturnActions(bot);

            actions.afterRandomDelay(900, 1100, action);
            actions.sayParty("wb");

            scheduler.verify(() -> AgentSchedulerRuntime.afterRandomDelay(900, 1100, action));
            replies.verify(() -> AgentReplyRuntime.sayPartyNow(bot, "wb"));
        }
    }

    @Test
    void afkReturnActionsUseReplyAndSchedulerRuntimes() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(mock(Character.class), null, null);
        Runnable action = () -> {
        };

        try (MockedStatic<AgentSchedulerRuntime> scheduler =
                     mockStatic(AgentSchedulerRuntime.class);
             MockedStatic<AgentReplyRuntime> replies =
                     mockStatic(AgentReplyRuntime.class)) {
            AgentChatStatusRuntime.AfkReturnActions actions = AgentStatusRuntime.afkReturnActions(entry);

            actions.afterRandomDelay(700, 900, action);
            actions.reply("back");

            scheduler.verify(() -> AgentSchedulerRuntime.afterRandomDelay(700, 900, action));
            replies.verify(() -> AgentReplyRuntime.replyNow(entry, "back"));
        }
    }
}
