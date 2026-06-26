package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import java.awt.Point;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentChatWelcomeBackFlowTest {
    @Test
    void shouldInitializeOwnerAfkTracking() {
        TestState state = new TestState();
        Point position = new Point(10, 20);
        TestCallbacks callbacks = new TestCallbacks();

        AgentChatWelcomeBackFlow.tickAfkCheck(state, position, 1_000L, callbacks);

        assertSame(position, state.position);
        assertEquals(1_000L, state.sinceMs);
        assertFalse(state.wasAfk);
        assertEquals(0, callbacks.returnedCount);
    }

    @Test
    void shouldMarkOwnerAfkAfterFiveMinutesAtSamePosition() {
        TestState state = new TestState();
        state.position = new Point(10, 20);
        state.sinceMs = 1_000L;

        AgentChatWelcomeBackFlow.tickAfkCheck(state, new Point(10, 20), 301_000L, new TestCallbacks());

        assertTrue(state.wasAfk);
    }

    @Test
    void shouldEmitWelcomeBackWhenAfkOwnerMoves() {
        TestState state = new TestState();
        state.position = new Point(10, 20);
        state.sinceMs = 1_000L;
        state.wasAfk = true;
        Point newPosition = new Point(30, 40);
        TestCallbacks callbacks = new TestCallbacks();

        AgentChatWelcomeBackFlow.tickAfkCheck(state, newPosition, 302_000L, callbacks);

        assertFalse(state.wasAfk);
        assertSame(newPosition, state.position);
        assertEquals(302_000L, state.sinceMs);
        assertEquals(1, callbacks.returnedCount);
    }

    @Test
    void shouldRefreshPositionWithoutWelcomeBackWhenOwnerWasNotAfk() {
        TestState state = new TestState();
        state.position = new Point(10, 20);
        state.sinceMs = 1_000L;
        Point newPosition = new Point(30, 40);
        TestCallbacks callbacks = new TestCallbacks();

        AgentChatWelcomeBackFlow.tickAfkCheck(state, newPosition, 2_000L, callbacks);

        assertSame(newPosition, state.position);
        assertEquals(2_000L, state.sinceMs);
        assertEquals(0, callbacks.returnedCount);
    }

    @Test
    void welcomeBackReplyShouldUseLegacyCatalog() {
        String reply = AgentChatWelcomeBackFlow.welcomeBackReply();

        assertTrue(AgentDialogueCatalog.welcomeBackReplies().contains(reply));
    }

    @Test
    void offlineWelcomeBackReplyShouldUseLegacyFormattedCatalog() {
        String reply = AgentChatWelcomeBackFlow.welcomeBackOfflinePartyReply("Henesys");
        List<String> possibleReplies = AgentDialogueCatalog.welcomeBackOfflinePartyTemplates().stream()
                .map(template -> AgentDialogueReportFormatter.welcomeBackOfflineReply(template, "Henesys"))
                .toList();

        assertTrue(possibleReplies.contains(reply));
    }

    @Test
    void offlineWelcomeBackReplyShouldPreserveTownFallback() {
        String reply = AgentChatWelcomeBackFlow.welcomeBackOfflinePartyReply(null);
        List<String> possibleReplies = AgentDialogueCatalog.welcomeBackOfflinePartyTemplates().stream()
                .map(template -> AgentDialogueReportFormatter.welcomeBackOfflineReply(template, null))
                .toList();

        assertTrue(possibleReplies.contains(reply));
    }

    private static final class TestState implements AgentChatWelcomeBackFlow.AfkState {
        private Point position;
        private long sinceMs;
        private boolean wasAfk;

        @Override
        public Point ownerAfkPosition() {
            return position;
        }

        @Override
        public void setOwnerAfkPosition(Point position) {
            this.position = position;
        }

        @Override
        public long ownerAfkSinceMs() {
            return sinceMs;
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

    private static final class TestCallbacks implements AgentChatWelcomeBackFlow.WelcomeBackCallbacks {
        private int returnedCount;

        @Override
        public void ownerReturned() {
            returnedCount++;
        }
    }
}
