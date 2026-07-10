package server.agents.capabilities.movement;

import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentMovementTargetRuntimeTest {
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
}
