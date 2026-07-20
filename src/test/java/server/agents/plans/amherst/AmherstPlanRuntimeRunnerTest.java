package server.agents.plans.amherst;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import server.agents.capabilities.objective.AgentObjectiveRecoveryPolicy;
import server.agents.capabilities.runtime.AgentCapabilityRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.testing.MutablePrimitiveGatewayFixture;

import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AmherstPlanRuntimeRunnerTest {
    private static final long TICK_MS = 50L;

    @TempDir
    Path tempDir;

    @Test
    void minimalPlanExecutesInOrderAndPersistsOnlyAfterLiveVerification() throws Exception {
        var fixture = new MutablePrimitiveGatewayFixture();
        AmherstPlanCard card = minimalCard();
        FileAmherstPlanProgressStore store = new FileAmherstPlanProgressStore(tempDir);
        AmherstPlanRuntimeRunner runner = runner(card, store, fixture);
        runner.start(fixture.entry, fixture.agent, 1L);

        drive(runner, fixture.entry, fixture, 2L, 300);

        assertTrue(fixture.entry.amherstPlanExecutionState().completed());
        assertEquals(2, fixture.quests.get(1031));
        assertEquals(2, fixture.quests.get(1021));
        assertEquals(1, fixture.questCompletions.get(1031));
        assertEquals(1, fixture.questCompletions.get(1021));
        assertEquals(20000, fixture.mapId.get());

        AmherstPlanProgressSnapshot persisted = store.load(card.planId(), fixture.agent.getId());
        assertTrue(card.objectives().stream().allMatch(objective ->
                persisted.objectives().get(objective.objectiveId()).status()
                        == AmherstObjectiveProgressStatus.SATISFIED));
        assertEquals(AmherstPlanJournalEventType.PLAN_COMPLETED,
                persisted.journal().get(persisted.journal().size() - 1).type());
        assertTrue(persisted.journal().stream().anyMatch(event ->
                event.type() == AmherstPlanJournalEventType.CHILD_HANDOFF));
    }

    @Test
    void automaticPlanWaitsConfiguredDelayBeforeAssigningNextObjective() throws Exception {
        var fixture = new MutablePrimitiveGatewayFixture();
        AmherstPlanCard card = minimalCard();
        FileAmherstPlanProgressStore store = new FileAmherstPlanProgressStore(tempDir);
        AmherstPlanRuntimeRunner runner = new AmherstPlanRuntimeRunner(
                card, store, new AmherstPlanProgressService(),
                new AmherstObjectiveReconciler(fixture.gateway),
                new AmherstObjectiveHandlerRegistry(fixture.gateway), () -> 50L);
        runner.start(fixture.entry, fixture.agent, 1L);

        driveUntilSatisfied(runner, fixture.entry, fixture, "q1031", 2L, 100);
        long readyAt = fixture.entry.amherstPlanExecutionState().nextObjectiveAtMs;
        assertTrue(readyAt > 0L);
        assertNull(fixture.entry.amherstPlanExecutionState().assignedObjectiveId());

        assertTrue(runner.tick(fixture.entry, fixture.agent, readyAt - 1L));
        assertNull(fixture.entry.amherstPlanExecutionState().assignedObjectiveId());
        assertTrue(runner.tick(fixture.entry, fixture.agent, readyAt));
        assertEquals("q1021-start", fixture.entry.amherstPlanExecutionState().assignedObjectiveId());
    }

    @Test
    void initialDelayPreventsFirstObjectiveAssignment() throws Exception {
        var fixture = new MutablePrimitiveGatewayFixture();
        AmherstPlanRuntimeRunner runner = runner(
                minimalCard(), new FileAmherstPlanProgressStore(tempDir), fixture);

        runner.start(fixture.entry, fixture.agent, 1_000L,
                AmherstPlanExecutionMode.AUTO, AmherstPlanObserver.NONE, 3_000L);

        assertTrue(runner.tick(fixture.entry, fixture.agent, 3_999L));
        assertNull(fixture.entry.amherstPlanExecutionState().assignedObjectiveId());
        assertTrue(runner.tick(fixture.entry, fixture.agent, 4_000L));
        assertEquals("q1031", fixture.entry.amherstPlanExecutionState().assignedObjectiveId());
    }

    @Test
    void restartReconcilesAndResumesAtFirstUnsatisfiedWithoutRepeatingReward() throws Exception {
        var fixture = new MutablePrimitiveGatewayFixture();
        AmherstPlanCard card = minimalCard();
        FileAmherstPlanProgressStore store = new FileAmherstPlanProgressStore(tempDir);
        AmherstPlanRuntimeRunner firstRunner = runner(card, store, fixture);
        firstRunner.start(fixture.entry, fixture.agent, 1L);

        long now = driveUntilSatisfied(firstRunner, fixture.entry, fixture, "q1031", 2L, 100);
        assertEquals(1, fixture.questCompletions.get(1031));

        AgentRuntimeEntry restartedEntry = new AgentRuntimeEntry(fixture.agent, mock(client.Character.class), null);
        AmherstPlanRuntimeRunner restartedRunner = runner(card, store, fixture);
        restartedRunner.start(restartedEntry, fixture.agent, now);
        restartedRunner.tick(restartedEntry, fixture.agent, now + 1);

        assertEquals("q1021-start", restartedEntry.amherstPlanExecutionState().assignedObjectiveId());
        drive(restartedRunner, restartedEntry, fixture, now + 2, 300);
        assertEquals(1, fixture.questCompletions.get(1031));
        assertTrue(restartedEntry.amherstPlanExecutionState().completed());
    }

    @Test
    void stalePersistedSuccessIsReopenedFromLiveState() throws Exception {
        var fixture = new MutablePrimitiveGatewayFixture();
        AmherstPlanCard card = minimalCard();
        FileAmherstPlanProgressStore store = new FileAmherstPlanProgressStore(tempDir);
        AmherstPlanProgressService progress = new AmherstPlanProgressService();
        AmherstPlanProgressSnapshot stale = AmherstPlanProgressSnapshot.empty(card.planId(), fixture.agent.getId());
        stale = progress.reconcile(stale, "q1031", true, "stale success", 1L);
        store.save(stale);

        AmherstPlanRuntimeRunner runner = runner(card, store, fixture);
        runner.start(fixture.entry, fixture.agent, 2L);
        runner.tick(fixture.entry, fixture.agent, 3L);

        assertEquals("q1031", fixture.entry.amherstPlanExecutionState().assignedObjectiveId());
        assertTrue(fixture.entry.amherstPlanExecutionState().progress().journal().stream().anyMatch(event ->
                event.type() == AmherstPlanJournalEventType.RECONCILED_REOPENED));
    }

    @Test
    void cancellationPersistsAndASecondStartCanResumeSafely() throws Exception {
        var fixture = new MutablePrimitiveGatewayFixture();
        AmherstPlanCard card = minimalCard();
        FileAmherstPlanProgressStore store = new FileAmherstPlanProgressStore(tempDir);
        AmherstPlanRuntimeRunner runner = runner(card, store, fixture);
        runner.start(fixture.entry, fixture.agent, 1L);
        runner.tick(fixture.entry, fixture.agent, 2L);
        AgentCapabilityRuntime.tick(fixture.entry, fixture.agent, 3L);

        runner.cancel(fixture.entry);
        AgentCapabilityRuntime.tick(fixture.entry, fixture.agent, 4L);
        runner.tick(fixture.entry, fixture.agent, 5L);

        assertFalse(fixture.entry.amherstPlanExecutionState().active());
        assertEquals(AmherstObjectiveProgressStatus.CANCELLED,
                store.load(card.planId(), fixture.agent.getId()).objectives().get("q1031").status());

        runner.start(fixture.entry, fixture.agent, 6L);
        drive(runner, fixture.entry, fixture, 7L, 300);

        assertTrue(fixture.entry.amherstPlanExecutionState().completed());
        assertEquals(2, store.load(card.planId(), fixture.agent.getId())
                .objectives().get("q1031").attempts());
    }

    @Test
    void stalledObjectiveIsNudgedThenReplannedWithoutStoppingThePlan() throws Exception {
        var fixture = new MutablePrimitiveGatewayFixture();
        AmherstPlanCard card = minimalCard();
        FileAmherstPlanProgressStore store = new FileAmherstPlanProgressStore(tempDir);
        AmherstPlanRuntimeRunner runner = new AmherstPlanRuntimeRunner(
                card, store, new AmherstPlanProgressService(),
                new AmherstObjectiveReconciler(fixture.gateway),
                new AmherstObjectiveHandlerRegistry(fixture.gateway),
                AmherstObjectiveDelay.NONE,
                new AgentObjectiveRecoveryPolicy(5_000L, 15_000L, 2, 500L));
        runner.start(fixture.entry, fixture.agent, 1L);
        runner.tick(fixture.entry, fixture.agent, 2L);

        assertFalse(runner.tick(fixture.entry, fixture.agent, 5_002L));
        assertTrue(fixture.entry.amherstPlanExecutionState().active());
        assertEquals("q1031", fixture.entry.amherstPlanExecutionState().assignedObjectiveId());
        assertEquals(5_002L, fixture.entry.amherstPlanExecutionState()
                .objectiveWatchdog.lastNudgeAtMs());

        assertTrue(runner.tick(fixture.entry, fixture.agent, 15_002L));
        assertTrue(fixture.entry.amherstPlanExecutionState().active());
        assertNull(fixture.entry.amherstPlanExecutionState().assignedObjectiveId());
        assertFalse(fixture.entry.capabilityRuntimeState().hasActiveCapability());
        assertEquals(AmherstObjectiveProgressStatus.FAILED,
                fixture.entry.amherstPlanExecutionState().progress()
                        .objectives().get("q1031").status());

        assertTrue(runner.tick(fixture.entry, fixture.agent, 15_502L));
        assertEquals("q1031", fixture.entry.amherstPlanExecutionState().assignedObjectiveId());
        assertEquals(2, fixture.entry.amherstPlanExecutionState().progress()
                .objectives().get("q1031").attempts());
    }

    @Test
    void exhaustedWorldResourceObjectiveWaitsAndPeriodicallyResumes() throws Exception {
        var fixture = new MutablePrimitiveGatewayFixture();
        fixture.mapId.set(1010000);
        fixture.quests.put(1045, 1);
        AmherstPlanCard card = worldResourceCard();
        AmherstPlanRuntimeRunner runner = new AmherstPlanRuntimeRunner(
                card, new FileAmherstPlanProgressStore(tempDir), new AmherstPlanProgressService(),
                new AmherstObjectiveReconciler(fixture.gateway),
                new AmherstObjectiveHandlerRegistry(fixture.gateway),
                AmherstObjectiveDelay.NONE,
                new AgentObjectiveRecoveryPolicy(5_000L, 15_000L, 0, 500L));
        runner.start(fixture.entry, fixture.agent, 1L);

        assertTrue(runner.tick(fixture.entry, fixture.agent, 2L));
        assertEquals("wait-for-orange-mushroom",
                fixture.entry.amherstPlanExecutionState().assignedObjectiveId());

        assertTrue(runner.tick(fixture.entry, fixture.agent, 15_002L));
        assertTrue(fixture.entry.amherstPlanExecutionState().active());
        assertNull(fixture.entry.amherstPlanExecutionState().assignedObjectiveId());
        assertEquals(30_002L, fixture.entry.amherstPlanExecutionState().nextObjectiveAtMs);
        assertTrue(fixture.entry.amherstPlanExecutionState().progress().journal().stream()
                .anyMatch(event -> event.type() == AmherstPlanJournalEventType.RETRY
                        && event.message().contains("world-resource recheck")));

        assertTrue(runner.tick(fixture.entry, fixture.agent, 30_001L));
        assertNull(fixture.entry.amherstPlanExecutionState().assignedObjectiveId());
        assertTrue(runner.tick(fixture.entry, fixture.agent, 30_002L));
        assertEquals("wait-for-orange-mushroom",
                fixture.entry.amherstPlanExecutionState().assignedObjectiveId());

        assertTrue(runner.tick(fixture.entry, fixture.agent, 45_002L));
        assertTrue(fixture.entry.amherstPlanExecutionState().active());
        assertEquals(60_002L, fixture.entry.amherstPlanExecutionState().nextObjectiveAtMs);
    }

    @Test
    void declaredIndependentWorkRunsBeforeADeferredWorldResourceRetry() throws Exception {
        var fixture = new MutablePrimitiveGatewayFixture();
        fixture.mapId.set(1010000);
        fixture.quests.put(1045, 1);
        AmherstPlanCard base = worldResourceCard();
        AmherstPlanObjective independent = objective(
                "independent-quest", AmherstPlanObjectiveKind.QUEST_CHAIN, 1010000,
                null, List.of(1031), null, null, null, null, null);
        AmherstPlanCard card = new AmherstPlanCard(
                base.schemaVersion(), base.planId(), base.title(), base.category(), base.priority(),
                base.status(), base.objectiveMode(), base.focusPolicy(), base.entryCriteria(),
                base.exitCriteria(), base.requiredQuestIds(), base.excludedQuestIds(),
                List.of(base.objectives().get(0), independent));
        AmherstPlanObjectiveDeferralPolicy policy = new AmherstPlanObjectiveDeferralPolicy() {
            @Override
            public boolean canDefer(AmherstPlanCard ignoredCard,
                                    AmherstPlanObjective ignoredObjective,
                                    server.agents.capabilities.runtime.AgentCapabilityResult ignoredResult) {
                return true;
            }

            @Override
            public List<AmherstPlanObjective> independentAlternatives(
                    AmherstPlanCard ignoredCard,
                    AmherstPlanObjective ignoredObjective,
                    AmherstPlanProgressSnapshot ignoredProgress,
                    int ignoredDeferralStage) {
                return List.of(independent);
            }
        };
        AmherstPlanRuntimeRunner runner = new AmherstPlanRuntimeRunner(
                card, new FileAmherstPlanProgressStore(tempDir), new AmherstPlanProgressService(),
                new AmherstObjectiveReconciler(fixture.gateway),
                new AmherstObjectiveHandlerRegistry(fixture.gateway), AmherstObjectiveDelay.NONE,
                new AgentObjectiveRecoveryPolicy(5_000L, 15_000L, 2, 500L), policy);
        runner.start(fixture.entry, fixture.agent, 1L);

        assertTrue(runner.tick(fixture.entry, fixture.agent, 2L));
        assertTrue(runner.tick(fixture.entry, fixture.agent, 15_002L));
        assertNull(fixture.entry.amherstPlanExecutionState().assignedObjectiveId());
        assertTrue(runner.tick(fixture.entry, fixture.agent, 15_502L));

        assertEquals("independent-quest",
                fixture.entry.amherstPlanExecutionState().assignedObjectiveId());
        assertEquals(1, fixture.entry.amherstPlanExecutionState()
                .objectiveDeferralStages.get("wait-for-orange-mushroom"));
        assertTrue(fixture.entry.amherstPlanExecutionState().progress().journal().stream()
                .anyMatch(event -> event.message().contains("deferred for independent plan work")));
    }

    @Test
    void activeQuestCapabilityContinuesWhenLiveQuestStateAlreadySatisfied() throws Exception {
        var fixture = new MutablePrimitiveGatewayFixture();
        AmherstPlanCard card = minimalCard();
        FileAmherstPlanProgressStore store = new FileAmherstPlanProgressStore(tempDir);
        AmherstPlanRuntimeRunner runner = runner(card, store, fixture);
        when(fixture.gateway.completeQuest(fixture.agent, 1031, 2100)).thenAnswer(invocation -> {
            fixture.quests.put(1031, 2);
            return false;
        });
        runner.start(fixture.entry, fixture.agent, 1L);

        long nextTick = driveUntilSatisfied(
                runner, fixture.entry, fixture, "q1031", 2L, 150);

        assertTrue(fixture.entry.amherstPlanExecutionState().active());
        assertEquals(AmherstObjectiveProgressStatus.SATISFIED,
                store.load(card.planId(), fixture.agent.getId()).objectives().get("q1031").status());
        runner.tick(fixture.entry, fixture.agent, nextTick);
        assertEquals("q1021-start",
                fixture.entry.amherstPlanExecutionState().assignedObjectiveId());
    }

    @Test
    void manualModeRunsExactlyOneObjectivePerAdvanceAndPublishesProgress() throws Exception {
        var fixture = new MutablePrimitiveGatewayFixture();
        AmherstPlanCard card = minimalCard();
        FileAmherstPlanProgressStore store = new FileAmherstPlanProgressStore(tempDir);
        AmherstPlanRuntimeRunner runner = runner(card, store, fixture);
        List<String> events = new ArrayList<>();
        fixture.questExpRewards.put(1031, 5);
        runner.start(fixture.entry, fixture.agent, 1L,
                AmherstPlanExecutionMode.MANUAL, events::add);

        long now = driveUntilPaused(runner, fixture.entry, fixture, 2L, 100);

        assertEquals(2, fixture.quests.get(1031));
        assertEquals(0, fixture.quests.getOrDefault(1021, 0));
        assertTrue(fixture.entry.amherstPlanExecutionState().waitingForAdvance());
        assertTrue(events.stream().anyMatch(message -> message.startsWith("Starting 1/")));
        assertTrue(events.stream().anyMatch(message -> message.startsWith("SUCCESS 1/")));
        assertTrue(events.stream().anyMatch(message ->
                message.equals("Agent progress: Lv1 EXP 0 -> Lv1 EXP 5.")));
        assertTrue(events.stream().anyMatch(message -> message.contains("Overall progress: 1/")));

        for (int i = 0; i < 5; i++) {
            assertFalse(runner.tick(fixture.entry, fixture.agent, now + i));
        }
        assertEquals(0, fixture.quests.getOrDefault(1021, 0));

        assertTrue(runner.requestAdvance(fixture.entry));
        driveUntilPaused(runner, fixture.entry, fixture, now + 10, 100);

        assertEquals(1, fixture.quests.get(1021));
    }

    private static AmherstPlanRuntimeRunner runner(AmherstPlanCard card,
                                                   AmherstPlanProgressStore store,
                                                   MutablePrimitiveGatewayFixture fixture) {
        return new AmherstPlanRuntimeRunner(card, store, new AmherstPlanProgressService(),
                new AmherstObjectiveReconciler(fixture.gateway),
                new AmherstObjectiveHandlerRegistry(fixture.gateway));
    }

    private static void drive(AmherstPlanRuntimeRunner runner,
                              AgentRuntimeEntry entry,
                              MutablePrimitiveGatewayFixture fixture,
                              long startMs,
                              int maxTicks) {
        for (int i = 0; i < maxTicks && entry.amherstPlanExecutionState().active(); i++) {
            long now = startMs + i * TICK_MS;
            boolean consumed = runner.tick(entry, fixture.agent, now);
            if (!consumed && entry.capabilityRuntimeState().hasActiveCapability()) {
                AgentCapabilityRuntime.tick(entry, fixture.agent, now);
            }
        }
        assertFalse(entry.amherstPlanExecutionState().active(),
                () -> "plan did not stop: error=" + entry.amherstPlanExecutionState().lastError()
                        + " objective=" + entry.amherstPlanExecutionState().assignedObjectiveId()
                        + " capability=" + entry.capabilityRuntimeState().activeCapabilityId()
                        + " result=" + entry.capabilityRuntimeState().lastResult());
    }

    private static long driveUntilSatisfied(AmherstPlanRuntimeRunner runner,
                                            AgentRuntimeEntry entry,
                                            MutablePrimitiveGatewayFixture fixture,
                                            String objectiveId,
                                            long startMs,
                                            int maxTicks) {
        for (int i = 0; i < maxTicks; i++) {
            long now = startMs + i * TICK_MS;
            boolean consumed = runner.tick(entry, fixture.agent, now);
            if (!consumed && entry.capabilityRuntimeState().hasActiveCapability()) {
                AgentCapabilityRuntime.tick(entry, fixture.agent, now);
            }
            AmherstObjectiveProgress progress = entry.amherstPlanExecutionState().progress()
                    .objectives().get(objectiveId);
            if (progress != null && progress.status() == AmherstObjectiveProgressStatus.SATISFIED) {
                return now + TICK_MS;
            }
        }
        throw new AssertionError("objective was not satisfied");
    }

    private static long driveUntilPaused(AmherstPlanRuntimeRunner runner,
                                         AgentRuntimeEntry entry,
                                         MutablePrimitiveGatewayFixture fixture,
                                         long startMs,
                                         int maxTicks) {
        for (int i = 0; i < maxTicks; i++) {
            long now = startMs + i * TICK_MS;
            boolean consumed = runner.tick(entry, fixture.agent, now);
            if (!consumed && entry.capabilityRuntimeState().hasActiveCapability()) {
                AgentCapabilityRuntime.tick(entry, fixture.agent, now);
            }
            if (entry.amherstPlanExecutionState().waitingForAdvance()
                    && !entry.capabilityRuntimeState().hasActiveCapability()) {
                return now + TICK_MS;
            }
        }
        throw new AssertionError("manual plan did not pause");
    }

    private static AmherstPlanCard minimalCard() {
        List<AmherstPlanObjective> objectives = List.of(
                objective("q1031", AmherstPlanObjectiveKind.QUEST_CHAIN, 10000,
                        null, List.of(1031), null, null, null, null, null),
                objective("q1021-start", AmherstPlanObjectiveKind.QUEST_START, 20000,
                        1021, List.of(), 2000, null, null, null, null),
                objective("q1021-use", AmherstPlanObjectiveKind.USE_ITEM, 20000,
                        1021, List.of(), null, 2010007, null, null, null),
                objective("q1021-complete", AmherstPlanObjectiveKind.QUEST_COMPLETE, 20000,
                        1021, List.of(), 2000, null, null, null, null),
                objective("stop", AmherstPlanObjectiveKind.STOP_PLAN, 20000,
                        null, List.of(), null, null, null, null, "minimal proof complete"));
        return new AmherstPlanCard(1, "amherst-phase2-minimal", "minimal", "test", "high", "test",
                "ordered-with-live-quest-validation",
                new AmherstPlanCard.FocusPolicy("locked", false, Set.of("emergency"), "always"),
                new AmherstPlanCard.EntryCriteria(10000, "maple-island", "clean"),
                new AmherstPlanCard.ExitCriteria("all", 20000, Set.of(1028), Set.of(1010000), Set.of(22000)),
                Set.of(1031, 1021), Set.of(1000, 1001, 1028), objectives);
    }

    private static AmherstPlanCard worldResourceCard() {
        List<AmherstPlanObjective> objectives = List.of(
                objective("wait-for-orange-mushroom", AmherstPlanObjectiveKind.KILL_MOBS, 1010000,
                        1045, List.of(), null, null, List.of(1001000), List.of(1),
                        "wait for a world-spawned Orange Mushroom"));
        return new AmherstPlanCard(1, "world-resource-recovery", "resource recovery", "test", "high", "test",
                "live-world-resource-retry",
                new AmherstPlanCard.FocusPolicy("locked", false, Set.of("emergency"), "always"),
                new AmherstPlanCard.EntryCriteria(1010000, "maple-island", "clean"),
                new AmherstPlanCard.ExitCriteria("all", 1010000, Set.of(), Set.of(), Set.of()),
                Set.of(1045), Set.of(), objectives);
    }

    private static AmherstPlanObjective objective(String id,
                                                  AmherstPlanObjectiveKind kind,
                                                  int mapId,
                                                  Integer questId,
                                                  List<Integer> questIds,
                                                  Integer npcId,
                                                  Integer itemId,
                                                  List<Integer> mobs,
                                                  List<Integer> counts,
                                                  String reason) {
        return new AmherstPlanObjective(id, kind, 0, 0, mapId, questId, questIds,
                npcId, List.of(), itemId, List.of(), mobs == null ? List.of() : mobs,
                counts == null ? List.of() : counts, null, reason);
    }
}
