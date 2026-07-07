package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotFarmAnchorStateRuntime;
import server.agents.integration.AgentBotMoveTargetStateRuntime;
import server.agents.integration.AgentBotNavigationDebugStateRuntime;
import server.agents.integration.AgentBotOwnerMotionStateRuntime;
import server.agents.integration.AgentBotPatrolStateRuntime;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTickStateMaintenanceServiceTest {
    @Test
    void updatesObservedLeaderMotionFromPreviousLeaderPosition() {
        AgentRuntimeEntry entry = entry();
        AgentBotOwnerMotionStateRuntime.rememberOwnerPosition(entry, new Point(10, 20));

        AgentTickStateMaintenanceService.updateObservedLeaderMotion(entry, new Point(13, 18));

        assertEquals(3, AgentBotOwnerMotionStateRuntime.observedOwnerStepX(entry));
        assertEquals(-2, AgentBotOwnerMotionStateRuntime.observedOwnerStepY(entry));
    }

    @Test
    void ignoresMissingLeaderMotionInputs() {
        AgentRuntimeEntry entry = entry();

        AgentTickStateMaintenanceService.updateObservedLeaderMotion(null, new Point(13, 18));
        AgentTickStateMaintenanceService.updateObservedLeaderMotion(entry, null);

        assertEquals(0, AgentBotOwnerMotionStateRuntime.observedOwnerStepX(entry));
        assertEquals(0, AgentBotOwnerMotionStateRuntime.observedOwnerStepY(entry));
    }

    @Test
    void keepsFarmAnchorOnSameMap() {
        AgentRuntimeEntry entry = entry();
        Character agent = agentOnMap(100000000);
        AgentBotFarmAnchorStateRuntime.setFarmAnchor(entry, new Point(50, 60), 100000000);
        AgentBotMoveTargetStateRuntime.setPreciseMoveTarget(entry, new Point(50, 60));

        AgentTickStateMaintenanceService.clearFarmAnchorOnMapChange(entry, agent);

        assertTrue(AgentBotFarmAnchorStateRuntime.hasFarmAnchor(entry));
        assertTrue(AgentBotMoveTargetStateRuntime.hasMoveTarget(entry));
    }

    @Test
    void clearsFarmAnchorAndPreciseMoveTargetOnMapChange() {
        AgentRuntimeEntry entry = entry();
        Character agent = agentOnMap(200000000);
        AgentBotFarmAnchorStateRuntime.setFarmAnchor(entry, new Point(50, 60), 100000000);
        AgentBotMoveTargetStateRuntime.setPreciseMoveTarget(entry, new Point(50, 60));

        AgentTickStateMaintenanceService.clearFarmAnchorOnMapChange(entry, agent);

        assertFalse(AgentBotFarmAnchorStateRuntime.hasFarmAnchor(entry));
        assertFalse(AgentBotMoveTargetStateRuntime.hasMoveTarget(entry));
    }

    @Test
    void clearsReachedNormalMoveTargetUsingConfiguredDistance() {
        Character agent = agentAt(new Point(95, 100));
        AgentRuntimeEntry entry = entry(agent);
        AgentBotMoveTargetStateRuntime.setMoveTarget(entry, new Point(100, 100), false);

        AgentTickStateMaintenanceService.clearReachedMoveTarget(entry, 10);

        assertFalse(AgentBotMoveTargetStateRuntime.hasMoveTarget(entry));
    }

    @Test
    void keepsUnreachedMoveTarget() {
        Character agent = agentAt(new Point(80, 100));
        AgentRuntimeEntry entry = entry(agent);
        AgentBotMoveTargetStateRuntime.setMoveTarget(entry, new Point(100, 100), false);

        AgentTickStateMaintenanceService.clearReachedMoveTarget(entry, 10);

        assertTrue(AgentBotMoveTargetStateRuntime.hasMoveTarget(entry));
    }

    @Test
    void preciseMoveTargetUsesPreciseArrivalDistance() {
        Character agent = agentAt(new Point(91, 100));
        AgentRuntimeEntry entry = entry(agent);
        AgentBotMoveTargetStateRuntime.setPreciseMoveTarget(entry, new Point(100, 100));

        AgentTickStateMaintenanceService.clearReachedMoveTarget(entry, 10);

        assertTrue(AgentBotMoveTargetStateRuntime.hasMoveTarget(entry));
    }

    @Test
    void keepsPatrolOnSameMap() {
        Character agent = agentOnMap(100000000);
        AgentRuntimeEntry entry = entry(agent);
        AgentBotPatrolStateRuntime.startPatrol(entry, 7, 100000000);

        AgentTickStateMaintenanceService.clearPatrolOnMapChange(entry, agent);

        assertTrue(AgentBotPatrolStateRuntime.hasPatrolRegion(entry));
    }

    @Test
    void clearsPatrolOnMapChange() {
        Character agent = agentOnMap(200000000);
        AgentRuntimeEntry entry = entry(agent);
        AgentBotPatrolStateRuntime.startPatrol(entry, 7, 100000000);

        AgentTickStateMaintenanceService.clearPatrolOnMapChange(entry, agent);

        assertFalse(AgentBotPatrolStateRuntime.hasPatrolRegion(entry));
    }

    @Test
    void marksPreciseNavigationTargetForPreciseMoveTarget() {
        AgentRuntimeEntry entry = entry();
        AgentBotMoveTargetStateRuntime.setPreciseMoveTarget(entry, new Point(100, 100));

        AgentTickStateMaintenanceService.markPreciseNavigationTargetIfNeeded(entry);

        assertTrue(AgentBotNavigationDebugStateRuntime.navPreciseTarget(entry));
    }

    @Test
    void doesNotMarkPreciseNavigationTargetForNormalMoveTarget() {
        AgentRuntimeEntry entry = entry();
        AgentBotMoveTargetStateRuntime.setMoveTarget(entry, new Point(100, 100), false);

        AgentTickStateMaintenanceService.markPreciseNavigationTargetIfNeeded(entry);

        assertFalse(AgentBotNavigationDebugStateRuntime.navPreciseTarget(entry));
    }

    private static Character agentOnMap(int mapId) {
        Character agent = mock(Character.class);
        when(agent.getMapId()).thenReturn(mapId);
        return agent;
    }

    private static Character agentAt(Point position) {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(position));
        return agent;
    }

    private static AgentRuntimeEntry entry() {
        return new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
    }

    private static AgentRuntimeEntry entry(Character agent) {
        return new AgentRuntimeEntry(agent, mock(Character.class), null);
    }
}
