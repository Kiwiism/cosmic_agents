package server.agents.capabilities.dialogue;

import client.Job;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentChatJobAdvancementFlowTest {
    @Test
    void shouldRouteValidJobAdvancementChoice() {
        TestCallbacks callbacks = new TestCallbacks();

        assertTrue(AgentChatJobAdvancementFlow.handle("fighter", Job.WARRIOR, 30, callbacks));

        assertEquals(Job.FIGHTER, callbacks.job);
    }

    @Test
    void shouldIgnoreNonJobMessages() {
        TestCallbacks callbacks = new TestCallbacks();

        assertFalse(AgentChatJobAdvancementFlow.handle("follow me", Job.WARRIOR, 30, callbacks));

        assertEquals(null, callbacks.job);
    }

    @Test
    void shouldIgnoreJobChoicesThatDoNotMeetLevelGate() {
        TestCallbacks callbacks = new TestCallbacks();

        assertFalse(AgentChatJobAdvancementFlow.handle("fighter", Job.WARRIOR, 29, callbacks));

        assertEquals(null, callbacks.job);
    }

    @Test
    void shouldBuildJobChangeReplyFromLegacyTemplates() {
        String reply = AgentChatJobAdvancementFlow.jobChangeReply(Job.FIGHTER);
        List<String> possibleReplies = AgentDialogueCatalog.jobChangeReplyTemplates().stream()
                .map(template -> AgentDialogueReportFormatter.jobChangeReply(
                        template, AgentDialogueReportFormatter.jobDisplayName(Job.FIGHTER)))
                .toList();

        assertTrue(possibleReplies.contains(reply));
    }

    private static final class TestCallbacks implements AgentChatJobAdvancementFlow.JobAdvancementCallbacks {
        private Job job;

        @Override
        public void advanceTo(Job job) {
            this.job = job;
        }
    }
}
