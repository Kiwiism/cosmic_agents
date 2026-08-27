package server.agents.capabilities.partyquest.lpq;

import client.Character;
import org.junit.jupiter.api.Test;
import scripting.event.EventInstanceManager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentLpqSessionRegistryTest {
    @Test
    void managedEventDistinguishesRegisteredParticipantFromObserverForReward() {
        AgentLpqSession session = new AgentLpqSession(
                AgentLpqSession.Mode.TEST_OBSERVATION, 1L, 900, 5, 1_000L);
        session.addMember(101, AgentLpqMemberState.MemberType.AGENT);
        session.addMember(102, AgentLpqMemberState.MemberType.AGENT);
        session.addMember(103, AgentLpqMemberState.MemberType.AGENT);
        session.addMember(104, AgentLpqMemberState.MemberType.AGENT);
        session.addMember(900, AgentLpqMemberState.MemberType.HUMAN);
        EventInstanceManager event = mock(EventInstanceManager.class);
        Character participant = mock(Character.class);
        Character observer = mock(Character.class);
        session.bindEventInstance(event);
        session.freezeRewardEligibility();
        when(participant.getId()).thenReturn(900);
        when(participant.getEventInstance()).thenReturn(event);
        when(observer.getId()).thenReturn(777);
        when(observer.getEventInstance()).thenReturn(event);
        AgentLpqSessionRegistry.registerComplete(session);
        try {
            assertTrue(AgentLpqSessionRegistry.isManagedEvent(observer));
            assertFalse(AgentLpqSessionRegistry.isRegisteredParticipant(observer));
            assertFalse(AgentLpqSessionRegistry.beginRewardClaim(observer));
            assertTrue(AgentLpqSessionRegistry.beginRewardClaim(participant));
            assertTrue(AgentLpqSessionRegistry.completeRewardClaim(participant));
        } finally {
            AgentLpqSessionRegistry.remove(session);
        }
    }
}
