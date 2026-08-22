package server.agents.social.conversation;

import client.Character;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.async.AgentAsyncExecutorRegistry;
import server.agents.runtime.async.AgentAsyncTaskGateway;
import server.agents.runtime.async.AgentAsyncWorkKind;
import server.agents.social.contracts.DialogueContextSnapshot;
import server.agents.social.contracts.DialogueMode;
import server.agents.social.contracts.DialogueRequest;
import server.agents.social.contracts.DialogueResult;
import server.agents.social.contracts.DialogueStyleSnapshot;
import server.agents.social.provider.DeterministicDialogueProvider;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentSocialDialogueApplicationTest {
    private AgentRuntimeEntry entry;

    @BeforeEach
    void setUp() {
        AgentAsyncTaskGateway.runtime().clearAll();
        entry = entry(4001);
        AgentRuntimeRegistry.registerEntry(5001, entry);
    }

    @AfterEach
    void tearDown() {
        AgentAsyncTaskGateway.runtime().clearAll();
        AgentAsyncExecutorRegistry.runtime().shutdown(AgentAsyncWorkKind.LLM_NETWORK);
        AgentRuntimeRegistry.clear();
        AgentSocialDialogueApplication.clearAgentRuntimeState(4001);
    }

    @Test
    void blockingProviderDoesNotBlockSubmittingRuntimeThread() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<DialogueResult> delivered = new AtomicReference<>();
        AgentSocialDialogueApplication application = application(request -> {
            started.countDown();
            await(release);
            return Optional.of(DialogueResult.model("model reply", "test:model", 1));
        });

        long startedAt = System.nanoTime();
        assertEquals(AgentSocialDialogueApplication.SubmissionStatus.ACCEPTED,
                application.submit(entry, request("one"), delivered::set));
        long submitMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        assertTrue(submitMs < 250, "submission blocked for " + submitMs + "ms");
        assertTrue(started.await(5, TimeUnit.SECONDS));
        assertNull(delivered.get());
        release.countDown();
        awaitMailbox(entry);
        entry.actionMailbox().drain(entry, 8);
        assertEquals("model reply", delivered.get().displayText());
    }

    @Test
    void secondConcurrentRequestFallsBackInsteadOfQueueingAnotherInference() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<DialogueResult> second = new AtomicReference<>();
        AgentSocialDialogueApplication application = application(request -> {
            started.countDown();
            await(release);
            return Optional.of(DialogueResult.model("first", "test:model", 1));
        });
        application.submit(entry, request("first"), ignored -> { });
        assertTrue(started.await(5, TimeUnit.SECONDS));

        assertEquals(AgentSocialDialogueApplication.SubmissionStatus.FALLBACK_QUEUED,
                application.submit(entry, request("second"), second::set));
        if (second.get() == null) {
            awaitMailbox(entry);
            entry.actionMailbox().drain(entry, 8);
        }
        assertEquals(DialogueResult.Source.DETERMINISTIC, second.get().source());
        assertEquals("model-in-flight", second.get().fallbackReason());
        release.countDown();
    }

    @Test
    void staleSessionDropsCompletedModelReply() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<DialogueResult> delivered = new AtomicReference<>();
        AgentSocialDialogueApplication application = application(request -> {
            started.countDown();
            await(release);
            return Optional.of(DialogueResult.model("late", "test:model", 1));
        });
        application.submit(entry, request("stale"), delivered::set);
        assertTrue(started.await(5, TimeUnit.SECONDS));

        AgentRuntimeRegistry.unregisterEntry(entry);
        AgentRuntimeRegistry.registerEntry(5001, entry(4001));
        release.countDown();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (AgentAsyncTaskGateway.runtime().pendingCount() > 0 && System.nanoTime() < deadline) {
            Thread.sleep(5L);
        }

        assertEquals(0, AgentAsyncTaskGateway.runtime().pendingCount());
        assertNull(delivered.get());
    }

    private static AgentSocialDialogueApplication application(
            server.agents.social.provider.DialogueProvider provider) {
        return new AgentSocialDialogueApplication(new AgentSocialDialogueService(
                DialogueMode.DIALOGUE_ONLY, provider, new DeterministicDialogueProvider()));
    }

    private static DialogueRequest request(String id) {
        return new DialogueRequest(
                id,
                "casual.general",
                "Alice",
                "hello",
                new DialogueContextSnapshot(
                        4001,
                        1,
                        "Mina",
                        "relaxed-v1",
                        new DialogueStyleSnapshot("friendly-casual-v1", 1, "friendly and casual",
                                25, 45, 40, 80, 15, List.of("yo")),
                        "between activities",
                        "No prior relationship history.",
                        100,
                        Map.of()),
                List.of(),
                List.of("hey", "yeah"),
                true,
                64,
                5_000);
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

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }
}
