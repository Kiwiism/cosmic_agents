package server.agents.capabilities.movement;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.movement.AgentMovementStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentAirbornePhysicsServiceTest {
    @Test
    void stepAirborneMapsLegacyContinueResult() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(10, 20));
        when(agent.getHp()).thenReturn(1);
        when(agent.getMap()).thenReturn(null);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentMovementStateRuntime.setInAir(entry, true);
        AgentMovementPhysicsStateRuntime.setPhysicsPosition(entry, new Point(10, 20));

        AgentAirborneStepResult result = AgentAirbornePhysicsService.stepAirborne(entry, agent);

        assertEquals(AgentAirborneStepResult.CONTINUE, result);
        assertTrue(AgentMovementStateRuntime.inAir(entry));
    }

    @Test
    void stepAirborneAppliesAgentOwnedAirSteering() {
        Character agent = mock(Character.class);
        when(agent.getPosition()).thenReturn(new Point(10, 20));
        when(agent.getHp()).thenReturn(1);
        when(agent.getMap()).thenReturn(null);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentMovementStateRuntime.setInAir(entry, true);
        AgentMovementStateRuntime.setMoveDirection(entry, 1);
        AgentMovementPhysicsStateRuntime.setPhysicsPosition(entry, new Point(10, 20));

        AgentAirborneStepResult result = AgentAirbornePhysicsService.stepAirborne(entry, agent);

        assertEquals(AgentAirborneStepResult.CONTINUE, result);
        assertEquals(0.5, AgentMovementPhysicsStateRuntime.airSteerVelocityX(entry));
        assertEquals(1, AgentMovementStateRuntime.facingDirection(entry));
    }

    @Test
    void stepAirborneRecoversAgentThatHasFallenBelowKnownMapBounds() {
        MapleMap map = mock(MapleMap.class);
        when(map.getMapArea()).thenReturn(new Rectangle(0, 0, 1000, 500));
        when(map.getPointBelow(any(Point.class))).thenAnswer(invocation -> {
            Point probe = invocation.getArgument(0);
            return probe.x == 250 ? new Point(250, 400) : null;
        });

        Character agent = mock(Character.class);
        AtomicReference<Point> position = new AtomicReference<>(new Point(250, 565));
        when(agent.getPosition()).thenAnswer(invocation -> new Point(position.get()));
        doAnswer(invocation -> {
            position.set(new Point(invocation.getArgument(0)));
            return null;
        }).when(agent).setPosition(any(Point.class));
        when(agent.getHp()).thenReturn(1);
        when(agent.getMap()).thenReturn(map);

        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        AgentMovementStateRuntime.setInAir(entry, true);
        AgentMovementPhysicsStateRuntime.setPhysicsPosition(entry, position.get());

        AgentAirborneStepResult result = AgentAirbornePhysicsService.stepAirborne(entry, agent);

        assertEquals(AgentAirborneStepResult.LANDED, result);
        assertEquals(new Point(250, 400), position.get());
        assertFalse(AgentMovementStateRuntime.inAir(entry));
    }
}
