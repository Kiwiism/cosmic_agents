package server.agents.capabilities.partyquest;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotPqRuntime;
import server.bots.BotEntry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentPartyQuestHooksTest {
    @Test
    void requiresGrindOnlyDuringKpqStageOneGrinding() {
        BotEntry entry = new BotEntry(null, null, null);
        Character bot = mock(Character.class);
        when(bot.getMapId()).thenReturn(103000800);

        assertFalse(AgentPartyQuestHooks.requiresGrind(entry, bot));

        AgentBotPqRuntime.setKpqStageState(entry, 3);

        assertTrue(AgentPartyQuestHooks.requiresGrind(entry, bot));
    }

    @Test
    void requiresFollowInLaterKpqStageMaps() {
        BotEntry entry = new BotEntry(null, null, null);
        Character bot = mock(Character.class);

        when(bot.getMapId()).thenReturn(103000801);
        assertTrue(AgentPartyQuestHooks.requiresFollow(entry, bot));

        when(bot.getMapId()).thenReturn(103000806);
        assertFalse(AgentPartyQuestHooks.requiresFollow(entry, bot));
    }

    @Test
    void skipsCouponLootAfterStageOneGrinding() {
        BotEntry entry = new BotEntry(null, null, null);

        assertFalse(AgentPartyQuestHooks.shouldSkipCouponLoot(entry));

        AgentBotPqRuntime.setKpqStageState(entry, 4);

        assertTrue(AgentPartyQuestHooks.shouldSkipCouponLoot(entry));
    }
}
