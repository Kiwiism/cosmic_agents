package server.bots;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatEquipmentFlow;
import server.agents.integration.AgentBotEquipmentRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

class AgentBotEquipmentRuntimeTest {
    @Test
    void equipmentCallbacksScheduleLegacyEquipmentSideEffects() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentChatEquipmentFlow.EquipmentCallbacks callbacks = AgentBotEquipmentRuntime.equipmentCallbacks(entry);

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class)) {
            assertTrue(callbacks.unequipSlot("hat"));
            callbacks.unequipAll();
            callbacks.autoEquipDebug();
            callbacks.autoEquip();

            scheduler.verify(() -> AgentBotSchedulerRuntime.afterRandomDelay(eq(500), eq(700), any(Runnable.class)),
                    times(2));
            scheduler.verify(() -> AgentBotSchedulerRuntime.afterRandomDelay(eq(400), eq(600), any(Runnable.class)),
                    times(2));
        }
    }

    @Test
    void unknownUnequipSlotDoesNotScheduleWork() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentChatEquipmentFlow.EquipmentCallbacks callbacks = AgentBotEquipmentRuntime.equipmentCallbacks(entry);

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class)) {
            assertFalse(callbacks.unequipSlot("not-a-slot"));

            scheduler.verifyNoInteractions();
        }
    }
}
