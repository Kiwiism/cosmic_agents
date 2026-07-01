package server.agents.runtime;

import client.Character;
import client.inventory.InventoryType;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotScriptTaskStateRuntime;
import server.agents.plans.AgentTask;
import server.bots.BotEntry;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentScriptTaskQueueServiceTest {
    @Test
    void queueTaskIsNullSafe() {
        BotEntry entry = entry();

        AgentScriptTaskQueueService.queueTask(null, AgentTask.stop());
        AgentScriptTaskQueueService.queueTask(entry, null);

        assertFalse(AgentScriptTaskQueueService.hasQueuedTasks(entry));
    }

    @Test
    void clearTasksBumpsEpochAndClearsQueue() {
        BotEntry entry = entry();
        int before = AgentBotScriptTaskStateRuntime.activityEpoch(entry);
        AgentScriptTaskQueueService.queueTask(entry, AgentTask.stop());

        AgentScriptTaskQueueService.clearTasks(entry);

        assertFalse(AgentScriptTaskQueueService.hasQueuedTasks(entry));
        assertNotEquals(before, AgentBotScriptTaskStateRuntime.activityEpoch(entry));
    }

    @Test
    void queuesMoveToWithCombatMode() {
        BotEntry entry = entry();
        Point target = new Point(10, 20);

        AgentScriptTaskQueueService.queueMoveTo(entry, target, true, AgentTask.MoveCombatMode.LOCAL_OPPORTUNITY);

        AgentTask task = AgentBotScriptTaskStateRuntime.activateNextTask(entry);
        assertEquals(AgentTask.Type.MOVE_TO, task.type());
        assertEquals(target, task.point());
        assertTrue(task.precise());
        assertEquals(AgentTask.MoveCombatMode.LOCAL_OPPORTUNITY, task.moveCombatMode());
    }

    @Test
    void queuesMoveThenDropItemInOrder() {
        BotEntry entry = entry();
        Point target = new Point(10, 20);

        AgentScriptTaskQueueService.queueMoveThenDropItem(entry, target, false, InventoryType.USE, 2000000, (short) 2);

        AgentTask move = AgentBotScriptTaskStateRuntime.activateNextTask(entry);
        AgentBotScriptTaskStateRuntime.clearActiveTask(entry);
        AgentTask drop = AgentBotScriptTaskStateRuntime.activateNextTask(entry);

        assertEquals(AgentTask.Type.MOVE_TO, move.type());
        assertEquals(target, move.point());
        assertEquals(AgentTask.Type.DROP_ITEM, drop.type());
        assertEquals(InventoryType.USE, drop.inventoryType());
        assertEquals(2000000, drop.itemId());
        assertEquals((short) 2, drop.quantity());
    }

    @Test
    void queuesFollowThenDropItemInOrder() {
        BotEntry entry = entry();
        Character target = character(300);

        AgentScriptTaskQueueService.queueFollowThenDropItem(entry, target, 25, InventoryType.ETC, 4000000, (short) 1);

        AgentTask follow = AgentBotScriptTaskStateRuntime.activateNextTask(entry);
        AgentBotScriptTaskStateRuntime.clearActiveTask(entry);
        AgentTask drop = AgentBotScriptTaskStateRuntime.activateNextTask(entry);

        assertEquals(AgentTask.Type.FOLLOW_UNTIL_NEAR, follow.type());
        assertEquals(target.getId(), follow.targetCharacterId());
        assertEquals(25, follow.nearPx());
        assertEquals(AgentTask.Type.DROP_ITEM, drop.type());
        assertEquals(InventoryType.ETC, drop.inventoryType());
        assertEquals(4000000, drop.itemId());
    }

    private static BotEntry entry() {
        return new BotEntry(character(200), character(100), null);
    }

    private static Character character(int id) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        return character;
    }
}
