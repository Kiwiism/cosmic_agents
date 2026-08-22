package server.agents.social.personality;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogueStyleProfileRepositoryTest {
    @Test
    void resolvesAllFourOperationalPersonalityProfiles() {
        DialogueStyleProfileRepository repository = DialogueStyleProfileRepository.defaultRepository();

        assertEquals("quiet-practical-v1", repository.resolve("efficient-v1").styleId());
        assertEquals("friendly-casual-v1", repository.resolve("relaxed-v1").styleId());
        assertEquals("playful-social-v1", repository.resolve("restless-v1").styleId());
        assertEquals("curious-millennial-v1", repository.resolve("explorer-v1").styleId());
        assertTrue(repository.resolve("unknown").snapshot().toneInstruction().contains("friendly"));
    }
}
