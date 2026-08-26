package server.agents.capabilities.partyquest.lpq;

import client.Character;
import client.Skill;
import constants.skills.FPWizard;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import scripting.event.EventInstanceManager;
import server.StatEffect;
import server.agents.capabilities.navigation.AgentMovementSkillPolicy;
import server.agents.capabilities.navigation.AgentNavigationGraph;
import server.maps.MapleMap;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentLpqMovementSkillPolicyTest {
    @Test
    void authorsTeleportEdgesOnlyForTheStageFiveEntranceAndTeleportRoom() {
        assertTrue(AgentLpqMovementSkillPolicy.authorsTeleportEdges(922_010_500));
        assertTrue(AgentLpqMovementSkillPolicy.authorsTeleportEdges(922_010_501));
        assertFalse(AgentLpqMovementSkillPolicy.authorsTeleportEdges(922_010_502));
        assertFalse(AgentLpqMovementSkillPolicy.authorsTeleportEdges(922_010_400));
    }

    @Test
    void permitsOnlyTheAssignedAgentTeleportRunnerInItsActiveEvent() {
        EventInstanceManager event = mock(EventInstanceManager.class);
        AgentLpqSession session = session(70_001, 71_001, event);
        Character agent = agent(71_001, 922_010_500, event);
        try {
            assertTrue(AgentLpqMovementSkillPolicy.allowsActiveTeleport(agent));

            when(agent.getMapId()).thenReturn(922_010_501);
            assertTrue(AgentLpqMovementSkillPolicy.allowsActiveTeleport(agent));

            when(agent.getMapId()).thenReturn(922_010_502);
            assertFalse(AgentLpqMovementSkillPolicy.allowsActiveTeleport(agent));

            when(agent.getMapId()).thenReturn(922_010_500);
            when(agent.getEventInstance()).thenReturn(mock(EventInstanceManager.class));
            assertFalse(AgentLpqMovementSkillPolicy.allowsActiveTeleport(agent));
        } finally {
            AgentLpqSessionRegistry.remove(session);
        }
    }

    @Test
    void rejectsAStageFiveMemberWithoutTheTeleportAssignment() {
        EventInstanceManager event = mock(EventInstanceManager.class);
        AgentLpqSession session = session(70_002, 71_002, event);
        session.member(71_002).assign(AgentLpqMemberState.Role.GENERAL, 922_010_502);
        try {
            assertFalse(AgentLpqMovementSkillPolicy.allowsActiveTeleport(
                    agent(71_002, 922_010_500, event)));
        } finally {
            AgentLpqSessionRegistry.remove(session);
        }
    }

    @Test
    void activatesAnAuthoredTeleportEdgeWhileGlobalTeleportRemainsOff() {
        EventInstanceManager event = mock(EventInstanceManager.class);
        AgentLpqSession session = session(70_003, 71_003, event);
        Character agent = agent(71_003, 922_010_500, event);
        MapleMap map = mock(MapleMap.class);
        Skill teleport = mock(Skill.class);
        StatEffect effect = mock(StatEffect.class);
        AgentNavigationGraph.Edge edge = new AgentNavigationGraph.Edge(
                1, 2, AgentNavigationGraph.EdgeType.TELEPORT,
                new Point(0, 0), new Point(0, -150),
                0, 0, 0, 0, 0, 150);
        when(agent.getMap()).thenReturn(map);
        when(map.getFieldLimit()).thenReturn(0);
        when(agent.getSkillLevel(FPWizard.TELEPORT)).thenReturn(1);
        when(agent.getMaxMp()).thenReturn(1_000);
        when(agent.getMp()).thenReturn(1_000);
        when(teleport.getEffect(1)).thenReturn(effect);
        when(effect.getMpCon()).thenReturn((short) 10);
        try (MockedStatic<client.SkillFactory> skills = mockStatic(client.SkillFactory.class)) {
            skills.when(() -> client.SkillFactory.getSkill(FPWizard.TELEPORT)).thenReturn(teleport);
            assertTrue(AgentMovementSkillPolicy.canUseActivePath(agent, edge));
        } finally {
            AgentLpqSessionRegistry.remove(session);
        }
    }

    private static AgentLpqSession session(int operatorId, int characterId,
                                           EventInstanceManager event) {
        AgentLpqSession session = new AgentLpqSession(
                AgentLpqSession.Mode.TEST_OBSERVATION, 1L, operatorId, 5, 1_000L);
        session.addMember(characterId, AgentLpqMemberState.MemberType.AGENT);
        session.setLeadership(characterId, characterId);
        session.bindEventInstance(event);
        session.transition(AgentLpqSession.Phase.STAGE_5, 2_000L);
        session.member(characterId).assign(
                AgentLpqMemberState.Role.TELEPORT_RUNNER, 922_010_501);
        AgentLpqSessionRegistry.registerComplete(session);
        return session;
    }

    private static Character agent(int characterId, int mapId,
                                   EventInstanceManager event) {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(characterId);
        when(agent.getMapId()).thenReturn(mapId);
        when(agent.getEventInstance()).thenReturn(event);
        return agent;
    }
}
