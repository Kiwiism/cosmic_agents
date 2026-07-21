package server.agents.capabilities.dialogue.semantic;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.dialogue.AgentDialogueAudience;
import server.agents.personality.AgentPersonalityProfile;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class AgentDeterministicDialogueRealizerTest {
    private final AgentDeterministicDialogueRealizer realizer = new AgentDeterministicDialogueRealizer();

    @Test
    void realizesSameSemanticActDeterministically() {
        AgentSemanticDialogueAct act = act(AgentDialogueTopicRegistry.GREETING, "open", 41L);
        AgentPersonalityProfile profile = profile(50);

        assertEquals(realizer.realize(act, profile), realizer.realize(act, profile));
    }

    @Test
    void expressivenessInfluencesWordingWithoutChangingSemanticTopic() {
        AgentSemanticDialogueAct act = act(AgentDialogueTopicRegistry.GREETING, "open", 0L);

        String reserved = realizer.realize(act, profile(0));
        String expressive = realizer.realize(act, profile(100));

        assertNotEquals(reserved, expressive);
        assertEquals("Hey, Mira.", reserved);
        assertEquals("Oh hey Mira!", expressive);
    }

    private static AgentSemanticDialogueAct act(String topicId, String actKey, long seed) {
        return new AgentSemanticDialogueAct(1, 2, 100L, topicId, actKey,
                AgentDialogueAudience.NEARBY_REAL_PLAYER, "test", 0L, seed,
                Map.of("listenerName", "Mira"));
    }

    private static AgentPersonalityProfile profile(int expressiveness) {
        return new AgentPersonalityProfile("test", 1,
                new AgentPersonalityProfile.Traits(50, 50, expressiveness, 50, 50, 50, 50));
    }
}
