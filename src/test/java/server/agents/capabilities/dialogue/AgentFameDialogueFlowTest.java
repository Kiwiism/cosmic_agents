package server.agents.capabilities.dialogue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import client.Character;
import org.junit.jupiter.api.Test;

class AgentFameDialogueFlowTest {
    @Test
    void repliesWhenTargetIsMissing() {
        TestCallbacks callbacks = new TestCallbacks();
        callbacks.targetExists = false;

        AgentFameDialogueFlow.handle("Alice", callbacks);

        assertEquals("can't find Alice on the map", callbacks.reply);
        assertFalse(callbacks.gainFameCalled);
    }

    @Test
    void repliesWhenTargetIsSelf() {
        TestCallbacks callbacks = new TestCallbacks();
        callbacks.targetIsSelf = true;

        AgentFameDialogueFlow.handle("me", callbacks);

        assertEquals("lol can't fame myself", callbacks.reply);
        assertFalse(callbacks.gainFameCalled);
    }

    @Test
    void repliesWhenAgentLevelTooLow() {
        TestCallbacks callbacks = new TestCallbacks();
        callbacks.agentLevel = 14;

        AgentFameDialogueFlow.handle("Alice", callbacks);

        assertEquals("i'm too low level to fame", callbacks.reply);
        assertFalse(callbacks.gainFameCalled);
    }

    @Test
    void repliesWhenFameIsOnDailyCooldown() {
        TestCallbacks callbacks = new TestCallbacks();
        callbacks.fameStatus = Character.FameStatus.NOT_TODAY;

        AgentFameDialogueFlow.handle("Alice", callbacks);

        assertEquals("cooldown reply", callbacks.reply);
        assertFalse(callbacks.gainFameCalled);
    }

    @Test
    void repliesWhenSameTargetWasFamedThisMonth() {
        TestCallbacks callbacks = new TestCallbacks();
        callbacks.fameStatus = Character.FameStatus.NOT_THIS_MONTH;

        AgentFameDialogueFlow.handle("Alice", callbacks);

        assertEquals("same Alice", callbacks.reply);
        assertFalse(callbacks.gainFameCalled);
    }

    @Test
    void repliesAndMarksFameOnSuccess() {
        TestCallbacks callbacks = new TestCallbacks();
        callbacks.gainFameResult = true;

        AgentFameDialogueFlow.handle("Alice", callbacks);

        assertEquals("famed Alice", callbacks.reply);
        assertTrue(callbacks.gainFameCalled);
        assertTrue(callbacks.markFameGivenCalled);
    }

    @Test
    void repliesWhenFameMutationFails() {
        TestCallbacks callbacks = new TestCallbacks();
        callbacks.gainFameResult = false;

        AgentFameDialogueFlow.handle("Alice", callbacks);

        assertEquals("fame failed, might be at max already", callbacks.reply);
        assertTrue(callbacks.gainFameCalled);
        assertFalse(callbacks.markFameGivenCalled);
    }

    private static final class TestCallbacks implements AgentFameDialogueFlow.FameCallbacks {
        private boolean targetExists = true;
        private boolean targetIsSelf;
        private int agentLevel = 15;
        private Character.FameStatus fameStatus = Character.FameStatus.OK;
        private boolean gainFameResult;
        private boolean gainFameCalled;
        private boolean markFameGivenCalled;
        private String reply;

        @Override
        public boolean targetExists() {
            return targetExists;
        }

        @Override
        public boolean targetIsSelf() {
            return targetIsSelf;
        }

        @Override
        public int agentLevel() {
            return agentLevel;
        }

        @Override
        public Character.FameStatus fameStatus() {
            return fameStatus;
        }

        @Override
        public boolean gainFame() {
            gainFameCalled = true;
            return gainFameResult;
        }

        @Override
        public void markFameGiven() {
            markFameGivenCalled = true;
        }

        @Override
        public String targetDisplayName() {
            return "Alice";
        }

        @Override
        public String randomOkReply() {
            return "famed %s";
        }

        @Override
        public String randomFameCooldownReply() {
            return "cooldown reply";
        }

        @Override
        public String randomSamePersonReply() {
            return "same %s";
        }

        @Override
        public void reply(String message) {
            reply = message;
        }
    }
}
