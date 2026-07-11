package server.agents.runtime;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentBoundedExecutorFactoryTest {
    @Test
    void rejectsWorkAfterWorkersAndQueueAreFull() throws Exception {
        ThreadPoolExecutor executor = AgentBoundedExecutorFactory.fixed("agent-bounded-test", 1, 1);
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        try {
            executor.execute(() -> {
                workerStarted.countDown();
                await(releaseWorker);
            });
            assertTrue(workerStarted.await(5, TimeUnit.SECONDS));
            executor.execute(() -> { });

            assertEquals(1, executor.getQueue().remainingCapacity() + executor.getQueue().size());
            assertThrows(RejectedExecutionException.class, () -> executor.execute(() -> { }));
        } finally {
            releaseWorker.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void createsNamedDaemonWorkers() throws Exception {
        ThreadPoolExecutor executor = AgentBoundedExecutorFactory.fixed("agent-worker", 1, 1);
        CountDownLatch ran = new CountDownLatch(1);
        boolean[] daemon = new boolean[1];
        String[] name = new String[1];
        try {
            executor.execute(() -> {
                daemon[0] = Thread.currentThread().isDaemon();
                name[0] = Thread.currentThread().getName();
                ran.countDown();
            });
            assertTrue(ran.await(5, TimeUnit.SECONDS));
            assertTrue(daemon[0]);
            assertEquals("agent-worker", name[0]);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void appliesRequestedWorkerPriority() throws Exception {
        ThreadPoolExecutor executor = AgentBoundedExecutorFactory.fixed(
                "agent-priority", "agent-priority-worker", 1, 1, Thread.MIN_PRIORITY);
        try {
            java.util.concurrent.CompletableFuture<Integer> priority = new java.util.concurrent.CompletableFuture<>();
            executor.execute(() -> priority.complete(Thread.currentThread().getPriority()));
            assertEquals(Thread.MIN_PRIORITY, priority.get(5, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
