package server.agents.runtime.scheduler;

import client.Character;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.mailbox.AgentMailboxOptions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentQuiescenceControllerTest {
    @Test
    void freezesOrdinaryActionsAndCompletesAfterCriticalWorkDrains() {
        AtomicLong now = new AtomicLong(1_000L);
        AtomicInteger pendingAsync = new AtomicInteger();
        AgentRuntimeEntry entry = entry(101);
        AgentQuiescenceController controller = controller(entry, now, pendingAsync);
        List<String> executed = new ArrayList<>();
        entry.actionMailbox().submit(
                entry.sessionGeneration(),
                ignored -> {
                    executed.add("ordinary");
                    return null;
                },
                AgentMailboxOptions.fifo());
        entry.actionMailbox().submit(
                entry.sessionGeneration(),
                ignored -> {
                    executed.add("completion");
                    return null;
                },
                AgentMailboxOptions.completionCoalesceLatest("async-result"));

        CompletableFuture<AgentQuiescenceToken> result = controller.request(
                AgentQuiescenceReason.PROFILE_EXCHANGE,
                Duration.ofSeconds(5)).toCompletableFuture();

        assertEquals(AgentQuiescenceController.ExecutionMode.QUIESCENCE_MAINTENANCE,
                controller.beforeExecution());
        controller.runMaintenance();
        controller.afterExecution();

        AgentQuiescenceToken token = result.join();
        assertEquals(List.of("completion"), executed);
        assertEquals(AgentSessionId.from(entry), token.sessionId());
        assertTrue(controller.validates(token));
        assertTrue(entry.actionMailbox().ordinaryWorkFrozen());
        assertEquals(1, entry.actionMailbox().size());

        assertTrue(controller.resume(token));
        assertFalse(entry.actionMailbox().ordinaryWorkFrozen());
        entry.actionMailbox().drain(entry, 8);
        assertEquals(List.of("completion", "ordinary"), executed);
    }

    @Test
    void waitsForGenerationBoundAsyncWorkBeforeIssuingToken() {
        AtomicLong now = new AtomicLong(1_000L);
        AtomicInteger pendingAsync = new AtomicInteger(1);
        AgentRuntimeEntry entry = entry(101);
        AgentQuiescenceController controller = controller(entry, now, pendingAsync);
        CompletableFuture<AgentQuiescenceToken> result = controller.request(
                AgentQuiescenceReason.CONSISTENT_SNAPSHOT,
                Duration.ofSeconds(5)).toCompletableFuture();

        controller.runMaintenance();
        controller.afterExecution();
        assertFalse(result.isDone());

        pendingAsync.set(0);
        controller.runMaintenance();
        controller.afterExecution();
        assertTrue(result.isDone());
        assertEquals(AgentQuiescenceReason.CONSISTENT_SNAPSHOT, result.join().reason());
    }

    @Test
    void timeoutFailsAndRestoresOrdinaryMailboxExecution() {
        AtomicLong now = new AtomicLong(1_000L);
        AtomicInteger pendingAsync = new AtomicInteger(1);
        AgentRuntimeEntry entry = entry(101);
        AgentQuiescenceController controller = controller(entry, now, pendingAsync);
        CompletableFuture<AgentQuiescenceToken> result = controller.request(
                AgentQuiescenceReason.RELEASE,
                Duration.ofMillis(50)).toCompletableFuture();

        now.addAndGet(50L);
        assertEquals(AgentQuiescenceController.ExecutionMode.NORMAL_TICK, controller.beforeExecution());

        CompletionException failure = assertThrows(CompletionException.class, result::join);
        assertEquals(AgentQuiescenceException.Reason.TIMEOUT,
                ((AgentQuiescenceException) failure.getCause()).reason());
        assertFalse(entry.actionMailbox().ordinaryWorkFrozen());
        assertFalse(controller.quiescent());
    }

    @Test
    void closingSessionFailsOutstandingRequest() {
        AtomicLong now = new AtomicLong(1_000L);
        AgentRuntimeEntry entry = entry(101);
        AgentQuiescenceController controller = controller(entry, now, new AtomicInteger());
        CompletableFuture<AgentQuiescenceToken> result = controller.request(
                AgentQuiescenceReason.SHUTDOWN,
                Duration.ofSeconds(5)).toCompletableFuture();

        controller.close();

        CompletionException failure = assertThrows(CompletionException.class, result::join);
        assertEquals(AgentQuiescenceException.Reason.CLOSED,
                ((AgentQuiescenceException) failure.getCause()).reason());
    }

    @Test
    void staleSessionFailsOutstandingRequestAndRestoresOrdinaryWork() {
        AtomicLong now = new AtomicLong(1_000L);
        AtomicBoolean active = new AtomicBoolean(true);
        AgentRuntimeEntry entry = entry(101);
        AgentQuiescenceController controller = new AgentQuiescenceController(
                entry,
                AgentSessionId.from(entry),
                now::get,
                () -> 0,
                active::get);
        CompletableFuture<AgentQuiescenceToken> result = controller.request(
                AgentQuiescenceReason.RELEASE,
                Duration.ofSeconds(5)).toCompletableFuture();

        active.set(false);
        assertEquals(AgentQuiescenceController.ExecutionMode.SKIP, controller.beforeExecution());

        CompletionException failure = assertThrows(CompletionException.class, result::join);
        assertEquals(AgentQuiescenceException.Reason.STALE_SESSION,
                ((AgentQuiescenceException) failure.getCause()).reason());
        assertFalse(entry.actionMailbox().ordinaryWorkFrozen());
    }

    @Test
    void issuedTokenStopsValidatingWhenSessionBecomesInactive() {
        AtomicLong now = new AtomicLong(1_000L);
        AtomicBoolean active = new AtomicBoolean(true);
        AgentRuntimeEntry entry = entry(101);
        AgentQuiescenceController controller = new AgentQuiescenceController(
                entry,
                AgentSessionId.from(entry),
                now::get,
                () -> 0,
                active::get);
        CompletableFuture<AgentQuiescenceToken> result = controller.request(
                AgentQuiescenceReason.CONSISTENT_SNAPSHOT,
                Duration.ofSeconds(5)).toCompletableFuture();
        controller.afterExecution();
        AgentQuiescenceToken token = result.join();

        active.set(false);

        assertFalse(controller.validates(token));
        assertFalse(controller.resume(token));
    }

    private static AgentQuiescenceController controller(
            AgentRuntimeEntry entry,
            AtomicLong now,
            AtomicInteger pendingAsync) {
        return new AgentQuiescenceController(
                entry,
                AgentSessionId.from(entry),
                now::get,
                pendingAsync::get,
                () -> true);
    }

    private static AgentRuntimeEntry entry(int agentId) {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(agentId);
        return new AgentRuntimeEntry(agent, null, null);
    }
}
