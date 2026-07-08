package server.agents.runtime;

import server.agents.plans.AgentScriptTaskStateRuntime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.plans.AgentTask;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentScriptTaskTickServiceTest {
    @Test
    void skipsWhenEntryHasNoAgentCharacter() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, mock(Character.class), null);
        AtomicInteger starts = new AtomicInteger();

        AgentScriptTaskTickService.tick(entry, (ignoredEntry, ignoredTask) -> starts.incrementAndGet(), (ignoredEntry, ignoredTask) -> true);

        assertEquals(0, starts.get());
    }

    @Test
    void activatesAndStartsNextTaskWhenNoneActive() {
        AgentRuntimeEntry entry = entry();
        AgentTask task = AgentTask.stop();
        AgentScriptTaskStateRuntime.queueTask(entry, task);
        List<AgentTask> started = new ArrayList<>();

        AgentScriptTaskTickService.tick(entry, (ignoredEntry, startedTask) -> started.add(startedTask), (ignoredEntry, ignoredTask) -> false);

        assertEquals(List.of(task), started);
        assertSame(task, AgentScriptTaskStateRuntime.activeTask(entry));
    }

    @Test
    void keepsIncompleteActiveTask() {
        AgentRuntimeEntry entry = entry();
        AgentTask task = AgentTask.grind();
        AgentScriptTaskStateRuntime.queueTask(entry, task);

        AgentScriptTaskTickService.tick(entry, (ignoredEntry, ignoredTask) -> {}, (ignoredEntry, ignoredTask) -> false);

        assertSame(task, AgentScriptTaskStateRuntime.activeTask(entry));
    }

    @Test
    void clearsCompletedTasksAndStartsFollowingQueuedTasksInSameTick() {
        AgentRuntimeEntry entry = entry();
        AgentTask first = AgentTask.stop();
        AgentTask second = AgentTask.grind();
        AgentScriptTaskStateRuntime.queueTask(entry, first);
        AgentScriptTaskStateRuntime.queueTask(entry, second);
        List<AgentTask> started = new ArrayList<>();

        AgentScriptTaskTickService.tick(entry, (ignoredEntry, task) -> started.add(task), (ignoredEntry, ignoredTask) -> true);

        assertEquals(List.of(first, second), started);
        assertNull(AgentScriptTaskStateRuntime.activeTask(entry));
    }

    private static AgentRuntimeEntry entry() {
        return new AgentRuntimeEntry(character(200), mock(Character.class), null);
    }

    private static Character character(int id) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        return character;
    }
}
