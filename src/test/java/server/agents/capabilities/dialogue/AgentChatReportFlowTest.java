package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentChatReportFlowTest {
    @Test
    void shouldStopAfterTerminalInfoCommands() {
        TestCallbacks callbacks = new TestCallbacks();

        assertTrue(AgentChatReportFlow.handle("help", callbacks));
        assertTrue(AgentChatReportFlow.handle("do you need anything", callbacks));
        assertTrue(AgentChatReportFlow.handle("recommended gear", callbacks));
        assertTrue(AgentChatReportFlow.handle("skills", callbacks));

        assertEquals("help;request;gear;skills;", callbacks.events);
    }

    @Test
    void shouldRouteNonTerminalReportQueriesWithoutStopping() {
        TestCallbacks callbacks = new TestCallbacks();

        assertFalse(AgentChatReportFlow.handle("stats", callbacks));
        assertFalse(AgentChatReportFlow.handle("movement", callbacks));
        assertFalse(AgentChatReportFlow.handle("range", callbacks));
        assertFalse(AgentChatReportFlow.handle("build", callbacks));
        assertFalse(AgentChatReportFlow.handle("inventory", callbacks));
        assertFalse(AgentChatReportFlow.handle("mesos", callbacks));
        assertFalse(AgentChatReportFlow.handle("exp", callbacks));
        assertFalse(AgentChatReportFlow.handle("slots", callbacks));
        assertFalse(AgentChatReportFlow.handle("scrolls", callbacks));
        assertFalse(AgentChatReportFlow.handle("potions", callbacks));
        assertFalse(AgentChatReportFlow.handle("debug stats", callbacks));
        assertFalse(AgentChatReportFlow.handle("crit debug", callbacks));
        assertFalse(AgentChatReportFlow.handle("pot debug", callbacks));

        assertEquals("stats;move;range;build;inv;mesos;exp;slots;scrolls;pots;debug;crit;potdebug;",
                callbacks.events);
    }

    @Test
    void shouldIgnoreUnmatchedText() {
        TestCallbacks callbacks = new TestCallbacks();

        assertFalse(AgentChatReportFlow.handle("follow me", callbacks));

        assertEquals("", callbacks.events);
    }

    @Test
    void shouldExposeLegacyHelpLines() {
        assertEquals(AgentDialogueCatalog.helpLines(), AgentChatReportFlow.helpLines());
    }

    private static final class TestCallbacks implements AgentChatReportFlow.ReportCallbacks {
        private String events = "";

        @Override
        public void help() {
            events += "help;";
        }

        @Override
        public void requestUpgrade() {
            events += "request;";
        }

        @Override
        public void recommendedGear() {
            events += "gear;";
        }

        @Override
        public void skills() {
            events += "skills;";
        }

        @Override
        public void stats() {
            events += "stats;";
        }

        @Override
        public void movementStats() {
            events += "move;";
        }

        @Override
        public void range() {
            events += "range;";
        }

        @Override
        public void build() {
            events += "build;";
        }

        @Override
        public void inventory() {
            events += "inv;";
        }

        @Override
        public void mesos() {
            events += "mesos;";
        }

        @Override
        public void exp() {
            events += "exp;";
        }

        @Override
        public void inventorySlots() {
            events += "slots;";
        }

        @Override
        public void scrolls() {
            events += "scrolls;";
        }

        @Override
        public void potions() {
            events += "pots;";
        }

        @Override
        public void debugStats() {
            events += "debug;";
        }

        @Override
        public void critDebug() {
            events += "crit;";
        }

        @Override
        public void potDebug() {
            events += "potdebug;";
        }
    }
}
