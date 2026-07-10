package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.capabilities.follow.AgentOwnerMotionStateRuntime;
import server.agents.capabilities.movement.AgentFormationService;
import server.agents.capabilities.movement.AgentTargetSnapshot;
import server.maps.MapleMap;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentMovementOnlyTickCoordinatorTest {
    @Test
    void returnsFalseWithoutAgent() {
        boolean runAiTick = AgentMovementOnlyTickCoordinator.stepMovementOnly(
                new AgentRuntimeEntry(null, mock(Character.class), null),
                1_000L,
                config());

        assertFalse(runAiTick);
    }

    @Test
    void preparesTickUpdatesLeaderMotionAndRunsMovementOnlyStep() {
        Character leader = character(100, "Leader", new Point(10, 20));
        Character agent = character(200, "Agent", new Point(1, 2));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, leader, null);
        AgentTargetSnapshot snapshot = new AgentTargetSnapshot(
                AgentFormationService.defaultStagger(60, 120),
                new Point(10, 20),
                new Point(10, 20),
                "Leader",
                new Point(70, 20),
                new Point(70, 20),
                null,
                null,
                null,
                new Point(30, 40),
                "test-target");

        try (MockedStatic<AgentTargetSnapshotCoordinator> snapshots = mockStatic(AgentTargetSnapshotCoordinator.class);
             MockedStatic<AgentMovementOnlyModeCoordinator> movementOnly = mockStatic(AgentMovementOnlyModeCoordinator.class)) {
            snapshots.when(() -> AgentTargetSnapshotCoordinator.captureTargetSnapshot(entry)).thenReturn(snapshot);
            movementOnly.when(() -> AgentMovementOnlyModeCoordinator.stepMovementOnly(
                            eq(entry),
                            eq(new Point(30, 40)),
                            eq(false),
                            any(Long.class),
                            any(),
                            any(AgentMovementOnlyModeCoordinator.ModeConfig.class)))
                    .thenAnswer(invocation -> null);

            boolean runAiTick = AgentMovementOnlyTickCoordinator.stepMovementOnly(entry, 1_000L, config());

            assertFalse(runAiTick);
            assertTrue(AgentTickStateRuntime.lastTickAtMs(entry) > 0);
            assertTrue(AgentOwnerMotionStateRuntime.lastOwnerPosition(entry).equals(new Point(10, 20)));
            movementOnly.verify(() -> AgentMovementOnlyModeCoordinator.stepMovementOnly(
                    eq(entry),
                    eq(new Point(30, 40)),
                    eq(false),
                    any(Long.class),
                    any(),
                    any(AgentMovementOnlyModeCoordinator.ModeConfig.class)));
        }
    }

    @Test
    void defaultPointStepUsesRuntimeConfigAndRunsMovementOnlyStep() {
        Character leader = character(100, "Leader", new Point(10, 20));
        Character agent = character(200, "Agent", new Point(1, 2));
        AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, leader, null);
        Point target = new Point(30, 40);

        try (MockedStatic<AgentMovementOnlyModeCoordinator> movementOnly = mockStatic(AgentMovementOnlyModeCoordinator.class)) {
            movementOnly.when(() -> AgentMovementOnlyModeCoordinator.stepMovementOnly(
                            eq(entry),
                            eq(target),
                            eq(true),
                            any(Long.class),
                            any(),
                            any(AgentMovementOnlyModeCoordinator.ModeConfig.class)))
                    .thenAnswer(invocation -> null);

            AgentMovementOnlyTickCoordinator.stepMovementOnly(entry, target, true);

            movementOnly.verify(() -> AgentMovementOnlyModeCoordinator.stepMovementOnly(
                    eq(entry),
                    eq(target),
                    eq(true),
                    any(Long.class),
                    any(),
                    any(AgentMovementOnlyModeCoordinator.ModeConfig.class)));
        }
    }

    private static AgentMovementOnlyTickCoordinator.TickConfig config() {
        return new AgentMovementOnlyTickCoordinator.TickConfig(50, 100, 800, 1200, 2, 150, 35, true);
    }

    private static Character character(int id, String name, Point position) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getName()).thenReturn(name);
        when(character.isLoggedinWorld()).thenReturn(true);
        when(character.getPosition()).thenReturn(new Point(position));
        when(character.getMapId()).thenReturn(100000000);
        when(character.getMap()).thenReturn(mock(MapleMap.class));
        return character;
    }
}
