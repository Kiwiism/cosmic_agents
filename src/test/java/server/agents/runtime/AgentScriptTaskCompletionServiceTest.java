package server.agents.runtime;

import client.Character;
import client.inventory.InventoryType;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotMoveTargetStateRuntime;
import server.agents.plans.AgentTask;
import server.agents.runtime.AgentRuntimeEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentScriptTaskCompletionServiceTest {
    @Test
    void moveToCompletesWhenMoveTargetCleared() {
        AgentRuntimeEntry entry = entryAt(100000000, new Point(0, 0));

        assertTrue(AgentScriptTaskCompletionService.isComplete(
                entry, AgentTask.moveTo(new Point(500, 0), false), 50, ignored -> null));
    }

    @Test
    void moveToCompletesWhenAgentIsNearTarget() {
        AgentRuntimeEntry entry = entryAt(100000000, new Point(100, 100));
        AgentBotMoveTargetStateRuntime.setMoveTarget(entry, new Point(110, 105), false);

        assertTrue(AgentScriptTaskCompletionService.isComplete(
                entry, AgentTask.moveTo(new Point(110, 105), false), 50, ignored -> null));
    }

    @Test
    void preciseMoveToUsesLegacyEightPixelDistance() {
        AgentRuntimeEntry entry = entryAt(100000000, new Point(100, 100));
        AgentBotMoveTargetStateRuntime.setMoveTarget(entry, new Point(109, 100), true);

        assertFalse(AgentScriptTaskCompletionService.isComplete(
                entry, AgentTask.moveTo(new Point(109, 100), true), 50, ignored -> null));
    }

    @Test
    void followUntilNearRequiresResolvedTargetSameMapAndNear() {
        Character target = character(300, 100000000, new Point(120, 100));
        AgentRuntimeEntry entry = entryAt(100000000, new Point(100, 100));

        assertTrue(AgentScriptTaskCompletionService.isComplete(
                entry, AgentTask.followUntilNear(target, 25), 50, ignored -> target));
    }

    @Test
    void followUntilNearIsIncompleteWhenTargetMissingFarOrDifferentMap() {
        Character target = character(300, 100000001, new Point(100, 100));
        AgentRuntimeEntry entry = entryAt(100000000, new Point(100, 100));

        assertFalse(AgentScriptTaskCompletionService.isComplete(
                entry, AgentTask.followUntilNear(target, 25), 50, ignored -> null));
        assertFalse(AgentScriptTaskCompletionService.isComplete(
                entry, AgentTask.followUntilNear(target, 25), 50, ignored -> target));

        Character farTarget = character(300, 100000000, new Point(200, 100));
        assertFalse(AgentScriptTaskCompletionService.isComplete(
                entry, AgentTask.followUntilNear(farTarget, 25), 50, ignored -> farTarget));
    }

    @Test
    void nonWaitingTasksCompleteImmediately() {
        AgentRuntimeEntry entry = entryAt(100000000, new Point(100, 100));
        Character target = character(300, 100000000, new Point(100, 100));

        assertTrue(AgentScriptTaskCompletionService.isComplete(entry, AgentTask.followOwner(), 50, ignored -> target));
        assertTrue(AgentScriptTaskCompletionService.isComplete(entry, AgentTask.follow(target), 50, ignored -> target));
        assertTrue(AgentScriptTaskCompletionService.isComplete(entry, AgentTask.grind(), 50, ignored -> target));
        assertTrue(AgentScriptTaskCompletionService.isComplete(entry, AgentTask.stop(), 50, ignored -> target));
        assertTrue(AgentScriptTaskCompletionService.isComplete(
                entry, AgentTask.dropItem(InventoryType.USE, 2000000, (short) 1), 50, ignored -> target));
    }

    private static AgentRuntimeEntry entryAt(int mapId, Point position) {
        return new AgentRuntimeEntry(character(200, mapId, position), mock(Character.class), null);
    }

    private static Character character(int id, int mapId, Point position) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        when(character.getMapId()).thenReturn(mapId);
        when(character.getPosition()).thenReturn(new Point(position));
        return character;
    }
}
