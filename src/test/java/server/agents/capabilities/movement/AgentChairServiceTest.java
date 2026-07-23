package server.agents.capabilities.movement;

import client.Character;
import constants.game.CharacterStance;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentChairServiceTest {
    @Test
    void buildsSoloMaplingCompatibleRightFacingChairMovement() {
        byte[] movement = AgentChairService.chairMovementData(
                new Point(0x1234, -2), CharacterStance.SIT_RIGHT_STANCE);

        assertArrayEquals(new byte[]{
                1, 11,
                0x34, 0x12,
                (byte) 0xFE, (byte) 0xFF,
                0, 0,
                20,
                0, 0
        }, movement);
    }

    @Test
    void buildsLeftFacingChairMovement() {
        byte[] movement = AgentChairService.chairMovementData(
                new Point(0, 0), CharacterStance.SIT_LEFT_STANCE);

        assertArrayEquals(new byte[]{1, 11, 0, 0, 0, 0, 0, 0, 21, 0, 0}, movement);
    }

    @Test
    void acceptsNativeMapSeatZero() {
        AtomicInteger chair = new AtomicInteger(-1);
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(41);
        when(agent.getPosition()).thenReturn(new Point(2_404, 525));
        when(agent.getChair()).thenAnswer(ignored -> chair.get());
        doAnswer(invocation -> {
            chair.set(invocation.getArgument(0));
            return null;
        }).when(agent).sitChair(0);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, agent, null);
        AgentMovementStateRuntime.setInAir(entry, true);

        assertTrue(AgentChairService.sit(entry, agent, 0));
        assertFalse(AgentMovementStateRuntime.inAir(entry));
    }

    @Test
    void nativeMapSeatSnapsToAuthoredSeatAnchorBeforeSitting() {
        AtomicInteger chair = new AtomicInteger(-1);
        AtomicReference<Point> position = new AtomicReference<>(new Point(2_394, 527));
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(42);
        when(agent.getPosition()).thenAnswer(ignored -> new Point(position.get()));
        doAnswer(invocation -> {
            position.set(new Point(invocation.getArgument(0)));
            return null;
        }).when(agent).setPosition(new Point(2_404, 525));
        when(agent.getChair()).thenAnswer(ignored -> chair.get());
        doAnswer(invocation -> {
            chair.set(invocation.getArgument(0));
            return null;
        }).when(agent).sitChair(0);
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, agent, null);

        assertTrue(AgentChairService.sitMapSeat(entry, agent, 0, new Point(2_404, 525)));
        assertEquals(new Point(2_404, 525), position.get());
    }
}
