package server.agents.runtime;

import org.junit.jupiter.api.Test;
import server.agents.monitoring.AgentAsyncQueueMetrics;
import server.agents.runtime.scheduler.AgentScheduleHandle;
import server.agents.runtime.scheduler.AgentSessionId;
import server.agents.runtime.mailbox.AgentMailboxFailureReason;
import server.agents.runtime.mailbox.AgentMailboxOptions;
import server.agents.runtime.mailbox.AgentMailboxRejectedException;
import server.agents.runtime.mailbox.AgentMailboxSubmission;
import server.agents.runtime.mailbox.AgentMailboxSubmissionStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentActionMailboxTest {
    @Test
    void drainsActionsInSubmissionOrder() {
        AgentRuntimeEntry entry = entry();
        List<Integer> order = new ArrayList<>();
        List<CompletableFuture<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            int value = i;
            results.add(entry.actionMailbox().submit(entry.sessionGeneration(), ignored -> {
                order.add(value);
                return value;
            }));
        }

        assertEquals(3, entry.actionMailbox().drain(entry, 10));
        assertEquals(List.of(0, 1, 2), order);
        assertEquals(List.of(0, 1, 2), results.stream().map(CompletableFuture::join).toList());
    }

    @Test
    void rejectsStaleSessionActionWithoutExecutingIt() {
        AgentRuntimeEntry entry = entry();
        AtomicInteger executions = new AtomicInteger();
        AgentAsyncQueueMetrics.Snapshot before = AgentAsyncQueueMetrics.snapshot("mailbox");
        CompletableFuture<Integer> result = entry.actionMailbox().submit(
                entry.sessionGeneration() + 1,
                ignored -> executions.incrementAndGet());

        entry.actionMailbox().drain(entry, 10);

        assertEquals(0, executions.get());
        assertThrows(Exception.class, result::join);
        AgentAsyncQueueMetrics.Snapshot after = AgentAsyncQueueMetrics.snapshot("mailbox");
        assertEquals(before.stale() + 1L, after.stale());
        assertEquals(before.drained() + 1L, after.drained());
    }

    @Test
    void closingMailboxRejectsPendingAndFutureActions() {
        AgentRuntimeEntry entry = entry();
        CompletableFuture<Integer> pending = AgentMailboxRuntime.submit(entry, ignored -> 1);

        AgentMailboxRuntime.close(entry);

        assertTrue(entry.actionMailbox().isClosed());
        assertThrows(Exception.class, pending::join);
        assertThrows(Exception.class, () -> AgentMailboxRuntime.submit(entry, ignored -> 2).join());
    }

    @Test
    void fixtureDiscardRejectsPendingButKeepsMailboxUsable() {
        AgentRuntimeEntry entry = entry();
        CompletableFuture<Integer> pending = AgentMailboxRuntime.submit(entry, ignored -> 1);

        entry.actionMailbox().discardPending("fixture reset");

        assertFalse(entry.actionMailbox().isClosed());
        assertThrows(Exception.class, pending::join);
        CompletableFuture<Integer> next = AgentMailboxRuntime.submit(entry, ignored -> 2);
        assertEquals(1, entry.actionMailbox().drain(entry, 1));
        assertEquals(2, next.join());
    }

    @Test
    void boundsPendingActions() {
        AgentRuntimeEntry entry = entry();
        AgentActionMailbox mailbox = new AgentActionMailbox(1);
        assertFalse(mailbox.submit(entry.sessionGeneration(), ignored -> 1).isCompletedExceptionally());
        assertTrue(mailbox.submit(entry.sessionGeneration(), ignored -> 2).isCompletedExceptionally());
    }

    @Test
    void reportsStructuredCapacityRejection() {
        AgentRuntimeEntry entry = entry();
        AgentActionMailbox mailbox = new AgentActionMailbox(1);
        mailbox.submit(entry.sessionGeneration(), ignored -> 1);

        AgentMailboxSubmission<Integer> rejected = mailbox.submit(
                entry.sessionGeneration(), ignored -> 2, AgentMailboxOptions.fifo());

        assertEquals(AgentMailboxSubmissionStatus.REJECTED_FULL, rejected.status());
        assertEquals(AgentMailboxFailureReason.FULL, rejectionReason(rejected.result()));
    }

    @Test
    void coalescesOnlyActionsWithTheSameExplicitKey() {
        AgentRuntimeEntry entry = entry();
        AgentActionMailbox mailbox = new AgentActionMailbox(2);
        List<Integer> executed = new ArrayList<>();
        AgentMailboxSubmission<Integer> first = mailbox.submit(
                entry.sessionGeneration(), ignored -> {
                    executed.add(1);
                    return 1;
                }, AgentMailboxOptions.coalesceLatest("movement-target"));
        AgentMailboxSubmission<Integer> latest = mailbox.submit(
                entry.sessionGeneration(), ignored -> {
                    executed.add(2);
                    return 2;
                }, AgentMailboxOptions.coalesceLatest("movement-target"));

        assertEquals(AgentMailboxSubmissionStatus.ACCEPTED, first.status());
        assertEquals(AgentMailboxSubmissionStatus.COALESCED, latest.status());
        assertEquals(AgentMailboxFailureReason.COALESCED, rejectionReason(first.result()));
        assertEquals(1, mailbox.drain(entry, 2));
        assertEquals(List.of(2), executed);
        assertEquals(2, latest.result().join());
    }

    @Test
    void expiresQueuedActionWithoutExecutingIt() {
        AgentRuntimeEntry entry = entry();
        AtomicLong now = new AtomicLong(1_000L);
        AgentActionMailbox mailbox = new AgentActionMailbox(2, now::get);
        AtomicInteger executions = new AtomicInteger();
        AgentAsyncQueueMetrics.Snapshot before = AgentAsyncQueueMetrics.snapshot("mailbox");
        AgentMailboxSubmission<Integer> submission = mailbox.submit(
                entry.sessionGeneration(), ignored -> executions.incrementAndGet(),
                AgentMailboxOptions.expiringAt(1_050L));

        now.set(1_050L);
        assertEquals(1, mailbox.drain(entry, 1));

        assertEquals(0, executions.get());
        assertEquals(AgentMailboxFailureReason.EXPIRED, rejectionReason(submission.result()));
        AgentAsyncQueueMetrics.Snapshot after = AgentAsyncQueueMetrics.snapshot("mailbox");
        assertEquals(before.expired() + 1L, after.expired());
        assertEquals(before.drained() + 1L, after.drained());
    }

    @Test
    void rejectsAlreadyExpiredActionWithoutConsumingCapacity() {
        AgentRuntimeEntry entry = entry();
        AtomicLong now = new AtomicLong(1_000L);
        AgentActionMailbox mailbox = new AgentActionMailbox(1, now::get);

        AgentMailboxSubmission<Integer> expired = mailbox.submit(
                entry.sessionGeneration(), ignored -> 1, AgentMailboxOptions.expiringAt(999L));

        assertEquals(AgentMailboxSubmissionStatus.REJECTED_EXPIRED, expired.status());
        assertEquals(AgentMailboxFailureReason.EXPIRED, rejectionReason(expired.result()));
        assertEquals(0, mailbox.size());
    }

    @Test
    void acceptsConcurrentSubmissionsWithoutLosingActions() throws Exception {
        AgentRuntimeEntry entry = entry();
        AgentActionMailbox mailbox = new AgentActionMailbox(256);
        int producers = 8;
        int actionsPerProducer = 20;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(producers);
        List<CompletableFuture<Integer>> results = java.util.Collections.synchronizedList(new ArrayList<>());
        try {
            for (int producer = 0; producer < producers; producer++) {
                executor.execute(() -> {
                    await(start);
                    for (int i = 0; i < actionsPerProducer; i++) {
                        results.add(mailbox.submit(entry.sessionGeneration(), ignored -> 1));
                    }
                });
            }
            start.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

            assertEquals(producers * actionsPerProducer, mailbox.drain(entry, 256));
            assertEquals(producers * actionsPerProducer, results.stream().mapToInt(CompletableFuture::join).sum());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void acceptedSubmissionRequestsGenerationBoundSchedulerWake() {
        AgentRuntimeEntry entry = entry();
        AgentScheduleHandle handle = mock(AgentScheduleHandle.class);
        when(handle.sessionId()).thenReturn(AgentSessionId.from(entry));
        when(handle.wake()).thenReturn(true);
        entry.scheduledTaskState().attachScheduledTask(handle);

        CompletableFuture<Integer> result = AgentMailboxRuntime.submit(entry, ignored -> 1);

        assertFalse(result.isDone());
        verify(handle).wake();
    }

    @Test
    void quiescenceFreezesOrdinaryWorkButDrainsCriticalCompletion() {
        AgentRuntimeEntry entry = entry();
        AgentActionMailbox mailbox = entry.actionMailbox();
        List<String> executed = new ArrayList<>();
        AgentMailboxSubmission<String> ordinary = mailbox.submit(
                entry.sessionGeneration(),
                ignored -> {
                    executed.add("ordinary");
                    return "ordinary";
                },
                AgentMailboxOptions.fifo());
        AgentMailboxSubmission<String> completion = mailbox.submit(
                entry.sessionGeneration(),
                ignored -> {
                    executed.add("completion");
                    return "completion";
                },
                AgentMailboxOptions.completionCoalesceLatest("completion"));

        assertTrue(mailbox.beginQuiescence());
        assertEquals(1, mailbox.drain(entry, 8));

        assertEquals(List.of("completion"), executed);
        assertFalse(ordinary.result().isDone());
        assertEquals("completion", completion.result().join());
        assertEquals(1, mailbox.size());
        assertEquals(0, mailbox.quiescenceCriticalSize());

        mailbox.endQuiescence();
        assertEquals(1, mailbox.drain(entry, 8));
        assertEquals(List.of("completion", "ordinary"), executed);
        assertEquals("ordinary", ordinary.result().join());
    }

    @Test
    void criticalReserveAcceptsCompletionWhenOrdinaryCapacityIsFull() {
        AgentRuntimeEntry entry = entry();
        AgentActionMailbox mailbox = new AgentActionMailbox(1);
        AgentMailboxSubmission<Integer> ordinary = mailbox.submit(
                entry.sessionGeneration(), ignored -> 1, AgentMailboxOptions.fifo());

        AgentMailboxSubmission<Integer> completion = mailbox.submit(
                entry.sessionGeneration(),
                ignored -> 2,
                AgentMailboxOptions.completionCoalesceLatest("completion"));

        assertTrue(completion.accepted());
        assertEquals(2, mailbox.size());
        assertTrue(mailbox.beginQuiescence());
        assertEquals(1, mailbox.drain(entry, 2));
        assertEquals(2, completion.result().join());
        assertFalse(ordinary.result().isDone());
    }

    private static AgentRuntimeEntry entry() {
        return new AgentRuntimeEntry(null, null, null);
    }

    private static AgentMailboxFailureReason rejectionReason(CompletableFuture<?> future) {
        CompletionException failure = assertThrows(CompletionException.class, future::join);
        return ((AgentMailboxRejectedException) failure.getCause()).reason();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
