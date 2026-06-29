package server.agents.registry;

import client.BotClient;
import client.Character;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentResolvedCharacterTest {
    @Test
    void offlineCharacterIsNotOnlineOrBotControlled() {
        AgentResolvedCharacter character = new AgentResolvedCharacter(1, "agent123", 2, null);

        assertFalse(character.isOnline());
        assertFalse(character.isOnlineAsBot());
    }

    @Test
    void onlineBotCharacterKeepsRuntimeReference() {
        Character online = mock(Character.class);
        BotClient botClient = mock(BotClient.class);
        when(online.getClient()).thenReturn(botClient);

        AgentResolvedCharacter character = new AgentResolvedCharacter(1, "agent123", 2, online);

        assertTrue(character.isOnline());
        assertTrue(character.isOnlineAsBot());
        assertSame(online, character.onlineCharacter());
    }
}
