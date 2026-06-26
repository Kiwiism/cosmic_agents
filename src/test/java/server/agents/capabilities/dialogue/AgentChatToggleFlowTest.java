package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentChatToggleFlowTest {
    @Test
    void shouldRouteSupportAndHealToggles() {
        TestCallbacks callbacks = new TestCallbacks();

        assertTrue(AgentChatToggleFlow.handle("support off", callbacks));
        assertTrue(AgentChatToggleFlow.handle("support on", callbacks));
        assertTrue(AgentChatToggleFlow.handle("heals off", callbacks));
        assertTrue(AgentChatToggleFlow.handle("heals on", callbacks));

        assertEquals("support:false;support:true;heals:false;heals:true;", callbacks.events);
    }

    @Test
    void shouldRouteBuffConsumableToggles() {
        TestCallbacks callbacks = new TestCallbacks();

        assertTrue(AgentChatToggleFlow.handle("no buff pots", callbacks));
        assertTrue(AgentChatToggleFlow.handle("auto buff pot", callbacks));
        assertTrue(AgentChatToggleFlow.handle("buff cheap", callbacks));
        assertTrue(AgentChatToggleFlow.handle("buff pots max", callbacks));

        assertEquals("buff:false;buff:true;cheap:true;cheap:false;", callbacks.events);
    }

    @Test
    void shouldRouteProactiveOfferToggles() {
        TestCallbacks callbacks = new TestCallbacks();

        assertTrue(AgentChatToggleFlow.handle("proactive offers off", callbacks));
        assertTrue(AgentChatToggleFlow.handle("proactive offers on", callbacks));

        assertEquals("offers:false;offers:true;", callbacks.events);
    }

    @Test
    void shouldIgnoreNonToggleCommands() {
        TestCallbacks callbacks = new TestCallbacks();

        assertFalse(AgentChatToggleFlow.handle("follow me", callbacks));

        assertEquals("", callbacks.events);
    }

    private static final class TestCallbacks implements AgentChatToggleFlow.ToggleCallbacks {
        private String events = "";

        @Override
        public void setSupport(boolean enabled) {
            events += "support:" + enabled + ";";
        }

        @Override
        public void setHeals(boolean enabled) {
            events += "heals:" + enabled + ";";
        }

        @Override
        public void setBuffConsumables(boolean enabled) {
            events += "buff:" + enabled + ";";
        }

        @Override
        public void setBuffConsumablesCheapMode(boolean cheapMode) {
            events += "cheap:" + cheapMode + ";";
        }

        @Override
        public void setProactiveOffers(boolean enabled) {
            events += "offers:" + enabled + ";";
        }
    }
}
