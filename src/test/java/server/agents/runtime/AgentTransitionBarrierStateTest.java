package server.agents.runtime;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentTransitionBarrierStateTest {
    @Test
    void pauseWaitsForInFlightTickAndRejectsNewTicks() throws Exception {
        AgentTransitionBarrierState barrier = new AgentTransitionBarrierState();
        CountDownLatch tickEntered = new CountDownLatch(1);
        CountDownLatch releaseTick = new CountDownLatch(1);
        CountDownLatch pauseAcquired = new CountDownLatch(1);
        CountDownLatch releasePause = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            executor.submit(() -> {
                try (AgentTransitionBarrierState.TickPermit ignored = barrier.tryEnterTick()) {
                    tickEntered.countDown();
                    await(releaseTick);
                }
            });
            assertTrue(tickEntered.await(2, TimeUnit.SECONDS));

            executor.submit(() -> {
                try (AgentTransitionBarrierState.PauseLease lease = barrier.pauseAndDrain()) {
                    pauseAcquired.countDown();
                    await(releasePause);
                }
            });

            assertFalse(pauseAcquired.await(100, TimeUnit.MILLISECONDS));
            releaseTick.countDown();
            assertTrue(pauseAcquired.await(2, TimeUnit.SECONDS));
            assertTrue(barrier.isPaused());
            assertNull(barrier.tryEnterTick());

            releasePause.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
            assertFalse(barrier.isPaused());
            assertEquals(1L, barrier.generation());
        } finally {
            releaseTick.countDown();
            releasePause.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void mailboxWorkFromPreviousTransitionGenerationIsRejected() {
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);
        CompletableFuture<Integer> action = AgentMailboxRuntime.submit(entry, ignored -> 1);

        try (AgentTransitionBarrierState.PauseLease ignored = entry.transitionBarrierState().pauseAndDrain()) {
            // Generation advances while the Agent is drained.
        }
        entry.actionMailbox().drain(entry, 10);

        assertTrue(action.isCompletedExceptionally());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
