package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentLeaderSessionRuntimeTest {
    @Test
    void delegatesLeaderResolutionToServiceWithRuntimeLookup() {
        Character offlineLeader = character(100, false);
        Character refreshedLeader = character(100, true);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(character(200, true), offlineLeader, null);
        AtomicInteger requestedLeaderId = new AtomicInteger();

        Character resolved = AgentLeaderSessionRuntime.resolveTickLeader(entry, refreshedLeader.getId(), id -> {
            requestedLeaderId.set(id);
            return refreshedLeader;
        });

        assertSame(refreshedLeader, resolved);
        assertSame(refreshedLeader, entry.owner());
        assertEquals(refreshedLeader.getId(), requestedLeaderId.get());
    }

    private static Character character(int id, boolean loggedIn) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.isLoggedinWorld()).thenReturn(loggedIn);
        return character;
    }
}
