package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentChatTextSanitizerTest {
    @Test
    void preservesCleanChatText() {
        assertEquals("hello ok", AgentChatTextSanitizer.sanitize("hello ok"));
        assertNull(AgentChatTextSanitizer.sanitize(null));
    }

    @Test
    void replacesCommonTypographicCharactersWithAsciiFallbacks() {
        assertEquals("\"hi\" - ok...", AgentChatTextSanitizer.sanitize("“hi” — ok…"));
        assertEquals("it ' works", AgentChatTextSanitizer.sanitize("it ’ works"));
    }
}
