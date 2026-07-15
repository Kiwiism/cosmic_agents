package server.agents.plans.mapleisland;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import server.agents.capabilities.objective.AmherstNpcInteractionDelay;
import server.agents.capabilities.movement.AgentRelaxerSpotCatalog;
import server.agents.capabilities.movement.AgentRelaxerSpotReservationRuntime;
import server.agents.capabilities.quest.AmherstQuestCatalog;
import server.agents.capabilities.quest.AmherstScopePolicy;
import server.agents.capabilities.quest.MapleIslandSouthperryQuestCatalog;
import server.agents.capabilities.runtime.AgentCapabilityRuntime;
import server.agents.plans.amherst.AmherstObjectiveDelay;
import server.agents.plans.amherst.AmherstObjectiveHandlerRegistry;
import server.agents.plans.amherst.AmherstObjectiveProgressStatus;
import server.agents.plans.amherst.AmherstObjectiveReconciler;
import server.agents.plans.amherst.AmherstPlanCard;
import server.agents.plans.amherst.AmherstPlanCardLoader;
import server.agents.plans.amherst.AmherstPlanObjective;
import server.agents.plans.amherst.AmherstPlanObjectiveKind;
import server.agents.plans.amherst.AmherstPlanProgressService;
import server.agents.plans.amherst.AmherstPlanRuntimeRunner;
import server.agents.plans.amherst.AmherstPlanValidationCode;
import server.agents.plans.amherst.AmherstPlanValidationException;
import server.agents.plans.amherst.AmherstPlanValidator;
import server.agents.plans.amherst.FileAmherstPlanProgressStore;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.testing.MutablePrimitiveGatewayFixture;

import java.awt.Point;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MapleIslandSouthperryPlanTest {
    private static final long TEST_TICK_MS = 100L;
    private static final Path CARD = Path.of(
            "docs", "agents", "plans", "maple-island-southperry-mvp.plan.json");

    @TempDir
    Path tempDir;

    @AfterEach
    void releaseRelaxerSpot() {
        AgentRelaxerSpotReservationRuntime.release(77);
    }

    @Test
    void planLoadsWithVerifiedPrerequisiteOrderAndNoAmherstObjectives() throws Exception {
        AmherstPlanCard card = loadCard();

        assertEquals("maple-island-southperry-mvp", card.planId());
        assertEquals(2000000, card.exitCriteria().finalMapId());
        assertEquals(Set.of(1046), card.exitCriteria().startOnlyQuestIds());
        assertEquals(Set.of(1039, 1040, 1041, 1042, 1043, 1044, 1045, 1046,
                        8020, 8021, 8022, 8023, 8024, 8025),
                card.requiredQuestIds());
        assertTrue(index(card, AmherstPlanObjectiveKind.QUEST_COMPLETE, 1039)
                < index(card, AmherstPlanObjectiveKind.FORCE_COMPLETE_QUEST, 8020));
        assertTrue(index(card, AmherstPlanObjectiveKind.FORCE_COMPLETE_QUEST, 8020)
                < index(card, AmherstPlanObjectiveKind.FORCE_COMPLETE_QUEST, 8021));
        assertTrue(index(card, AmherstPlanObjectiveKind.FORCE_COMPLETE_QUEST, 8022)
                < index(card, AmherstPlanObjectiveKind.FORCE_COMPLETE_QUEST, 8023));
        assertTrue(index(card, AmherstPlanObjectiveKind.FORCE_COMPLETE_QUEST, 8023)
                < index(card, AmherstPlanObjectiveKind.FORCE_COMPLETE_QUEST, 8024));
        assertTrue(index(card, AmherstPlanObjectiveKind.FORCE_COMPLETE_QUEST, 8025)
                < index(card, AmherstPlanObjectiveKind.QUEST_START, 1041));
        assertTrue(index(card, AmherstPlanObjectiveKind.QUEST_START, 1040)
                < index(card, AmherstPlanObjectiveKind.QUEST_START, 1041));
        assertTrue(index(card, AmherstPlanObjectiveKind.QUEST_COMPLETE, 1041)
                < index(card, AmherstPlanObjectiveKind.QUEST_START, 1042));
        assertTrue(index(card, AmherstPlanObjectiveKind.QUEST_COMPLETE, 1044)
                < index(card, AmherstPlanObjectiveKind.QUEST_START, 1045));
        assertTrue(index(card, AmherstPlanObjectiveKind.QUEST_COMPLETE, 1045)
                < index(card, AmherstPlanObjectiveKind.QUEST_START, 1046));
        assertTrue(card.objectives().stream().flatMap(objective -> objective.allQuestIds().stream())
                .noneMatch(AmherstQuestCatalog.requiredQuestIdSet()::contains));
    }

    @Test
    void validationRejectsCompletingForbiddenShanksQuest() throws Exception {
        String json = Files.readString(CARD)
                .replaceFirst("\"questId\": 1046", "\"questId\": 1028");
        Path invalid = tempDir.resolve("forbidden-1028.json");
        Files.writeString(invalid, json);

        AmherstPlanValidationException failure = assertThrows(AmherstPlanValidationException.class,
                () -> loader().load(invalid));

        assertTrue(failure.issues().stream().anyMatch(issue ->
                issue.code() == AmherstPlanValidationCode.FORBIDDEN_QUEST));
    }

    @Test
    void finalStateRequiresBiggsStoryStartedNotCompletedAndShanksQuestIncomplete() throws Exception {
        AmherstPlanCard card = loadCard();
        AmherstPlanObjective stop = card.objectives().getLast();
        MutablePrimitiveGatewayFixture fixture = new MutablePrimitiveGatewayFixture();
        fixture.mapId.set(2000000);
        MapleIslandSouthperryQuestCatalog.completedQuestIdSet()
                .forEach(questId -> fixture.quests.put(questId, 2));
        fixture.quests.put(1046, 1);
        AmherstObjectiveReconciler reconciler = new AmherstObjectiveReconciler(fixture.gateway);

        assertTrue(reconciler.reconcile(card, stop, fixture.agent).satisfied());
        fixture.quests.put(1046, 2);
        assertFalse(reconciler.reconcile(card, stop, fixture.agent).satisfied());
        fixture.quests.put(1046, 1);
        fixture.quests.put(1028, 2);
        assertFalse(reconciler.reconcile(card, stop, fixture.agent).satisfied());
    }

    @Test
    void reconciliationRequiresAllThreeBranchesForMaisFirstTraining() throws Exception {
        AmherstPlanCard card = loadCard();
        AmherstPlanObjective training = card.objectives().stream()
                .filter(objective -> objective.kind() == AmherstPlanObjectiveKind.KILL_MOBS)
                .filter(objective -> Integer.valueOf(1041).equals(objective.questId()))
                .findFirst().orElseThrow();
        MutablePrimitiveGatewayFixture fixture = new MutablePrimitiveGatewayFixture();
        fixture.quests.put(1041, 1);
        fixture.progress.put(MutablePrimitiveGatewayFixture.key(1041, 130100), 5);
        fixture.items.put(4000003, 1);
        AmherstObjectiveReconciler reconciler = new AmherstObjectiveReconciler(fixture.gateway);

        assertFalse(reconciler.reconcile(card, training, fixture.agent).satisfied());
        fixture.items.put(4000003, 3);
        assertTrue(reconciler.reconcile(card, training, fixture.agent).satisfied());
    }

    @Test
    void simulatedFullRunRetriesPortalAndFinishesAtSouthperryWithoutReplayingAmherst() throws Exception {
        AmherstPlanCard card = loadCard();
        MutablePrimitiveGatewayFixture fixture = southperryFixture();
        fixture.mapId.set(1000000);
        fixture.level.set(6);
        fixture.experience.set(79);
        fixture.lootRewards.put(4000003, 3);
        fixture.lootRewards.put(4000004, 1);
        fixture.lootRewards.put(4000001, 1);
        AtomicInteger portalAttempts = new AtomicInteger();
        configureRegularPortalEntry(fixture, portalAttempts);

        FileAmherstPlanProgressStore store = new FileAmherstPlanProgressStore(tempDir);
        AmherstPlanRuntimeRunner runner = runner(card, store, fixture);
        runner.start(fixture.entry, fixture.agent, 1L);
        assertEquals(1000000, fixture.mapId.get());
        assertTrue(fixture.entry.amherstPlanExecutionState().active());
        drive(runner, fixture.entry, fixture, 2L, 12_000);

        assertTrue(fixture.entry.amherstPlanExecutionState().completed(),
                () -> "error=" + fixture.entry.amherstPlanExecutionState().lastError()
                        + " active=" + fixture.entry.amherstPlanExecutionState().active()
                        + " assigned=" + fixture.entry.amherstPlanExecutionState().assignedObjectiveId()
                        + " child=" + fixture.entry.capabilityRuntimeState().activeCapabilityId()
                        + " map=" + fixture.mapId.get() + " quests=" + fixture.quests
                        + " progress=" + fixture.progress + " items=" + fixture.items);
        assertEquals(2000000, fixture.mapId.get());
        assertEquals(1, fixture.quests.get(1046));
        assertEquals(0, fixture.quests.getOrDefault(1028, 0));
        assertTrue(MapleIslandSouthperryQuestCatalog.completedQuestIdSet().stream()
                .allMatch(questId -> fixture.quests.getOrDefault(questId, 0) == 2));
        assertTrue(AmherstQuestCatalog.requiredQuestIdSet().stream()
                .noneMatch(fixture.questCompletions::containsKey));
        assertTrue(portalAttempts.get() > 1);
        assertTrue(AgentRelaxerSpotCatalog.spots(AgentRelaxerSpotCatalog.Pool.SOUTHPERRY_RIGHT).stream()
                .anyMatch(spot -> spot.x() == fixture.position.x && spot.y() == fixture.position.y),
                () -> "unexpected Southperry rest position=" + fixture.position);
        verify(fixture.gateway).sitChair(fixture.agent, 3010000);
        verify(fixture.gateway, atLeastOnce()).facePosition(eq(fixture.agent), any(Point.class));
        var persisted = store.load(card.planId(), fixture.agent.getId());
        assertTrue(card.objectives().stream().allMatch(objective ->
                persisted.objectives()
                        .get(objective.objectiveId()).status() == AmherstObjectiveProgressStatus.SATISFIED));
    }

    @Test
    void persistedSouthperryProgressResumesAtFirstLiveUnsatisfiedObjective() throws Exception {
        AmherstPlanCard card = loadCard();
        MutablePrimitiveGatewayFixture fixture = southperryFixture();
        fixture.mapId.set(1000000);
        FileAmherstPlanProgressStore store = new FileAmherstPlanProgressStore(tempDir);
        AmherstPlanRuntimeRunner first = runner(card, store, fixture);
        first.start(fixture.entry, fixture.agent, 1L);

        long now = driveUntilQuest(first, fixture.entry, fixture, 1040, 1, 2L, 300);
        AgentRuntimeEntry resumedEntry = new AgentRuntimeEntry(
                fixture.agent, mock(client.Character.class), null);
        AmherstPlanRuntimeRunner resumed = runner(card, store, fixture);
        resumed.start(resumedEntry, fixture.agent, now);
        resumed.tick(resumedEntry, fixture.agent, now + 1L);

        assertEquals(1, index(card, resumedEntry.amherstPlanExecutionState().assignedObjectiveId()));
        assertEquals(1, fixture.quests.get(1040));
    }

    private static MutablePrimitiveGatewayFixture southperryFixture() {
        MutablePrimitiveGatewayFixture fixture = new MutablePrimitiveGatewayFixture();
        fixture.items.put(3010000, 1);
        when(fixture.gateway.itemCount(any(), eq(3010000))).thenReturn(1);
        when(fixture.gateway.npcPosition(any(), eq(MapleIslandSouthperryQuestCatalog.SHANKS_NPC_ID)))
                .thenReturn(new Point(3341, -105));
        fixture.directPortalEdges.addAll(Set.of(
                "1000000:1010000", "1010000:1000000", "1010000:1020000",
                "1010100:1010000", "1010200:1010000", "1010300:1010000",
                "1010400:1010000", "1020000:1010000", "1020000:2000000",
                "2000000:1020000"));
        doAnswer(invocation -> {
            @SuppressWarnings("unchecked") Set<Integer> mobs = invocation.getArgument(1);
            int activeQuest = MapleIslandSouthperryQuestCatalog.requiredQuestIdSet().stream()
                    .filter(questId -> fixture.quests.getOrDefault(questId, 0) == 1)
                    .filter(questId -> questId != 1040)
                    .findFirst().orElse(0);
            mobs.forEach(mobId -> fixture.progress.put(
                    MutablePrimitiveGatewayFixture.key(activeQuest, mobId), 999));
            return null;
        }).when(fixture.gateway).grind(any(), any());
        return fixture;
    }

    private static void configureRegularPortalEntry(MutablePrimitiveGatewayFixture fixture,
                                                    AtomicInteger attempts) {
        doAnswer(invocation -> {
            if (attempts.incrementAndGet() == 1) {
                return false;
            }
            int current = fixture.mapId.get();
            int portalId = invocation.getArgument(1);
            if (current == 1010000 && portalId == 1) {
                fixture.mapId.set(fixture.quests.getOrDefault(1041, 0) == 1 ? 1010100
                        : fixture.quests.getOrDefault(1042, 0) == 1 ? 1010200
                        : fixture.quests.getOrDefault(1043, 0) == 1 ? 1010300 : 1010400);
                return true;
            }
            Integer destination = null;
            if (current == 1000000) {
                destination = 1010000;
            } else if (current == 1010100 || current == 1010200
                    || current == 1010300 || current == 1010400) {
                destination = 1010000;
            } else if (current == 1010000 && fixture.quests.getOrDefault(1040, 0) == 1
                    && fixture.quests.getOrDefault(1044, 0) == 2) {
                destination = 1000000;
            } else if (current == 1010000) {
                destination = 1020000;
            } else if (current == 1020000) {
                destination = 2000000;
            }
            if (destination == null) {
                return false;
            }
            fixture.mapId.set(destination);
            return true;
        }).when(fixture.gateway).enterPortal(any(), anyInt());
    }

    private static AmherstPlanRuntimeRunner runner(AmherstPlanCard card,
                                                   FileAmherstPlanProgressStore store,
                                                   MutablePrimitiveGatewayFixture fixture) {
        return new AmherstPlanRuntimeRunner(card, store, new AmherstPlanProgressService(),
                new AmherstObjectiveReconciler(fixture.gateway),
                new AmherstObjectiveHandlerRegistry(fixture.gateway,
                        AmherstNpcInteractionDelay.NONE, AmherstScopePolicy.southperry()),
                AmherstObjectiveDelay.NONE);
    }

    private static void drive(AmherstPlanRuntimeRunner runner,
                              AgentRuntimeEntry entry,
                              MutablePrimitiveGatewayFixture fixture,
                              long startMs,
                              int maxTicks) {
        for (int i = 0; i < maxTicks && entry.amherstPlanExecutionState().active(); i++) {
            long now = startMs + i * TEST_TICK_MS;
            boolean consumed = runner.tick(entry, fixture.agent, now);
            if (!consumed && entry.capabilityRuntimeState().hasActiveCapability()) {
                AgentCapabilityRuntime.tick(entry, fixture.agent, now);
            }
        }
    }

    private static long driveUntilQuest(AmherstPlanRuntimeRunner runner,
                                        AgentRuntimeEntry entry,
                                        MutablePrimitiveGatewayFixture fixture,
                                        int questId,
                                        int status,
                                        long startMs,
                                        int maxTicks) {
        for (int i = 0; i < maxTicks; i++) {
            long now = startMs + i * TEST_TICK_MS;
            boolean consumed = runner.tick(entry, fixture.agent, now);
            if (!consumed && entry.capabilityRuntimeState().hasActiveCapability()) {
                AgentCapabilityRuntime.tick(entry, fixture.agent, now);
            }
            if (fixture.quests.getOrDefault(questId, 0) == status
                    && !entry.capabilityRuntimeState().hasActiveCapability()) {
                return now + TEST_TICK_MS;
            }
        }
        throw new AssertionError("quest state was not reached");
    }

    private static AmherstPlanCard loadCard() throws Exception {
        return loader().load(CARD);
    }

    private static AmherstPlanCardLoader loader() {
        return new AmherstPlanCardLoader(new ObjectMapper(), AmherstPlanValidator.southperry());
    }

    private static int index(AmherstPlanCard card, AmherstPlanObjectiveKind kind, int questId) {
        for (int i = 0; i < card.objectives().size(); i++) {
            AmherstPlanObjective objective = card.objectives().get(i);
            if (objective.kind() == kind && objective.allQuestIds().contains(questId)) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    private static int index(AmherstPlanCard card, String objectiveId) {
        for (int i = 0; i < card.objectives().size(); i++) {
            if (card.objectives().get(i).objectiveId().equals(objectiveId)) {
                return i;
            }
        }
        return -1;
    }
}
