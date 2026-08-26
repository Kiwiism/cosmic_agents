package server.agents.capabilities.partyquest.lpq;

import client.Character;
import org.junit.jupiter.api.Test;
import scripting.event.EventInstanceManager;
import server.maps.MapleMap;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentLpqEventGuardTest {
    @Test
    void onlyGuardsPhasesThatRequireAnActiveInstance() {
        assertFalse(AgentLpqCoordinator.requiresLiveEvent(AgentLpqSession.Phase.PREPARING));
        assertTrue(AgentLpqCoordinator.requiresLiveEvent(AgentLpqSession.Phase.STAGE_1));
        assertTrue(AgentLpqCoordinator.requiresLiveEvent(AgentLpqSession.Phase.STAGE_8));
        assertTrue(AgentLpqCoordinator.requiresLiveEvent(AgentLpqSession.Phase.CLAIMING_REWARD));
        assertFalse(AgentLpqCoordinator.requiresLiveEvent(AgentLpqSession.Phase.EXITING));
    }

    @Test
    void acceptsMatchingLeaderTimerMapAndFiveRegisteredMembers() {
        Fixture fixture = fixture(5);

        assertEquals("", AgentLpqCoordinator.liveEventFailure(fixture.session, fixture.leader));
    }

    @Test
    void rejectsExpiredOrDetachedEventsBeforeNpcInteraction() {
        Fixture fixture = fixture(5);
        when(fixture.event.getTimeLeft()).thenReturn(0L);
        assertEquals("The LPQ event timer expired",
                AgentLpqCoordinator.liveEventFailure(fixture.session, fixture.leader));

        fixture.session.transition(AgentLpqSession.Phase.BONUS, 1_150L);
        assertEquals("", AgentLpqCoordinator.liveEventFailure(fixture.session, fixture.leader));

        fixture.session.transition(AgentLpqSession.Phase.CLAIMING_REWARD, 1_200L);
        assertEquals("", AgentLpqCoordinator.liveEventFailure(fixture.session, fixture.leader));

        when(fixture.leader.getEventInstance()).thenReturn(null);
        assertEquals("The LPQ event instance ended or expired",
                AgentLpqCoordinator.liveEventFailure(fixture.session, fixture.leader));
    }

    @Test
    void rejectsLeaderMismatchAndInsufficientRegisteredRoster() {
        Fixture fixture = fixture(4);
        when(fixture.event.getLeaderId()).thenReturn(999);
        assertEquals("The LPQ event leader is no longer registered correctly",
                AgentLpqCoordinator.liveEventFailure(fixture.session, fixture.leader));

        when(fixture.event.getLeaderId()).thenReturn(101);
        assertEquals("LPQ no longer has five registered session members",
                AgentLpqCoordinator.liveEventFailure(fixture.session, fixture.leader));
    }

    private static Fixture fixture(int registeredCount) {
        EventInstanceManager event = mock(EventInstanceManager.class);
        AgentLpqSession session = new AgentLpqSession(
                AgentLpqSession.Mode.TEST_OBSERVATION, 7L, 900, 6, 1_000L);
        List<Character> players = new ArrayList<>();
        Character leader = null;
        for (int index = 0; index < 6; index++) {
            int id = 101 + index;
            Character member = mock(Character.class);
            MapleMap map = mock(MapleMap.class);
            when(member.getId()).thenReturn(id);
            when(member.getEventInstance()).thenReturn(event);
            when(member.getMap()).thenReturn(map);
            when(member.getMapId()).thenReturn(AgentLpqDefinition.stage(8).mapId());
            when(map.getEventInstance()).thenReturn(event);
            session.addMember(id, AgentLpqMemberState.MemberType.AGENT);
            if (index < registeredCount) players.add(member);
            if (index == 0) leader = member;
        }
        session.setLeadership(101, 101);
        session.bindEventInstance(event);
        session.transition(AgentLpqSession.Phase.STAGE_8, 1_100L);
        when(leader.isPartyLeader()).thenReturn(true);
        when(event.getLeaderId()).thenReturn(101);
        when(event.getPlayerById(101)).thenReturn(leader);
        when(event.getPlayers()).thenReturn(players);
        when(event.isEventDisposed()).thenReturn(false);
        when(event.isTimerStarted()).thenReturn(true);
        when(event.getTimeLeft()).thenReturn(60_000L);
        return new Fixture(session, event, leader);
    }

    private record Fixture(AgentLpqSession session, EventInstanceManager event, Character leader) { }
}
