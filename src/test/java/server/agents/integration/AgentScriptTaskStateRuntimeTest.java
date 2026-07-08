package server.agents.integration;

import server.agents.runtime.AgentRuntimeEntry;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentModeStateRuntime;
import server.agents.integration.AgentMoveTargetStateRuntime;
import server.agents.integration.AgentScriptTaskStateRuntime;
import server.agents.plans.AgentTask;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentScriptTaskStateRuntimeTest {
    @Test
    void queuesAndActivatesTasksInOrder() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        AgentTask first = AgentTask.moveTo(new Point(10, 20), true);
        AgentTask second = AgentTask.stop();

        AgentScriptTaskStateRuntime.queueTask(entry, first);
        AgentScriptTaskStateRuntime.queueTask(entry, second);

        assertTrue(AgentScriptTaskStateRuntime.hasQueuedTasks(entry));
        assertSame(first, AgentScriptTaskStateRuntime.activateNextTask(entry));
        assertSame(first, AgentScriptTaskStateRuntime.activateNextTask(entry));

        AgentScriptTaskStateRuntime.clearActiveTask(entry);
        assertSame(second, AgentScriptTaskStateRuntime.activateNextTask(entry));
    }

    @Test
    void clearingTasksBumpsActivityEpoch() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        int epoch = AgentScriptTaskStateRuntime.activityEpoch(entry);
        AgentScriptTaskStateRuntime.queueTask(entry, AgentTask.stop());

        AgentScriptTaskStateRuntime.clearTasksAndBumpEpoch(entry);

        assertEquals(epoch + 1, AgentScriptTaskStateRuntime.activityEpoch(entry));
        assertFalse(AgentScriptTaskStateRuntime.isCurrentActivityEpoch(entry, epoch));
        assertFalse(AgentScriptTaskStateRuntime.hasQueuedTasks(entry));
    }

    @Test
    void detectsActiveLocalOpportunityMove() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        Point point = new Point(10, 20);
        AgentTask task = AgentTask.moveTo(point, true, AgentTask.MoveCombatMode.LOCAL_OPPORTUNITY);

        AgentScriptTaskStateRuntime.queueTask(entry, task);
        AgentScriptTaskStateRuntime.activateNextTask(entry);
        AgentMoveTargetStateRuntime.setPreciseMoveTarget(entry, point);

        assertTrue(AgentScriptTaskStateRuntime.isActiveLocalOpportunityMoveTo(entry, point));

        AgentModeStateRuntime.setFollowing(entry, true);
        assertFalse(AgentScriptTaskStateRuntime.isActiveLocalOpportunityMoveTo(entry, point));
    }

    @Test
    void scriptRuntimeStateMovesThroughAgentBoundary() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        assertFalse(AgentScriptTaskStateRuntime.hasScriptId(entry));
        AgentScriptTaskStateRuntime.resetScript(entry, "kpq-stage-1");

        assertTrue(AgentScriptTaskStateRuntime.hasScriptId(entry));
        assertEquals("kpq-stage-1", AgentScriptTaskStateRuntime.scriptId(entry));
        assertEquals(0, AgentScriptTaskStateRuntime.scriptStepIndex(entry));
        assertFalse(AgentScriptTaskStateRuntime.scriptStepEntered(entry));

        AgentScriptTaskStateRuntime.markScriptStepEntered(entry);
        assertTrue(AgentScriptTaskStateRuntime.scriptStepEntered(entry));

        AgentScriptTaskStateRuntime.advanceScriptStep(entry);
        assertEquals(1, AgentScriptTaskStateRuntime.scriptStepIndex(entry));
        assertFalse(AgentScriptTaskStateRuntime.scriptStepEntered(entry));

        AgentScriptTaskStateRuntime.setScriptInt(entry, "coupons", 7);
        assertEquals(7, AgentScriptTaskStateRuntime.scriptInt(entry, "coupons"));
        assertEquals(0, AgentScriptTaskStateRuntime.scriptInt(entry, "missing"));

        AgentScriptTaskStateRuntime.waitScriptUntil(entry, 500L);
        assertFalse(AgentScriptTaskStateRuntime.scriptWaitDone(entry, 499L));
        assertTrue(AgentScriptTaskStateRuntime.scriptWaitDone(entry, 500L));

        AgentScriptTaskStateRuntime.resetScript(entry, null);
        assertFalse(AgentScriptTaskStateRuntime.hasScriptId(entry));
        assertEquals(0, AgentScriptTaskStateRuntime.scriptInt(entry, "coupons"));
    }
}
