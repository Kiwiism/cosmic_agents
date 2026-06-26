package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentItemQueryNormalizerTest {
    @Test
    void shouldNormalizeLegacyItemQueryText() {
        assertEquals("warrior potion", AgentItemQueryNormalizer.normalize("Warrior Potions?!"));
        assertEquals("chaos scroll", AgentItemQueryNormalizer.normalize("Chaos Scrolls"));
        assertEquals("flaming feather", AgentItemQueryNormalizer.normalize("Flaming Feathers"));
        assertEquals("ring 2", AgentItemQueryNormalizer.normalize("Ring 2"));
        assertEquals("", AgentItemQueryNormalizer.normalize(null));
    }
}
