package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentChatMovementFlowTest {
    @Test
    void shouldRouteMovementAndGreetingCommands() {
        TestCallbacks callbacks = new TestCallbacks(true);

        assertTrue(AgentChatMovementFlow.handle("farm here", callbacks));
        assertTrue(AgentChatMovementFlow.handle("patrol", callbacks));
        assertTrue(AgentChatMovementFlow.handle("move here", callbacks));
        assertTrue(AgentChatMovementFlow.handle("follow me", callbacks));
        assertTrue(AgentChatMovementFlow.handle("grind", callbacks));
        assertTrue(AgentChatMovementFlow.handle("stop", callbacks));
        assertTrue(AgentChatMovementFlow.handle("fidget", callbacks));
        assertTrue(AgentChatMovementFlow.handle("hi", callbacks));

        assertEquals("farm;patrol;move;follow;grind;stop;fidget;greeting;", callbacks.events);
    }

    @Test
    void shouldPropagateRejectedPositionDependentCommands() {
        TestCallbacks callbacks = new TestCallbacks(false);

        assertFalse(AgentChatMovementFlow.handle("farm here", callbacks));
        assertFalse(AgentChatMovementFlow.handle("patrol", callbacks));
        assertFalse(AgentChatMovementFlow.handle("move here", callbacks));

        assertEquals("farm;patrol;move;", callbacks.events);
    }

    @Test
    void shouldIgnoreNonMovementCommands() {
        TestCallbacks callbacks = new TestCallbacks(true);

        assertFalse(AgentChatMovementFlow.handle("buff list", callbacks));

        assertEquals("", callbacks.events);
    }

    @Test
    void shouldSelectRepliesFromMovementDialoguePools() {
        assertTrue(AgentDialogueCatalog.moveHereReplies().contains(AgentChatMovementFlow.moveHereReply()));
        assertTrue(AgentDialogueCatalog.followReplies().contains(AgentChatMovementFlow.followReply()));
        assertTrue(AgentDialogueCatalog.stopReplies().contains(AgentChatMovementFlow.stopReply()));
        assertTrue(AgentDialogueCatalog.greetingReplies().contains(AgentChatMovementFlow.greetingReply()));
    }

    private static final class TestCallbacks implements AgentChatMovementFlow.MovementCallbacks {
        private final boolean acceptPositionCommand;
        private String events = "";

        private TestCallbacks(boolean acceptPositionCommand) {
            this.acceptPositionCommand = acceptPositionCommand;
        }

        @Override
        public boolean farmHere() {
            events += "farm;";
            return acceptPositionCommand;
        }

        @Override
        public boolean patrol() {
            events += "patrol;";
            return acceptPositionCommand;
        }

        @Override
        public boolean moveHere() {
            events += "move;";
            return acceptPositionCommand;
        }

        @Override
        public void follow() {
            events += "follow;";
        }

        @Override
        public void grind() {
            events += "grind;";
        }

        @Override
        public void stop() {
            events += "stop;";
        }

        @Override
        public void fidget() {
            events += "fidget;";
        }

        @Override
        public void greeting() {
            events += "greeting;";
        }
    }
}
