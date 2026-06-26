package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentSocialDialogueClassifierTest {
    @Test
    void shouldClassifyWholeMessageGreetingsOnly() {
        assertTrue(AgentSocialDialogueClassifier.isGreeting("hi"));
        assertTrue(AgentSocialDialogueClassifier.isGreeting("how are you?"));
        assertTrue(AgentSocialDialogueClassifier.isGreeting("what's good"));
        assertFalse(AgentSocialDialogueClassifier.isGreeting("hi how are you today"));
    }

    @Test
    void shouldParseFameTarget() {
        assertEquals("me", AgentSocialDialogueClassifier.matchFameTarget("fame me"));
        assertEquals("Clawer", AgentSocialDialogueClassifier.matchFameTarget("fame Clawer!"));
        assertNull(AgentSocialDialogueClassifier.matchFameTarget("give fame Clawer"));
    }
}
