package server.bots;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.dialogue.AgentChatEquipmentFlow;
import server.agents.integration.AgentBotEquipmentReplyRuntime;
import server.agents.integration.AgentBotEquipmentRuntime;
import server.agents.integration.AgentBotEquipmentSchedulerRuntime;
import server.agents.integration.AgentBotReplyRuntime;
import server.agents.integration.AgentBotSchedulerRuntime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

class AgentBotEquipmentRuntimeTest {
    @Test
    void equipmentVisibleReplyDelegatesToEquipmentReplyAdapter() {
        try (MockedStatic<AgentBotEquipmentReplyRuntime> replies =
                     mockStatic(AgentBotEquipmentReplyRuntime.class)) {
            AgentBotEquipmentRuntime.sayMapNow(null, "gear");

            replies.verify(() -> AgentBotEquipmentReplyRuntime.sayMapNow(null, "gear"));
        }
    }

    @Test
    void equipmentCallbacksScheduleLegacyEquipmentSideEffects() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentChatEquipmentFlow.EquipmentCallbacks callbacks = AgentBotEquipmentRuntime.equipmentCallbacks(entry);

        try (MockedStatic<AgentBotEquipmentSchedulerRuntime> scheduler =
                     mockStatic(AgentBotEquipmentSchedulerRuntime.class)) {
            assertTrue(callbacks.unequipSlot("hat"));
            callbacks.unequipAll();
            callbacks.autoEquipDebug();
            callbacks.autoEquip();

            scheduler.verify(() -> AgentBotEquipmentSchedulerRuntime.afterRandomDelay(eq(500), eq(700), any(Runnable.class)),
                    times(2));
            scheduler.verify(() -> AgentBotEquipmentSchedulerRuntime.afterRandomDelay(eq(400), eq(600), any(Runnable.class)),
                    times(2));
        }
    }

    @Test
    void unknownUnequipSlotDoesNotScheduleWork() {
        BotEntry entry = new BotEntry(null, null, null);
        AgentChatEquipmentFlow.EquipmentCallbacks callbacks = AgentBotEquipmentRuntime.equipmentCallbacks(entry);

        try (MockedStatic<AgentBotEquipmentSchedulerRuntime> scheduler =
                     mockStatic(AgentBotEquipmentSchedulerRuntime.class)) {
            assertFalse(callbacks.unequipSlot("not-a-slot"));

            scheduler.verifyNoInteractions();
        }
    }

    @Test
    void equipmentReplyAdapterDelegatesToBroadReplyRuntime() {
        BotEntry entry = new BotEntry(null, null, null);

        try (MockedStatic<AgentBotReplyRuntime> replies = mockStatic(AgentBotReplyRuntime.class)) {
            AgentBotEquipmentReplyRuntime.sayMapNow(null, "map");
            AgentBotEquipmentReplyRuntime.replyNow(entry, "owner");

            replies.verify(() -> AgentBotReplyRuntime.sayMapNow(null, "map"));
            replies.verify(() -> AgentBotReplyRuntime.replyNow(entry, "owner"));
        }
    }

    @Test
    void equipmentSchedulerAdapterDelegatesToBroadSchedulerRuntime() {
        Runnable action = () -> {
        };

        try (MockedStatic<AgentBotSchedulerRuntime> scheduler = mockStatic(AgentBotSchedulerRuntime.class)) {
            AgentBotEquipmentSchedulerRuntime.afterRandomDelay(400, 600, action);

            scheduler.verify(() -> AgentBotSchedulerRuntime.afterRandomDelay(400, 600, action));
        }
    }
}
