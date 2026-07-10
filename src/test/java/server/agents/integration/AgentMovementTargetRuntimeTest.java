package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMovementTargetSnapshot;
import server.agents.capabilities.movement.AgentMovementTargetRuntime;
import server.agents.capabilities.navigation.AgentNavigationDebugStateRuntime;
import server.agents.capabilities.movement.AgentFormationService;
import server.agents.capabilities.movement.AgentTargetSnapshot;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentMovementTargetRuntimeTest {
    @Test
    void targetSnapshotDefensivelyCopiesPoints() {
        Point rawOwner = new Point(1, 2);
        Point primary = new Point(3, 4);
        AgentMovementTargetSnapshot snapshot = new AgentMovementTargetSnapshot(
                "STAGGER",
                60,
                120,
                rawOwner,
                null,
                "owner",
                null,
                null,
                null,
                null,
                null,
                primary,
                "owner-raw",
                primary,
                "owner-raw");

        rawOwner.x = 99;
        Point exposedPrimary = snapshot.primaryTargetPosition();
        exposedPrimary.x = 88;

        assertEquals(new Point(1, 2), snapshot.rawOwnerPosition());
        assertEquals(new Point(3, 4), snapshot.primaryTargetPosition());
    }

    @Test
    void targetSnapshotPreservesNullPoints() {
        AgentMovementTargetSnapshot snapshot = new AgentMovementTargetSnapshot(
                "STACK",
                0,
                120,
                null,
                null,
                "owner",
                null,
                null,
                null,
                null,
                null,
                null,
                "owner-raw",
                null,
                "owner-raw");

        assertNull(snapshot.rawOwnerPosition());
        assertNull(snapshot.followTargetPosition());
        assertNull(snapshot.primaryTargetPosition());
        assertNull(snapshot.steeringTargetPosition());
    }

    @Test
    void conversionFromAgentTargetSnapshotPreservesFields() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentTargetSnapshot targetSnapshot = new AgentTargetSnapshot(
                new AgentFormationService.FormationState(AgentFormationService.FormationType.SPREAD, 70, 140),
                new Point(10, 20),
                new Point(11, 21),
                "Leader",
                new Point(12, 22),
                new Point(13, 23),
                new Point(14, 24),
                new Point(15, 25),
                new Point(16, 26),
                new Point(17, 27),
                "move-target");

        AgentMovementTargetSnapshot snapshot = AgentMovementTargetRuntime.from(entry, targetSnapshot);

        assertEquals("SPREAD", snapshot.formationType());
        assertEquals(70, snapshot.formationPx());
        assertEquals(140, snapshot.formationSnapRange());
        assertEquals(new Point(10, 20), snapshot.rawOwnerPosition());
        assertEquals(new Point(11, 21), snapshot.followAnchorPosition());
        assertEquals("Leader", snapshot.followAnchorName());
        assertEquals(new Point(12, 22), snapshot.followBasePosition());
        assertEquals(new Point(13, 23), snapshot.followTargetPosition());
        assertEquals(new Point(14, 24), snapshot.moveTargetPosition());
        assertEquals(new Point(15, 25), snapshot.farmAnchorPosition());
        assertEquals(new Point(16, 26), snapshot.grindTargetPosition());
        assertEquals(new Point(17, 27), snapshot.primaryTargetPosition());
        assertEquals("move-target", snapshot.primaryTargetSource());
        assertEquals(new Point(17, 27), snapshot.steeringTargetPosition());
        assertEquals("move-target", snapshot.steeringTargetSource());
    }

    @Test
    void conversionUsesNavigationWaypointAsSteeringTargetWhenPresent() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Point navTargetPos = new Point(90, 91);
        AgentNavigationDebugStateRuntime.setNavTargetPosition(entry, navTargetPos);
        AgentTargetSnapshot targetSnapshot = new AgentTargetSnapshot(
                new AgentFormationService.FormationState(AgentFormationService.FormationType.STACK, 0, 120),
                new Point(1, 1),
                new Point(2, 2),
                "owner",
                new Point(3, 3),
                new Point(4, 4),
                null,
                null,
                null,
                new Point(5, 5),
                "follow-target");

        AgentMovementTargetSnapshot snapshot = AgentMovementTargetRuntime.from(entry, targetSnapshot);

        assertEquals(new Point(90, 91), snapshot.steeringTargetPosition());
        assertEquals("nav-waypoint", snapshot.steeringTargetSource());
        navTargetPos.x = 123;
        assertEquals(new Point(90, 91), snapshot.steeringTargetPosition());
    }

    @Test
    void conversionCanOverridePrimaryTargetForNavigationInput() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentTargetSnapshot targetSnapshot = new AgentTargetSnapshot(
                new AgentFormationService.FormationState(AgentFormationService.FormationType.STACK, 0, 120),
                new Point(1, 1),
                new Point(2, 2),
                "owner",
                new Point(3, 3),
                new Point(4, 4),
                null,
                null,
                null,
                new Point(5, 5),
                "follow-target");
        Point rawTarget = new Point(60, 61);

        AgentMovementTargetSnapshot snapshot = AgentMovementTargetRuntime.from(entry, new AgentTargetSnapshot(
                targetSnapshot.formation(),
                targetSnapshot.rawOwnerPos(),
                targetSnapshot.followAnchorPos(),
                targetSnapshot.followAnchorName(),
                targetSnapshot.followBasePos(),
                targetSnapshot.followTargetPos(),
                targetSnapshot.moveTargetPos(),
                targetSnapshot.farmAnchorPos(),
                targetSnapshot.grindTargetPos(),
                rawTarget,
                "nav-input"));

        rawTarget.x = 999;
        assertEquals(new Point(60, 61), snapshot.primaryTargetPosition());
        assertEquals("nav-input", snapshot.primaryTargetSource());
    }
}
