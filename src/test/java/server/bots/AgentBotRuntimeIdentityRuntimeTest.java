package server.bots;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotRuntimeIdentityRuntime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentBotRuntimeIdentityRuntimeTest {
    @Test
    void adaptsRuntimeIdentity() {
        Character bot = mock(Character.class);
        Character owner = mock(Character.class);
        when(bot.getId()).thenReturn(101);
        when(bot.getName()).thenReturn("AgentOne");
        when(owner.getId()).thenReturn(202);
        BotEntry entry = new BotEntry(bot, owner, null);

        assertSame(bot, AgentBotRuntimeIdentityRuntime.bot(entry));
        assertSame(owner, AgentBotRuntimeIdentityRuntime.owner(entry));
        assertEquals(101, AgentBotRuntimeIdentityRuntime.botId(entry));
        assertEquals(202, AgentBotRuntimeIdentityRuntime.ownerId(entry));
        assertTrue(AgentBotRuntimeIdentityRuntime.hasBot(entry));
        assertTrue(AgentBotRuntimeIdentityRuntime.botIs(entry, 101));
        assertTrue(AgentBotRuntimeIdentityRuntime.botNameEquals(entry, "agentone"));
        assertFalse(AgentBotRuntimeIdentityRuntime.botNameEquals(entry, "other"));
    }

    @Test
    void handlesMissingRuntimeIdentity() {
        BotEntry entry = new BotEntry(null, null, null);

        assertEquals(-1, AgentBotRuntimeIdentityRuntime.botId(null));
        assertEquals(-1, AgentBotRuntimeIdentityRuntime.ownerId(null));
        assertEquals(-1, AgentBotRuntimeIdentityRuntime.botId(entry));
        assertEquals(-1, AgentBotRuntimeIdentityRuntime.ownerId(entry));
        assertFalse(AgentBotRuntimeIdentityRuntime.hasBot(entry));
        assertFalse(AgentBotRuntimeIdentityRuntime.botIs(entry, 101));
        assertFalse(AgentBotRuntimeIdentityRuntime.botNameEquals(entry, "agentone"));
    }
}
