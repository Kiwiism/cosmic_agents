package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentChatSupplyRequestFlowTest {
    @Test
    void shouldRouteSupplyRequests() {
        TestCallbacks callbacks = new TestCallbacks();

        assertTrue(AgentChatSupplyRequestFlow.handle("need hp pots", callbacks));
        assertTrue(AgentChatSupplyRequestFlow.handle("need mp pots", callbacks));
        assertTrue(AgentChatSupplyRequestFlow.handle("need pots", callbacks));
        assertTrue(AgentChatSupplyRequestFlow.handle("need ammo", callbacks));

        assertEquals("pot:true;pot:false;any;ammo;", callbacks.events);
    }

    @Test
    void shouldIgnoreNonSupplyRequests() {
        TestCallbacks callbacks = new TestCallbacks();

        assertFalse(AgentChatSupplyRequestFlow.handle("follow me", callbacks));

        assertEquals("", callbacks.events);
    }

    private static final class TestCallbacks implements AgentChatSupplyRequestFlow.SupplyRequestCallbacks {
        private String events = "";

        @Override
        public void requestPotion(boolean hpPotion) {
            events += "pot:" + hpPotion + ";";
        }

        @Override
        public void requestAnyPotion() {
            events += "any;";
        }

        @Override
        public void requestAmmo() {
            events += "ammo;";
        }
    }
}
