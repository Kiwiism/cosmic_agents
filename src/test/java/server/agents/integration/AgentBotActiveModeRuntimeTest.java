package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import server.agents.capabilities.trade.AgentOfferService;

import server.agents.capabilities.supplies.AgentPotionService;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatStatusRuntime;
import server.agents.capabilities.equipment.AgentEquipmentService;
import server.agents.integration.AgentBotActiveModeRuntime;
import server.agents.integration.AgentBotOfferStateRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

class AgentBotActiveModeRuntimeTest {
    @Test
    void activeModeActionsDelegateLegacySideEffects() {
        Character bot = mock(Character.class);
        Character owner = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, owner, null);
        AgentBotOfferStateRuntime.setNextGearSuggestionAt(entry, 99L);
        AgentChatStatusRuntime.ActiveModeActions actions = AgentBotActiveModeRuntime.activeModeActions(entry);

        try (MockedStatic<AgentEquipmentService> equips = mockStatic(AgentEquipmentService.class);
             MockedStatic<AgentPotionService> potions = mockStatic(AgentPotionService.class);
             MockedStatic<AgentOfferService> offers = mockStatic(AgentOfferService.class)) {
            offers.when(() -> AgentOfferService.offerBestGearToSibling(entry, bot)).thenReturn(true);

            actions.autoEquip();
            actions.resetGearSuggestionCooldown();
            actions.maybeSuggestGearToSiblings();
            actions.setupAutopot();
            actions.checkPotShareOnModeStart();

            equips.verify(() -> AgentEquipmentService.autoEquip(bot, owner, null));
            assertTrue(AgentBotOfferStateRuntime.nextGearSuggestionAt(entry) > System.currentTimeMillis());
            offers.verify(() -> AgentOfferService.offerBestGearToSibling(entry, bot));
            potions.verify(() -> AgentPotionService.setupAutopotForBot(bot));
            potions.verify(() -> AgentPotionService.checkPotShareOnModeStart(entry, bot));
        }
    }

    @Test
    void autoEquipAndSuggestGearToSiblingsMatchesSharedFollowStopPreparation() {
        Character bot = mock(Character.class);
        Character owner = mock(Character.class);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, owner, null);
        AgentBotOfferStateRuntime.setNextGearSuggestionAt(entry, 44L);

        try (MockedStatic<AgentEquipmentService> equips = mockStatic(AgentEquipmentService.class);
             MockedStatic<AgentOfferService> offers = mockStatic(AgentOfferService.class)) {
            offers.when(() -> AgentOfferService.offerBestGearToSibling(entry, bot)).thenReturn(false);

            AgentBotActiveModeRuntime.autoEquipAndSuggestGearToSiblings(entry);

            equips.verify(() -> AgentEquipmentService.autoEquip(bot, owner, null));
            offers.verify(() -> AgentOfferService.offerBestGearToSibling(entry, bot));
            assertEquals(0L, AgentBotOfferStateRuntime.nextGearSuggestionAt(entry));
        }
    }
}
