package server.bots;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatStatusRuntime;
import server.agents.capabilities.dialogue.AgentChatWelcomeBackFlow;
import server.agents.integration.AgentBotActivityStateRuntime;
import server.agents.integration.AgentBotOfferStateRuntime;
import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;
import server.agents.integration.AgentBotStatusRuntime;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotStatusRuntimeTest {
    @Test
    void statusStateAdaptsBotEntryAfkFields() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentChatStatusRuntime.StatusState state = AgentBotStatusRuntime.statusState(entry);
        Point position = new Point(10, 20);

        state.setOwnerAfkPosition(position);
        state.setOwnerAfkSinceMs(1234L);
        state.setOwnerWasAfk(true);

        assertEquals(position, AgentBotActivityStateRuntime.ownerAfkPosition(entry));
        assertEquals(1234L, AgentBotActivityStateRuntime.ownerAfkSinceMs(entry));
        assertTrue(state.ownerWasAfk());
        assertTrue(AgentBotActivityStateRuntime.ownerWasAfk(entry));
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
    void activityStateAdapterBacksAfkFields() {
        BotEntry entry = new BotEntry(null, null, null);
        Point position = new Point(50, 60);

        AgentBotActivityStateRuntime.setOwnerAfkPosition(entry, position);
        AgentBotActivityStateRuntime.setOwnerAfkSinceMs(entry, 4321L);
        AgentBotActivityStateRuntime.setOwnerWasAfk(entry, true);

        assertEquals(position, AgentBotActivityStateRuntime.ownerAfkPosition(entry));
        assertEquals(4321L, AgentBotActivityStateRuntime.ownerAfkSinceMs(entry));
        assertTrue(AgentBotActivityStateRuntime.ownerWasAfk(entry));
    }

    @Test
    void statusCheckStateAdaptsSpawnUpgradeFlag() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentChatStatusRuntime.StatusCheckState state = AgentBotStatusRuntime.statusCheckState(entry);

        assertFalse(state.spawnUpgradeCheckDone());

        state.setSpawnUpgradeCheckDone(true);

        assertTrue(AgentBotOfferStateRuntime.spawnUpgradeCheckDone(entry));
        assertTrue(state.spawnUpgradeCheckDone());
    }

    @Test
    void gearSuggestionStateAdaptsCooldown() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentChatStatusRuntime.GearSuggestionState state = AgentBotStatusRuntime.gearSuggestionState(entry);

        state.setNextGearSuggestionAt(9000L);

        assertEquals(9000L, AgentBotOfferStateRuntime.nextGearSuggestionAt(entry));
        assertEquals(9000L, state.nextGearSuggestionAt());
    }

    @Test
    void recommendedGearReportStateAdaptsCooldown() {
        BotEntry entry = new BotEntry(null, null, null);

        AgentBotStatusRuntime.recommendedGearReportState(entry).setNextGearSuggestionAt(12_000L);

        assertEquals(12_000L, AgentBotOfferStateRuntime.nextGearSuggestionAt(entry));
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

    @Test
    void offlineReturnActionsUseReplyAndSchedulerRuntimes() {
        Character bot = mock(Character.class);
        Runnable action = () -> {
        };

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler =
                     mockStatic(AgentBotSchedulerRuntime.class);
             MockedStatic<AgentBotReplyRuntime> replies =
                     mockStatic(AgentBotReplyRuntime.class)) {
            AgentChatStatusRuntime.OfflineReturnActions actions =
                    AgentBotStatusRuntime.offlineReturnActions(bot);

            actions.afterRandomDelay(900, 1100, action);
            actions.sayParty("wb");

            scheduler.verify(() -> AgentBotSchedulerRuntime.afterRandomDelay(900, 1100, action));
            replies.verify(() -> AgentBotReplyRuntime.sayPartyNow(bot, "wb"));
        }
    }

    @Test
    void afkReturnActionsUseReplyAndSchedulerRuntimes() {
        BotEntry entry = new BotEntry(mock(Character.class), null, null);
        Runnable action = () -> {
        };

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler =
                     mockStatic(AgentBotSchedulerRuntime.class);
             MockedStatic<AgentBotReplyRuntime> replies =
                     mockStatic(AgentBotReplyRuntime.class)) {
            AgentChatStatusRuntime.AfkReturnActions actions = AgentBotStatusRuntime.afkReturnActions(entry);

            actions.afterRandomDelay(700, 900, action);
            actions.reply("back");

            scheduler.verify(() -> AgentBotSchedulerRuntime.afterRandomDelay(700, 900, action));
            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, "back"));
        }
    }
}
