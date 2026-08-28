package server.agents.capabilities.partyquest.lpq;

import client.BuffStat;
import client.Character;
import org.junit.jupiter.api.Test;
import scripting.event.EventInstanceManager;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentLpqSessionRegistryTest {
    @Test
    void activeSessionUsesNormalObservedAndBackgroundSimulationCadence() {
        int agentId = 81_001;
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(agentId);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentRuntimeRegistry.registerEntry(81_000, entry);
        AgentLpqSession session = new AgentLpqSession(
                AgentLpqSession.Mode.TEST_OBSERVATION, 1L, 81_000, 5, 1_000L);
        session.addMember(agentId, AgentLpqMemberState.MemberType.AGENT);
        session.addMember(81_002, AgentLpqMemberState.MemberType.HUMAN);
        session.addMember(81_003, AgentLpqMemberState.MemberType.HUMAN);
        session.addMember(81_004, AgentLpqMemberState.MemberType.HUMAN);
        session.addMember(81_005, AgentLpqMemberState.MemberType.HUMAN);
        try {
            AgentLpqSessionRegistry.registerComplete(session);
            assertFalse(entry.simulationState().fullRateSimulationRequired());

            AgentLpqSessionRegistry.remove(session);
            assertFalse(entry.simulationState().fullRateSimulationRequired());
        } finally {
            AgentLpqSessionRegistry.remove(session);
            AgentRuntimeRegistry.unregisterEntry(81_000, entry);
        }
    }

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

    @Test
    void darkSightRoomSuppressesTouchOnlyForManagedHiddenAgent() {
        AgentLpqSession session = new AgentLpqSession(
                AgentLpqSession.Mode.TEST_OBSERVATION, 1L, 901, 5, 1_000L);
        session.addMember(201, AgentLpqMemberState.MemberType.AGENT);
        EventInstanceManager event = mock(EventInstanceManager.class);
        Character agent = mock(Character.class);
        session.bindEventInstance(event);
        session.transition(AgentLpqSession.Phase.STAGE_5, 2_000L);
        when(agent.getId()).thenReturn(201);
        when(agent.getEventInstance()).thenReturn(event);
        when(agent.getMapId()).thenReturn(AgentLpqDefinition.STAGE_5_DARK_SIGHT_ROOM);
        when(agent.getBuffedValue(BuffStat.DARKSIGHT)).thenReturn(1);
        AgentLpqSessionRegistry.registerComplete(session);
        try {
            assertTrue(AgentLpqSessionRegistry.suppressesDarkSightRoomTouch(agent));

            when(agent.getMapId()).thenReturn(922_010_505);
            assertFalse(AgentLpqSessionRegistry.suppressesDarkSightRoomTouch(agent));
        } finally {
            AgentLpqSessionRegistry.remove(session);
        }
    }
}
