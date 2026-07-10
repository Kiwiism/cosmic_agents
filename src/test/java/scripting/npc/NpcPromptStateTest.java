package scripting.npc;

import client.Client;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class NpcPromptStateTest {
    @Test
    void shouldEnforceNumberBoundsAndResponseType() {
        NpcPromptState prompt = NpcPromptState.number(1, 100);

        assertTrue(prompt.accepts((byte) 3, (byte) 1, 1));
        assertTrue(prompt.accepts((byte) 3, (byte) 1, 100));
        assertFalse(prompt.accepts((byte) 3, (byte) 1, -1));
        assertFalse(prompt.accepts((byte) 3, (byte) 1, 101));
        assertFalse(prompt.accepts((byte) 4, (byte) 1, 1));
    }

    @Test
    void shouldAcceptOnlyAdvertisedMenuSelections() {
        NpcPromptState prompt = NpcPromptState.menu("#L0#First#l #L5#Second#l");

        assertTrue(prompt.accepts((byte) 4, (byte) 1, 0));
        assertTrue(prompt.accepts((byte) 4, (byte) 1, 5));
        assertFalse(prompt.accepts((byte) 4, (byte) 1, 1));
    }

    @Test
    void shouldValidateStyleIndexAndAllowCancel() {
        NpcPromptState prompt = NpcPromptState.style(3);

        assertTrue(prompt.accepts((byte) 7, (byte) 1, 2));
        assertFalse(prompt.accepts((byte) 7, (byte) 1, 3));
        assertTrue(prompt.accepts((byte) 7, (byte) 0, -1));
    }

    @Test
    void conversationShouldConsumePromptOnlyOnce() {
        NPCConversationManager conversation = new NPCConversationManager(mock(Client.class), 10000, "test");
        conversation.sendGetNumber("Quantity", 1, 1, 10);

        assertTrue(conversation.validateAndConsumePrompt((byte) 3, (byte) 1, 5));
        assertFalse(conversation.validateAndConsumePrompt((byte) 3, (byte) 1, 5));
    }
}
