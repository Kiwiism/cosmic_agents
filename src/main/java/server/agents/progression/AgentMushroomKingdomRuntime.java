package server.agents.progression;

import client.BuffStat;
import client.Character;
import client.Job;
import client.QuestStatus;
import server.agents.capabilities.combat.AgentCombatPolicyConfig;
import server.agents.capabilities.combat.AgentSpawnPressurePolicy;
import server.agents.capabilities.navigation.AgentRouteOutcome;
import server.agents.capabilities.navigation.AgentRouteStatus;
import server.agents.capabilities.objective.AgentNpcInteractionReachabilityService;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.hunting.AgentHuntingVisitRequest;
import server.maps.MapleMap;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Resumable Mushroom Kingdom quest executor built on generic Questing/Hunting capabilities. */
public final class AgentMushroomKingdomRuntime {
    private static final int INTERACTION_DISTANCE_PX = config.AgentTuning.intValue(
            "server.agents.progression.AgentMushroomKingdomRuntime.INTERACTION_DISTANCE_PX");
    private static final long INTERACTION_RETRY_MS = config.AgentTuning.longValue(
            "server.agents.progression.AgentMushroomKingdomRuntime.INTERACTION_RETRY_MS");
    private static final long OBJECTIVE_TIMEOUT_MS = 45 * 60_000L;
    private static final long UNOBSERVED_NPC_STAGING_DELAY_MS = config.AgentTuning.longValue(
            "server.agents.progression.AgentMushroomKingdomRuntime.UNOBSERVED_NPC_STAGING_DELAY_MS");
    private static final long BOSS_INSTANCE_RETRY_MS = config.AgentTuning.longValue(
            "server.agents.progression.AgentMushroomKingdomRuntime.BOSS_INSTANCE_RETRY_MS");
    private static final double SCRIPTED_PORTAL_INTERACTION_DISTANCE_PX = config.AgentTuning.doubleValue(
            "server.agents.progression.AgentMushroomKingdomRuntime.SCRIPTED_PORTAL_INTERACTION_DISTANCE_PX");
    private static final int BELOW_MAP_RECOVERY_MARGIN_PX = config.AgentTuning.intValue(
            "server.agents.progression.AgentMushroomKingdomRuntime.BELOW_MAP_RECOVERY_MARGIN_PX");
    private static final long HUNT_MAP_RESERVATION_MS = config.AgentTuning.longValue(
            "server.agents.progression.AgentMushroomKingdomRuntime.HUNT_MAP_RESERVATION_MS");
    private static final long YETI_AGENT_SCAN_MS = config.AgentTuning.longValue(
            "server.agents.progression.AgentMushroomKingdomRuntime.YETI_AGENT_SCAN_MS");
    private static final long YETI_HUMAN_RESPONSE_MS = config.AgentTuning.longValue(
            "server.agents.progression.AgentMushroomKingdomRuntime.YETI_HUMAN_RESPONSE_MS");
    private static final long YETI_LOOT_GRACE_MS = config.AgentTuning.longValue(
            "server.agents.progression.AgentMushroomKingdomRuntime.YETI_LOOT_GRACE_MS");
    private static final int SNIPER_PILL_ITEM_ID = 2_002_008;
    private static final int FIRST_THORN_WALL_PORTAL_ID = 3;
    static final int FIRST_THORN_BARRIER_UNLOCK_QUEST_ID = 30_000;
    private static final Set<Integer> PEPE_KING_VARIANTS = Set.of(3300005, 3300006, 3300007);

    private AgentMushroomKingdomRuntime() { }

    public static boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        return tick(entry, agent, nowMs, AgentPrimitiveCapabilityGatewayRuntime.gateway());
    }

    static boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs,
                        PrimitiveCapabilityGateway gateway) {
        AgentMushroomKingdomState state = entry.capabilityStates()
                .require(AgentMushroomKingdomState.STATE_KEY);
        if (state.phase() != AgentMushroomKingdomState.Phase.ACTIVE) return false;
        if (!gateway.alive(agent)) {
            gateway.stop(entry);
            state.active("waiting to revive before resuming Mushroom Kingdom");
            return true;
        }
        if (recoverBelowMap(entry, agent, state, gateway)) return true;
        if (gateway.questStatus(agent, AgentMushroomKingdomCatalog.FINAL_QUEST_ID)
                == QuestStatus.Status.COMPLETED.getId()) {
            gateway.stop(entry);
            releaseHuntMap(agent, state);
            state.complete("Mushroom Kingdom mainline and non-repeatable side quests complete");
            return true;
        }
        int entryQuest = AgentMushroomKingdomCatalog.entryQuestForJob(agent.getJob().getId());
        if (gateway.questStatus(agent, entryQuest) != QuestStatus.Status.COMPLETED.getId()) {
            return entryQuest(entry, agent, entryQuest, state, gateway, nowMs);
        }

        AgentMushroomKingdomCatalog.QuestNode node = AgentMushroomKingdomCatalog.mainline().stream()
                .filter(candidate -> gateway.questStatus(agent, candidate.questId())
                        != QuestStatus.Status.COMPLETED.getId())
                .findFirst().orElse(null);
        if (node == null) {
            gateway.stop(entry);
            releaseHuntMap(agent, state);
            state.complete("all catalogued Mushroom Kingdom quests complete");
            return true;
        }
        if (state.currentQuestId() != 0 && state.currentQuestId() != node.questId()) {
            releaseHuntMap(agent, state);
        }
        int metric = objectiveMetric(agent, node, gateway);
        state.observe(node.questId(), metric, gateway.mapId(agent), gateway.position(agent), nowMs);
        state.active(reason(node, metric));
        if (nowMs - state.progressAtMs() > OBJECTIVE_TIMEOUT_MS) {
            return block(entry, state, gateway, "quest " + node.questId()
                    + " made no quest, map, or physical progress for 45 minutes");
        }

        return switch (node.questId()) {
            case 2314 -> scriptedInvestigation(entry, agent, node, 106020300, 1,
                    state, gateway, nowMs);
            case 2322 -> scriptedInvestigation(entry, agent, node, 106020400, 3,
                    state, gateway, nowMs);
            case 2324 -> thornBarrier(entry, agent, state, gateway, nowMs);
            case 2330 -> pepeKings(entry, agent, state, gateway, nowMs);
            case 2332 -> enterPrimeMinister(entry, agent, state, gateway, nowMs);
            case 2335 -> secretRoom(entry, agent, state, gateway, nowMs);
            case 2331 -> royalSeal(entry, agent, state, gateway, nowMs);
            case 2336 -> truthAndReturn(entry, agent, state, gateway, nowMs);
            default -> ordinary(entry, agent, node, state, gateway, nowMs);
        };
    }

    public static void cancel(AgentRuntimeEntry entry, Character agent) {
        AgentPrimitiveCapabilityGatewayRuntime.gateway().stop(entry);
        releaseHuntMap(agent, entry.capabilityStates()
                .require(AgentMushroomKingdomState.STATE_KEY));
    }

    private static boolean entryQuest(AgentRuntimeEntry entry, Character agent, int questId,
                                      AgentMushroomKingdomState state,
                                      PrimitiveCapabilityGateway gateway, long nowMs) {
        int status = gateway.questStatus(agent, questId);
        state.observe(questId, status, gateway.mapId(agent),
                gateway.position(agent), nowMs);
        if (status == QuestStatus.Status.NOT_STARTED.getId()) {
            state.active("accepting the Mushroom Kingdom recommendation from the job instructor");
            if (gateway.itemCount(agent, 4032375) < 1
                    && !requireCapacity(entry, agent, 4032375, 1,
                    "Explorer recommendation letter", state, gateway)) return false;
            if (!travel(entry, agent, AgentMushroomKingdomCatalog.entryLeaderMap(questId),
                    state, gateway, nowMs)) return true;
            int leaderNpc = AgentMushroomKingdomCatalog.entryLeaderNpc(questId);
            return interact(entry, agent, leaderNpc, state, gateway, nowMs,
                    () -> gateway.startQuest(agent, questId, leaderNpc));
        }
        state.active("presenting Explorer recommendation at Mushroom Kingdom entrance");
        if (!travel(entry, agent, AgentMushroomKingdomCatalog.ENTRANCE_MAP_ID, state, gateway, nowMs)) {
            return true;
        }
        return interact(entry, agent, 1300005, state, gateway, nowMs,
                () -> gateway.completeQuest(agent, questId, 1300005));
    }

    private static boolean ordinary(AgentRuntimeEntry entry, Character agent,
                                    AgentMushroomKingdomCatalog.QuestNode node,
                                    AgentMushroomKingdomState state,
                                    PrimitiveCapabilityGateway gateway, long nowMs) {
        int status = gateway.questStatus(agent, node.questId());
        if (node.questId() == 2333 && gateway.mapId(agent) == 106021402) {
            if (!nearPortal(entry, agent, 2, gateway)) return true;
            gateway.stop(entry);
            if (nowMs < state.nextActionAtMs()) {
                state.active("waiting to retry the Prime Minister instance entrance");
                return true;
            }
            boolean entered = gateway.enterPortal(agent, 2);
            state.nextActionAtMs(nowMs + BOSS_INSTANCE_RETRY_MS);
            if (entered) {
                state.capabilityProgress();
                state.active("waiting for the Prime Minister instance map transfer");
            } else {
                state.active("Prime Minister instance is busy; waiting to retry");
            }
            return true;
        }
        if (status == QuestStatus.Status.NOT_STARTED.getId()) {
            int startRewardItem = startRewardItem(node.questId());
            if (startRewardItem > 0 && gateway.itemCount(agent, startRewardItem) < 1
                    && !requireCapacity(entry, agent, startRewardItem, 1,
                    "quest " + node.questId() + " start reward", state, gateway)) return false;
            if (!travel(entry, agent, node.startMapId(), state, gateway, nowMs)) return true;
            return interact(entry, agent, node.startNpcId(), state, gateway, nowMs,
                    () -> gateway.startQuest(agent, node.questId(), node.startNpcId()));
        }
        if (node.questId() == 2333 && gateway.itemCount(agent, 4001318) < 1) {
            gateway.lootNearby(agent, Set.of(4001318));
        }
        if (node.hunting() && objectiveMetric(agent, node, gateway) < node.requiredCount()) {
            if (node.itemId() > 0 && gateway.itemCount(agent, node.itemId()) == 0
                    && !requireCapacity(entry, agent, node.itemId(), 1,
                    "quest " + node.questId() + " collection", state, gateway)) return false;
            if ((node.huntMapId() == 106020400 || node.huntMapId() == 106020401)
                    && gateway.mapId(agent) != 106020400
                    && gateway.questStatus(agent, FIRST_THORN_BARRIER_UNLOCK_QUEST_ID)
                    == QuestStatus.Status.NOT_STARTED.getId()) {
                return killerSporeBarrier(entry, agent, state, gateway, nowMs);
            }
            int huntMapId = selectedHuntMap(agent, node, state, gateway, nowMs);
            if (!travel(entry, agent, huntMapId, state, gateway, nowMs)) return true;
            maintainHuntMapReservation(agent, huntMapId, gateway.mapId(agent), nowMs);
            if (node.itemId() > 0) gateway.lootNearby(agent, Set.of(node.itemId()));
            if (!node.mobIds().isEmpty()) {
                refreshMeleeAccuracySupply(agent, gateway);
                Set<Integer> incidental = spawnPressureMobIds(agent, node.mobIds(), gateway);
                AgentQuestHuntingBridge.engage(entry, agent, gateway,
                        "mushroom-kingdom:" + node.questId(),
                        AgentHuntingVisitRequest.Purpose.QUEST_OBJECTIVE,
                        node.mobIds(), incidental, nowMs);
            }
            return true;
        }
        releaseHuntMap(agent, state);
        if (node.questId() == 2318 && gateway.itemCount(agent, 2430014) < 1
                && !requireCapacity(entry, agent, 2430014, 1,
                "Killer Mushroom Spore reward", state, gateway)) return false;
        if (!travel(entry, agent, node.completeMapId(), state, gateway, nowMs)) return true;
        return interact(entry, agent, node.completeNpcId(), state, gateway, nowMs,
                () -> gateway.completeQuest(agent, node.questId(), node.completeNpcId()));
    }

    private static int selectedHuntMap(Character agent,
                                       AgentMushroomKingdomCatalog.QuestNode node,
                                       AgentMushroomKingdomState state,
                                       PrimitiveCapabilityGateway gateway,
                                       long nowMs) {
        int selected = state.selectedHuntMap(node.questId());
        if (selected > 0) {
            var lease = maintainHuntMapReservation(
                    agent, selected, gateway.mapId(agent), nowMs);
            if (lease == AgentMushroomKingdomMapReservationRuntime.LeaseState.TRAVELING
                    || lease == AgentMushroomKingdomMapReservationRuntime.LeaseState.OCCUPYING) {
                return selected;
            }
            state.clearHuntMap();
        }

        var rankedMaps = AgentMushroomKingdomCatalog.huntMapsFor(node);
        if (rankedMaps.isEmpty()) return node.huntMapId();
        Map<Integer, Integer> occupancy = new LinkedHashMap<>();
        for (AgentMushroomKingdomCatalog.HuntMap map : rankedMaps) {
            int count = Math.max(0, gateway.characterCount(agent, map.mapId()));
            if (gateway.mapId(agent) == map.mapId() && count > 0) count--;
            occupancy.put(map.mapId(), count);
        }
        ReservationScope scope = reservationScope(agent);
        AgentMushroomKingdomHuntMapSelector.Selection decision =
                AgentMushroomKingdomMapReservationRuntime.selectAndReserve(
                        agent.getId(), scope.world(), scope.channel(), rankedMaps,
                        occupancy, nowMs, HUNT_MAP_RESERVATION_MS).orElseThrow();
        state.selectHuntMap(node.questId(), decision.map().mapId());
        return decision.map().mapId();
    }

    private static ReservationScope reservationScope(Character agent) {
        var clients = AgentClientGatewayRuntime.clients();
        return agent != null && clients.hasClient(agent)
                ? new ReservationScope(clients.world(agent), clients.channel(agent))
                : new ReservationScope(0, 0);
    }

    private static AgentMushroomKingdomMapReservationRuntime.LeaseState maintainHuntMapReservation(
            Character agent, int selectedMapId, int currentMapId, long nowMs) {
        return AgentMushroomKingdomMapReservationRuntime.maintain(
                agent.getId(), selectedMapId, currentMapId, nowMs, HUNT_MAP_RESERVATION_MS);
    }

    private static void releaseHuntMap(Character agent, AgentMushroomKingdomState state) {
        if (agent != null) AgentMushroomKingdomMapReservationRuntime.release(agent.getId());
        if (state != null) state.clearHuntMap();
    }

    private record ReservationScope(int world, int channel) { }

    static Set<Integer> spawnPressureMobIds(Character agent,
                                            Set<Integer> preferredMobIds,
                                            PrimitiveCapabilityGateway gateway) {
        Set<Integer> fallback = new LinkedHashSet<>(
                gateway.configuredMonsterSpawnCounts(agent).keySet());
        fallback.removeAll(preferredMobIds);
        return AgentSpawnPressurePolicy.selectFallbackMobIds(
                gateway.configuredMonsterSpawnCounts(agent),
                gateway.liveMonsterCounts(agent),
                preferredMobIds,
                fallback,
                AgentCombatPolicyConfig.spawnPressureMinTargetSharePercent());
    }

    private static boolean killerSporeBarrier(AgentRuntimeEntry entry, Character agent,
                                               AgentMushroomKingdomState state,
                                               PrimitiveCapabilityGateway gateway, long nowMs) {
        if (gateway.questStatus(agent, FIRST_THORN_BARRIER_UNLOCK_QUEST_ID)
                == QuestStatus.Status.NOT_STARTED.getId()
                && gateway.itemCount(agent, 2430014) < 1) {
            return recoverKillerSpore(entry, agent, state, gateway, nowMs);
        }
        if (!travel(entry, agent, 106020300, state, gateway, nowMs)) return true;
        if (!nearPortal(entry, agent, FIRST_THORN_WALL_PORTAL_ID, gateway)) return true;
        gateway.stop(entry);
        if (gateway.itemCount(agent, 2430014) > 0) {
            gateway.useItem(agent, 2430014);
            if (gateway.questStatus(agent, FIRST_THORN_BARRIER_UNLOCK_QUEST_ID)
                    == QuestStatus.Status.NOT_STARTED.getId()) {
                // Item-manager scripts can consume a USE item while dropping their synthetic
                // quest transition. Persist the v83-compatible barrier flag before entering.
                gateway.forceCompleteQuest(agent, FIRST_THORN_BARRIER_UNLOCK_QUEST_ID, 0);
            }
        }
        if (gateway.questStatus(agent, FIRST_THORN_BARRIER_UNLOCK_QUEST_ID)
                == QuestStatus.Status.NOT_STARTED.getId()) {
            return capabilityFailure(entry, state, gateway,
                    "Killer Mushroom Spore did not open the first thorn barrier", nowMs);
        }
        state.capabilityProgress();
        if (!gateway.enterPortal(agent, FIRST_THORN_WALL_PORTAL_ID)) {
            return capabilityFailure(entry, state, gateway,
                    "first thorn barrier portal did not open", nowMs);
        }
        return true;
    }

    private static boolean recoverKillerSpore(AgentRuntimeEntry entry, Character agent,
                                               AgentMushroomKingdomState state,
                                               PrimitiveCapabilityGateway gateway, long nowMs) {
        if (!requireCapacity(entry, agent, 2430014, 1,
                "replacement Killer Mushroom Spore", state, gateway)) return false;
        if (!travel(entry, agent, AgentMushroomKingdomCatalog.ENTRANCE_MAP_ID,
                state, gateway, nowMs)) return true;
        return interact(entry, agent, 1300007, state, gateway, nowMs, () -> {
            gateway.startQuest(agent, 2338, 1300007);
            return gateway.itemCount(agent, 2430014) > 0;
        });
    }

    private static boolean scriptedInvestigation(AgentRuntimeEntry entry, Character agent,
                                                 AgentMushroomKingdomCatalog.QuestNode node,
                                                 int mapId, int portalId,
                                                 AgentMushroomKingdomState state,
                                                 PrimitiveCapabilityGateway gateway, long nowMs) {
        int status = gateway.questStatus(agent, node.questId());
        if (status == QuestStatus.Status.NOT_STARTED.getId()) {
            return ordinary(entry, agent, node, state, gateway, nowMs);
        }
        if (gateway.questProgress(agent, node.questId(), node.questId()) < 1) {
            if (mapId == 106020400
                    && gateway.questStatus(agent, FIRST_THORN_BARRIER_UNLOCK_QUEST_ID)
                    == QuestStatus.Status.NOT_STARTED.getId()) {
                return killerSporeBarrier(entry, agent, state, gateway, nowMs);
            }
            if (!travel(entry, agent, mapId, state, gateway, nowMs)) return true;
            if (!nearPortal(entry, agent, portalId, gateway)) return true;
            gateway.stop(entry);
            gateway.enterPortal(agent, portalId);
            return true;
        }
        if (!travel(entry, agent, node.completeMapId(), state, gateway, nowMs)) return true;
        return interact(entry, agent, node.completeNpcId(), state, gateway, nowMs,
                () -> gateway.completeQuest(agent, node.questId(), node.completeNpcId()));
    }

    private static boolean thornBarrier(AgentRuntimeEntry entry, Character agent,
                                        AgentMushroomKingdomState state,
                                        PrimitiveCapabilityGateway gateway, long nowMs) {
        int status = gateway.questStatus(agent, 2324);
        if (status == QuestStatus.Status.NOT_STARTED.getId()) {
            if (!requireCapacity(entry, agent, 2430015, 1,
                    "Thorn Remover", state, gateway)) return false;
            AgentMushroomKingdomCatalog.QuestNode node = AgentMushroomKingdomCatalog.require(2324);
            return ordinary(entry, agent, node, state, gateway, nowMs);
        }
        if (!travel(entry, agent, 106020400, state, gateway, nowMs)) return true;
        if (!nearPortal(entry, agent, 3, gateway)) return true;
        gateway.stop(entry);
        if (gateway.itemCount(agent, 2430015) > 0) gateway.useItem(agent, 2430015);
        if (gateway.questStatus(agent, 2324) != QuestStatus.Status.COMPLETED.getId()) {
            return capabilityFailure(entry, state, gateway,
                    "Thorn Remover did not clear the castle route", nowMs);
        }
        state.capabilityProgress();
        if (!gateway.enterPortal(agent, 3)) {
            return capabilityFailure(entry, state, gateway,
                    "castle thorn portal did not open", nowMs);
        }
        return true;
    }

    private static boolean pepeKings(AgentRuntimeEntry entry, Character agent,
                                     AgentMushroomKingdomState state,
                                     PrimitiveCapabilityGateway gateway, long nowMs) {
        int mapId = gateway.mapId(agent);
        if (mapId != AgentMushroomKingdomYetiPartyRuntime.LOBBY_MAP_ID) {
            AgentMushroomKingdomYetiPartyRuntime.leaveLobby(state);
        }
        if (mapId != 106021500) state.clearYetiLootGrace();
        int status = gateway.questStatus(agent, 2330);
        if (status == QuestStatus.Status.NOT_STARTED.getId()) {
            return ordinary(entry, agent, AgentMushroomKingdomCatalog.require(2330), state, gateway, nowMs);
        }
        int progress = PEPE_KING_VARIANTS.stream()
                .mapToInt(mob -> Math.min(1, gateway.questProgress(agent, 2330, mob))).sum();
        if (progress >= 3) {
            if (gateway.mapId(agent) == 106021500) {
                if (gateway.itemCount(agent, 4032388) < 1
                        && !requireCapacity(entry, agent, 4032388, 1,
                        "Wedding Hall key", state, gateway)) return false;
                if (waitForYetiLoot(state, nowMs)) return true;
                if (!nearPortal(entry, agent, 1, gateway)) return true;
                if (!gateway.enterPortal(agent, 1)) {
                    return capabilityFailure(entry, state, gateway,
                            "Yeti instance exit did not grant the Wedding Hall key", nowMs);
                }
                return true;
            }
            if (gateway.itemCount(agent, 4032388) < 1) {
                return block(entry, state, gateway,
                        "Wedding Hall key is missing after all three Yeti variants were credited");
            }
            AgentMushroomKingdomYetiPartyRuntime.leaveLobby(state);
            if (!ensureSolo(agent, state)) return true;
            return ordinary(entry, agent, AgentMushroomKingdomCatalog.require(2330), state, gateway, nowMs);
        }
        if (gateway.mapId(agent) == 106021500) {
            if (gateway.liveMonsterCount(agent, PEPE_KING_VARIANTS) > 0) {
                state.clearYetiLootGrace();
                refreshMeleeAccuracySupply(agent, gateway);
                AgentQuestHuntingBridge.engage(entry, agent, gateway, "mushroom-kingdom:2330",
                        AgentHuntingVisitRequest.Purpose.QUEST_OBJECTIVE,
                        PEPE_KING_VARIANTS, Set.of(), nowMs);
                return true;
            }
            if (waitForYetiLoot(state, nowMs)) return true;
            if (!nearPortal(entry, agent, 1, gateway)) return true;
            if (!gateway.enterPortal(agent, 1)) {
                return capabilityFailure(entry, state, gateway,
                        "empty Yeti instance could not be exited for a reroll", nowMs);
            }
            return true;
        }
        if (!travel(entry, agent, 106021400, state, gateway, nowMs)) return true;
        boolean atPortal = nearPortal(entry, agent, 2, gateway);
        AgentMushroomKingdomYetiPartyRuntime.Decision partyDecision =
                AgentMushroomKingdomYetiPartyRuntime.prepare(
                        agent, state, gateway, nowMs,
                        YETI_AGENT_SCAN_MS, YETI_HUMAN_RESPONSE_MS);
        if (partyDecision == AgentMushroomKingdomYetiPartyRuntime.Decision.WAITING) {
            state.active("approaching King Pepe while matching up to three compatible party members");
            return true;
        }
        if (!atPortal) return true;
        gateway.stop(entry);
        if (!gateway.runPortalNpcScript(agent, 2, 1300013, 1)) {
            return capabilityFailure(entry, state, gateway,
                    "King Pepe portal dialogue did not start an instance", nowMs);
        }
        state.capabilityProgress();
        return true;
    }

    private static boolean waitForYetiLoot(AgentMushroomKingdomState state, long nowMs) {
        state.beginYetiLootGrace(nowMs);
        if (state.yetiLootGraceExpired(nowMs, YETI_LOOT_GRACE_MS)) return false;
        state.active("allowing King Pepe class-box priority before leaving the instance");
        return true;
    }

    private static boolean enterPrimeMinister(AgentRuntimeEntry entry, Character agent,
                                              AgentMushroomKingdomState state,
                                              PrimitiveCapabilityGateway gateway, long nowMs) {
        if (gateway.questStatus(agent, 2332) == QuestStatus.Status.COMPLETED.getId()) return true;
        if (gateway.questStatus(agent, 2331) == QuestStatus.Status.NOT_STARTED.getId()) {
            if (!requireCapacity(entry, agent, 4001318, 1,
                    "Royal Seal boss drop", state, gateway)) return false;
            AgentMushroomKingdomCatalog.QuestNode seal = AgentMushroomKingdomCatalog.require(2331);
            if (!travel(entry, agent, seal.startMapId(), state, gateway, nowMs)) return true;
            return interact(entry, agent, seal.startNpcId(), state, gateway, nowMs,
                    () -> gateway.startQuest(agent, 2331, seal.startNpcId()));
        }
        if (gateway.itemCount(agent, 4032388) < 1) {
            return block(entry, state, gateway,
                    "Wedding Hall key is required before entering the Prime Minister instance");
        }
        if (gateway.questStatus(agent, 2332) == QuestStatus.Status.NOT_STARTED.getId()
                && !gateway.startQuest(agent, 2332, 1300002)) {
            return capabilityFailure(entry, state, gateway,
                    "Princess rescue quest could not start after receiving the key", nowMs);
        }
        if (!ensureSolo(agent, state)) return true;
        if (!travel(entry, agent, 106021402, state, gateway, nowMs)) return true;
        if (!nearPortal(entry, agent, 2, gateway)) return true;
        gateway.stop(entry);
        if (!gateway.enterPortal(agent, 2)) {
            return capabilityFailure(entry, state, gateway,
                    "Prime Minister scripted portal did not start an instance", nowMs);
        }
        state.capabilityProgress();
        return true;
    }

    private static boolean secretRoom(AgentRuntimeEntry entry, Character agent,
                                      AgentMushroomKingdomState state,
                                      PrimitiveCapabilityGateway gateway, long nowMs) {
        // Truth Revealed is offered by Violetta after q2334, while the player is
        // still in the rescued Princess room.  Accept it before q2335 sends the
        // player away: after q2331 is turned in, the boss door intentionally
        // becomes the rematch selector and can no longer reach map 106021600.
        if (gateway.questStatus(agent, 2336) == QuestStatus.Status.NOT_STARTED.getId()) {
            return startTruthQuest(entry, agent, state, gateway, nowMs);
        }
        int status = gateway.questStatus(agent, 2335);
        if (status == QuestStatus.Status.NOT_STARTED.getId()) {
            if (!requireCapacity(entry, agent, 4032405, 1,
                    "secret-room key", state, gateway)) return false;
            if (!ensurePrincessMap(entry, agent, state, gateway, nowMs)) return true;
            return interact(entry, agent, 1300002, state, gateway, nowMs,
                    () -> gateway.startQuest(agent, 2335, 1300002));
        }
        if (status == QuestStatus.Status.STARTED.getId()
                && gateway.itemCount(agent, 4032405) < 1) {
            if (!requireCapacity(entry, agent, 4032405, 1,
                    "replacement secret-room key", state, gateway)) return false;
            if (!ensurePrincessMap(entry, agent, state, gateway, nowMs)) return true;
            return interact(entry, agent, 1300002, state, gateway, nowMs,
                    () -> gateway.startQuest(agent, 2335, 1300002));
        }
        if (!travel(entry, agent, 106021000, state, gateway, nowMs)) return true;
        if (!nearPortal(entry, agent, 3, gateway)) return true;
        gateway.stop(entry);
        gateway.enterPortal(agent, 3);
        return true;
    }

    private static boolean royalSeal(AgentRuntimeEntry entry, Character agent,
                                     AgentMushroomKingdomState state,
                                     PrimitiveCapabilityGateway gateway, long nowMs) {
        AgentMushroomKingdomCatalog.QuestNode node = AgentMushroomKingdomCatalog.require(2331);
        if (gateway.questStatus(agent, 2331) == QuestStatus.Status.NOT_STARTED.getId()) {
            return ordinary(entry, agent, node, state, gateway, nowMs);
        }
        if (gateway.itemCount(agent, 4001318) < 1) {
            if (!requireCapacity(entry, agent, 4001318, 1,
                    "replacement Royal Seal", state, gateway)) return false;
            if (gateway.mapId(agent) == 106021600) {
                return interact(entry, agent, 1300002, state, gateway, nowMs, () -> {
                    gateway.startQuest(agent, 2342, 1300002);
                    return gateway.itemCount(agent, 4001318) > 0;
                });
            }
            if (!travel(entry, agent, 106021402, state, gateway, nowMs)) return true;
            if (!nearPortal(entry, agent, 2, gateway)) return true;
            gateway.stop(entry);
            gateway.enterPortal(agent, 2);
            return true;
        }
        return ordinary(entry, agent, node, state, gateway, nowMs);
    }

    private static boolean truthAndReturn(AgentRuntimeEntry entry, Character agent,
                                          AgentMushroomKingdomState state,
                                          PrimitiveCapabilityGateway gateway, long nowMs) {
        if (gateway.questStatus(agent, 2336) == QuestStatus.Status.NOT_STARTED.getId()) {
            if (gateway.questStatus(agent, 2331) == QuestStatus.Status.COMPLETED.getId()) {
                return block(entry, state, gateway,
                        "Truth Revealed was not accepted from Princess Violetta before "
                                + "the Royal Seal turn-in closed the rescued Princess room");
            }
            return startTruthQuest(entry, agent, state, gateway, nowMs);
        }
        if (!requireCapacity(entry, agent, 1082254, 1,
                "Mushroom Kingdom completion reward", state, gateway)) return false;
        if (!travel(entry, agent, AgentMushroomKingdomCatalog.ENTRANCE_MAP_ID,
                state, gateway, nowMs)) return true;
        return interact(entry, agent, 1300000, state, gateway, nowMs,
                () -> gateway.completeQuest(agent, 2336, 1300000));
    }

    private static boolean startTruthQuest(AgentRuntimeEntry entry, Character agent,
                                           AgentMushroomKingdomState state,
                                           PrimitiveCapabilityGateway gateway, long nowMs) {
        int truthSlots = (gateway.itemCount(agent, 4032387) < 1 ? 1 : 0)
                + (gateway.itemCount(agent, 4032386) < 1 ? 1 : 0);
        if (truthSlots > 0 && !requireCapacity(entry, agent, 4032387, truthSlots,
                "two truth items", state, gateway)) return false;
        if (!ensurePrincessMap(entry, agent, state, gateway, nowMs)) return true;
        return interact(entry, agent, 1300002, state, gateway, nowMs,
                () -> gateway.startQuest(agent, 2336, 1300002));
    }

    private static boolean ensurePrincessMap(AgentRuntimeEntry entry, Character agent,
                                             AgentMushroomKingdomState state,
                                             PrimitiveCapabilityGateway gateway, long nowMs) {
        if (gateway.mapId(agent) == 106021600) return true;
        if (!travel(entry, agent, 106021402, state, gateway, nowMs)) return false;
        if (!nearPortal(entry, agent, 2, gateway)) return false;
        gateway.stop(entry);
        if (!gateway.enterPortal(agent, 2)) {
            capabilityFailure(entry, state, gateway,
                    "could not re-enter the rescued Princess room", nowMs);
            return false;
        }
        return gateway.mapId(agent) == 106021600;
    }

    private static boolean travel(AgentRuntimeEntry entry, Character agent, int mapId,
                                  AgentMushroomKingdomState state,
                                  PrimitiveCapabilityGateway gateway, long nowMs) {
        if (gateway.mapId(agent) == mapId) {
            state.capabilityProgress();
            return true;
        }
        Boolean scripted = scriptedTravel(entry, agent, mapId, state, gateway, nowMs);
        if (scripted != null) return scripted;
        AgentRouteOutcome outcome = gateway.travelTo(entry, agent, mapId, nowMs);
        if (outcome.status() == AgentRouteStatus.ARRIVED || outcome.status() == AgentRouteStatus.MOVING) {
            state.capabilityProgress();
            return outcome.status() == AgentRouteStatus.ARRIVED;
        }
        gateway.refreshNavigation(entry, agent);
        capabilityFailure(entry, state, gateway, "route " + gateway.mapId(agent) + " -> " + mapId
                + " returned " + outcome.status(), nowMs);
        return false;
    }

    private static Boolean scriptedTravel(AgentRuntimeEntry entry, Character agent,
                                           int destinationMapId,
                                           AgentMushroomKingdomState state,
                                           PrimitiveCapabilityGateway gateway, long nowMs) {
        int currentMapId = gateway.mapId(agent);
        if (destinationMapId == AgentMushroomKingdomCatalog.ENTRANCE_MAP_ID
                && currentMapId == 100000000) {
            AgentRouteOutcome outcome = gateway.travelTo(entry, agent, 100000002, nowMs);
            if (outcome.status() == AgentRouteStatus.ARRIVED
                    || outcome.status() == AgentRouteStatus.MOVING) {
                state.capabilityProgress();
                return false;
            }
            gateway.refreshNavigation(entry, agent);
            capabilityFailure(entry, state, gateway,
                    "Bruce return route to Henesys Pet Park returned " + outcome.status(), nowMs);
            return false;
        }
        if (destinationMapId == AgentMushroomKingdomCatalog.ENTRANCE_MAP_ID
                && currentMapId == 100000002) {
            return enterScriptedRoutePortal(entry, agent, 4,
                    AgentMushroomKingdomCatalog.ENTRANCE_MAP_ID, destinationMapId,
                    "Mushroom Kingdom return", state, gateway, nowMs);
        }
        if (currentMapId == 106020401 && destinationMapId < 106020401) {
            return enterScriptedRoutePortal(entry, agent, 4, 106020400,
                    destinationMapId, "Intoxicated Pig field exit",
                    state, gateway, nowMs);
        }
        if (currentMapId == 106020402 && destinationMapId < 106020402) {
            return enterScriptedRoutePortal(entry, agent, 3, 106020401,
                    destinationMapId, "Intoxicated Pig lower field exit",
                    state, gateway, nowMs);
        }
        if (currentMapId >= AgentMushroomKingdomCatalog.ENTRANCE_MAP_ID
                && currentMapId <= 106020200
                && destinationMapId >= 106020400 && destinationMapId <= 106021700) {
            AgentRouteOutcome outcome = gateway.travelTo(entry, agent, 106020300, nowMs);
            if (outcome.status() == AgentRouteStatus.ARRIVED
                    || outcome.status() == AgentRouteStatus.MOVING) {
                state.capabilityProgress();
                return false;
            }
            gateway.refreshNavigation(entry, agent);
            capabilityFailure(entry, state, gateway,
                    "first thorn barrier staging route returned " + outcome.status(), nowMs);
            return false;
        }
        if (currentMapId == 106020300
                && destinationMapId >= 106020400 && destinationMapId <= 106021700) {
            if (gateway.questStatus(agent, FIRST_THORN_BARRIER_UNLOCK_QUEST_ID)
                    == QuestStatus.Status.NOT_STARTED.getId()) {
                killerSporeBarrier(entry, agent, state, gateway, nowMs);
                return false;
            }
            return enterScriptedRoutePortal(entry, agent, FIRST_THORN_WALL_PORTAL_ID,
                    106020400, destinationMapId,
                    "first thorn barrier", state, gateway, nowMs);
        }
        if (currentMapId == 106020400
                && destinationMapId >= 106020500 && destinationMapId <= 106021700) {
            if (gateway.questStatus(agent, 2324) != QuestStatus.Status.COMPLETED.getId()) {
                thornBarrier(entry, agent, state, gateway, nowMs);
                return false;
            }
            return enterScriptedRoutePortal(entry, agent, 3, 106020501, destinationMapId,
                    "castle thorn gate", state, gateway, nowMs);
        }
        if (currentMapId == 106021000
                && destinationMapId > 106021000 && destinationMapId <= 106021700) {
            return enterScriptedRoutePortal(entry, agent, 2, 106021100,
                    destinationMapId, "West Castle Tower entrance",
                    state, gateway, nowMs);
        }
        if (currentMapId >= 106020501 && currentMapId < 106021400
                && destinationMapId >= 106021401 && destinationMapId <= 106021700) {
            AgentRouteOutcome outcome = gateway.travelTo(entry, agent, 106021400, nowMs);
            if (outcome.status() == AgentRouteStatus.ARRIVED
                    || outcome.status() == AgentRouteStatus.MOVING) {
                state.capabilityProgress();
                return false;
            }
            gateway.refreshNavigation(entry, agent);
            capabilityFailure(entry, state, gateway,
                    "Wedding Hall approach route returned " + outcome.status(), nowMs);
            return false;
        }
        if (currentMapId == 106021400) {
            boolean east = destinationMapId > 106021400;
            return enterScriptedRoutePortal(entry, agent, east ? 2 : 1,
                    east ? 106021401 : 106021300,
                    destinationMapId,
                    east ? "Yeti-completion gate" : "East Castle Tower exit",
                    state, gateway, nowMs);
        }
        if (currentMapId == 106021401 && destinationMapId > 106021401) {
            return enterScriptedRoutePortal(entry, agent, 2, 106021402,
                    destinationMapId, "Wedding Hall inner entrance",
                    state, gateway, nowMs);
        }
        if (currentMapId == 106021600 && destinationMapId != 106021600) {
            return enterScriptedRoutePortal(entry, agent, 1, 106021402,
                    destinationMapId, "rescued Princess room exit",
                    state, gateway, nowMs);
        }
        if (currentMapId == 106021001 && destinationMapId != 106021001) {
            return enterScriptedRoutePortal(entry, agent, 1, 106021000,
                    destinationMapId, "secret room exit",
                    state, gateway, nowMs);
        }
        if (currentMapId == 106021402 && destinationMapId < 106021402) {
            return enterScriptedRoutePortal(entry, agent, 1, 106021401,
                    destinationMapId, "Wedding Hall inner exit",
                    state, gateway, nowMs);
        }
        if (currentMapId == 106021401 && destinationMapId < 106021401) {
            return enterScriptedRoutePortal(entry, agent, 1, 106021400,
                    destinationMapId, "Wedding Hall outer exit",
                    state, gateway, nowMs);
        }
        return null;
    }

    private static boolean enterScriptedRoutePortal(AgentRuntimeEntry entry, Character agent,
                                                     int portalId, int expectedMapId,
                                                     int destinationMapId, String description,
                                                     AgentMushroomKingdomState state,
                                                     PrimitiveCapabilityGateway gateway, long nowMs) {
        if (!nearPortal(entry, agent, portalId, gateway)) {
            stageStalledUnobservedPortalApproach(
                    entry, agent, portalId, description, state, gateway, nowMs);
            return false;
        }
        int sourceMapId = gateway.mapId(agent);
        gateway.stop(entry);
        if (!gateway.enterPortal(agent, portalId)) {
            capabilityFailure(entry, state, gateway,
                    description + " portal rejected entry", nowMs);
            return false;
        }
        int observedMapId = gateway.mapId(agent);
        if (observedMapId != sourceMapId && observedMapId != expectedMapId) {
            capabilityFailure(entry, state, gateway,
                    description + " reached unexpected map " + observedMapId, nowMs);
            return false;
        }
        state.capabilityProgress();
        return observedMapId == destinationMapId;
    }

    private static void stageStalledUnobservedPortalApproach(
            AgentRuntimeEntry entry, Character agent, int portalId, String description,
            AgentMushroomKingdomState state, PrimitiveCapabilityGateway gateway, long nowMs) {
        if (gateway.observedByPlayer(agent)
                || nowMs - state.progressAtMs() < UNOBSERVED_NPC_STAGING_DELAY_MS) return;
        Point portal = gateway.portalPosition(agent, portalId);
        if (portal == null) return;
        Point groundedPortal = gateway.groundPoint(agent.getMap(), portal);
        if (groundedPortal == null) groundedPortal = portal;
        gateway.stop(entry);
        gateway.stagePosition(entry, agent, groundedPortal);
        gateway.refreshNavigation(entry, agent);
        state.capabilityProgress();
        state.active("recovering stalled " + description + " portal approach");
    }

    private static boolean recoverBelowMap(AgentRuntimeEntry entry, Character agent,
                                           AgentMushroomKingdomState state,
                                           PrimitiveCapabilityGateway gateway) {
        MapleMap map = agent.getMap();
        Point position = gateway.position(agent);
        Rectangle area = map == null ? null : map.getMapArea();
        if (position == null || area == null || area.width <= 0 || area.height <= 0
                || position.y <= area.y + area.height + BELOW_MAP_RECOVERY_MARGIN_PX) {
            return false;
        }
        Point portal = gateway.portalPosition(agent, 0);
        if (portal == null) return false;
        Point recovery = gateway.groundPoint(map, portal);
        if (recovery == null) recovery = portal;
        gateway.stop(entry);
        gateway.stagePosition(entry, agent, recovery);
        gateway.refreshNavigation(entry, agent);
        state.capabilityProgress();
        state.active("recovering from a fall below Mushroom Kingdom map " + gateway.mapId(agent));
        return true;
    }

    private static boolean interact(AgentRuntimeEntry entry, Character agent, int npcId,
                                    AgentMushroomKingdomState state,
                                    PrimitiveCapabilityGateway gateway, long nowMs, Action action) {
        Point npc = gateway.npcPosition(agent, npcId);
        if (npc == null) {
            return capabilityFailure(entry, state, gateway,
                    "NPC " + npcId + " is absent from map " + gateway.mapId(agent), nowMs);
        }
        if (!gateway.grounded(agent)
                || !AgentNpcInteractionReachabilityService.canInteract(
                entry, agent, npc, INTERACTION_DISTANCE_PX)) {
            if (!gateway.observedByPlayer(agent)
                    && nowMs - state.progressAtMs() >= UNOBSERVED_NPC_STAGING_DELAY_MS) {
                Point groundedNpc = gateway.groundPoint(agent.getMap(), npc);
                if (groundedNpc != null) {
                    gateway.stagePosition(entry, agent, groundedNpc);
                    state.capabilityProgress();
                    return true;
                }
            }
            gateway.navigate(entry, npc, true);
            return true;
        }
        if (nowMs < state.nextActionAtMs()) return true;
        gateway.facePosition(agent, npc);
        gateway.stop(entry);
        boolean success = action.run();
        state.nextActionAtMs(nowMs + INTERACTION_RETRY_MS);
        if (success) state.capabilityProgress();
        else capabilityFailure(entry, state, gateway,
                "NPC " + npcId + " did not advance quest " + state.currentQuestId(), nowMs);
        return true;
    }

    private static boolean nearPortal(AgentRuntimeEntry entry, Character agent, int portalId,
                                      PrimitiveCapabilityGateway gateway) {
        Point portal = gateway.portalPosition(agent, portalId);
        if (portal == null) return false;
        Point position = gateway.position(agent);
        if (!gateway.grounded(agent) || position == null
                || position.distance(portal) > SCRIPTED_PORTAL_INTERACTION_DISTANCE_PX) {
            gateway.navigate(entry, portal, true);
            return false;
        }
        return true;
    }

    private static boolean ensureSolo(Character agent, AgentMushroomKingdomState state) {
        if (!AgentPartyGatewayRuntime.party().hasParty(agent)) return true;
        AgentPartyGatewayRuntime.party().leaveCurrentParty(agent);
        if (AgentPartyGatewayRuntime.party().hasParty(agent)) {
            state.active("leaving the current party before a solo Mushroom Kingdom instance");
            return false;
        }
        state.capabilityProgress();
        return true;
    }

    private static void refreshMeleeAccuracySupply(Character agent,
                                                    PrimitiveCapabilityGateway gateway) {
        Job job = agent.getJob();
        int itemCount = gateway.itemCount(agent, SNIPER_PILL_ITEM_ID);
        boolean buffed = agent.getBuffedValue(BuffStat.ACC) != null;
        if (job != null && accuracySupplyNeeded(job.getId(), agent.getDex(), buffed, itemCount)) {
            gateway.useItem(agent, SNIPER_PILL_ITEM_ID);
        }
    }

    static boolean accuracySupplyNeeded(int jobId, int baseDex,
                                        boolean accuracyBuffActive, int itemCount) {
        Job job = Job.getById(jobId);
        boolean accuracyDependentMelee = job != null
                && (job.isA(Job.WARRIOR) || job.isA(Job.BRAWLER));
        int dexThreshold = job != null && job.isA(Job.BRAWLER) ? 30 : 20;
        return accuracyDependentMelee && baseDex <= dexThreshold
                && !accuracyBuffActive && itemCount > 0;
    }

    private static boolean requireCapacity(AgentRuntimeEntry entry, Character agent,
                                           int itemId, int slots, String description,
                                           AgentMushroomKingdomState state,
                                           PrimitiveCapabilityGateway gateway) {
        if (gateway.freeSlots(agent, itemId) >= slots) return true;
        block(entry, state, gateway, "need " + slots + " free " + description
                + " inventory slot" + (slots == 1 ? "" : "s"));
        return false;
    }

    private static boolean capabilityFailure(AgentRuntimeEntry entry,
                                             AgentMushroomKingdomState state,
                                             PrimitiveCapabilityGateway gateway,
                                             String reason, long nowMs) {
        if (state.capabilityFailure() >= 8) {
            block(entry, state, gateway, reason);
            return false;
        }
        state.active(reason + "; retrying");
        state.nextActionAtMs(nowMs + INTERACTION_RETRY_MS);
        return true;
    }

    private static boolean block(AgentRuntimeEntry entry, AgentMushroomKingdomState state,
                                 PrimitiveCapabilityGateway gateway, String reason) {
        gateway.stop(entry);
        AgentMushroomKingdomMapReservationRuntime.release(
                server.agents.integration.AgentRuntimeIdentityRuntime.botId(entry));
        state.clearHuntMap();
        state.block(reason);
        return false;
    }

    private static int objectiveMetric(Character agent,
                                       AgentMushroomKingdomCatalog.QuestNode node,
                                       PrimitiveCapabilityGateway gateway) {
        if (node.itemId() > 0) return gateway.itemCount(agent, node.itemId());
        if (!node.mobIds().isEmpty()) {
            return node.mobIds().stream()
                    .mapToInt(mob -> gateway.questProgress(agent, node.questId(), mob)).sum();
        }
        return gateway.questStatus(agent, node.questId());
    }

    private static int startRewardItem(int questId) {
        return switch (questId) {
            case 2319, 2320 -> 4032389;
            default -> 0;
        };
    }

    private static String reason(AgentMushroomKingdomCatalog.QuestNode node, int metric) {
        return node.hunting() ? "quest " + node.questId() + " objective " + metric + '/'
                + node.requiredCount() : "quest " + node.questId() + " dialogue/travel";
    }

    @FunctionalInterface
    private interface Action { boolean run(); }
}
