package server.bots;

import server.agents.capabilities.supplies.AgentPotionService;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatStatusRuntime;
import server.agents.integration.AgentBotActiveModeRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotActiveModeRuntimeTest {
    @Test
    void activeModeActionsDelegateLegacySideEffects() {
        Character bot = mock(Character.class);
        Character owner = mock(Character.class);
        BotEntry entry = new BotEntry(bot, owner, null);
        entry.setNextGearSuggestionAt(99L);
        AgentChatStatusRuntime.ActiveModeActions actions = AgentBotActiveModeRuntime.activeModeActions(entry);

        try (MockedStatic<BotEquipManager> equips = mockStatic(BotEquipManager.class);
             MockedStatic<AgentPotionService> potions = mockStatic(AgentPotionService.class);
             MockedStatic<BotOfferManager> offers = mockStatic(BotOfferManager.class)) {
            offers.when(() -> BotOfferManager.offerBestGearToSibling(entry, bot)).thenReturn(true);

            actions.autoEquip();
            actions.resetGearSuggestionCooldown();
            actions.maybeSuggestGearToSiblings();
            actions.setupAutopot();
            actions.checkPotShareOnModeStart();

            equips.verify(() -> BotEquipManager.autoEquip(bot, owner, null));
            assertTrue(entry.nextGearSuggestionAt() > System.currentTimeMillis());
            offers.verify(() -> BotOfferManager.offerBestGearToSibling(entry, bot));
            potions.verify(() -> AgentPotionService.setupAutopotForBot(bot));
            potions.verify(() -> AgentPotionService.checkPotShareOnModeStart(entry, bot));
        }
    }

    @Test
    void autoEquipAndSuggestGearToSiblingsMatchesSharedFollowStopPreparation() {
        Character bot = mock(Character.class);
        Character owner = mock(Character.class);
        BotEntry entry = new BotEntry(bot, owner, null);
        entry.setNextGearSuggestionAt(44L);

        try (MockedStatic<BotEquipManager> equips = mockStatic(BotEquipManager.class);
             MockedStatic<BotOfferManager> offers = mockStatic(BotOfferManager.class)) {
            offers.when(() -> BotOfferManager.offerBestGearToSibling(entry, bot)).thenReturn(false);

            AgentBotActiveModeRuntime.autoEquipAndSuggestGearToSiblings(entry);

            equips.verify(() -> BotEquipManager.autoEquip(bot, owner, null));
            offers.verify(() -> BotOfferManager.offerBestGearToSibling(entry, bot));
            assertEquals(0L, entry.nextGearSuggestionAt());
        }
    }
}
