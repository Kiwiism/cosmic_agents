package server.agents.capabilities.navigation;

import client.Character;
import client.Skill;
import constants.skills.FPWizard;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import scripting.event.EventInstanceManager;
import server.StatEffect;
import server.agents.capabilities.partyquest.lpq.AgentLpqMemberState;
import server.agents.capabilities.partyquest.lpq.AgentLpqSession;
import server.agents.capabilities.partyquest.lpq.AgentLpqSessionRegistry;
import server.maps.MapleMap;

import java.awt.Point;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentLpqTeleportNavigationTest {
    @Test
    void assignedRunnerCanPlanToTheTeleportRoomAndUseItsTeleportEdges() {
        MapleMap map = AgentNavigationMapLoader.loadMapGeometry(922_010_500);
        AgentNavigationGraph graph = AgentNavigationGraphService.rebuildGraph(map);
        Point start = new Point(-208, -219);
        Point target = new Point(-145, -1_292);
        int startRegion = graph.findRegionId(map, start);
        int targetRegion = graph.findRegionId(map, target);
        EventInstanceManager event = mock(EventInstanceManager.class);
        AgentLpqSession session = session(event);
        Character agent = agent(map, event, start);
        Skill teleport = mock(Skill.class);
        StatEffect effect = mock(StatEffect.class);
        when(teleport.getEffect(1)).thenReturn(effect);
        when(effect.getMpCon()).thenReturn((short) 10);
        try (MockedStatic<client.SkillFactory> skills = mockStatic(client.SkillFactory.class)) {
            skills.when(() -> client.SkillFactory.getSkill(FPWizard.TELEPORT)).thenReturn(teleport);
            AgentNavigationPathService.MovementPathSelection selection =
                    AgentNavigationPathService.findNextEdgeSelectionVaried(
                            graph, agent, start, startRegion, targetRegion, target,
                            null, edge -> true);

            assertEquals(AgentNavigationPathService.RouteCompleteness.COMPLETE,
                    selection.completeness());
            assertTrue(selection.path().stream()
                    .anyMatch(edge -> edge.type == AgentNavigationGraph.EdgeType.TELEPORT));

            MapleMap room = AgentNavigationMapLoader.loadMapGeometry(922_010_501);
            AgentNavigationGraph roomGraph = AgentNavigationGraphService.rebuildGraph(room);
            Point roomStart = new Point(-194, -3_513);
            Point roomExit = new Point(193, -262);
            when(agent.getMapId()).thenReturn(922_010_501);
            when(agent.getMap()).thenReturn(room);
            AgentNavigationPathService.MovementPathSelection roomSelection =
                    AgentNavigationPathService.findNextEdgeSelectionVaried(
                            roomGraph, agent, roomStart,
                            roomGraph.findRegionId(room, roomStart),
                            roomGraph.findRegionId(room, roomExit), roomExit,
                            null, edge -> true);

            assertEquals(AgentNavigationPathService.RouteCompleteness.COMPLETE,
                    roomSelection.completeness());
            AgentNavigationGraph.Edge roomTeleport = roomGraph.regions.stream()
                    .flatMap(region -> roomGraph.getOutgoing(region.id).stream())
                    .filter(edge -> edge.type == AgentNavigationGraph.EdgeType.TELEPORT)
                    .findFirst().orElseThrow();
            assertTrue(AgentMovementSkillPolicy.canUseActivePath(agent, roomTeleport));

            boolean reactorRouteUsesTeleport = false;
            for (Point reactor : java.util.List.of(
                    new Point(-96, -1_914), new Point(101, -2_132),
                    new Point(-78, -2_350), new Point(-106, -3_379))) {
                AgentNavigationPathService.MovementPathSelection reactorSelection =
                        AgentNavigationPathService.findNextEdgeSelectionVaried(
                                roomGraph, agent, roomStart,
                                roomGraph.findRegionId(room, roomStart),
                                roomGraph.findRegionId(room, reactor), reactor,
                                null, edge -> true);
                assertEquals(AgentNavigationPathService.RouteCompleteness.COMPLETE,
                        reactorSelection.completeness(), "reactor " + reactor);
                reactorRouteUsesTeleport |= reactorSelection.path().stream()
                        .anyMatch(edge -> edge.type == AgentNavigationGraph.EdgeType.TELEPORT);
            }
            assertTrue(reactorRouteUsesTeleport);

            Point portalTwo = room.getPortal(2).getPosition();
            int portalTwoRegion = roomGraph.findRegionId(room, portalTwo);
            assertTrue(AgentNavigationRouteOverlayPolicy.applies(roomGraph, portalTwoRegion));
            Map<Point, List<Integer>> exitRegions = Map.of(
                    new Point(-96, -1_914), List.of(10, 9, 8, 7, 6, 5, 15, 4, 3),
                    new Point(101, -2_132), List.of(8, 7, 6, 5, 15, 4, 3),
                    new Point(-78, -2_350), List.of(6, 5, 15, 4, 3),
                    new Point(-106, -3_379), List.of(4, 3));
            for (Map.Entry<Point, List<Integer>> expected : exitRegions.entrySet()) {
                Point reactor = expected.getKey();
                int reactorRegion = roomGraph.findRegionId(room, reactor);
                AgentNavigationPathService.MovementPathSelection exitSelection =
                        AgentNavigationPathService.findNextEdgeSelectionVaried(
                                roomGraph, agent, reactor, reactorRegion,
                                portalTwoRegion, portalTwo, null, edge -> true);
                assertEquals(AgentNavigationPathService.RouteCompleteness.COMPLETE,
                        exitSelection.completeness());
                java.util.ArrayList<Integer> actual = new java.util.ArrayList<>();
                actual.add(reactorRegion);
                exitSelection.path().forEach(edge -> actual.add(edge.toRegionId));
                assertEquals(expected.getValue(), actual);
            }
        } finally {
            AgentLpqSessionRegistry.remove(session);
        }
    }

    private static AgentLpqSession session(EventInstanceManager event) {
        AgentLpqSession session = new AgentLpqSession(
                AgentLpqSession.Mode.TEST_OBSERVATION, 1L, 70_004, 5, 1_000L);
        session.addMember(71_004, AgentLpqMemberState.MemberType.AGENT);
        session.setLeadership(71_004, 71_004);
        session.bindEventInstance(event);
        session.transition(AgentLpqSession.Phase.STAGE_5, 2_000L);
        session.member(71_004).assign(
                AgentLpqMemberState.Role.TELEPORT_RUNNER, 922_010_501);
        AgentLpqSessionRegistry.registerComplete(session);
        return session;
    }

    private static Character agent(MapleMap map, EventInstanceManager event, Point position) {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(71_004);
        when(agent.getMapId()).thenReturn(922_010_500);
        when(agent.getMap()).thenReturn(map);
        when(agent.getPosition()).thenReturn(position);
        when(agent.getEventInstance()).thenReturn(event);
        when(agent.getSkillLevel(FPWizard.TELEPORT)).thenReturn(1);
        when(agent.getMaxMp()).thenReturn(1_000);
        when(agent.getMp()).thenReturn(1_000);
        return agent;
    }
}
