package server.agents.plans;

import client.Character;
import client.inventory.InventoryType;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotScriptTaskStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.bots.BotEntry;

import java.awt.Point;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentScriptContextTest {
    @Test
    void queuesMoveAndLocalCombatMoveThroughAgentQueueService() {
        AgentScriptContext context = context();

        context.queueMoveTo(new Point(10, 20), true);
        AgentTask move = AgentBotScriptTaskStateRuntime.activateNextTask(context.entry());
        AgentBotScriptTaskStateRuntime.clearActiveTask(context.entry());
        context.queueMoveToWithLocalCombat(new Point(30, 40), false);
        AgentTask localMove = AgentBotScriptTaskStateRuntime.activateNextTask(context.entry());

        assertEquals(AgentTask.Type.MOVE_TO, move.type());
        assertTrue(move.precise());
        assertEquals(AgentTask.MoveCombatMode.NONE, move.moveCombatMode());
        assertEquals(AgentTask.Type.MOVE_TO, localMove.type());
        assertFalse(localMove.precise());
        assertEquals(AgentTask.MoveCombatMode.LOCAL_OPPORTUNITY, localMove.moveCombatMode());
    }

    @Test
    void queuesFollowGrindStopAndDropThroughAgentQueueService() {
        AgentScriptContext context = context();

        context.queueFollowUntilNearOwner(25);
        AgentTask follow = nextTask(context);
        context.queueGrind();
        AgentTask grind = nextTask(context);
        context.queueStop();
        AgentTask stop = nextTask(context);
        context.queueDrop(InventoryType.ETC, 4000000, (short) 2);
        AgentTask drop = nextTask(context);

        assertEquals(AgentTask.Type.FOLLOW_UNTIL_NEAR, follow.type());
        assertEquals(25, follow.nearPx());
        assertEquals(AgentTask.Type.GRIND, grind.type());
        assertEquals(AgentTask.Type.STOP, stop.type());
        assertEquals(AgentTask.Type.DROP_ITEM, drop.type());
        assertEquals(InventoryType.ETC, drop.inventoryType());
        assertEquals(4000000, drop.itemId());
        assertEquals((short) 2, drop.quantity());
    }

    @Test
    void tasksDoneReflectsAgentQueueState() {
        AgentScriptContext context = context();

        assertTrue(context.tasksDone());
        context.queueStop();

        assertFalse(context.tasksDone());
    }

    @Test
    void cheapMoveTargetUsesInjectedCheck() {
        AgentScriptContext context = context();
        AtomicReference<AgentRuntimeEntry> checkedEntry = new AtomicReference<>();
        AgentScriptContext checkedContext = new AgentScriptContext(
                context.entry(),
                context.bot(),
                context.owner(),
                (entry, point, maxPathCost, fallbackRangeX, fallbackRangeY) -> {
                    checkedEntry.set(entry);
                    assertEquals(new Point(10, 20), point);
                    assertEquals(100, maxPathCost);
                    assertEquals(30, fallbackRangeX);
                    assertEquals(40, fallbackRangeY);
                    return true;
                },
                (entry, type, itemId, quantity) -> false);

        assertTrue(checkedContext.isCheapMoveTarget(new Point(10, 20), 100, 30, 40));
        assertSame(context.entry(), checkedEntry.get());
    }

    @Test
    void dropItemUsesInjectedAction() {
        AgentScriptContext context = context();
        AtomicReference<AgentRuntimeEntry> droppedEntry = new AtomicReference<>();
        AgentScriptContext dropContext = new AgentScriptContext(
                context.entry(),
                context.bot(),
                context.owner(),
                (entry, point, maxPathCost, fallbackRangeX, fallbackRangeY) -> false,
                (entry, type, itemId, quantity) -> {
                    droppedEntry.set(entry);
                    assertEquals(InventoryType.ETC, type);
                    assertEquals(4000000, itemId);
                    assertEquals((short) 2, quantity);
                    return true;
                });

        assertTrue(dropContext.dropItem(InventoryType.ETC, 4000000, (short) 2));
        assertSame(context.entry(), droppedEntry.get());
    }

    private static AgentTask nextTask(AgentScriptContext context) {
        AgentTask task = AgentBotScriptTaskStateRuntime.activateNextTask(context.entry());
        AgentBotScriptTaskStateRuntime.clearActiveTask(context.entry());
        return task;
    }

    private static AgentScriptContext context() {
        Character owner = character(100);
        BotEntry entry = new BotEntry(character(200), owner, null);
        return new AgentScriptContext(entry, entry.bot(), owner, (ignoredEntry, ignoredPoint, ignoredMaxPathCost,
                ignoredFallbackRangeX, ignoredFallbackRangeY) -> false, (ignoredEntry, ignoredType, ignoredItemId,
                ignoredQuantity) -> false);
    }

    private static Character character(int id) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        return character;
    }
}
