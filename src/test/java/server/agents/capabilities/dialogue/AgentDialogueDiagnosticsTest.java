package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.dialogue.semantic.AgentDialogueTopicRegistry;
import server.agents.plans.mapleisland.dialogue.MapleIslandConversationTopicProvider;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDialogueDiagnosticsTest {
    @Test
    void topicCommandAppliesAndClearsAReversibleLiveOverride() {
        String topic = AgentDialogueTopicRegistry.HUNTING;
        AgentDialogueTopicRegistry.setOverride(topic, null);
        try {
            List<String> disabled = AgentDialogueDiagnostics.lines(
                    new String[]{"topic", topic, "off"});
            assertFalse(AgentDialogueTopicRegistry.enabled(topic));
            assertTrue(disabled.getFirst().contains("live override"));

            List<String> restored = AgentDialogueDiagnostics.lines(
                    new String[]{"topic", topic, "default"});
            assertTrue(AgentDialogueTopicRegistry.liveOverride(topic) == null);
            assertTrue(restored.getFirst().contains("config default"));
        } finally {
            AgentDialogueTopicRegistry.setOverride(topic, null);
        }
    }

    @Test
    void topicCommandCanToggleAContentProviderTopicBeforeAnySessionStarts() {
        String topic = MapleIslandConversationTopicProvider.PIO_BOXES;
        try {
            AgentDialogueDiagnostics.lines(new String[]{"topic", topic, "off"});
            assertFalse(AgentDialogueTopicRegistry.enabled(topic));

            AgentDialogueDiagnostics.lines(new String[]{"topic", topic, "default"});
            assertTrue(AgentDialogueTopicRegistry.liveOverride(topic) == null);
        } finally {
            AgentDialogueTopicRegistry.setOverride(topic, null);
        }
    }
}
