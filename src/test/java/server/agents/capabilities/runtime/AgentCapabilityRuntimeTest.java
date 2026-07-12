package server.agents.capabilities.runtime;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.AgentCapabilityStatus;
import server.agents.runtime.AgentRuntimeEntry;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AgentCapabilityRuntimeTest {
    private record Command(String type) implements AgentCapabilityCommand {
    }

    @Test
    void returnsFalseAndDoesNothingWhenNoCapabilityIsAssigned() {
        AgentRuntimeEntry entry = entry();

        assertFalse(AgentCapabilityRuntime.tick(entry, entry.bot(), 100L));
        assertFalse(entry.capabilityRuntimeState().hasActiveCapability());
        assertNull(entry.capabilityRuntimeState().lastResult());
        assertTrue(entry.capabilityRuntimeState().journalSnapshot().isEmpty());
    }

    @Test
    void childRunsAloneThenParentResumesWithItsResult() {
        AgentRuntimeEntry entry = entry();
        AtomicInteger parentTicks = new AtomicInteger();
        AtomicInteger childTicks = new AtomicInteger();
        AgentExecutableCapability<Command> child = capability("child", (context, command) -> {
            childTicks.incrementAndGet();
            return AgentCapabilityStep.terminal(AgentCapabilityResult.success("child complete"));
        });
        AgentExecutableCapability<Command> parent = capability("parent", (context, command) -> {
            parentTicks.incrementAndGet();
            if (context.childResult() == null) {
                context.memory().putInt("stage", 1);
                return AgentCapabilityStep.handoff(
                        new AgentCapabilityInvocation<>(child, new Command("child-command"), 1000L, 0),
                        "child needed");
            }
            assertEquals(AgentCapabilityStatus.SUCCESS, context.childResult().status());
            assertEquals(1, context.memory().intValue("stage", 0));
            return AgentCapabilityStep.terminal(AgentCapabilityResult.success("parent complete"));
        });

        assertTrue(AgentCapabilityRuntime.assign(entry,
                new AgentCapabilityInvocation<>(parent, new Command("parent-command"), 5000L, 0)));
        assertTrue(AgentCapabilityRuntime.tick(entry, entry.bot(), 100L));
        assertEquals(1, parentTicks.get());
        assertEquals(0, childTicks.get());
        assertEquals(2, entry.capabilityRuntimeState().frameCount());

        AgentCapabilityRuntime.tick(entry, entry.bot(), 110L);
        assertEquals(1, parentTicks.get());
        assertEquals(1, childTicks.get());
        assertEquals(1, entry.capabilityRuntimeState().frameCount());

        AgentCapabilityRuntime.tick(entry, entry.bot(), 120L);
        assertEquals(2, parentTicks.get());
        assertFalse(entry.capabilityRuntimeState().hasActiveCapability());
        assertEquals(AgentCapabilityStatus.SUCCESS, entry.capabilityRuntimeState().lastResult().status());
        assertEquals(List.of(
                        AgentCapabilityJournalEventType.STARTED,
                        AgentCapabilityJournalEventType.HANDOFF_REQUESTED,
                        AgentCapabilityJournalEventType.CHILD_STARTED,
                        AgentCapabilityJournalEventType.SUCCEEDED,
                        AgentCapabilityJournalEventType.CHILD_RESULT,
                        AgentCapabilityJournalEventType.PARENT_RESUMED,
                        AgentCapabilityJournalEventType.SUCCEEDED),
                entry.capabilityRuntimeState().journalSnapshot().stream()
                        .map(AgentCapabilityJournalEvent::type)
                        .toList());
    }

    @Test
    void retryIsBoundedAndFailsAfterConfiguredLimit() {
        AgentRuntimeEntry entry = entry();
        AgentExecutableCapability<Command> retrying = capability("retrying",
                (context, command) -> AgentCapabilityStep.retry("again"));
        AgentCapabilityRuntime.assign(entry,
                new AgentCapabilityInvocation<>(retrying, new Command("retry"), 1000L, 2));

        AgentCapabilityRuntime.tick(entry, entry.bot(), 10L);
        AgentCapabilityRuntime.tick(entry, entry.bot(), 20L);
        AgentCapabilityRuntime.tick(entry, entry.bot(), 30L);

        assertFalse(entry.capabilityRuntimeState().hasActiveCapability());
        assertEquals(AgentCapabilityReasonCode.RETRIES_EXHAUSTED,
                entry.capabilityRuntimeState().lastResult().reasonCode());
        assertEquals(2, entry.capabilityRuntimeState().journalSnapshot().stream()
                .filter(event -> event.type() == AgentCapabilityJournalEventType.RETRY)
                .count());
    }

    @Test
    void timeoutStopsCapabilityBeforeAnotherExecution() {
        AgentRuntimeEntry entry = entry();
        AtomicInteger ticks = new AtomicInteger();
        AgentExecutableCapability<Command> running = capability("running", (context, command) -> {
            ticks.incrementAndGet();
            return AgentCapabilityStep.running("working");
        });
        AgentCapabilityRuntime.assign(entry,
                new AgentCapabilityInvocation<>(running, new Command("run"), 50L, 0));

        AgentCapabilityRuntime.tick(entry, entry.bot(), 100L);
        AgentCapabilityRuntime.tick(entry, entry.bot(), 150L);

        assertEquals(1, ticks.get());
        assertEquals(AgentCapabilityStatus.TIMED_OUT,
                entry.capabilityRuntimeState().lastResult().status());
    }

    @Test
    void cancellationClearsParentAndChildWithoutExecutingEitherAgain() {
        AgentRuntimeEntry entry = entry();
        AtomicInteger terminalCallbacks = new AtomicInteger();
        AgentExecutableCapability<Command> child = capability("child",
                terminalCallbacks,
                (context, command) -> AgentCapabilityStep.running("waiting"));
        AgentExecutableCapability<Command> parent = capability("parent",
                terminalCallbacks,
                (context, command) -> AgentCapabilityStep.handoff(
                        new AgentCapabilityInvocation<>(child, new Command("child"), 1000L, 0),
                        "child needed"));
        AgentCapabilityRuntime.assign(entry,
                new AgentCapabilityInvocation<>(parent, new Command("parent"), 1000L, 0));
        AgentCapabilityRuntime.tick(entry, entry.bot(), 1L);

        AgentCapabilityRuntime.requestCancellation(entry);
        AgentCapabilityRuntime.tick(entry, entry.bot(), 2L);

        assertFalse(entry.capabilityRuntimeState().hasActiveCapability());
        assertEquals(AgentCapabilityStatus.CANCELLED,
                entry.capabilityRuntimeState().lastResult().status());
        assertEquals(2, terminalCallbacks.get());
    }

    @Test
    void parentDeadlineAbortsRunningChildAndCleansBothFrames() {
        AgentRuntimeEntry entry = entry();
        AtomicInteger terminalCallbacks = new AtomicInteger();
        AgentExecutableCapability<Command> child = capability("child", terminalCallbacks,
                (context, command) -> AgentCapabilityStep.running("waiting"));
        AgentExecutableCapability<Command> parent = capability("parent", terminalCallbacks,
                (context, command) -> AgentCapabilityStep.handoff(
                        new AgentCapabilityInvocation<>(child, new Command("child"), 1000L, 0),
                        "child needed"));
        AgentCapabilityRuntime.assign(entry,
                new AgentCapabilityInvocation<>(parent, new Command("parent"), 50L, 0));

        AgentCapabilityRuntime.tick(entry, entry.bot(), 100L);
        AgentCapabilityRuntime.tick(entry, entry.bot(), 110L);
        AgentCapabilityRuntime.tick(entry, entry.bot(), 150L);

        assertFalse(entry.capabilityRuntimeState().hasActiveCapability());
        assertEquals(AgentCapabilityStatus.TIMED_OUT,
                entry.capabilityRuntimeState().lastResult().status());
        assertEquals(2, terminalCallbacks.get());
    }

    @Test
    void activeCapabilityCanDelegateCurrentTickToReconstructedLegacyBehavior() {
        AgentRuntimeEntry entry = entry();
        AgentExecutableCapability<Command> delegating = capability("navigation",
                (context, command) -> AgentCapabilityStep.running("legacy movement should tick", false));
        AgentCapabilityRuntime.assign(entry,
                new AgentCapabilityInvocation<>(delegating, new Command("navigate"), 1000L, 0));

        assertFalse(AgentCapabilityRuntime.tick(entry, entry.bot(), 1L));
        assertTrue(entry.capabilityRuntimeState().hasActiveCapability());
    }

    @Test
    void blockerIsJournaledAsStructuredTerminalResult() {
        AgentRuntimeEntry entry = entry();
        AgentExecutableCapability<Command> blocked = capability("blocked", (context, command) ->
                AgentCapabilityStep.terminal(new AgentCapabilityResult(
                        AgentCapabilityStatus.BLOCKED_FORBIDDEN_MAP,
                        AgentCapabilityReasonCode.BLOCKED_BY_SCOPE,
                        "outside scope")));
        AgentCapabilityRuntime.assign(entry,
                new AgentCapabilityInvocation<>(blocked, new Command("blocked"), 1000L, 0));

        AgentCapabilityRuntime.tick(entry, entry.bot(), 1L);

        assertEquals(AgentCapabilityStatus.BLOCKED_FORBIDDEN_MAP,
                entry.capabilityRuntimeState().lastResult().status());
        assertEquals(AgentCapabilityJournalEventType.BLOCKED,
                entry.capabilityRuntimeState().journalSnapshot().get(1).type());
    }

    @Test
    void journalRetainsOnlyMostRecentBoundedEvents() {
        AgentRuntimeEntry entry = entry();
        AgentExecutableCapability<Command> done = capability("done", (context, command) ->
                AgentCapabilityStep.terminal(AgentCapabilityResult.success("done")));

        for (int i = 0; i < 200; i++) {
            assertTrue(AgentCapabilityRuntime.assign(entry,
                    new AgentCapabilityInvocation<>(done, new Command("done"), 1000L, 0)));
            AgentCapabilityRuntime.tick(entry, entry.bot(), i + 1L);
        }

        assertEquals(256, entry.capabilityRuntimeState().journalSnapshot().size());
    }

    private static AgentExecutableCapability<Command> capability(
            String id,
            CapabilityTick tick) {
        return capability(id, null, tick);
    }

    private static AgentExecutableCapability<Command> capability(
            String id,
            AtomicInteger terminalCallbacks,
            CapabilityTick tick) {
        return new AgentExecutableCapability<>() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public AgentCapabilityStep tick(AgentCapabilityContext context, Command command) {
                return tick.tick(context, command);
            }

            @Override
            public void onTerminal(AgentCapabilityContext context,
                                   Command command,
                                   AgentCapabilityResult result) {
                if (terminalCallbacks != null) {
                    terminalCallbacks.incrementAndGet();
                }
            }
        };
    }

    private static AgentRuntimeEntry entry() {
        return new AgentRuntimeEntry(mock(Character.class), mock(Character.class), null);
    }

    @FunctionalInterface
    private interface CapabilityTick {
        AgentCapabilityStep tick(AgentCapabilityContext context, Command command);
    }
}
