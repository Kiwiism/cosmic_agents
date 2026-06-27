package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMovementTargetSnapshot;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentBotMovementTargetRuntimeTest {
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
    void conversionFromBotTargetSnapshotPreservesFields() {
        BotEntry entry = new BotEntry(null, null, null);
        BotManager.TargetSnapshot targetSnapshot = new BotManager.TargetSnapshot(
                new BotManager.FormationState(BotManager.FormationType.SPREAD, 70, 140),
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

        AgentMovementTargetSnapshot snapshot = BotMovementTargetSideEffects.from(entry, targetSnapshot);

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
        BotEntry entry = new BotEntry(null, null, null);
        entry.navTargetPos = new Point(90, 91);
        BotManager.TargetSnapshot targetSnapshot = new BotManager.TargetSnapshot(
                new BotManager.FormationState(BotManager.FormationType.STACK, 0, 120),
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

        AgentMovementTargetSnapshot snapshot = BotMovementTargetSideEffects.from(entry, targetSnapshot);

        assertEquals(new Point(90, 91), snapshot.steeringTargetPosition());
        assertEquals("nav-waypoint", snapshot.steeringTargetSource());
        entry.navTargetPos.x = 123;
        assertEquals(new Point(90, 91), snapshot.steeringTargetPosition());
    }
}
