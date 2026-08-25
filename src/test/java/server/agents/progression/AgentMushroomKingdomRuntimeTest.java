package server.agents.progression;

import client.Character;
import client.Job;
import client.QuestStatus;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.navigation.AgentRouteOutcome;
import server.agents.capabilities.navigation.AgentRouteStatus;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;
import server.maps.MapleMap;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentMushroomKingdomRuntimeTest {
    @Test
    void stagesAnUnobservedAgentAfterNpcTopologyMakesNoProgress() {
        Harness harness = new Harness(110, List.of(3300005, 3300006, 3300007));
        harness.completeBefore(2324);
        harness.mapId = AgentMushroomKingdomCatalog.ENTRANCE_MAP_ID;
        harness.position = new Point(128, 335);
        Point npc = new Point(-45, 26);
        when(harness.gateway.npcPosition(eq(harness.agent), anyInt())).thenReturn(npc);
        when(harness.gateway.groundPoint(any(), eq(npc))).thenReturn(npc);

        harness.tick();
        harness.nowMs += 20_001L;
        harness.tick();

        assertEquals(npc, harness.position);
    }

    @Test
    void q2323ReturnSnapshotRecoversACharacterThatFallsBelowTheMapBounds() {
        Harness harness = new Harness(110, List.of(3300005, 3300006, 3300007));
        harness.completeBefore(2323);
        harness.statuses.put(2323, QuestStatus.Status.STARTED.getId());
        harness.mapId = 106020401;
        harness.position = new Point(382, 2_214);
        harness.mapArea = new Rectangle(180, -660, 1_350, 1_290);

        harness.tick();

        assertEquals(new Point(0, 0), harness.position);
        assertEquals(106020401, harness.mapId);
        assertTrue(harness.state.reason().contains("map 106020401"));

        harness.tick();

        assertTrue(harness.portalEntries.contains("106020401:4"));
        assertEquals(106020400, harness.mapId);
    }

    @Test
    void q2323ReturnSnapshotUsesTheAuthoredExitPortalAfterCompletingTheObjective() {
        Harness harness = new Harness(110, List.of(3300005, 3300006, 3300007));
        harness.completeBefore(2323);
        harness.statuses.put(2323, QuestStatus.Status.STARTED.getId());
        harness.mapId = 106020401;

        harness.tick();

        assertTrue(harness.portalEntries.contains("106020401:4"));
        assertEquals(106020400, harness.mapId);
    }

    @Test
    void refreshesAccuracySupplyOnlyForUnbuffedAccuracyDependentMeleeBuilds() {
        assertTrue(AgentMushroomKingdomRuntime.accuracySupplyNeeded(110, 20, false, 1));
        assertTrue(AgentMushroomKingdomRuntime.accuracySupplyNeeded(120, 20, false, 200));
        assertTrue(AgentMushroomKingdomRuntime.accuracySupplyNeeded(510, 30, false, 200));
        assertFalse(AgentMushroomKingdomRuntime.accuracySupplyNeeded(130, 40, false, 200));
        assertFalse(AgentMushroomKingdomRuntime.accuracySupplyNeeded(120, 20, true, 200));
        assertFalse(AgentMushroomKingdomRuntime.accuracySupplyNeeded(510, 30, true, 200));
        assertFalse(AgentMushroomKingdomRuntime.accuracySupplyNeeded(120, 20, false, 0));
        assertFalse(AgentMushroomKingdomRuntime.accuracySupplyNeeded(310, 20, false, 200));
        assertFalse(AgentMushroomKingdomRuntime.accuracySupplyNeeded(520, 30, false, 200));
    }

    @Test
    void clearsAuthoredFillerWhenAQuestSpeciesIsSpawnStarved() {
        Harness harness = new Harness(310, List.of(3300005, 3300006, 3300007));
        harness.completeBefore(2323);
        harness.statuses.put(2323, QuestStatus.Status.STARTED.getId());
        harness.statuses.put(AgentMushroomKingdomRuntime.FIRST_THORN_BARRIER_UNLOCK_QUEST_ID,
                QuestStatus.Status.STARTED.getId());
        harness.items.put(4000501, 0);
        harness.mapId = 106020401;
        when(harness.gateway.configuredMonsterSpawnCounts(harness.agent))
                .thenReturn(Map.of(3300001, 6, 3300002, 4));
        when(harness.gateway.liveMonsterCounts(harness.agent))
                .thenReturn(Map.of(3300001, 10));

        harness.tick();

        assertEquals(Set.of(3300002), harness.lastPreferredMobIds);
        assertEquals(Set.of(3300001), harness.lastFallbackMobIds);
    }

    private static final List<Integer> EXPLORER_SECOND_JOBS = List.of(
            110, 120, 130, 210, 220, 230, 310, 320, 410, 420, 510, 520);

    @Test
    void everyExplorerSecondJobCompletesTheRealOpenerAndQuest2336() {
        for (int jobId : EXPLORER_SECOND_JOBS) {
            Harness harness = new Harness(jobId, List.of(3300005, 3300005, 3300006, 3300007));

            harness.runToCompletion();

            int entryQuest = AgentMushroomKingdomCatalog.entryQuestForJob(jobId);
            assertEquals(QuestStatus.Status.COMPLETED.getId(), harness.status(entryQuest));
            assertEquals(QuestStatus.Status.COMPLETED.getId(), harness.status(2336));
            assertTrue(harness.transitions.indexOf("start:2331")
                            < harness.transitions.indexOf("complete:2333"),
                    "Royal Seal quest must be active before the Prime Minister dies");
            assertEquals(4, harness.yetiEntries,
                    "one duplicate color should require a fourth solo instance");
        }
    }

    @Test
    void everyQuestBoundaryCanReconcileAndFinishAfterRestart() {
        for (AgentMushroomKingdomCatalog.QuestNode boundary
                : AgentMushroomKingdomCatalog.mainline()) {
            Harness harness = new Harness(110, List.of(3300005, 3300006, 3300007));
            harness.completeBefore(boundary.questId());
            if (harness.status(2335) == QuestStatus.Status.COMPLETED.getId()) {
                // q2336 is intentionally accepted from Violetta before q2335;
                // this is the valid persisted state after the secret-room boundary.
                harness.statuses.put(2336, QuestStatus.Status.STARTED.getId());
                harness.items.put(4032387, 1);
                harness.items.put(4032386, 1);
            }

            harness.runToCompletion();

            assertEquals(QuestStatus.Status.COMPLETED.getId(), harness.status(2336),
                    "resume failed at quest " + boundary.questId());
        }
    }

    @Test
    void bothBossInstancesResumeFromLiveQuestState() {
        Harness yeti = new Harness(110, List.of(3300006, 3300007));
        yeti.completeBefore(2330);
        yeti.statuses.put(2330, QuestStatus.Status.STARTED.getId());
        yeti.progress.put("2330:3300005", 1);
        yeti.mapId = 106021500;
        yeti.runToCompletion();
        assertEquals(QuestStatus.Status.COMPLETED.getId(), yeti.status(2330));

        Harness primeMinister = new Harness(110, List.of(3300005, 3300006, 3300007));
        primeMinister.completeBefore(2333);
        primeMinister.statuses.put(2331, QuestStatus.Status.STARTED.getId());
        primeMinister.statuses.put(2332, QuestStatus.Status.COMPLETED.getId());
        primeMinister.statuses.put(2333, QuestStatus.Status.STARTED.getId());
        primeMinister.mapId = 106021600;
        primeMinister.runToCompletion();
        assertEquals(QuestStatus.Status.COMPLETED.getId(), primeMinister.status(2333));

        Harness bossDoorTransition = new Harness(110, List.of(3300005, 3300006, 3300007));
        bossDoorTransition.completeBefore(2333);
        bossDoorTransition.statuses.put(2331, QuestStatus.Status.STARTED.getId());
        bossDoorTransition.mapId = 106021402;
        bossDoorTransition.bossDoorRejections = 1;
        bossDoorTransition.tick();
        assertTrue(bossDoorTransition.portalEntries.contains("106021402:2"));
        assertEquals(106021402, bossDoorTransition.mapId);
        assertTrue(bossDoorTransition.state.reason().contains("busy"));
        assertEquals(1, bossDoorTransition.bossDoorAttempts);
        bossDoorTransition.tick();
        assertEquals(1, bossDoorTransition.bossDoorAttempts);
        bossDoorTransition.nowMs += 4_001L;
        bossDoorTransition.tick();
        assertEquals(2, bossDoorTransition.bossDoorAttempts);
        assertEquals(106021600, bossDoorTransition.mapId);
    }

    @Test
    void scriptedCastleRouteCrossesBothQuestGatedBoundaries() {
        Harness stagedApproach = new Harness(110, List.of(3300005, 3300006, 3300007));
        stagedApproach.completeBefore(2323);
        stagedApproach.statuses.put(2323, QuestStatus.Status.STARTED.getId());
        stagedApproach.statuses.put(
                AgentMushroomKingdomRuntime.FIRST_THORN_BARRIER_UNLOCK_QUEST_ID,
                QuestStatus.Status.COMPLETED.getId());
        stagedApproach.items.put(4000501, 0);
        stagedApproach.mapId = AgentMushroomKingdomCatalog.ENTRANCE_MAP_ID;
        stagedApproach.tick();
        assertEquals(106020300, stagedApproach.mapId);

        Harness firstBarrier = new Harness(110, List.of(3300005, 3300006, 3300007));
        firstBarrier.completeBefore(2323);
        firstBarrier.statuses.put(2323, QuestStatus.Status.STARTED.getId());
        firstBarrier.mapId = 106020300;
        firstBarrier.statuses.put(AgentMushroomKingdomRuntime.FIRST_THORN_BARRIER_UNLOCK_QUEST_ID,
                QuestStatus.Status.STARTED.getId());
        firstBarrier.items.put(4000501, 0);
        firstBarrier.tick();
        assertTrue(firstBarrier.portalEntries.contains("106020300:3"));

        Harness castleGate = new Harness(110, List.of(3300005, 3300006, 3300007));
        castleGate.completeBefore(2325);
        castleGate.statuses.put(2325, QuestStatus.Status.STARTED.getId());
        castleGate.statuses.put(2324, QuestStatus.Status.COMPLETED.getId());
        castleGate.mapId = 106020400;
        castleGate.tick();
        assertTrue(castleGate.portalEntries.contains("106020400:3"));

        Harness eastTower = new Harness(110, List.of(3300005, 3300006, 3300007));
        eastTower.completeBefore(2332);
        eastTower.statuses.put(2331, QuestStatus.Status.STARTED.getId());
        eastTower.statuses.put(2332, QuestStatus.Status.STARTED.getId());
        eastTower.items.put(4032388, 1);
        eastTower.mapId = 106021400;
        eastTower.tick();
        assertTrue(eastTower.portalEntries.contains("106021400:2"));

        Harness weddingHallApproach = new Harness(110, List.of(3300005, 3300006, 3300007));
        weddingHallApproach.completeBefore(2332);
        weddingHallApproach.statuses.put(2331, QuestStatus.Status.STARTED.getId());
        weddingHallApproach.statuses.put(2332, QuestStatus.Status.STARTED.getId());
        weddingHallApproach.items.put(4032388, 1);
        weddingHallApproach.mapId = 106020501;
        weddingHallApproach.tick();
        assertEquals(106021400, weddingHallApproach.mapId);
        weddingHallApproach.tick();
        assertEquals(106021401, weddingHallApproach.mapId);
        weddingHallApproach.tick();
        assertTrue(weddingHallApproach.portalEntries.contains("106021401:2"));
        assertTrue(weddingHallApproach.portalEntries.contains("106021402:2"));
        assertEquals(106021600, weddingHallApproach.mapId);
        assertEquals(QuestStatus.Status.COMPLETED.getId(), weddingHallApproach.status(2332));

        Harness princessExit = new Harness(110, List.of(3300005, 3300006, 3300007));
        princessExit.completeBefore(2335);
        princessExit.statuses.put(2336, QuestStatus.Status.STARTED.getId());
        princessExit.items.put(4032387, 1);
        princessExit.items.put(4032386, 1);
        princessExit.statuses.put(2335, QuestStatus.Status.STARTED.getId());
        princessExit.items.put(4032405, 1);
        princessExit.mapId = 106021600;
        princessExit.tick();
        assertTrue(princessExit.portalEntries.contains("106021600:1"));
        assertEquals(106021402, princessExit.mapId);
        princessExit.tick();
        assertTrue(princessExit.portalEntries.contains("106021402:1"));
        assertEquals(106021401, princessExit.mapId);
        princessExit.tick();
        assertTrue(princessExit.portalEntries.contains("106021401:1"));
        assertEquals(106021400, princessExit.mapId);

        Harness secretRoomExit = new Harness(110, List.of(3300005, 3300006, 3300007));
        secretRoomExit.completeBefore(2331);
        secretRoomExit.statuses.put(2331, QuestStatus.Status.STARTED.getId());
        secretRoomExit.items.put(4001318, 1);
        secretRoomExit.mapId = 106021001;
        secretRoomExit.tick();
        assertTrue(secretRoomExit.portalEntries.contains("106021001:1"));
        assertEquals(106021000, secretRoomExit.mapId);
    }

    @Test
    void acceptsTruthRevealedBeforeTheSecretRoomSendsThePlayerAway() {
        Harness harness = new Harness(110, List.of(3300005, 3300006, 3300007));
        harness.completeBefore(2335);
        harness.mapId = 106021600;

        harness.tick();

        assertTrue(harness.transitions.contains("start:2336"));
        assertEquals(QuestStatus.Status.STARTED.getId(), harness.status(2336));
        assertEquals(1, harness.itemCount(4032387));
        assertEquals(1, harness.itemCount(4032386));
        assertEquals(QuestStatus.Status.NOT_STARTED.getId(), harness.status(2335));

        harness.tick();

        assertTrue(harness.transitions.contains("start:2335"));
    }

    @Test
    void activeSecretRoomQuestRecoversItsMissingScriptGrantedKeyBeforeLeavingPrincess() {
        Harness harness = new Harness(110, List.of(3300005, 3300006, 3300007));
        harness.completeBefore(2335);
        harness.statuses.put(2336, QuestStatus.Status.STARTED.getId());
        harness.items.put(4032387, 1);
        harness.items.put(4032386, 1);
        harness.statuses.put(2335, QuestStatus.Status.STARTED.getId());
        harness.items.put(4032405, 0);
        harness.mapId = 106021600;

        harness.tick();

        assertTrue(harness.transitions.contains("start:2335"));
        assertEquals(1, harness.itemCount(4032405));
        assertEquals(106021600, harness.mapId);
    }

    @Test
    void bruceBranchReturnsThroughTheHenesysPetParkThemeDungeonPortal() {
        Harness harness = new Harness(110, List.of(3300005, 3300006, 3300007));
        harness.completeBefore(2321);
        harness.statuses.put(2321, QuestStatus.Status.STARTED.getId());
        harness.mapId = 100000000;

        harness.tick();
        assertEquals(100000002, harness.mapId);

        harness.tick();
        assertTrue(harness.portalEntries.contains("100000002:4"));
        assertEquals(AgentMushroomKingdomCatalog.ENTRANCE_MAP_ID, harness.mapId);
    }

    @Test
    void missingKillerSporeUsesQuest2338Recovery() {
        Harness harness = new Harness(110, List.of(3300005, 3300006, 3300007));
        harness.completeBefore(2322);
        harness.statuses.put(2322, QuestStatus.Status.STARTED.getId());
        harness.mapId = 106020300;
        harness.items.put(2430014, 0);
        harness.statuses.put(AgentMushroomKingdomRuntime.FIRST_THORN_BARRIER_UNLOCK_QUEST_ID,
                QuestStatus.Status.NOT_STARTED.getId());

        harness.tick();

        assertTrue(harness.transitions.contains("start:2338"));
        assertEquals(1, harness.itemCount(2430014));
    }

    @Test
    void missingRoyalSealUsesQuest2342Recovery() {
        Harness harness = new Harness(110, List.of(3300005, 3300006, 3300007));
        harness.completeBefore(2331);
        harness.statuses.put(2331, QuestStatus.Status.STARTED.getId());
        harness.mapId = 106021600;
        harness.items.put(4001318, 0);

        harness.tick();

        assertTrue(harness.transitions.contains("start:2342"));
        assertEquals(1, harness.itemCount(4001318));
    }

    @Test
    void fullInventoryBlocksBeforeTheFinalYetiExitLosesTheKey() {
        Harness harness = new Harness(110, List.of(3300005, 3300006, 3300007));
        harness.completeBefore(2330);
        harness.statuses.put(2330, QuestStatus.Status.STARTED.getId());
        harness.progress.put("2330:3300005", 1);
        harness.progress.put("2330:3300006", 1);
        harness.progress.put("2330:3300007", 1);
        harness.mapId = 106021500;
        harness.freeSlots = 0;

        harness.tick();

        assertEquals(AgentMushroomKingdomState.Phase.BLOCKED, harness.state.phase());
        assertTrue(harness.state.reason().contains("Wedding Hall key"));
    }

    @Test
    void deadAgentWaitsForRevivalAndStableFailureEventuallyTimesOut() {
        Harness dead = new Harness(110, List.of(3300005, 3300006, 3300007));
        when(dead.gateway.alive(dead.agent)).thenReturn(false);
        dead.tick();
        assertEquals(AgentMushroomKingdomState.Phase.ACTIVE, dead.state.phase());
        assertTrue(dead.state.reason().contains("revive"));

        Harness stalled = new Harness(110, List.of(3300005, 3300006, 3300007));
        stalled.completeBefore(2312);
        stalled.statuses.put(2312, QuestStatus.Status.STARTED.getId());
        stalled.mapId = AgentMushroomKingdomCatalog.ENTRANCE_MAP_ID;
        when(stalled.gateway.npcPosition(eq(stalled.agent), anyInt())).thenReturn(null);
        stalled.tick();
        stalled.nowMs += 45 * 60_000L + 1L;
        stalled.tick();
        assertEquals(AgentMushroomKingdomState.Phase.BLOCKED, stalled.state.phase());
        assertTrue(stalled.state.reason().contains("45 minutes"));
    }

    private static final class Harness {
        private final Character agent = mock(Character.class);
        private final MapleMap map = mock(MapleMap.class);
        private final PrimitiveCapabilityGateway gateway = mock(PrimitiveCapabilityGateway.class);
        private final AgentRuntimeEntry entry = new AgentRuntimeEntry(agent, null, null);
        private final AgentMushroomKingdomState state = entry.capabilityStates()
                .require(AgentMushroomKingdomState.STATE_KEY);
        private final Map<Integer, Integer> statuses = new HashMap<>();
        private final Map<Integer, Integer> items = new HashMap<>();
        private final Map<String, Integer> progress = new HashMap<>();
        private final List<String> transitions = new ArrayList<>();
        private final Set<String> portalEntries = new HashSet<>();
        private final List<Integer> yetiRolls;
        private int mapId;
        private Point position = new Point(0, 0);
        private Rectangle mapArea = new Rectangle();
        private int freeSlots = 10;
        private int yetiEntries;
        private int bossDoorAttempts;
        private int bossDoorRejections;
        private long nowMs = 1_000L;
        private Set<Integer> lastPreferredMobIds = Set.of();
        private Set<Integer> lastFallbackMobIds = Set.of();

        private Harness(int jobId, List<Integer> yetiRolls) {
            this.yetiRolls = List.copyOf(yetiRolls);
            int entryQuest = AgentMushroomKingdomCatalog.entryQuestForJob(jobId);
            mapId = AgentMushroomKingdomCatalog.entryLeaderMap(entryQuest);
            state.begin(1L);
            items.put(4000499, 100);
            items.put(4000500, 100);
            items.put(4000501, 100);
            items.put(4001317, 1);
            items.put(4000502, 200);
            items.put(4000503, 200);

            when(agent.getJob()).thenReturn(Job.getById(jobId));
            when(agent.getLevel()).thenReturn(30);
            when(agent.getMapId()).thenAnswer(ignored -> mapId);
            when(agent.getMap()).thenReturn(map);
            when(agent.getPosition()).thenAnswer(ignored -> new Point(position));
            when(map.getMapArea()).thenAnswer(ignored -> new Rectangle(mapArea));
            when(gateway.alive(agent)).thenReturn(true);
            when(gateway.grounded(agent)).thenReturn(true);
            when(gateway.mapId(agent)).thenAnswer(ignored -> mapId);
            when(gateway.position(agent)).thenAnswer(ignored -> new Point(position));
            when(gateway.npcPosition(eq(agent), anyInt())).thenReturn(new Point(0, 0));
            when(gateway.portalPosition(eq(agent), anyInt())).thenReturn(new Point(0, 0));
            when(gateway.groundPoint(eq(map), org.mockito.ArgumentMatchers.any(Point.class)))
                    .thenAnswer(invocation -> new Point(invocation.getArgument(1)));
            doAnswer(invocation -> {
                position = new Point(invocation.getArgument(2));
                return null;
            }).when(gateway).stagePosition(eq(entry), eq(agent), org.mockito.ArgumentMatchers.any(Point.class));
            when(gateway.questStatus(eq(agent), anyInt())).thenAnswer(invocation ->
                    status(invocation.getArgument(1)));
            when(gateway.questProgress(eq(agent), anyInt(), anyInt())).thenAnswer(invocation -> {
                int questId = invocation.getArgument(1);
                int progressId = invocation.getArgument(2);
                if (questId == 2333 && progressId == 3300008
                        && status(2333) == QuestStatus.Status.STARTED.getId()) return 1;
                return progress.getOrDefault(questId + ":" + progressId, 0);
            });
            when(gateway.itemCount(eq(agent), anyInt())).thenAnswer(invocation ->
                    itemCount(invocation.getArgument(1)));
            when(gateway.freeSlots(eq(agent), anyInt())).thenAnswer(ignored -> freeSlots);
            when(gateway.travelTo(eq(entry), eq(agent), anyInt(), anyLong())).thenAnswer(invocation -> {
                int source = mapId;
                int destination = invocation.getArgument(2);
                mapId = destination;
                return new AgentRouteOutcome(AgentRouteStatus.ARRIVED, source,
                        destination, destination, false);
            });
            when(gateway.startQuest(eq(agent), anyInt(), anyInt())).thenAnswer(invocation -> {
                startQuest(invocation.getArgument(1));
                return true;
            });
            when(gateway.completeQuest(eq(agent), anyInt(), anyInt())).thenAnswer(invocation -> {
                completeQuest(invocation.getArgument(1));
                return true;
            });
            when(gateway.useItem(eq(agent), anyInt())).thenAnswer(invocation -> {
                int itemId = invocation.getArgument(1);
                if (itemId == 2430014) statuses.put(
                        AgentMushroomKingdomRuntime.FIRST_THORN_BARRIER_UNLOCK_QUEST_ID,
                        QuestStatus.Status.STARTED.getId());
                if (itemId == 2430015) statuses.put(2324, QuestStatus.Status.COMPLETED.getId());
                items.put(itemId, 0);
                return true;
            });
            when(gateway.enterPortal(eq(agent), anyInt())).thenAnswer(invocation ->
                    enterPortal(invocation.getArgument(1)));
            when(gateway.runPortalNpcScript(eq(agent), anyInt(), anyInt(),
                    org.mockito.ArgumentMatchers.<int[]>any())).thenAnswer(ignored -> {
                int roll = yetiRolls.get(Math.min(yetiEntries, yetiRolls.size() - 1));
                yetiEntries++;
                progress.put("2330:" + roll, 1);
                mapId = 106021500;
                return true;
            });
            when(gateway.liveMonsterCount(eq(agent), org.mockito.ArgumentMatchers.anySet()))
                    .thenReturn(0);
            doAnswer(invocation -> {
                lastPreferredMobIds = Set.copyOf(invocation.getArgument(1));
                lastFallbackMobIds = Set.copyOf(invocation.getArgument(2));
                return null;
            }).when(gateway).grind(eq(entry), org.mockito.ArgumentMatchers.anySet(),
                    org.mockito.ArgumentMatchers.anySet());
            when(gateway.lootNearby(eq(agent), org.mockito.ArgumentMatchers.anySet())).thenReturn(true);
        }

        private void runToCompletion() {
            for (int tick = 0; tick < 500 && state.phase() == AgentMushroomKingdomState.Phase.ACTIVE; tick++) {
                tick();
            }
            assertEquals(AgentMushroomKingdomState.Phase.COMPLETE, state.phase(), state.reason());
        }

        private void tick() {
            AgentMushroomKingdomRuntime.tick(entry, agent, nowMs, gateway);
            nowMs += 1_000L;
        }

        private int status(int questId) {
            return statuses.getOrDefault(questId, QuestStatus.Status.NOT_STARTED.getId());
        }

        private int itemCount(int itemId) {
            return items.getOrDefault(itemId, 0);
        }

        private void completeBefore(int questId) {
            statuses.put(AgentMushroomKingdomCatalog.entryQuestForJob(agent.getJob().getId()),
                    QuestStatus.Status.COMPLETED.getId());
            for (AgentMushroomKingdomCatalog.QuestNode node : AgentMushroomKingdomCatalog.mainline()) {
                if (node.questId() == questId) break;
                statuses.put(node.questId(), QuestStatus.Status.COMPLETED.getId());
            }
            if (status(2318) == QuestStatus.Status.COMPLETED.getId()) items.put(2430014, 1);
            if (status(2324) == QuestStatus.Status.COMPLETED.getId()) {
                statuses.put(AgentMushroomKingdomRuntime.FIRST_THORN_BARRIER_UNLOCK_QUEST_ID,
                        QuestStatus.Status.STARTED.getId());
            }
            if (status(2330) == QuestStatus.Status.COMPLETED.getId()) {
                items.put(4032388, 1);
                statuses.put(2331, QuestStatus.Status.STARTED.getId());
            }
            if (status(2333) == QuestStatus.Status.COMPLETED.getId()) items.put(4001318, 1);
        }

        private void startQuest(int questId) {
            transitions.add("start:" + questId);
            statuses.put(questId, QuestStatus.Status.STARTED.getId());
            if (questId >= 2300 && questId <= 2304) {
                items.put(4032375, 1);
                mapId = AgentMushroomKingdomCatalog.ENTRANCE_MAP_ID;
            } else if (questId == 2324) {
                items.put(2430015, 1);
            } else if (questId == 2334) {
                statuses.put(questId, QuestStatus.Status.COMPLETED.getId());
            } else if (questId == 2335) {
                items.put(4032405, 1);
            } else if (questId == 2336) {
                items.put(4032387, 1);
                items.put(4032386, 1);
            } else if (questId == 2338) {
                items.put(2430014, 1);
                statuses.put(questId, QuestStatus.Status.COMPLETED.getId());
            } else if (questId == 2342) {
                items.put(4001318, 1);
                statuses.put(questId, QuestStatus.Status.COMPLETED.getId());
            }
        }

        private void completeQuest(int questId) {
            transitions.add("complete:" + questId);
            statuses.put(questId, QuestStatus.Status.COMPLETED.getId());
            if (questId >= 2300 && questId <= 2304) {
                items.put(4032375, 0);
                statuses.put(2312, QuestStatus.Status.STARTED.getId());
            } else if (questId == 2318) {
                items.put(2430014, 1);
            } else if (questId == 2333 && status(2331) == QuestStatus.Status.STARTED.getId()) {
                items.put(4001318, 1);
            }
        }

        private boolean enterPortal(int portalId) {
            portalEntries.add(mapId + ":" + portalId);
            if (mapId == 100000002 && portalId == 4) {
                mapId = AgentMushroomKingdomCatalog.ENTRANCE_MAP_ID;
            } else if (mapId == 106020300 && portalId == 1) {
                progress.put("2314:2314", 1);
            } else if (mapId == 106020300 && portalId == 3
                    && status(AgentMushroomKingdomRuntime.FIRST_THORN_BARRIER_UNLOCK_QUEST_ID)
                    == QuestStatus.Status.STARTED.getId()) {
                mapId = 106020400;
            } else if (mapId == 106020400 && portalId == 3) {
                if (status(2322) == QuestStatus.Status.STARTED.getId()
                        && progress.getOrDefault("2322:2322", 0) == 0) {
                    progress.put("2322:2322", 1);
                } else {
                    mapId = 106020501;
                }
            } else if (mapId == 106020401 && portalId == 4) {
                mapId = 106020400;
            } else if (mapId == 106021400 && portalId == 1) {
                mapId = 106021300;
            } else if (mapId == 106021400 && portalId == 2) {
                mapId = 106021401;
            } else if (mapId == 106021500 && portalId == 1) {
                if (progress.getOrDefault("2330:3300005", 0)
                        + progress.getOrDefault("2330:3300006", 0)
                        + progress.getOrDefault("2330:3300007", 0) == 3) {
                    items.put(4032388, 1);
                }
                mapId = 106021400;
            } else if (mapId == 106021402 && portalId == 2) {
                bossDoorAttempts++;
                if (bossDoorRejections-- > 0) return false;
                if (status(2332) == QuestStatus.Status.STARTED.getId()) {
                    statuses.put(2332, QuestStatus.Status.COMPLETED.getId());
                }
                mapId = 106021600;
            } else if (mapId == 106021402 && portalId == 1) {
                mapId = 106021401;
            } else if (mapId == 106021401 && portalId == 1) {
                mapId = 106021400;
            } else if (mapId == 106021401 && portalId == 2) {
                mapId = 106021402;
            } else if (mapId == 106021600 && portalId == 1) {
                mapId = 106021402;
            } else if (mapId == 106021001 && portalId == 1) {
                mapId = 106021000;
            } else if (mapId == 106021000 && portalId == 3) {
                statuses.put(2335, QuestStatus.Status.COMPLETED.getId());
                items.put(4032405, 0);
                mapId = 106021001;
            }
            return true;
        }
    }
}
