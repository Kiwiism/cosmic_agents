package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentLeaderSessionServiceTest {
    @Test
    void keepsCachedLeaderWhenItMatchesAndIsOnline() {
        Character leader = character(100, true);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(character(200, true), leader, null);
        IntFunction<Character> lookup = mockLookup(character(999, true));

        Character resolved = AgentLeaderSessionService.resolveTickLeader(entry, leader.getId(), lookup);

        assertSame(leader, resolved);
        verify(lookup, never()).apply(leader.getId());
    }

    @Test
    void refreshesMissingLeader() {
        Character refreshed = character(100, true);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(character(200, true), null, null);

        Character resolved = AgentLeaderSessionService.resolveTickLeader(entry, refreshed.getId(), id -> refreshed);

        assertSame(refreshed, resolved);
        assertSame(refreshed, entry.owner());
    }

    @Test
    void refreshesMismatchedLeader() {
        Character oldLeader = character(100, true);
        Character refreshed = character(101, true);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(character(200, true), oldLeader, null);

        Character resolved = AgentLeaderSessionService.resolveTickLeader(entry, refreshed.getId(), id -> refreshed);

        assertSame(refreshed, resolved);
        assertSame(refreshed, entry.owner());
    }

    @Test
    void refreshesOfflineLeader() {
        Character offlineLeader = character(100, false);
        Character refreshed = character(100, true);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(character(200, true), offlineLeader, null);

        Character resolved = AgentLeaderSessionService.resolveTickLeader(entry, refreshed.getId(), id -> refreshed);

        assertSame(refreshed, resolved);
        assertSame(refreshed, entry.owner());
    }

    @Test
    void passesRequestedLeaderIdToLookupAndCachesNullWhenNotFound() {
        Character offlineLeader = character(100, false);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(character(200, true), offlineLeader, null);
        AtomicInteger requestedId = new AtomicInteger();

        Character resolved = AgentLeaderSessionService.resolveTickLeader(entry, 123, id -> {
            requestedId.set(id);
            return null;
        });

        assertEquals(123, requestedId.get());
        assertNull(resolved);
        assertNull(entry.owner());
    }

    @SuppressWarnings("unchecked")
    private static IntFunction<Character> mockLookup(Character result) {
        IntFunction<Character> lookup = mock(IntFunction.class);
        when(lookup.apply(result.getId())).thenReturn(result);
        return lookup;
    }

    private static Character character(int id, boolean loggedIn) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.isLoggedinWorld()).thenReturn(loggedIn);
        return character;
    }
}
