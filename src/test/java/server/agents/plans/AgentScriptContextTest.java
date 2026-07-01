package server.agents.plans;

import client.Character;
import client.inventory.InventoryType;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotScriptTaskStateRuntime;
import server.bots.BotEntry;
import server.bots.BotManager;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private static AgentTask nextTask(AgentScriptContext context) {
        AgentTask task = AgentBotScriptTaskStateRuntime.activateNextTask(context.entry());
        AgentBotScriptTaskStateRuntime.clearActiveTask(context.entry());
        return task;
    }

    private static AgentScriptContext context() {
        Character owner = character(100);
        BotEntry entry = new BotEntry(character(200), owner, null);
        return new AgentScriptContext(entry, entry.bot(), owner, mock(BotManager.class));
    }

    private static Character character(int id) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        return character;
    }
}
