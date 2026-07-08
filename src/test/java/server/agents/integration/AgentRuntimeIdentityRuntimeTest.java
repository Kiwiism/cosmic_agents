package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.maps.MapleMap;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRuntimeIdentityRuntimeTest {
    @Test
    void adaptsRuntimeIdentity() {
        Character bot = mock(Character.class);
        Character owner = mock(Character.class);
        MapleMap map = mock(MapleMap.class);
        when(bot.getId()).thenReturn(101);
        when(bot.getName()).thenReturn("AgentOne");
        when(bot.getAccountID()).thenReturn(303);
        when(bot.getMapId()).thenReturn(100000000);
        when(bot.getMap()).thenReturn(map);
        when(bot.getPosition()).thenReturn(new Point(10, 20));
        when(owner.getId()).thenReturn(202);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(bot, owner, null);

        assertSame(bot, AgentRuntimeIdentityRuntime.bot(entry));
        assertSame(owner, AgentRuntimeIdentityRuntime.owner(entry));
        assertEquals(101, AgentRuntimeIdentityRuntime.botId(entry));
        assertEquals(303, AgentRuntimeIdentityRuntime.botAccountId(entry));
        assertEquals(202, AgentRuntimeIdentityRuntime.ownerId(entry));
        assertEquals("AgentOne", AgentRuntimeIdentityRuntime.botName(entry));
        assertTrue(AgentRuntimeIdentityRuntime.hasBot(entry));
        assertTrue(AgentRuntimeIdentityRuntime.botIs(entry, 101));
        assertTrue(AgentRuntimeIdentityRuntime.botNameEquals(entry, "agentone"));
        assertFalse(AgentRuntimeIdentityRuntime.botNameEquals(entry, "other"));
        assertEquals(100000000, AgentRuntimeIdentityRuntime.botMapId(entry));
        assertSame(map, AgentRuntimeIdentityRuntime.botMap(entry));
        assertTrue(AgentRuntimeIdentityRuntime.botHasMap(entry));
        Point exposed = AgentRuntimeIdentityRuntime.botPosition(entry);
        exposed.x = 999;
        assertEquals(new Point(10, 20), AgentRuntimeIdentityRuntime.botPosition(entry));
    }

    @Test
    void handlesMissingRuntimeIdentity() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertEquals(-1, AgentRuntimeIdentityRuntime.botId(null));
        assertEquals(-1, AgentRuntimeIdentityRuntime.botAccountId(null));
        assertEquals(-1, AgentRuntimeIdentityRuntime.ownerId(null));
        assertEquals(-1, AgentRuntimeIdentityRuntime.botId(entry));
        assertEquals(-1, AgentRuntimeIdentityRuntime.botAccountId(entry));
        assertEquals(-1, AgentRuntimeIdentityRuntime.ownerId(entry));
        assertEquals(null, AgentRuntimeIdentityRuntime.botName(entry));
        assertFalse(AgentRuntimeIdentityRuntime.hasBot(entry));
        assertFalse(AgentRuntimeIdentityRuntime.botIs(entry, 101));
        assertFalse(AgentRuntimeIdentityRuntime.botNameEquals(entry, "agentone"));
        assertEquals(-1, AgentRuntimeIdentityRuntime.botMapId(entry));
        assertFalse(AgentRuntimeIdentityRuntime.botHasMap(entry));
    }
}
