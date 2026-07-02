package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import server.agents.integration.AgentBotOwnerMotionStateRuntime;
import server.agents.integration.AgentBotTickStateRuntime;
import server.bots.BotEntry;
import server.maps.MapleMap;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class AgentMovementOnlyStepRuntimeTest {
    @Test
    void returnsFalseWithoutAgent() {
        boolean runAiTick = AgentMovementOnlyStepRuntime.stepMovementOnly(
                new BotEntry(null, mock(Character.class), null),
                1_000L,
                config());

        assertFalse(runAiTick);
    }

    @Test
    void preparesTickUpdatesLeaderMotionAndRunsMovementOnlyStep() {
        Character leader = character(100, "Leader", new Point(10, 20));
        Character agent = character(200, "Agent", new Point(1, 2));
        BotEntry entry = new BotEntry(agent, leader, null);
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

        try (MockedStatic<AgentTargetSnapshotRuntime> snapshots = mockStatic(AgentTargetSnapshotRuntime.class);
             MockedStatic<AgentMovementOnlyRuntime> movementOnly = mockStatic(AgentMovementOnlyRuntime.class)) {
            snapshots.when(() -> AgentTargetSnapshotRuntime.captureTargetSnapshot(entry)).thenReturn(snapshot);
            movementOnly.when(() -> AgentMovementOnlyRuntime.stepMovementOnly(
                            eq(entry),
                            eq(new Point(30, 40)),
                            eq(false),
                            any(Long.class),
                            any(),
                            any(AgentMovementOnlyRuntime.MovementOnlyConfig.class)))
                    .thenAnswer(invocation -> null);

            boolean runAiTick = AgentMovementOnlyStepRuntime.stepMovementOnly(entry, 1_000L, config());

            assertFalse(runAiTick);
            assertTrue(AgentBotTickStateRuntime.lastTickAtMs(entry) > 0);
            assertTrue(AgentBotOwnerMotionStateRuntime.lastOwnerPosition(entry).equals(new Point(10, 20)));
            movementOnly.verify(() -> AgentMovementOnlyRuntime.stepMovementOnly(
                    eq(entry),
                    eq(new Point(30, 40)),
                    eq(false),
                    any(Long.class),
                    any(),
                    any(AgentMovementOnlyRuntime.MovementOnlyConfig.class)));
        }
    }

    private static AgentMovementOnlyStepRuntime.MovementOnlyStepConfig config() {
        return new AgentMovementOnlyStepRuntime.MovementOnlyStepConfig(50, 100, 800, 1200, 2, 150, 35, true);
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
