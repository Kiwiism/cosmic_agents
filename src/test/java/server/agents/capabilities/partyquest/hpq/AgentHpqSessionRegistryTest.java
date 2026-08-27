package server.agents.capabilities.partyquest.hpq;

import client.Character;
import org.junit.jupiter.api.Test;
import scripting.event.EventInstanceManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentHpqSessionRegistryTest {
    @Test
    void keepsHpqMembershipAndRiceCakeOwnershipInsideHpq() {
        AgentHpqSession session = session(101);
        session.member(101).assign(AgentHpqMemberState.Role.CAKE_COLLECTOR, 0);
        AgentHpqSessionRegistry.registerComplete(session);
        try {
            assertSame(session, AgentHpqSessionRegistry.forMember(101));
            assertFalse(AgentHpqSessionRegistry.canLootRiceCake(101));
            session.transition(AgentHpqSession.Phase.DEFENDING_BUNNY, 2_000L);
            assertTrue(AgentHpqSessionRegistry.canLootRiceCake(101));
            assertEquals("member-101",
                    AgentHpqSessionRegistry.registrationBlocker(999, java.util.List.of(101)));
        } finally {
            AgentHpqSessionRegistry.remove(session);
        }
        assertFalse(AgentHpqSessionRegistry.active(101));
    }

    @Test
    void rejectsDuplicateHpqMemberIndexesAtomically() {
        AgentHpqSession first = session(201);
        AgentHpqSession second = new AgentHpqSession(
                AgentHpqSession.Mode.PRODUCTION, 8L, 202, 4, 1_000L);
        second.addMember(202, AgentHpqMemberState.MemberType.AGENT);
        second.addMember(203, AgentHpqMemberState.MemberType.HUMAN);
        second.addMember(204, AgentHpqMemberState.MemberType.HUMAN);
        second.addMember(201, AgentHpqMemberState.MemberType.AGENT);
        second.setLeadership(202, 202);
        AgentHpqSessionRegistry.registerComplete(first);
        try {
            assertThrows(IllegalStateException.class,
                    () -> AgentHpqSessionRegistry.registerComplete(second));
            assertSame(first, AgentHpqSessionRegistry.forMember(201));
        } finally {
            AgentHpqSessionRegistry.remove(first);
            AgentHpqSessionRegistry.remove(second);
        }
    }

    @Test
    void managedEventDistinguishesRegisteredParticipantFromObserverForReward() {
        AgentHpqSession session = session(301);
        EventInstanceManager event = mock(EventInstanceManager.class);
        Character participant = mock(Character.class);
        Character observer = mock(Character.class);
        session.bindEventInstance(event);
        session.freezeRewardEligibility();
        when(participant.getId()).thenReturn(302);
        when(participant.getEventInstance()).thenReturn(event);
        when(observer.getId()).thenReturn(999);
        when(observer.getEventInstance()).thenReturn(event);
        AgentHpqSessionRegistry.registerComplete(session);
        try {
            assertTrue(AgentHpqSessionRegistry.isManagedEvent(observer));
            assertFalse(AgentHpqSessionRegistry.isRegisteredParticipant(observer));
            assertFalse(AgentHpqSessionRegistry.beginRewardClaim(observer));
            assertTrue(AgentHpqSessionRegistry.isRegisteredParticipant(participant));
            assertTrue(AgentHpqSessionRegistry.beginRewardClaim(participant));
            assertTrue(AgentHpqSessionRegistry.completeRewardClaim(participant));
        } finally {
            AgentHpqSessionRegistry.remove(session);
        }
    }

    private static AgentHpqSession session(int operatorId) {
        AgentHpqSession session = new AgentHpqSession(
                AgentHpqSession.Mode.PRODUCTION, 7L, operatorId, 3, 1_000L);
        session.addMember(operatorId, AgentHpqMemberState.MemberType.AGENT);
        session.addMember(operatorId + 1, AgentHpqMemberState.MemberType.HUMAN);
        session.addMember(operatorId + 2, AgentHpqMemberState.MemberType.HUMAN);
        session.setLeadership(operatorId, operatorId);
        return session;
    }
}
