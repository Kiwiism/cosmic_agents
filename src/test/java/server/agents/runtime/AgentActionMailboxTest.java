package server.agents.runtime;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        CompletableFuture<Integer> result = entry.actionMailbox().submit(
                entry.sessionGeneration() + 1,
                ignored -> executions.incrementAndGet());

        entry.actionMailbox().drain(entry, 10);

        assertEquals(0, executions.get());
        assertThrows(Exception.class, result::join);
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
    void boundsPendingActions() {
        AgentRuntimeEntry entry = entry();
        AgentActionMailbox mailbox = new AgentActionMailbox(1);
        assertFalse(mailbox.submit(entry.sessionGeneration(), ignored -> 1).isCompletedExceptionally());
        assertTrue(mailbox.submit(entry.sessionGeneration(), ignored -> 2).isCompletedExceptionally());
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

    private static AgentRuntimeEntry entry() {
        return new AgentRuntimeEntry(null, null, null);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
