package server.agents.capabilities.partyquest.kpq;

import client.Character;
import org.junit.jupiter.api.Test;
import scripting.event.EventInstanceManager;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentKpqSessionRegistryTest {
    @Test
    void partyLeaverIsUnindexedWithoutRemovingTheRemainingSession() {
        AgentKpqSession session = new AgentKpqSession(
                AgentKpqSession.Mode.TEST_OBSERVATION, 1L, 999, 3, 1_000L);
        session.addMember(101, AgentKpqMemberState.MemberType.AGENT);
        session.addMember(102, AgentKpqMemberState.MemberType.AGENT);
        session.addMember(103, AgentKpqMemberState.MemberType.AGENT);
        AgentKpqSessionRegistry.registerComplete(session);
        try {
            AgentKpqSessionRegistry.unindexMember(session, 103);

            assertFalse(AgentKpqSessionRegistry.active(103));
            assertTrue(AgentKpqSessionRegistry.active(101));
            assertTrue(AgentKpqSessionRegistry.forOperator(999) == session);
        } finally {
            AgentKpqSessionRegistry.remove(session);
        }
    }

    @Test
    void lootRightsFollowExplicitMemberRoles() {
        AgentKpqSession session = new AgentKpqSession(
                AgentKpqSession.Mode.TEST_OBSERVATION, 1L, 999, 3, 1_000L);
        session.addMember(101, AgentKpqMemberState.MemberType.AGENT);
        session.addMember(102, AgentKpqMemberState.MemberType.AGENT);
        session.addMember(103, AgentKpqMemberState.MemberType.AGENT);
        AgentKpqSessionRegistry.registerComplete(session);
        try {
            session.member(102).setRole(AgentKpqMemberState.Role.COUPON_COLLECTOR);
            session.setSquishyShoesWinnerId(103);

            assertTrue(AgentKpqSessionRegistry.canLootPass(101));
            assertFalse(AgentKpqSessionRegistry.canLootPass(102));
            assertTrue(AgentKpqSessionRegistry.canLootCoupon(102));
            assertFalse(AgentKpqSessionRegistry.canLootCoupon(101));
            assertTrue(AgentKpqSessionRegistry.canLootSquishyShoes(103));
            assertFalse(AgentKpqSessionRegistry.canLootSquishyShoes(101));
        } finally {
            AgentKpqSessionRegistry.remove(session);
        }
    }

    @Test
    void managedEventDistinguishesRegisteredParticipantFromObserverForReward() {
        AgentKpqSession session = new AgentKpqSession(
                AgentKpqSession.Mode.TEST_OBSERVATION, 1L, 999, 3, 1_000L);
        session.addMember(101, AgentKpqMemberState.MemberType.AGENT);
        session.addMember(102, AgentKpqMemberState.MemberType.AGENT);
        session.addMember(103, AgentKpqMemberState.MemberType.HUMAN);
        EventInstanceManager event = mock(EventInstanceManager.class);
        Character participant = mock(Character.class);
        Character observer = mock(Character.class);
        session.bindEventInstance(event);
        session.freezeRewardEligibility();
        when(participant.getId()).thenReturn(103);
        when(participant.getEventInstance()).thenReturn(event);
        when(observer.getId()).thenReturn(777);
        when(observer.getEventInstance()).thenReturn(event);
        AgentKpqSessionRegistry.registerComplete(session);
        try {
            assertTrue(AgentKpqSessionRegistry.isManagedEvent(observer));
            assertFalse(AgentKpqSessionRegistry.isRegisteredParticipant(observer));
            assertFalse(AgentKpqSessionRegistry.beginRewardClaim(observer));
            assertTrue(AgentKpqSessionRegistry.beginRewardClaim(participant));
            assertTrue(AgentKpqSessionRegistry.completeRewardClaim(participant));
        } finally {
            AgentKpqSessionRegistry.remove(session);
        }
    }
}
