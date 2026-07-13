package server.agents.runtime.scheduler;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentSessionIdTest {
    @Test
    void matchesOnlyTheCapturedCharacterAndGeneration() {
        Character firstCharacter = mock(Character.class);
        Character replacementCharacter = mock(Character.class);
        when(firstCharacter.getId()).thenReturn(100);
        when(replacementCharacter.getId()).thenReturn(100);
        AgentRuntimeEntry first = new AgentRuntimeEntry(firstCharacter, null, null);
        AgentRuntimeEntry replacement = new AgentRuntimeEntry(replacementCharacter, null, null);

        AgentSessionId sessionId = AgentSessionId.from(first);

        assertTrue(sessionId.matches(first));
        assertFalse(sessionId.matches(replacement));
    }
}
