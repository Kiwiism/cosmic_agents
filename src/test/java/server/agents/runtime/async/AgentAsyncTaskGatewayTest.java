package server.agents.runtime.async;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.scheduler.AgentSessionId;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentAsyncTaskGatewayTest {
    private AgentAsyncExecutorRegistry executors;
    private AgentRuntimeEntry entry;

    @BeforeEach
    void setUp() {
        System.setProperty("agents.async.trade.threads", "1");
        System.setProperty("agents.async.trade.queueCapacity", "1");
        executors = new AgentAsyncExecutorRegistry();
        entry = entry(1001);
        AgentRuntimeRegistry.registerEntry(2001, entry);
    }

    @AfterEach
    void tearDown() {
        executors.close();
        AgentRuntimeRegistry.clear();
        System.clearProperty("agents.async.trade.threads");
        System.clearProperty("agents.async.trade.queueCapacity");
    }

    @Test
    void successfulWorkReturnsThroughOwningMailbox() throws Exception {
        AgentAsyncTaskGateway gateway = new AgentAsyncTaskGateway(executors);
        AtomicReference<AgentAsyncCompletion<String>> delivered = new AtomicReference<>();

        AgentAsyncTaskGateway.Submission submission = gateway.submit(
                entry,
                AgentAsyncWorkKind.ECONOMY_ANALYSIS,
                "query",
                () -> "result",
                (ignored, completion) -> delivered.set(completion));

        assertTrue(submission.accepted());
        awaitMailbox(entry);
        assertEquals(1, gateway.pendingCount());
        assertEquals(1, gateway.pendingCount(AgentSessionId.from(entry)));
        assertEquals(1, entry.actionMailbox().drain(entry, 8));
        assertEquals("result", delivered.get().result());
        assertEquals(entry.sessionGeneration(), delivered.get().sessionId().generation());
        assertEquals(submission.requestId(), delivered.get().requestId());
        assertEquals(0, gateway.pendingCount());
        assertEquals(0, gateway.pendingCount(AgentSessionId.from(entry)));
    }

    @Test
    void replacementRejectsStaleCompletion() throws Exception {
        AgentAsyncTaskGateway gateway = new AgentAsyncTaskGateway(executors);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<AgentAsyncCompletion<String>> delivered = new AtomicReference<>();

        gateway.submit(entry, AgentAsyncWorkKind.ECONOMY_ANALYSIS, "query", () -> {
            started.countDown();
            await(release);
            return "stale";
        }, (ignored, completion) -> delivered.set(completion));
        assertTrue(started.await(5, TimeUnit.SECONDS));

        AgentRuntimeRegistry.unregisterEntry(2001, entry);
        AgentRuntimeEntry replacement = entry(1001);
        AgentRuntimeRegistry.registerEntry(2001, replacement);
        release.countDown();

        awaitPendingCount(gateway, 0);
        assertEquals(0, entry.actionMailbox().size());
        assertEquals(null, delivered.get());
    }

    @Test
    void latestRequestWinsForSameSessionKey() throws Exception {
        AgentAsyncTaskGateway gateway = new AgentAsyncTaskGateway(executors);
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        AtomicReference<String> delivered = new AtomicReference<>();

        gateway.submit(entry, AgentAsyncWorkKind.ECONOMY_ANALYSIS, "query", () -> {
            firstStarted.countDown();
            await(releaseFirst);
            return "old";
        }, (ignored, completion) -> delivered.set(completion.result()));
        assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
        gateway.submit(entry, AgentAsyncWorkKind.ECONOMY_ANALYSIS, "query", () -> "new",
                (ignored, completion) -> delivered.set(completion.result()));

        releaseFirst.countDown();
        awaitMailbox(entry);
        entry.actionMailbox().drain(entry, 8);
        assertEquals("new", delivered.get());
        assertEquals(0, gateway.pendingCount());
    }

    @Test
    void saturationRejectsWithoutLeakingPendingState() throws Exception {
        AgentAsyncTaskGateway gateway = new AgentAsyncTaskGateway(executors);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        assertTrue(gateway.submit(entry, AgentAsyncWorkKind.ECONOMY_ANALYSIS, "one", () -> {
            started.countDown();
            await(release);
            return 1;
        }, (ignored, completion) -> { }).accepted());
        assertTrue(started.await(5, TimeUnit.SECONDS));
        assertTrue(gateway.submit(entry, AgentAsyncWorkKind.ECONOMY_ANALYSIS, "two", () -> 2,
                (ignored, completion) -> { }).accepted());
        AgentAsyncTaskGateway.Submission rejected = gateway.submit(
                entry, AgentAsyncWorkKind.ECONOMY_ANALYSIS, "three", () -> 3,
                (ignored, completion) -> { });

        assertFalse(rejected.accepted());
        assertEquals(2, gateway.pendingCount());
        release.countDown();
        awaitMailbox(entry);
        entry.actionMailbox().drain(entry, 8);
    }

    @Test
    void elapsedTimeoutIsDeliveredWithoutApplyingResult() throws Exception {
        AtomicLong nowNs = new AtomicLong();
        AgentAsyncTaskGateway gateway = new AgentAsyncTaskGateway(executors, nowNs::get);
        AtomicReference<AgentAsyncCompletion<String>> delivered = new AtomicReference<>();

        gateway.submit(entry, AgentAsyncWorkKind.ECONOMY_ANALYSIS, "slow", 1L, () -> {
            nowNs.addAndGet(2_000_000L);
            return "late";
        }, (ignored, completion) -> delivered.set(completion));

        awaitMailbox(entry);
        entry.actionMailbox().drain(entry, 8);
        assertEquals(AgentAsyncCompletion.Status.TIMED_OUT, delivered.get().status());
        assertEquals(null, delivered.get().result());
    }

    @Test
    void completionDeliveryRemainsRunnableWhileOrdinaryMailboxWorkIsFrozen() throws Exception {
        AgentAsyncTaskGateway gateway = new AgentAsyncTaskGateway(executors);
        AtomicReference<String> delivered = new AtomicReference<>();
        entry.actionMailbox().submit(entry.sessionGeneration(), ignored -> "ordinary");
        assertTrue(entry.actionMailbox().beginQuiescence());

        gateway.submit(
                entry,
                AgentAsyncWorkKind.ECONOMY_ANALYSIS,
                "quiescence",
                () -> "result",
                (ignored, completion) -> delivered.set(completion.result()));

        awaitMailboxDepth(entry, 2);
        assertEquals(1, entry.actionMailbox().drain(entry, 8));
        assertEquals("result", delivered.get());
        assertEquals(1, entry.actionMailbox().size());
        assertEquals(0, gateway.pendingCount(AgentSessionId.from(entry)));
    }

    private static AgentRuntimeEntry entry(int agentId) {
        Character agent = mock(Character.class);
        when(agent.getId()).thenReturn(agentId);
        return new AgentRuntimeEntry(agent, null, null);
    }

    private static void awaitMailbox(AgentRuntimeEntry runtimeEntry) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (runtimeEntry.actionMailbox().size() == 0 && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }
        assertTrue(runtimeEntry.actionMailbox().size() > 0, "completion did not reach mailbox");
    }

    private static void awaitMailboxDepth(AgentRuntimeEntry runtimeEntry, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (runtimeEntry.actionMailbox().size() < expected && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }
        assertEquals(expected, runtimeEntry.actionMailbox().size(), "completion did not reach mailbox");
    }

    private static void awaitPendingCount(AgentAsyncTaskGateway gateway, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (gateway.pendingCount() != expected && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }
        assertEquals(expected, gateway.pendingCount());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
