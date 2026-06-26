package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentChatEquipmentFlowTest {
    @Test
    void shouldRouteEquipmentCommands() {
        TestCallbacks callbacks = new TestCallbacks(true);

        assertTrue(AgentChatEquipmentFlow.handle("unequip hat", callbacks));
        assertTrue(AgentChatEquipmentFlow.handle("unequip all", callbacks));
        assertTrue(AgentChatEquipmentFlow.handle("autoequip debug", callbacks));
        assertTrue(AgentChatEquipmentFlow.handle("autoequip", callbacks));

        assertEquals("slot:hat;all;debug;auto;", callbacks.events);
    }

    @Test
    void shouldNotClaimRejectedSlotCommand() {
        TestCallbacks callbacks = new TestCallbacks(false);

        assertFalse(AgentChatEquipmentFlow.handle("unequip hat", callbacks));

        assertEquals("slot:hat;", callbacks.events);
    }

    @Test
    void shouldIgnoreNonEquipmentCommands() {
        TestCallbacks callbacks = new TestCallbacks(true);

        assertFalse(AgentChatEquipmentFlow.handle("follow me", callbacks));

        assertEquals("", callbacks.events);
    }

    private static final class TestCallbacks implements AgentChatEquipmentFlow.EquipmentCallbacks {
        private final boolean acceptSlot;
        private String events = "";

        private TestCallbacks(boolean acceptSlot) {
            this.acceptSlot = acceptSlot;
        }

        @Override
        public boolean unequipSlot(String slotName) {
            events += "slot:" + slotName + ";";
            return acceptSlot;
        }

        @Override
        public void unequipAll() {
            events += "all;";
        }

        @Override
        public void autoEquipDebug() {
            events += "debug;";
        }

        @Override
        public void autoEquip() {
            events += "auto;";
        }
    }
}
