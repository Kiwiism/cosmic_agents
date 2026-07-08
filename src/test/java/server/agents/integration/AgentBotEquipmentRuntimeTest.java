package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatEquipmentFlow;
import server.agents.integration.AgentBotEquipmentRuntime;
import server.agents.integration.AgentReplyRuntime;
import server.agents.integration.AgentSchedulerRuntime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

class AgentBotEquipmentRuntimeTest {
    @Test
    void equipmentVisibleReplyDelegatesToAgentReplyRuntime() {
        try (MockedStatic<AgentReplyRuntime> replies = mockStatic(AgentReplyRuntime.class)) {
            AgentBotEquipmentRuntime.sayMapNow(null, "gear");

            replies.verify(() -> AgentReplyRuntime.sayMapNow(null, "gear"));
        }
    }

    @Test
    void equipmentCallbacksScheduleLegacyEquipmentSideEffects() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentChatEquipmentFlow.EquipmentCallbacks callbacks = AgentBotEquipmentRuntime.equipmentCallbacks(entry);

        try (MockedStatic<AgentSchedulerRuntime> scheduler = mockStatic(AgentSchedulerRuntime.class)) {
            assertTrue(callbacks.unequipSlot("hat"));
            callbacks.unequipAll();
            callbacks.autoEquipDebug();
            callbacks.autoEquip();

            scheduler.verify(() -> AgentSchedulerRuntime.afterRandomDelay(eq(500), eq(700), any(Runnable.class)),
                    times(2));
            scheduler.verify(() -> AgentSchedulerRuntime.afterRandomDelay(eq(400), eq(600), any(Runnable.class)),
                    times(2));
        }
    }

    @Test
    void unknownUnequipSlotDoesNotScheduleWork() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentChatEquipmentFlow.EquipmentCallbacks callbacks = AgentBotEquipmentRuntime.equipmentCallbacks(entry);

        try (MockedStatic<AgentSchedulerRuntime> scheduler = mockStatic(AgentSchedulerRuntime.class)) {
            assertFalse(callbacks.unequipSlot("not-a-slot"));

            scheduler.verifyNoInteractions();
        }
    }
}
