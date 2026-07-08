package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentFarmAnchorStateRuntime;
import server.agents.capabilities.movement.AgentMoveTargetStateRuntime;
import server.agents.capabilities.navigation.AgentNavigationDebugStateRuntime;
import server.agents.runtime.AgentPatrolStateRuntime;

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
        AgentOwnerMotionStateRuntime.rememberOwnerPosition(entry, new Point(10, 20));

        AgentTickStateMaintenanceService.updateObservedLeaderMotion(entry, new Point(13, 18));

        assertEquals(3, AgentOwnerMotionStateRuntime.observedOwnerStepX(entry));
        assertEquals(-2, AgentOwnerMotionStateRuntime.observedOwnerStepY(entry));
    }

    @Test
    void ignoresMissingLeaderMotionInputs() {
        AgentRuntimeEntry entry = entry();

        AgentTickStateMaintenanceService.updateObservedLeaderMotion(null, new Point(13, 18));
        AgentTickStateMaintenanceService.updateObservedLeaderMotion(entry, null);

        assertEquals(0, AgentOwnerMotionStateRuntime.observedOwnerStepX(entry));
        assertEquals(0, AgentOwnerMotionStateRuntime.observedOwnerStepY(entry));
    }

    @Test
    void keepsFarmAnchorOnSameMap() {
        AgentRuntimeEntry entry = entry();
        Character agent = agentOnMap(100000000);
        AgentFarmAnchorStateRuntime.setFarmAnchor(entry, new Point(50, 60), 100000000);
        AgentMoveTargetStateRuntime.setPreciseMoveTarget(entry, new Point(50, 60));

        AgentTickStateMaintenanceService.clearFarmAnchorOnMapChange(entry, agent);

        assertTrue(AgentFarmAnchorStateRuntime.hasFarmAnchor(entry));
        assertTrue(AgentMoveTargetStateRuntime.hasMoveTarget(entry));
    }

    @Test
    void clearsFarmAnchorAndPreciseMoveTargetOnMapChange() {
        AgentRuntimeEntry entry = entry();
        Character agent = agentOnMap(200000000);
        AgentFarmAnchorStateRuntime.setFarmAnchor(entry, new Point(50, 60), 100000000);
        AgentMoveTargetStateRuntime.setPreciseMoveTarget(entry, new Point(50, 60));

        AgentTickStateMaintenanceService.clearFarmAnchorOnMapChange(entry, agent);

        assertFalse(AgentFarmAnchorStateRuntime.hasFarmAnchor(entry));
        assertFalse(AgentMoveTargetStateRuntime.hasMoveTarget(entry));
    }

    @Test
    void clearsReachedNormalMoveTargetUsingConfiguredDistance() {
        Character agent = agentAt(new Point(95, 100));
        AgentRuntimeEntry entry = entry(agent);
        AgentMoveTargetStateRuntime.setMoveTarget(entry, new Point(100, 100), false);

        AgentTickStateMaintenanceService.clearReachedMoveTarget(entry, 10);

        assertFalse(AgentMoveTargetStateRuntime.hasMoveTarget(entry));
    }

    @Test
    void keepsUnreachedMoveTarget() {
        Character agent = agentAt(new Point(80, 100));
        AgentRuntimeEntry entry = entry(agent);
        AgentMoveTargetStateRuntime.setMoveTarget(entry, new Point(100, 100), false);

        AgentTickStateMaintenanceService.clearReachedMoveTarget(entry, 10);

        assertTrue(AgentMoveTargetStateRuntime.hasMoveTarget(entry));
    }

    @Test
    void preciseMoveTargetUsesPreciseArrivalDistance() {
        Character agent = agentAt(new Point(91, 100));
        AgentRuntimeEntry entry = entry(agent);
        AgentMoveTargetStateRuntime.setPreciseMoveTarget(entry, new Point(100, 100));

        AgentTickStateMaintenanceService.clearReachedMoveTarget(entry, 10);

        assertTrue(AgentMoveTargetStateRuntime.hasMoveTarget(entry));
    }

    @Test
    void keepsPatrolOnSameMap() {
        Character agent = agentOnMap(100000000);
        AgentRuntimeEntry entry = entry(agent);
        AgentPatrolStateRuntime.startPatrol(entry, 7, 100000000);

        AgentTickStateMaintenanceService.clearPatrolOnMapChange(entry, agent);

        assertTrue(AgentPatrolStateRuntime.hasPatrolRegion(entry));
    }

    @Test
    void clearsPatrolOnMapChange() {
        Character agent = agentOnMap(200000000);
        AgentRuntimeEntry entry = entry(agent);
        AgentPatrolStateRuntime.startPatrol(entry, 7, 100000000);

        AgentTickStateMaintenanceService.clearPatrolOnMapChange(entry, agent);

        assertFalse(AgentPatrolStateRuntime.hasPatrolRegion(entry));
    }

    @Test
    void marksPreciseNavigationTargetForPreciseMoveTarget() {
        AgentRuntimeEntry entry = entry();
        AgentMoveTargetStateRuntime.setPreciseMoveTarget(entry, new Point(100, 100));

        AgentTickStateMaintenanceService.markPreciseNavigationTargetIfNeeded(entry);

        assertTrue(AgentNavigationDebugStateRuntime.navPreciseTarget(entry));
    }

    @Test
    void doesNotMarkPreciseNavigationTargetForNormalMoveTarget() {
        AgentRuntimeEntry entry = entry();
        AgentMoveTargetStateRuntime.setMoveTarget(entry, new Point(100, 100), false);

        AgentTickStateMaintenanceService.markPreciseNavigationTargetIfNeeded(entry);

        assertFalse(AgentNavigationDebugStateRuntime.navPreciseTarget(entry));
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
