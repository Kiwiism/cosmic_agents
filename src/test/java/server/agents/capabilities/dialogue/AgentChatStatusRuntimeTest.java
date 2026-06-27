package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentChatStatusRuntimeTest {
    @Test
    void markActiveClearsAfkAndCopiesOwnerPosition() {
        TestState state = new TestState();
        state.wasAfk = true;
        Point ownerPosition = new Point(10, 20);

        AgentChatStatusRuntime.markActive(state, ownerPosition, 1234L);

        assertFalse(state.wasAfk);
        assertEquals(1234L, state.sinceMs);
        assertEquals(ownerPosition, state.position);
        assertNotSame(ownerPosition, state.position);
    }

    @Test
    void markActiveAllowsMissingOwnerPosition() {
        TestState state = new TestState();

        AgentChatStatusRuntime.markActive(state, null, 500L);

        assertNull(state.position);
        assertEquals(500L, state.sinceMs);
    }

    @Test
    void ownerIdleMirrorsAfkFlag() {
        TestState state = new TestState();
        assertFalse(AgentChatStatusRuntime.isOwnerIdle(state));
        state.wasAfk = true;
        assertTrue(AgentChatStatusRuntime.isOwnerIdle(state));
    }

    @Test
    void randomFidgetExpressionUsesLegacyExpressionSet() {
        Set<Integer> allowed = Set.of(2, 3, 5, 6, 7);
        for (int i = 0; i < 100; i++) {
            assertTrue(allowed.contains(AgentChatStatusRuntime.randomFidgetExpression()));
        }
    }

    private static final class TestState implements AgentChatStatusRuntime.StatusState {
        private Point position;
        private long sinceMs;
        private boolean wasAfk;

        @Override
        public void setOwnerAfkPosition(Point position) {
            this.position = position;
        }

        @Override
        public void setOwnerAfkSinceMs(long sinceMs) {
            this.sinceMs = sinceMs;
        }

        @Override
        public boolean ownerWasAfk() {
            return wasAfk;
        }

        @Override
        public void setOwnerWasAfk(boolean wasAfk) {
            this.wasAfk = wasAfk;
        }
    }
}
