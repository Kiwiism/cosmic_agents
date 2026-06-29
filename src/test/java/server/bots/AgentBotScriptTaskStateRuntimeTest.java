package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotModeStateRuntime;
import server.agents.integration.AgentBotMoveTargetStateRuntime;
import server.agents.integration.AgentBotScriptTaskStateRuntime;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBotScriptTaskStateRuntimeTest {
    @Test
    void queuesAndActivatesTasksInOrder() {
        BotEntry entry = new BotEntry(null, null, null);
        BotTask first = BotTask.moveTo(new Point(10, 20), true);
        BotTask second = BotTask.stop();

        AgentBotScriptTaskStateRuntime.queueTask(entry, first);
        AgentBotScriptTaskStateRuntime.queueTask(entry, second);

        assertTrue(AgentBotScriptTaskStateRuntime.hasQueuedTasks(entry));
        assertSame(first, AgentBotScriptTaskStateRuntime.activateNextTask(entry));
        assertSame(first, AgentBotScriptTaskStateRuntime.activateNextTask(entry));

        AgentBotScriptTaskStateRuntime.clearActiveTask(entry);
        assertSame(second, AgentBotScriptTaskStateRuntime.activateNextTask(entry));
    }

    @Test
    void clearingTasksBumpsActivityEpoch() {
        BotEntry entry = new BotEntry(null, null, null);
        int epoch = AgentBotScriptTaskStateRuntime.activityEpoch(entry);
        AgentBotScriptTaskStateRuntime.queueTask(entry, BotTask.stop());

        AgentBotScriptTaskStateRuntime.clearTasksAndBumpEpoch(entry);

        assertEquals(epoch + 1, AgentBotScriptTaskStateRuntime.activityEpoch(entry));
        assertFalse(AgentBotScriptTaskStateRuntime.isCurrentActivityEpoch(entry, epoch));
        assertFalse(AgentBotScriptTaskStateRuntime.hasQueuedTasks(entry));
    }

    @Test
    void detectsActiveLocalOpportunityMove() {
        BotEntry entry = new BotEntry(null, null, null);
        Point point = new Point(10, 20);
        BotTask task = BotTask.moveTo(point, true, BotTask.MoveCombatMode.LOCAL_OPPORTUNITY);

        AgentBotScriptTaskStateRuntime.queueTask(entry, task);
        AgentBotScriptTaskStateRuntime.activateNextTask(entry);
        AgentBotMoveTargetStateRuntime.setPreciseMoveTarget(entry, point);

        assertTrue(AgentBotScriptTaskStateRuntime.isActiveLocalOpportunityMoveTo(entry, point));

        AgentBotModeStateRuntime.setFollowing(entry, true);
        assertFalse(AgentBotScriptTaskStateRuntime.isActiveLocalOpportunityMoveTo(entry, point));
    }

    @Test
    void scriptRuntimeStateMovesThroughAgentBoundary() {
        BotEntry entry = new BotEntry(null, null, null);

        assertFalse(AgentBotScriptTaskStateRuntime.hasScriptId(entry));
        AgentBotScriptTaskStateRuntime.resetScript(entry, "kpq-stage-1");

        assertTrue(AgentBotScriptTaskStateRuntime.hasScriptId(entry));
        assertEquals("kpq-stage-1", AgentBotScriptTaskStateRuntime.scriptId(entry));
        assertEquals(0, AgentBotScriptTaskStateRuntime.scriptStepIndex(entry));
        assertFalse(AgentBotScriptTaskStateRuntime.scriptStepEntered(entry));

        AgentBotScriptTaskStateRuntime.markScriptStepEntered(entry);
        assertTrue(AgentBotScriptTaskStateRuntime.scriptStepEntered(entry));

        AgentBotScriptTaskStateRuntime.advanceScriptStep(entry);
        assertEquals(1, AgentBotScriptTaskStateRuntime.scriptStepIndex(entry));
        assertFalse(AgentBotScriptTaskStateRuntime.scriptStepEntered(entry));

        AgentBotScriptTaskStateRuntime.setScriptInt(entry, "coupons", 7);
        assertEquals(7, AgentBotScriptTaskStateRuntime.scriptInt(entry, "coupons"));
        assertEquals(0, AgentBotScriptTaskStateRuntime.scriptInt(entry, "missing"));

        AgentBotScriptTaskStateRuntime.waitScriptUntil(entry, 500L);
        assertFalse(AgentBotScriptTaskStateRuntime.scriptWaitDone(entry, 499L));
        assertTrue(AgentBotScriptTaskStateRuntime.scriptWaitDone(entry, 500L));

        AgentBotScriptTaskStateRuntime.resetScript(entry, null);
        assertFalse(AgentBotScriptTaskStateRuntime.hasScriptId(entry));
        assertEquals(0, AgentBotScriptTaskStateRuntime.scriptInt(entry, "coupons"));
    }
}
