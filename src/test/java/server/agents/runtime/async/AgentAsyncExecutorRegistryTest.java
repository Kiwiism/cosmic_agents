package server.agents.runtime.async;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.agents.runtime.scheduler.AgentLoadSheddingConfig;
import server.agents.runtime.scheduler.AgentLoadSheddingController;
import server.agents.runtime.scheduler.AgentLoadSheddingRuntime;
import server.agents.runtime.scheduler.AgentSchedulerPressureSample;
import server.agents.runtime.scheduler.AgentServerHealthSnapshot;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentAsyncExecutorRegistryTest {
    private final AgentAsyncExecutorRegistry registry = new AgentAsyncExecutorRegistry();

    @AfterEach
    void tearDown() {
        registry.close();
        System.clearProperty("agents.async.navigation.threads");
        System.clearProperty("agents.async.navigation.queueCapacity");
        System.clearProperty("agents.async.llm.threads");
        System.clearProperty("agents.async.llm.queueCapacity");
        AgentLoadSheddingRuntime.clearShard(99);
    }

    @Test
    void loadSheddingRejectsLlmButKeepsRequiredNavigationLaneAvailable() throws Exception {
        AgentLoadSheddingConfig config = new AgentLoadSheddingConfig(
                true, 1, 2, 1L, 100L, 8, 75, 85.0d, 85.0d, 250L, 2, 2_000);
        AgentLoadSheddingController controller = new AgentLoadSheddingController(99, config);
        controller.evaluate(new AgentSchedulerPressureSample(
                1_000L,
                400L,
                1,
                0,
                100,
                0,
                AgentServerHealthSnapshot.healthy()));

        assertThrows(RejectedExecutionException.class,
                () -> registry.execute(AgentAsyncWorkKind.LLM_NETWORK, () -> { }));

        CountDownLatch navigationRan = new CountDownLatch(1);
        registry.execute(AgentAsyncWorkKind.NAVIGATION_GRAPH, navigationRan::countDown);
        assertTrue(navigationRan.await(5, TimeUnit.SECONDS));
    }

    @Test
    void workloadKindsOwnIndependentBoundedLanes() throws Exception {
        System.setProperty("agents.async.navigation.threads", "1");
        System.setProperty("agents.async.navigation.queueCapacity", "2");
        System.setProperty("agents.async.llm.threads", "1");
        System.setProperty("agents.async.llm.queueCapacity", "3");
        CountDownLatch navigationStarted = new CountDownLatch(1);
        CountDownLatch llmStarted = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        registry.execute(AgentAsyncWorkKind.NAVIGATION_GRAPH,
                () -> await(navigationStarted, release));
        registry.execute(AgentAsyncWorkKind.LLM_NETWORK,
                () -> await(llmStarted, release));

        assertTrue(navigationStarted.await(5, TimeUnit.SECONDS));
        assertTrue(llmStarted.await(5, TimeUnit.SECONDS));
        assertEquals(2, registry.queueCapacity(AgentAsyncWorkKind.NAVIGATION_GRAPH));
        assertEquals(3, registry.queueCapacity(AgentAsyncWorkKind.LLM_NETWORK));
        assertEquals(1, registry.activeCount(AgentAsyncWorkKind.NAVIGATION_GRAPH));
        assertEquals(1, registry.activeCount(AgentAsyncWorkKind.LLM_NETWORK));

        release.countDown();
        assertTrue(registry.shutdownAndAwait(
                AgentAsyncWorkKind.NAVIGATION_GRAPH, 5, TimeUnit.SECONDS));
        assertTrue(registry.shutdownAndAwait(
                AgentAsyncWorkKind.LLM_NETWORK, 5, TimeUnit.SECONDS));
        assertFalse(registry.isRunning(AgentAsyncWorkKind.NAVIGATION_GRAPH));
        assertFalse(registry.isRunning(AgentAsyncWorkKind.LLM_NETWORK));
    }

    @Test
    void boundedShutdownRejectsNewWorkUntilRegistryIsReopened() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        registry.execute(AgentAsyncWorkKind.ECONOMY_ANALYSIS, () -> await(started, release));
        assertTrue(started.await(5, TimeUnit.SECONDS));

        AgentAsyncExecutorRegistry.ShutdownResult result =
                registry.shutdownAllAndAwait(5, TimeUnit.SECONDS);

        assertEquals(1, result.executors());
        assertTrue(result.unterminatedExecutors().isEmpty());
        assertFalse(result.interrupted());
        assertFalse(registry.accepting());
        assertThrows(RejectedExecutionException.class,
                () -> registry.execute(AgentAsyncWorkKind.ECONOMY_ANALYSIS, () -> { }));

        registry.startAccepting();
        CountDownLatch restarted = new CountDownLatch(1);
        registry.execute(AgentAsyncWorkKind.ECONOMY_ANALYSIS, restarted::countDown);
        assertTrue(restarted.await(5, TimeUnit.SECONDS));
    }

    private static void await(CountDownLatch started, CountDownLatch release) {
        started.countDown();
        try {
            release.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
