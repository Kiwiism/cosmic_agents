package server.agents.capabilities.partyquest.lpq;

import client.Character;
import org.junit.jupiter.api.Test;
import scripting.event.EventInstanceManager;
import server.maps.MapleMap;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentLpqDirectBossTest {
    @Test
    void waitsUntilEveryParticipantIsRegisteredInTheSameRealEvent() {
        EventInstanceManager event = mock(EventInstanceManager.class);
        when(event.isEventDisposed()).thenReturn(false);
        List<Character> members = members(event, 6);

        assertTrue(AgentLpqTestService.directBossEntryReady(6, event, members));
        assertFalse(AgentLpqTestService.directBossEntryReady(6, event, members.subList(0, 5)));

        EventInstanceManager otherEvent = mock(EventInstanceManager.class);
        when(members.getLast().getEventInstance()).thenReturn(otherEvent);
        assertFalse(AgentLpqTestService.directBossEntryReady(6, event, members));
    }

    @Test
    void rejectsDisposedEventsAndNonLpqMaps() {
        EventInstanceManager event = mock(EventInstanceManager.class);
        List<Character> members = members(event, 6);
        when(event.isEventDisposed()).thenReturn(true);
        assertFalse(AgentLpqTestService.directBossEntryReady(6, event, members));

        when(event.isEventDisposed()).thenReturn(false);
        when(members.getFirst().getMapId()).thenReturn(100_000_000);
        assertFalse(AgentLpqTestService.directBossEntryReady(6, event, members));
    }

    private static List<Character> members(EventInstanceManager event, int count) {
        List<Character> members = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            Character member = mock(Character.class);
            when(member.getEventInstance()).thenReturn(event);
            when(member.getMap()).thenReturn(mock(MapleMap.class));
            when(member.getMapId()).thenReturn(AgentLpqDefinition.stage(1).mapId());
            members.add(member);
        }
        return members;
    }
}
