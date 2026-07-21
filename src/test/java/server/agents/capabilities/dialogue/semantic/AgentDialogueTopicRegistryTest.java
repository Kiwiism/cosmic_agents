package server.agents.capabilities.dialogue.semantic;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.dialogue.conversation.AgentConversationTopicRegistry;
import server.agents.plans.mapleisland.dialogue.MapleIslandConversationTopicProvider;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentDialogueTopicRegistryTest {
    @AfterEach
    void clearOverrides() {
        AgentDialogueTopicRegistry.resetForTests();
    }

    @Test
    void liveOverrideCanDisableAndRestoreAConfiguredTopic() {
        assertTrue(AgentDialogueTopicRegistry.configuredEnabled(AgentDialogueTopicRegistry.HUNTING));

        AgentDialogueTopicRegistry.setOverride(AgentDialogueTopicRegistry.HUNTING, false);
        assertFalse(AgentDialogueTopicRegistry.enabled(AgentDialogueTopicRegistry.HUNTING));

        AgentDialogueTopicRegistry.setOverride(AgentDialogueTopicRegistry.HUNTING, null);
        assertTrue(AgentDialogueTopicRegistry.enabled(AgentDialogueTopicRegistry.HUNTING));
    }

    @Test
    void rejectsUnknownTopicOverrides() {
        assertThrows(IllegalArgumentException.class,
                () -> AgentDialogueTopicRegistry.setOverride("not-a-topic", true));
    }

    @Test
    void discoversContentOwnedConversationTopicsThroughProviderBoundary() {
        AgentConversationTopicRegistry.ensureLoaded();

        assertTrue(AgentDialogueTopicRegistry.definitions().containsKey(
                MapleIslandConversationTopicProvider.PIO_BOXES));
        assertTrue(AgentDialogueTopicRegistry.definitions().containsKey(
                MapleIslandConversationTopicProvider.RAIN_QUIZ));
        assertTrue(AgentDialogueTopicRegistry.definitions().containsKey(
                MapleIslandConversationTopicProvider.YOONA_QUIZ));
    }
}
