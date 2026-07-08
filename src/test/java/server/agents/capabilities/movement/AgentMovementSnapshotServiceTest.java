package server.agents.capabilities.movement;

import client.Character;
import constants.game.CharacterStance;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentMovementSnapshotServiceTest {
    @Test
    void currentSnapshotPreservesLegacyPacketFacingMovementState() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(10, 20));
        when(agent.getHp()).thenReturn(1);
        when(agent.getStance()).thenReturn(CharacterStance.STAND_LEFT_STANCE);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentMovementStateRuntime.setFacingDirection(entry, 1);
        AgentMovementStateRuntime.setMovementVelocity(entry, 4, -2);

        AgentMovementPacketSnapshot snapshot = AgentMovementSnapshotService.currentSnapshot(entry);

        assertEquals(4, snapshot.velX());
        assertEquals(-2, snapshot.velY());
        assertEquals(CharacterStance.STAND_RIGHT_STANCE, snapshot.stance());
        verify(agent).setStance(CharacterStance.STAND_RIGHT_STANCE);
    }
}
