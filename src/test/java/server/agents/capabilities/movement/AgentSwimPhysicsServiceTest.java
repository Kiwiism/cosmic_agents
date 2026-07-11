package server.agents.capabilities.movement;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.Foothold;
import server.maps.FootholdTree;
import server.maps.MapleMap;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentSwimPhysicsServiceTest {
    @Test
    void applySwimMotionPreservesLegacySwimStateTransition() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(10, 20));
        when(agent.getHp()).thenReturn(1);
        when(agent.getMap()).thenReturn(null);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);

        AgentSwimPhysicsService.applySwimMotion(entry);

        assertTrue(AgentSwimStateRuntime.swimming(entry));
    }

    @Test
    void applySwimMotionAppliesAgentOwnedHorizontalIntent() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(10, 20));
        when(agent.getHp()).thenReturn(1);
        when(agent.getMap()).thenReturn(null);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentSwimStateRuntime.setSwimMoveDirection(entry, 1);

        AgentSwimPhysicsService.applySwimMotion(entry);

        assertEquals(1, AgentMovementStateRuntime.facingDirection(entry));
        assertTrue(AgentMovementPhysicsStateRuntime.horizontalSpeed(entry) > 0.0);
        assertTrue(AgentSwimStateRuntime.swimming(entry));
    }

    @Test
    void applySwimMotionRecordsSteeredWallCollision() {
        MapleMap map = new MapleMap(910000013, 0, 0, 910000013, 1.0f);
        FootholdTree footholds = new FootholdTree(new Point(-2000, -2000), new Point(2000, 2000));
        footholds.insert(new Foothold(new Point(50, 0), new Point(50, 100), 1));
        map.setFootholds(footholds);

        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(40, 50));
        when(agent.getHp()).thenReturn(1);
        when(agent.getMap()).thenReturn(map);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentSwimStateRuntime.setSwimming(entry, true);
        AgentSwimStateRuntime.setSwimMoveDirection(entry, 1);
        AgentMovementPhysicsStateRuntime.setPhysicsPosition(entry, 40, 50);
        AgentMovementPhysicsStateRuntime.setHorizontalSpeed(entry, 400);

        AgentSwimPhysicsService.applySwimMotion(entry);

        assertTrue(AgentSwimStateRuntime.swimWallBlocked(entry));
    }
}
