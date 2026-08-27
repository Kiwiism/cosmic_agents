package server.agents.progression;

import client.Character;
import client.CharacterDeletionService;
import client.Client;
import client.QuestStatus;
import constants.inventory.ItemConstants;
import net.server.Server;
import server.agents.integration.AgentAccountResolution;
import server.agents.integration.AgentBackingAccountSecurityRuntime;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentIdentityGatewayRuntime;
import server.agents.integration.AgentIdentityOrigin;
import server.agents.integration.AgentInventoryGatewayRuntime;
import server.agents.integration.AgentMapGatewayRuntime;
import server.agents.integration.AgentPersistenceGatewayRuntime;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.cosmic.CosmicAgentOfflineLoader;
import server.agents.integration.cosmic.CosmicAgentPopulationBackend;
import server.agents.plans.AgentPlanStartRequest;
import server.agents.plans.AgentUniversalPlanRuntime;
import server.agents.capabilities.combat.AgentGrindTargetStateRuntime;
import server.agents.field.AgentFieldObservationState;
import server.agents.runtime.AgentInteractionRuntime;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;
import server.agents.runtime.hunting.AgentHuntingVisitState;
import server.life.Monster;
import server.maps.MapleMap;
import server.quest.Quest;
import tools.DatabaseConnection;

import java.awt.Point;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Opt-in end-to-end Mushroom Kingdom runner using disposable live Agent characters. */
public final class AgentMushroomKingdomLiveSmokeMain {
    private static final int WORLD = 0;
    private static final int CHANNEL = 1;
    private static final Duration RUN_TIMEOUT = Duration.ofMinutes(180);
    private static final long STATUS_INTERVAL_MS = 10_000L;
    private static final long OFFLINE_MAP_MAINTENANCE_INTERVAL_MS = 5_000L;
    private static final boolean TEN_PERCENT_MODE =
            Boolean.getBoolean("mushroom.live.tenPercent");
    private static final Set<Integer> YETI_VARIANTS = Set.of(3300005, 3300006, 3300007);
    private static final AtomicBoolean CLEANUP_STARTED = new AtomicBoolean();

    private AgentMushroomKingdomLiveSmokeMain() { }

    public static void main(String[] args) {
        System.setProperty("polyglot.engine.WarnInterpreterOnly", "false");
        List<LiveAgent> agents = new ArrayList<>();
        Runtime.getRuntime().addShutdownHook(new Thread(
                () -> cleanup(agents), "mushroom-live-cleanup"));
        boolean passed = false;
        try {
            Server.getInstance().init();
            if (cleanupOnly(args)) {
                passed = true;
            } else {
                DiagnosticSnapshot snapshot = diagnosticSnapshot(
                        System.getProperty("mushroom.live.snapshot", ""));
                int startAtQuestId = snapshot == null
                        ? Integer.getInteger("mushroom.live.startAt", 0)
                        : snapshot.startAtQuestId();
                List<AgentSecondJobCatalog.Branch> branches = selectedBranches(args);
                System.out.printf("[MUSHROOM-LIVE] mode=%s branches=%s%n",
                        TEN_PERCENT_MODE ? "ten-percent" : "thirty-drop",
                        branches.stream().map(AgentSecondJobCatalog.Branch::id).toList());
                for (int ordinal = 0; ordinal < branches.size(); ordinal++) {
                    agents.add(launch(branches.get(ordinal), ordinal, startAtQuestId, snapshot));
                }
                observeToCompletion(agents);
                passed = true;
            }
        } catch (Throwable failure) {
            System.err.println("[MUSHROOM-LIVE] failure=" + failure.getMessage());
            failure.printStackTrace(System.err);
        } finally {
            passed &= cleanup(agents);
        }
        System.out.println("[MUSHROOM-LIVE] RESULT=" + (passed ? "PASS" : "FAIL"));
        System.exit(passed ? 0 : 1);
    }

    private static boolean cleanupOnly(String[] args) {
        if (args == null || args.length == 0 || !"--cleanup-only".equals(args[0])) return false;
        require(args.length > 1, "cleanup-only requires one or more account:character pairs");
        boolean clean = true;
        for (int index = 1; index < args.length; index++) {
            String[] ids = args[index].split(":", -1);
            require(ids.length == 2, "invalid cleanup identity: " + args[index]);
            int accountId = Integer.parseInt(ids[0]);
            int characterId = Integer.parseInt(ids[1]);
            clean &= cleanupIdentity(new TestIdentity(accountId, characterId, "cleanup-" + characterId), null);
        }
        require(clean, "one or more cleanup identities could not be removed");
        return true;
    }

    private static LiveAgent launch(AgentSecondJobCatalog.Branch branch, int ordinal,
                                    int startAtQuestId,
                                    DiagnosticSnapshot snapshot) throws Exception {
        TestIdentity identity = createTemporaryAgent(branch, ordinal);
        try {
            int entryQuest = AgentMushroomKingdomCatalog.entryQuestForJob(branch.targetJobId());
            MapleMap initialMap = AgentMapGatewayRuntime.map().resolveMap(
                    WORLD, CHANNEL, startAtQuestId == 0
                            ? AgentMushroomKingdomCatalog.entryLeaderMap(entryQuest)
                            : AgentMushroomKingdomCatalog.ENTRANCE_MAP_ID);
            require(initialMap != null, "entry map is unavailable for " + branch.id());
            Point initialSpawn = spawnPoint(initialMap, ordinal);
            Character agent = CosmicAgentOfflineLoader.loadOfflineAgent(
                    identity.characterId(), WORLD, CHANNEL, initialMap, initialSpawn);
            AgentRuntimeEntry entry = AgentInteractionRuntime.registerDirectorIdleAgent(agent);
            AgentMushroomKingdomFixtureService.Prepared prepared =
                    AgentMushroomKingdomFixtureService.prepare(
                            entry, branch, ordinal, mix(System.nanoTime(), ordinal),
                            System.currentTimeMillis());
            boolean entranceReady = entranceReadyScenario(startAtQuestId, snapshot);
            if (entranceReady) {
                AgentMushroomKingdomCohortService.prepareEntranceTurnIn(agent, entryQuest);
                System.out.printf("[MUSHROOM-LIVE] entrance-ready branch=%s q%d letter=4032375%n",
                        branch.id(), entryQuest);
            } else {
                fastForwardForDiagnostic(agent, branch, startAtQuestId);
                activateDiagnosticStart(agent, startAtQuestId,
                        snapshot == null ? Boolean.getBoolean("mushroom.live.activateStart")
                                : snapshot.activateQuest());
            }
            applyDiagnosticSnapshotItems(agent, snapshot);
            int stagedMapId = snapshot == null
                    ? Integer.getInteger("mushroom.live.stageMap", 0)
                    : snapshot.stageMapId();
            MapleMap stagedMap = stagedMapId == 0 ? initialMap
                    : AgentMapGatewayRuntime.map().resolveMap(WORLD, CHANNEL, stagedMapId);
            require(stagedMap != null, "diagnostic stage map is unavailable: " + stagedMapId);
            Point stagedSpawn = spawnPoint(stagedMap, ordinal);
            AgentMapGatewayRuntime.map().changeMapNear(agent, stagedMap, stagedSpawn);
            if (snapshot != null && snapshot.position() != null) {
                AgentPrimitiveCapabilityGatewayRuntime.gateway()
                        .stagePosition(entry, agent, snapshot.position());
            }
            AgentPrimitiveCapabilityGatewayRuntime.gateway().prepareNavigation(entry, agent);
            require(AgentUniversalPlanRuntime.start(entry, agent, "mushroom-kingdom-questline",
                    AgentPlanStartRequest.EMPTY, System.currentTimeMillis()),
                    "plan admission failed for " + branch.id());
            System.out.printf("[MUSHROOM-LIVE] launched branch=%s account=%d character=%d job=%d "
                            + "ap=%s sp=%s equipment=%s map=%d%n",
                    branch.id(), identity.accountId(), identity.characterId(), agent.getJob().getId(),
                    prepared.apProfileId(), prepared.spProfileId(), prepared.equipmentItemIds(),
                    agent.getMapId());
            return new LiveAgent(identity, branch, agent, entry);
        } catch (Throwable failure) {
            cleanupIdentity(identity, null);
            throw failure;
        }
    }

    private static void activateDiagnosticStart(Character agent, int startAtQuestId,
                                                boolean activate) {
        if (startAtQuestId == 0 || !activate) return;
        AgentMushroomKingdomCatalog.QuestNode node =
                AgentMushroomKingdomCatalog.require(startAtQuestId);
        Quest.getInstance(startAtQuestId).forceStartWithActions(agent, node.startNpcId());
        require(agent.getQuestStatus(startAtQuestId) == QuestStatus.Status.STARTED.getId(),
                "diagnostic could not activate q" + startAtQuestId);
        System.out.printf("[MUSHROOM-LIVE] activated q%d before staging%n", startAtQuestId);
    }

    private static void applyDiagnosticSnapshotItems(Character agent,
                                                     DiagnosticSnapshot snapshot) {
        if (snapshot == null || snapshot.itemId() <= 0 || snapshot.itemCount() <= 0) return;
        require(AgentInventoryGatewayRuntime.inventory().addItem(
                        agent, snapshot.itemId(), (short) snapshot.itemCount()),
                "diagnostic snapshot could not supply item " + snapshot.itemId());
        System.out.printf("[MUSHROOM-LIVE] snapshot=%s supplied item=%d count=%d%n",
                snapshot.id(), snapshot.itemId(), snapshot.itemCount());
    }

    static DiagnosticSnapshot diagnosticSnapshot(String value) {
        String id = value == null ? "" : value.trim().toLowerCase();
        return switch (id) {
            case "" -> null;
            case "q2323-return" -> new DiagnosticSnapshot(
                    id, 2323, 106020401, true, 4000501, 100, null);
            case "q2323-out-of-bounds" -> new DiagnosticSnapshot(
                    id, 2323, 106020401, true, 4000501, 100,
                    new Point(382, 2_214));
            case "q2325-entry" -> new DiagnosticSnapshot(
                    id, 2325, AgentMushroomKingdomCatalog.ENTRANCE_MAP_ID,
                    false, 0, 0, null);
            default -> throw new IllegalArgumentException(
                    "unknown Mushroom Kingdom diagnostic snapshot: " + value);
        };
    }

    static boolean entranceReadyScenario(int startAtQuestId, DiagnosticSnapshot snapshot) {
        return TEN_PERCENT_MODE && startAtQuestId == 2312 && snapshot == null;
    }

    private static void fastForwardForDiagnostic(Character agent,
                                                  AgentSecondJobCatalog.Branch branch,
                                                  int startAtQuestId) {
        if (startAtQuestId == 0) return;
        require(AgentMushroomKingdomCatalog.mainline().stream()
                        .anyMatch(node -> node.questId() == startAtQuestId),
                "unknown Mushroom Kingdom diagnostic start quest " + startAtQuestId);
        int entryQuestId = AgentMushroomKingdomCatalog.entryQuestForJob(branch.targetJobId());
        agent.updateQuestStatus(new QuestStatus(
                Quest.getInstance(entryQuestId), QuestStatus.Status.COMPLETED));
        for (AgentMushroomKingdomCatalog.QuestNode node
                : AgentMushroomKingdomCatalog.mainline()) {
            if (node.questId() >= startAtQuestId) break;
            agent.updateQuestStatus(new QuestStatus(
                    Quest.getInstance(node.questId()), QuestStatus.Status.COMPLETED));
        }
        if (startAtQuestId == 2322) {
            require(AgentInventoryGatewayRuntime.inventory().addItem(agent, 2430014, (short) 1),
                    "diagnostic fast-forward could not supply the Killer Mushroom Spore");
        } else if (startAtQuestId > 2322) {
            agent.updateQuestStatus(new QuestStatus(
                    Quest.getInstance(AgentMushroomKingdomRuntime
                            .FIRST_THORN_BARRIER_UNLOCK_QUEST_ID),
                    QuestStatus.Status.COMPLETED));
        }
        if (startAtQuestId >= 2332 && startAtQuestId <= 2335) {
            Quest.getInstance(2331).forceStartWithActions(agent, 1300003);
            require(AgentInventoryGatewayRuntime.inventory().addItem(agent, 4032388, (short) 1),
                    "diagnostic fast-forward could not supply the Wedding Hall key");
        }
        if (startAtQuestId == 2335) {
            require(AgentInventoryGatewayRuntime.inventory().addItem(agent, 4032405, (short) 1),
                    "diagnostic fast-forward could not supply the secret-room key");
            require(AgentInventoryGatewayRuntime.inventory().addItem(agent, 4001318, (short) 1),
                    "diagnostic fast-forward could not supply the recovered Royal Seal");
        }
        System.out.printf("[MUSHROOM-LIVE] diagnostic branch=%s startAt=q%d%n",
                branch.id(), startAtQuestId);
    }

    private static void observeToCompletion(List<LiveAgent> agents) throws Exception {
        long deadline = System.nanoTime() + RUN_TIMEOUT.toNanos();
        long nextStatusAt = 0L;
        long nextMapMaintenanceAt = 0L;
        while (System.nanoTime() < deadline) {
            long nowMs = System.currentTimeMillis();
            if (nowMs >= nextMapMaintenanceAt) {
                // Offline smoke-test characters are deliberately absent from channel player
                // storage, so RespawnTask skips their maps when no real client is connected.
                // Run the same map maintenance locally or finite initial spawns can exhaust.
                agents.stream().map(live -> live.agent.getMap()).distinct()
                        .forEach(MapleMap::respawn);
                nextMapMaintenanceAt = nowMs + OFFLINE_MAP_MAINTENANCE_INTERVAL_MS;
            }
            boolean allComplete = true;
            for (LiveAgent live : agents) {
                if (live.agent.getQuestStatus(AgentMushroomKingdomCatalog.FINAL_QUEST_ID)
                        == QuestStatus.Status.COMPLETED.getId()) continue;
                allComplete = false;
                AgentMushroomKingdomState state = live.entry.capabilityStates()
                        .find(AgentMushroomKingdomState.STATE_KEY).orElse(null);
                if (state != null && state.phase() == AgentMushroomKingdomState.Phase.BLOCKED) {
                    throw new IllegalStateException(live.branch.id() + " blocked at q"
                            + state.currentQuestId() + ": " + state.reason());
                }
                recordObservation(live, state);
            }
            for (LiveAgent live : agents) {
                AgentMushroomKingdomState state = live.entry.capabilityStates()
                        .find(AgentMushroomKingdomState.STATE_KEY).orElse(null);
                accelerate(live, state, agents);
            }
            if (allComplete) {
                for (LiveAgent live : agents) {
                    if (TEN_PERCENT_MODE) {
                        require(recoveryCoverageComplete(
                                        live.killerSporeLossInjected,
                                        live.killerSporeRecoveryConfirmed,
                                        live.royalSealLossInjected,
                                        live.royalSealRecoveryConfirmed),
                                live.branch.id() + " completed q2336 without proving q2338/q2342 recovery");
                    }
                    System.out.printf("[MUSHROOM-LIVE] complete branch=%s level=%d exp=%d map=%d accelerated=%s%n",
                            live.branch.id(), live.agent.getLevel(), live.agent.getExp(),
                            live.agent.getMapId(), live.acceleratedObjectives);
                }
                return;
            }
            if (nowMs >= nextStatusAt) {
                agents.forEach(AgentMushroomKingdomLiveSmokeMain::printStatus);
                nextStatusAt = nowMs + STATUS_INTERVAL_MS;
            }
            Thread.sleep(1_000L);
        }
        throw new IllegalStateException("Mushroom Kingdom run exceeded " + RUN_TIMEOUT.toMinutes() + " minutes");
    }

    private static void recordObservation(LiveAgent live, AgentMushroomKingdomState state) {
        if (state == null) return;
        int questId = state.currentQuestId();
        AgentFieldObservationState.Snapshot combat = live.entry.capabilityStates()
                .find(AgentFieldObservationState.STATE_KEY)
                .map(observation -> observation.snapshot(System.currentTimeMillis())).orElse(null);
        if (combat != null) {
            live.combatHitBaselines.putIfAbsent(questId, combat.hitLines());
            if (combat.hitLines() > live.combatHitBaselines.get(questId)) {
                live.combatProof.add(questId);
            }
        }
        AgentMushroomKingdomCatalog.QuestNode node = AgentMushroomKingdomCatalog.mainline().stream()
                .filter(candidate -> candidate.questId() == questId)
                .findFirst().orElse(null);
        if (node == null || node.itemId() <= 0
                || live.acceleratedObjectives.contains(questId)
                || live.agent.getQuestStatus(questId) != QuestStatus.Status.STARTED.getId()) return;
        int owned = AgentPrimitiveCapabilityGatewayRuntime.gateway().itemCount(live.agent, node.itemId());
        int observationLimit = TEN_PERCENT_MODE
                ? AgentMushroomKingdomCohortService.tenPercentRequirement(node.requiredCount())
                : 30;
        live.observedCounts.merge(
                questId, Math.min(observationLimit, Math.max(0, owned)), Math::max);
    }

    private static void accelerate(LiveAgent live, AgentMushroomKingdomState state,
                                   List<LiveAgent> agents) {
        if (state == null) return;
        exerciseRecoveryQuests(live, state);
        int questId = state.currentQuestId();
        if (accelerateRepeatedCastleTravel(live, questId, agents)) return;
        if (questId == 2335
                && live.agent.getQuestStatus(2335) == QuestStatus.Status.STARTED.getId()
                && AgentPrimitiveCapabilityGatewayRuntime.gateway()
                .itemCount(live.agent, 4032405) > 0
                && !live.stagedSecretRoom) {
            stageAtPortalForLiveProof(live, 106021000, 3, "secret-room doorway");
            require(AgentPrimitiveCapabilityGatewayRuntime.gateway()
                            .enterPortal(live.agent, 3),
                    "live-proof secret-room portal rejected entry");
            live.stagedSecretRoom = true;
            return;
        }
        if (questId == 2331
                && live.agent.getQuestStatus(2331) == QuestStatus.Status.STARTED.getId()
                && live.agent.getQuestStatus(2335) == QuestStatus.Status.COMPLETED.getId()
                && AgentPrimitiveCapabilityGatewayRuntime.gateway()
                .itemCount(live.agent, 4001318) < 1
                && !live.stagedRoyalSealRecovery) {
            stageAtPortalForLiveProof(live, 106021402, 2,
                    "Royal Seal recovery doorway");
            require(AgentPrimitiveCapabilityGatewayRuntime.gateway()
                            .enterPortal(live.agent, 2),
                    "live-proof Royal Seal recovery portal rejected entry");
            live.stagedRoyalSealRecovery = true;
            return;
        }
        if (questId == 2331
                && live.agent.getQuestStatus(2331) == QuestStatus.Status.STARTED.getId()
                && AgentPrimitiveCapabilityGatewayRuntime.gateway()
                .itemCount(live.agent, 4001318) > 0
                && live.combatProof.contains(2333)
                && !live.stagedSealReturn) {
            stageForLiveProof(live, AgentMushroomKingdomCatalog.ENTRANCE_MAP_ID,
                    "Royal Seal turn-in");
            live.stagedSealReturn = true;
            return;
        }
        if (questId == 2336) {
            accelerateFinaleTravel(live);
        }
        if (live.acceleratedObjectives.contains(questId)
                || live.agent.getQuestStatus(questId) != QuestStatus.Status.STARTED.getId()) return;
        AgentMushroomKingdomCatalog.QuestNode node = AgentMushroomKingdomCatalog.mainline().stream()
                .filter(candidate -> candidate.questId() == questId)
                .findFirst().orElse(null);
        if (questId == 2330 && live.agent.getMapId() == 106021500) {
            Monster yeti = AgentGrindTargetStateRuntime.target(live.entry);
            if (yeti != null && YETI_VARIANTS.contains(yeti.getId()) && yeti.isAlive()
                    && yeti.getHp() < yeti.getMaxHp()
                    && live.shortenedYetiInstances.add(System.identityHashCode(yeti))) {
                live.agent.getMap().killMonster(yeti, live.agent, true, (short) 0);
                System.out.printf("[MUSHROOM-LIVE] shortened Yeti variant=%d after real damage%n",
                        yeti.getId());
            }
        }
        if (node == null || node.itemId() <= 0) return;
        int owned = AgentPrimitiveCapabilityGatewayRuntime.gateway().itemCount(live.agent, node.itemId());
        if (questId == 2331 && owned == 0 && live.combatProof.contains(2333)
                && !(TEN_PERCENT_MODE && live.royalSealLossInjected
                && !live.royalSealRecoveryConfirmed)) {
            require(AgentInventoryGatewayRuntime.inventory().addItem(
                            live.agent, node.itemId(), (short) 1),
                    live.branch.id() + " could not supply the demonstrated Prime Minister drop");
            live.acceleratedObjectives.add(questId);
            System.out.printf("[MUSHROOM-LIVE] accelerated branch=%s quest=%d item=%d "
                            + "after real Prime Minister combat%n",
                    live.branch.id(), questId, node.itemId());
            return;
        }
        if (questId == 2326 && owned == 0
                && agents.stream().anyMatch(candidate -> candidate.combatProof.contains(2326))) {
            require(AgentInventoryGatewayRuntime.inventory().addItem(live.agent, node.itemId(), (short) 1),
                    live.branch.id() + " could not supply demonstrated q2326 rare drop");
            live.acceleratedObjectives.add(questId);
            System.out.printf("[MUSHROOM-LIVE] accelerated branch=%s quest=%d item=%d "
                            + "after real Helmet Pepe combat%n",
                    live.branch.id(), questId, node.itemId());
            return;
        }
        int demonstrated = agents.stream()
                .mapToInt(candidate -> candidate.observedCounts.getOrDefault(questId, 0)).sum();
        int topUp = TEN_PERCENT_MODE
                ? tenPercentTopUp(node, owned)
                : AgentMushroomKingdomCohortService.accelerationTopUp(
                        node, owned, demonstrated);
        // This opt-in live runner validates each build's combat separately and the
        // authored drop collectively. Do not make a zero-drop branch repeat a long
        // route solely until RNG gives it a personal copy once the cohort has
        // produced the requested 30 real drops.
        if (!TEN_PERCENT_MODE && topUp <= 0 && owned == 0
                && node.requiredCount() > 30 && demonstrated >= 30
                && live.combatProof.contains(questId)) {
            topUp = node.requiredCount();
        }
        if (topUp <= 0) return;
        require(AgentInventoryGatewayRuntime.inventory().addItem(live.agent, node.itemId(), (short) topUp),
                live.branch.id() + " could not top up q" + questId);
        live.acceleratedObjectives.add(questId);
        System.out.printf("[MUSHROOM-LIVE] accelerated branch=%s quest=%d item=%d count=%d->%d%n",
                live.branch.id(), questId, node.itemId(), owned, node.requiredCount());
    }

    static int tenPercentTopUp(AgentMushroomKingdomCatalog.QuestNode node, int owned) {
        if (node == null || node.itemId() <= 0) return 0;
        int required = node.requiredCount();
        int threshold = AgentMushroomKingdomCohortService.tenPercentRequirement(required);
        if (threshold >= required || owned < threshold || owned >= required) return 0;
        return required - owned;
    }

    private static void exerciseRecoveryQuests(LiveAgent live,
                                                AgentMushroomKingdomState state) {
        if (!TEN_PERCENT_MODE) return;
        var gateway = AgentPrimitiveCapabilityGatewayRuntime.gateway();
        if (state.currentQuestId() == 2322 && !live.killerSporeLossInjected) {
            removeAll(live.agent, 2430014);
            live.killerSporeLossInjected = true;
            System.out.printf("[MUSHROOM-LIVE] recovery branch=%s removed Killer Mushroom Spore; "
                    + "expecting q2338%n", live.branch.id());
        }
        if (live.killerSporeLossInjected && !live.killerSporeRecoveryConfirmed
                && live.agent.getQuestStatus(2338) == QuestStatus.Status.COMPLETED.getId()
                && gateway.itemCount(live.agent, 2430014) > 0) {
            live.killerSporeRecoveryConfirmed = true;
            System.out.printf("[MUSHROOM-LIVE] recovery branch=%s q2338=complete item=2430014%n",
                    live.branch.id());
        }
        if (live.agent.getQuestStatus(2333) == QuestStatus.Status.COMPLETED.getId()
                && live.agent.getQuestStatus(2331) == QuestStatus.Status.STARTED.getId()
                && !live.royalSealLossInjected) {
            removeAll(live.agent, 4001318);
            live.royalSealLossInjected = true;
            System.out.printf("[MUSHROOM-LIVE] recovery branch=%s removed Royal Seal; "
                    + "expecting q2342%n", live.branch.id());
        }
        if (live.royalSealLossInjected && !live.royalSealRecoveryConfirmed
                && live.agent.getQuestStatus(2342) == QuestStatus.Status.COMPLETED.getId()
                && gateway.itemCount(live.agent, 4001318) > 0) {
            live.royalSealRecoveryConfirmed = true;
            System.out.printf("[MUSHROOM-LIVE] recovery branch=%s q2342=complete item=4001318%n",
                    live.branch.id());
        }
    }

    private static void removeAll(Character agent, int itemId) {
        int owned = AgentPrimitiveCapabilityGatewayRuntime.gateway().itemCount(agent, itemId);
        if (owned > 0) {
            AgentInventoryGatewayRuntime.inventory().removeById(
                    agent, ItemConstants.getInventoryType(itemId), itemId, owned, false, false);
        }
        require(AgentPrimitiveCapabilityGatewayRuntime.gateway().itemCount(agent, itemId) == 0,
                "test could not remove item " + itemId);
    }

    static boolean recoveryCoverageComplete(boolean killerSporeLossInjected,
                                            boolean killerSporeRecoveryConfirmed,
                                            boolean royalSealLossInjected,
                                            boolean royalSealRecoveryConfirmed) {
        return killerSporeLossInjected && killerSporeRecoveryConfirmed
                && royalSealLossInjected && royalSealRecoveryConfirmed;
    }

    private static boolean accelerateRepeatedCastleTravel(LiveAgent live, int questId,
                                                           List<LiveAgent> agents) {
        boolean firstCollectionStarted = questId == 2328
                && live.agent.getQuestStatus(2328) == QuestStatus.Status.STARTED.getId();
        int mapId = live.agent.getMapId();
        if (firstCollectionStarted
                && mapId == AgentMushroomKingdomCatalog.ENTRANCE_MAP_ID) {
            live.firstCollectionRouteProven = true;
        }
        if (firstCollectionStarted && live.firstCollectionRouteProven
                && AgentPrimitiveCapabilityGatewayRuntime.gateway()
                .itemCount(live.agent, 4000502) < 200
                && mapId != 106021100) {
            stageForLiveProof(live, 106021100, "first collection field",
                    !live.stagedFirstCollection);
            live.stagedFirstCollection = true;
            return true;
        }
        if (firstCollectionStarted && live.firstCollectionRouteProven
                && AgentPrimitiveCapabilityGatewayRuntime.gateway()
                .itemCount(live.agent, 4000502) >= 200
                && mapId != AgentMushroomKingdomCatalog.ENTRANCE_MAP_ID) {
            stageForLiveProof(live, AgentMushroomKingdomCatalog.ENTRANCE_MAP_ID,
                    "first collection turn-in", !live.stagedFirstCollectionReturn);
            live.stagedFirstCollectionReturn = true;
            return true;
        }
        boolean secondCollectionStarted = questId == 2329
                && live.agent.getQuestStatus(2329) == QuestStatus.Status.STARTED.getId();
        if (secondCollectionStarted
                && AgentPrimitiveCapabilityGatewayRuntime.gateway()
                .itemCount(live.agent, 4000503) < 200
                && live.agent.getMapId() != 106021300) {
            stageForLiveProof(live, 106021300, "second collection field",
                    !live.stagedSecondCollection);
            live.stagedSecondCollection = true;
            return true;
        }
        if (secondCollectionStarted
                && AgentPrimitiveCapabilityGatewayRuntime.gateway()
                .itemCount(live.agent, 4000503) >= 200
                && live.agent.getMapId() != AgentMushroomKingdomCatalog.ENTRANCE_MAP_ID) {
            stageForLiveProof(live, AgentMushroomKingdomCatalog.ENTRANCE_MAP_ID,
                    "second collection turn-in", !live.stagedSecondCollectionReturn);
            live.stagedSecondCollectionReturn = true;
            return true;
        }
        boolean yetiQuestStarted = questId == 2330
                && live.agent.getQuestStatus(2330) == QuestStatus.Status.STARTED.getId();
        boolean hasAllYetiCredits = yetiQuestStarted
                && AgentPrimitiveCapabilityGatewayRuntime.gateway()
                .questProgress(live.agent, 2330, 3300005) > 0
                && AgentPrimitiveCapabilityGatewayRuntime.gateway()
                .questProgress(live.agent, 2330, 3300006) > 0
                && AgentPrimitiveCapabilityGatewayRuntime.gateway()
                .questProgress(live.agent, 2330, 3300007) > 0;
        boolean atYetiProofMap = mapId == 106021400
                || mapId == 106021500 || mapId == 106021501 || mapId == 106021502;
        if (yetiQuestStarted && !hasAllYetiCredits && !atYetiProofMap) {
            stageForLiveProof(live, 106021400, "Yeti doors", !live.stagedYetiDoors);
            live.stagedYetiDoors = true;
            return true;
        }
        // The real instance-exit portal grants the Wedding Hall key after the
        // third credited variant. Never stage out of the instance before that
        // scripted action has run.
        if (hasAllYetiCredits && mapId == 106021500) return false;
        if (hasAllYetiCredits
                && AgentPrimitiveCapabilityGatewayRuntime.gateway()
                .itemCount(live.agent, 4032388) > 0
                && mapId != AgentMushroomKingdomCatalog.ENTRANCE_MAP_ID) {
            stageForLiveProof(live, AgentMushroomKingdomCatalog.ENTRANCE_MAP_ID,
                    "Yeti quest turn-in", !live.stagedYetiReturn);
            live.stagedYetiReturn = true;
            return true;
        }
        boolean atBossRouteProofMap = mapId == 106021400 || mapId == 106021401
                || mapId == 106021402 || mapId == 106021600;
        if (questId == 2332 && bossRouteStagingReady(live.agent.getQuestStatus(2331))
                && !atBossRouteProofMap) {
            stageForLiveProof(live, 106021400, "boss-route junction",
                    !live.stagedBossRoute);
            live.stagedBossRoute = true;
            return true;
        }
        boolean bossOuterGateProven = agents.stream().anyMatch(candidate ->
                candidate.combatProof.contains(2333)
                        || AgentMushroomKingdomCohortService
                        .hasDurablePostPrimeMinisterEvidence(
                                candidate.agent.getQuestStatus(2333),
                                candidate.agent.getQuestStatus(2335),
                                candidate.agent.getQuestStatus(2331),
                                candidate.agent.getQuestStatus(2336)));
        if (questId == 2332 && mapId == 106021400 && bossOuterGateProven) {
            stageForLiveProof(live, 106021401, "boss-route outer gate",
                    !live.stagedBossOuterGate);
            live.stagedBossOuterGate = true;
            return true;
        }
        return false;
    }

    static boolean bossRouteStagingReady(int royalSealQuestStatus) {
        return royalSealQuestStatus != QuestStatus.Status.NOT_STARTED.getId();
    }

    private static void accelerateFinaleTravel(LiveAgent live) {
        int status = live.agent.getQuestStatus(2336);
        if (status == QuestStatus.Status.NOT_STARTED.getId() && !live.stagedFinaleStart) {
            stageForLiveProof(live, 106021402, "Princess doorway");
            live.stagedFinaleStart = true;
            return;
        }
        if (status == QuestStatus.Status.STARTED.getId() && !live.stagedFinaleReturn) {
            stageForLiveProof(live, AgentMushroomKingdomCatalog.ENTRANCE_MAP_ID,
                    "Mushroom Kingdom entrance");
            live.stagedFinaleReturn = true;
        }
    }

    private static void stageForLiveProof(LiveAgent live, int mapId, String label) {
        stageForLiveProof(live, mapId, label, true);
    }

    private static void stageForLiveProof(LiveAgent live, int mapId, String label,
                                          boolean announce) {
        MapleMap map = AgentMapGatewayRuntime.map().resolveMap(WORLD, CHANNEL, mapId);
        require(map != null, "live-proof stage map is unavailable: " + mapId);
        AgentMapGatewayRuntime.map().changeMapNear(live.agent, map, spawnPoint(map, 0));
        AgentPrimitiveCapabilityGatewayRuntime.gateway().prepareNavigation(live.entry, live.agent);
        if (announce) {
            System.out.printf("[MUSHROOM-LIVE] staged finale branch=%s at %s map=%d "
                            + "after natural route proof%n",
                    live.branch.id(), label, mapId);
        }
    }

    private static void stageAtPortalForLiveProof(LiveAgent live, int mapId, int portalId,
                                                   String label) {
        MapleMap map = AgentMapGatewayRuntime.map().resolveMap(WORLD, CHANNEL, mapId);
        require(map != null, "live-proof stage map is unavailable: " + mapId);
        require(map.getPortal(portalId) != null,
                "live-proof portal is unavailable: " + mapId + ':' + portalId);
        Point portal = new Point(map.getPortal(portalId).getPosition());
        AgentMapGatewayRuntime.map().changeMapNear(live.agent, map, portal);
        AgentPrimitiveCapabilityGatewayRuntime.gateway()
                .stagePosition(live.entry, live.agent, portal);
        AgentPrimitiveCapabilityGatewayRuntime.gateway().prepareNavigation(live.entry, live.agent);
        System.out.printf("[MUSHROOM-LIVE] staged finale branch=%s at %s map=%d portal=%d "
                        + "after natural route proof%n",
                live.branch.id(), label, mapId, portalId);
    }

    private static void printStatus(LiveAgent live) {
        AgentMushroomKingdomState state = live.entry.capabilityStates()
                .find(AgentMushroomKingdomState.STATE_KEY).orElse(null);
        String status = state == null ? "starting" : state.phase() + " q" + state.currentQuestId()
                + " " + state.reason();
        AgentMushroomKingdomCatalog.QuestNode node = state == null ? null
                : AgentMushroomKingdomCatalog.mainline().stream()
                .filter(candidate -> candidate.questId() == state.currentQuestId())
                .findFirst().orElse(null);
        int items = node == null || node.itemId() <= 0 ? 0
                : AgentPrimitiveCapabilityGatewayRuntime.gateway().itemCount(live.agent, node.itemId());
        int mobs = node == null ? 0 : AgentPrimitiveCapabilityGatewayRuntime.gateway()
                .liveMonsterCount(live.agent, node.mobIds());
        Monster target = AgentGrindTargetStateRuntime.target(live.entry);
        AgentFieldObservationState.Snapshot combat = live.entry.capabilityStates()
                .find(AgentFieldObservationState.STATE_KEY)
                .map(observation -> observation.snapshot(System.currentTimeMillis())).orElse(null);
        var visit = live.entry.capabilityStates().find(AgentHuntingVisitState.STATE_KEY)
                .map(AgentHuntingVisitState::snapshot).orElse(null);
        int entryQuest = AgentMushroomKingdomCatalog.entryQuestForJob(live.agent.getJob().getId());
        int entryStatus = live.agent.getQuestStatus(entryQuest);
        int recommendationLetters = AgentPrimitiveCapabilityGatewayRuntime.gateway()
                .itemCount(live.agent, 4032375);
        int infoProgress = state == null ? 0 : AgentPrimitiveCapabilityGatewayRuntime.gateway()
                .questProgress(live.agent, state.currentQuestId(), state.currentQuestId());
        int barrierQuestStatus = live.agent.getQuestStatus(
                AgentMushroomKingdomRuntime.FIRST_THORN_BARRIER_UNLOCK_QUEST_ID);
        int killerSporeCount = AgentPrimitiveCapabilityGatewayRuntime.gateway()
                .itemCount(live.agent, 2430014);
        int weddingHallKeys = AgentPrimitiveCapabilityGatewayRuntime.gateway()
                .itemCount(live.agent, 4032388);
        int royalSealStatus = live.agent.getQuestStatus(2331);
        int princessRescueStatus = live.agent.getQuestStatus(2332);
        int killerSporeRecoveryStatus = live.agent.getQuestStatus(2338);
        int royalSealRecoveryStatus = live.agent.getQuestStatus(2342);
        String yetiProgress = YETI_VARIANTS.stream().sorted()
                .map(mobId -> Integer.toString(AgentPrimitiveCapabilityGatewayRuntime.gateway()
                        .questProgress(live.agent, 2330, mobId)))
                .reduce((left, right) -> left + '/' + right).orElse("0/0/0");
        System.out.printf("[MUSHROOM-LIVE] status branch=%s map=%d pos=%s hp=%d/%d grind=%s target=%s "
                        + "combat=%s items=%d mobs=%d visit=%s entry=q%d:%d letter=%d "
                        + "info=%d barrier=%d spore=%d key=%d sealQ=%d rescueQ=%d "
                        + "sporeRecoveryQ=%d sealRecoveryQ=%d "
                        + "yeti=%s %s%n",
                live.branch.id(), live.agent.getMapId(), live.agent.getPosition(),
                live.agent.getHp(), live.agent.getMaxHp(),
                AgentModeStateRuntime.grinding(live.entry),
                target == null ? "none" : target.getId() + "@" + target.getPosition()
                        + " hp=" + target.getHp() + "/" + target.getMaxHp(),
                combat == null ? "none" : combat.attacks() + "a/" + combat.hitLines()
                        + "h/" + combat.missLines() + "m/" + combat.damage() + "d",
                items, mobs, visit == null || visit.request() == null ? "none" : visit.request().visitId(),
                entryQuest, entryStatus, recommendationLetters,
                infoProgress, barrierQuestStatus, killerSporeCount, weddingHallKeys,
                royalSealStatus, princessRescueStatus, killerSporeRecoveryStatus,
                royalSealRecoveryStatus, yetiProgress, status);
    }

    private static List<AgentSecondJobCatalog.Branch> selectedBranches(String[] args) {
        if (args == null || args.length == 0) return List.copyOf(AgentSecondJobCatalog.all().values());
        Map<String, AgentSecondJobCatalog.Branch> selected = new LinkedHashMap<>();
        for (String arg : args) {
            AgentSecondJobCatalog.Branch branch = AgentSecondJobCatalog.require(arg);
            selected.put(branch.id(), branch);
        }
        return List.copyOf(selected.values());
    }

    private static TestIdentity createTemporaryAgent(
            AgentSecondJobCatalog.Branch branch, int ordinal) throws Exception {
        String hex = Long.toHexString(System.nanoTime());
        String suffix = hex.substring(Math.max(0, hex.length() - 7));
        String name = "Mk" + Integer.toString(ordinal, 36) + suffix;
        AgentAccountResolution account = AgentPersistenceGatewayRuntime.persistence()
                .resolveOrCreateAgentAccount(name);
        require(account.isSuccess(), "temporary account creation failed: " + account.failureMessage());
        try {
            require(AgentBackingAccountSecurityRuntime.lockInteractiveLogin(account.accountId()),
                    "temporary account could not be locked");
            Client creationClient = AgentClientGatewayRuntime.clients().createHeadlessClient(WORLD, CHANNEL);
            creationClient.setAccID(account.accountId());
            creationClient.setAccountName(name);
            int characterId = AgentClientGatewayRuntime.clients().createBackingCharacter(creationClient, name);
            require(characterId > 0, "temporary character creation failed for " + branch.id());
            AgentIdentityGatewayRuntime.identities().register(
                    characterId, AgentIdentityOrigin.PROVISIONED, false);
            return new TestIdentity(account.accountId(), characterId, name);
        } catch (Throwable failure) {
            deleteTemporaryAccount(account.accountId());
            throw failure;
        }
    }

    private static Point spawnPoint(MapleMap map, int ordinal) {
        Point base = map.getPortal(0) == null ? new Point(0, 0) : map.getPortal(0).getPosition();
        Point candidate = new Point(base.x + ((ordinal % 6) - 2) * 42, base.y);
        Point grounded = AgentPrimitiveCapabilityGatewayRuntime.gateway().groundPoint(map, candidate);
        return grounded == null ? new Point(base) : grounded;
    }

    private static boolean cleanup(List<LiveAgent> agents) {
        if (!CLEANUP_STARTED.compareAndSet(false, true)) return true;
        boolean clean = true;
        for (LiveAgent live : List.copyOf(agents)) clean &= cleanupIdentity(live.identity, live.agent);
        return clean;
    }

    private static boolean cleanupIdentity(TestIdentity identity, Character agent) {
        boolean clean = true;
        try {
            if (AgentRuntimeRegistry.hasActiveAgentCharacterId(identity.characterId())) {
                new CosmicAgentPopulationBackend().stop(identity.characterId());
            } else if (agent != null && agent.getClient() != null && agent.getClient().getPlayer() != null) {
                agent.getClient().forceDisconnect();
            }
        } catch (Throwable failure) {
            System.err.println("[MUSHROOM-LIVE] cleanup disconnect failed for "
                    + identity.name() + ": " + failure.getMessage());
            clean = false;
        }
        try {
            CharacterDeletionService.Result result = deleteTemporaryCharacter(identity);
            require(result.isSuccess() || result == CharacterDeletionService.Result.NOT_FOUND,
                    "temporary character cleanup failed after retries: " + result);
            deleteTemporaryAccount(identity.accountId());
            System.out.printf("[MUSHROOM-LIVE] removed account=%d character=%d%n",
                    identity.accountId(), identity.characterId());
        } catch (Throwable failure) {
            System.err.println("[MUSHROOM-LIVE] cleanup database rows failed for "
                    + identity.name() + ": " + failure.getMessage());
            clean = false;
        }
        return clean;
    }

    private static CharacterDeletionService.Result deleteTemporaryCharacter(TestIdentity identity)
            throws InterruptedException {
        CharacterDeletionService.Result result = CharacterDeletionService.Result.ERROR;
        for (int attempt = 1; attempt <= 4; attempt++) {
            result = CharacterDeletionService.deleteCharacter(
                    identity.characterId(), identity.accountId());
            if (result.isSuccess() || result == CharacterDeletionService.Result.NOT_FOUND) return result;
            if (attempt < 4) Thread.sleep(500L * attempt);
        }
        return result;
    }

    private static void deleteTemporaryAccount(int accountId) throws Exception {
        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM accounts WHERE id = ?")) {
            statement.setInt(1, accountId);
            int removed = statement.executeUpdate();
            require(removed >= 0 && removed <= 1, "temporary account cleanup removed " + removed
                    + " rows for account " + accountId);
        }
    }

    private static long mix(long seed, long value) {
        return seed ^ (value + 0x9E3779B97F4A7C15L + (seed << 6) + (seed >>> 2));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    private record TestIdentity(int accountId, int characterId, String name) { }

    record DiagnosticSnapshot(String id, int startAtQuestId, int stageMapId,
                              boolean activateQuest, int itemId, int itemCount,
                              Point position) {
        DiagnosticSnapshot {
            position = position == null ? null : new Point(position);
        }
    }

    private static final class LiveAgent {
        private final TestIdentity identity;
        private final AgentSecondJobCatalog.Branch branch;
        private final Character agent;
        private final AgentRuntimeEntry entry;
        private final Set<Integer> acceleratedObjectives = new HashSet<>();
        private final Map<Integer, Integer> observedCounts = new LinkedHashMap<>();
        private final Map<Integer, Long> combatHitBaselines = new LinkedHashMap<>();
        private final Set<Integer> combatProof = new HashSet<>();
        private final Set<Integer> shortenedYetiInstances = new HashSet<>();
        private boolean stagedSecretRoom;
        private boolean stagedRoyalSealRecovery;
        private boolean stagedSealReturn;
        private boolean stagedFinaleStart;
        private boolean stagedFinaleReturn;
        private boolean firstCollectionRouteProven;
        private boolean stagedFirstCollection;
        private boolean stagedFirstCollectionReturn;
        private boolean stagedSecondCollection;
        private boolean stagedSecondCollectionReturn;
        private boolean stagedYetiDoors;
        private boolean stagedYetiReturn;
        private boolean stagedBossRoute;
        private boolean stagedBossOuterGate;
        private boolean killerSporeLossInjected;
        private boolean killerSporeRecoveryConfirmed;
        private boolean royalSealLossInjected;
        private boolean royalSealRecoveryConfirmed;

        private LiveAgent(TestIdentity identity, AgentSecondJobCatalog.Branch branch,
                          Character agent, AgentRuntimeEntry entry) {
            this.identity = identity;
            this.branch = branch;
            this.agent = agent;
            this.entry = entry;
        }
    }
}
