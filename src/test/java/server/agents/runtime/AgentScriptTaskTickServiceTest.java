package server.agents.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotScriptTaskStateRuntime;
import server.agents.plans.AgentTask;
import server.bots.BotEntry;

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
        BotEntry entry = new BotEntry(null, mock(Character.class), null);
        AtomicInteger starts = new AtomicInteger();

        AgentScriptTaskTickService.tick(entry, (ignoredEntry, ignoredTask) -> starts.incrementAndGet(), (ignoredEntry, ignoredTask) -> true);

        assertEquals(0, starts.get());
    }

    @Test
    void activatesAndStartsNextTaskWhenNoneActive() {
        BotEntry entry = entry();
        AgentTask task = AgentTask.stop();
        AgentBotScriptTaskStateRuntime.queueTask(entry, task);
        List<AgentTask> started = new ArrayList<>();

        AgentScriptTaskTickService.tick(entry, (ignoredEntry, startedTask) -> started.add(startedTask), (ignoredEntry, ignoredTask) -> false);

        assertEquals(List.of(task), started);
        assertSame(task, AgentBotScriptTaskStateRuntime.activeTask(entry));
    }

    @Test
    void keepsIncompleteActiveTask() {
        BotEntry entry = entry();
        AgentTask task = AgentTask.grind();
        AgentBotScriptTaskStateRuntime.queueTask(entry, task);

        AgentScriptTaskTickService.tick(entry, (ignoredEntry, ignoredTask) -> {}, (ignoredEntry, ignoredTask) -> false);

        assertSame(task, AgentBotScriptTaskStateRuntime.activeTask(entry));
    }

    @Test
    void clearsCompletedTasksAndStartsFollowingQueuedTasksInSameTick() {
        BotEntry entry = entry();
        AgentTask first = AgentTask.stop();
        AgentTask second = AgentTask.grind();
        AgentBotScriptTaskStateRuntime.queueTask(entry, first);
        AgentBotScriptTaskStateRuntime.queueTask(entry, second);
        List<AgentTask> started = new ArrayList<>();

        AgentScriptTaskTickService.tick(entry, (ignoredEntry, task) -> started.add(task), (ignoredEntry, ignoredTask) -> true);

        assertEquals(List.of(first, second), started);
        assertNull(AgentBotScriptTaskStateRuntime.activeTask(entry));
    }

    private static BotEntry entry() {
        return new BotEntry(character(200), mock(Character.class), null);
    }

    private static Character character(int id) {
        Character character = mock(Character.class);
        when(character.getId()).thenReturn(id);
        return character;
    }
}
