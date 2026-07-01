package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentDialogueSelectorTest {
    @Test
    void returnsOnlyReplyWhenOneOptionExists() {
        assertEquals("hello", AgentDialogueSelector.randomReply(List.of("hello")));
    }

    @Test
    void preservesLegacyExceptionForEmptyReplies() {
        assertThrows(IllegalArgumentException.class, () -> AgentDialogueSelector.randomReply(List.of()));
    }
}
