package server.agents.progression;

import client.Character;
import client.QuestStatus;
import server.ScrollTransactionService;
import server.agents.integration.AgentClientGatewayRuntime;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.hunting.AgentHuntingVisitRequest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One bounded completion of repeatable quest 2337 followed by one Pepe scroll attempt. */
public final class AgentMushroomKingdomPepeScrollRuntime {
    private static final int QUEST_ID = 2337;
    private static final int NPC_ID = 1300005;
    private static final int ENTRANCE_MAP_ID = 106_020_000;
    private static final long RESERVATION_MS = config.AgentTuning.longValue(
            "server.agents.progression.AgentMushroomKingdomPepeScrollRuntime.RESERVATION_MS");
    private static final long TIMEOUT_MS = 90L * 60L * 1_000L;
    private static final Map<Integer, Integer> REQUIRED = requirements();
    private static final Map<Integer, List<Integer>> HUNT_MAPS = huntMaps();

    private AgentMushroomKingdomPepeScrollRuntime() { }

    public static boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs) {
        return tick(entry, agent, nowMs, AgentPrimitiveCapabilityGatewayRuntime.gateway());
    }

    static boolean tick(AgentRuntimeEntry entry, Character agent, long nowMs,
                        PrimitiveCapabilityGateway gateway) {
        AgentMushroomKingdomPostStoryState state = entry.capabilityStates()
                .require(AgentMushroomKingdomPostStoryState.STATE_KEY);
        if (state.phase() != AgentMushroomKingdomPostStoryState.Phase.ACTIVE) return false;
        if (!gateway.alive(agent)) {
            gateway.stop(entry);
            state.active("waiting to revive before resuming King Pepe's Scroll");
            return true;
        }
        AgentPepeEquipmentSnapshot equipment = AgentPepeEquipmentCatalog.capture(agent);
        if (agent.getLevel() > 38 || gateway.questStatus(agent, 2336)
                != QuestStatus.Status.COMPLETED.getId() || !equipment.scrollable()) {
            block(entry, state, gateway,
                    "quest 2337 requires completed Mushroom Kingdom and a planned Pepe weapon with slots",
                    nowMs);
            return true;
        }
        if (gateway.itemCount(agent, equipment.scrollItemId()) == 0
                && gateway.freeSlots(agent, equipment.scrollItemId()) < 1) {
            block(entry, state, gateway,
                    "King Pepe's Scroll needs one free USE inventory slot", nowMs);
            return true;
        }
        if (gateway.itemCount(agent, equipment.scrollItemId()) > 0) {
            ScrollTransactionService.Result applied = AgentPepeEquipmentCatalog.applyOwnedScroll(agent);
            AgentMushroomKingdomFarmProgressRuntime.recordScrollAttempt(
                    agent.getId(), applied != null && applied.applied(),
                    applied == null ? "Pepe scroll could not be applied"
                            : "Pepe scroll outcome: " + applied.outcome(), nowMs);
            if (applied == null || !applied.applied()) {
                block(entry, state, gateway,
                        "Pepe scroll reward could not be applied safely", nowMs);
            } else {
                gateway.stop(entry);
                release(entry);
                state.complete("one King Pepe's Scroll quest and upgrade attempt completed: "
                        + applied.outcome());
            }
            return true;
        }

        int metric = REQUIRED.keySet().stream()
                .mapToInt(mobId -> Math.min(REQUIRED.get(mobId),
                        gateway.questProgress(agent, QUEST_ID, mobId))).sum();
        state.observe(gateway.mapId(agent), metric, nowMs);
        if (nowMs - state.progressAtMs() > TIMEOUT_MS) {
            block(entry, state, gateway,
                    "King Pepe's Scroll made no map or kill progress for 90 minutes", nowMs);
            return true;
        }

        int status = gateway.questStatus(agent, QUEST_ID);
        if (status != QuestStatus.Status.STARTED.getId()) {
            release(entry);
            state.clearScrollMap();
            if (!AgentMushroomKingdomPostStorySupport.travel(
                    entry, agent, ENTRANCE_MAP_ID, state, gateway, nowMs)) return true;
            state.active("accepting one repeat of King Pepe's Scroll");
            return AgentMushroomKingdomPostStorySupport.interact(
                    entry, agent, NPC_ID, state, gateway, nowMs,
                    () -> gateway.startQuest(agent, QUEST_ID, NPC_ID));
        }

        int targetMob = REQUIRED.entrySet().stream()
                .filter(value -> gateway.questProgress(agent, QUEST_ID, value.getKey())
                        < value.getValue())
                .mapToInt(Map.Entry::getKey).findFirst().orElse(0);
        if (targetMob == 0) {
            release(entry);
            state.clearScrollMap();
            if (!AgentMushroomKingdomPostStorySupport.travel(
                    entry, agent, ENTRANCE_MAP_ID, state, gateway, nowMs)) return true;
            state.active("submitting King Pepe's Scroll for the exact weapon reward");
            int selection = equipment.rewardSelectionIndex();
            return AgentMushroomKingdomPostStorySupport.interact(
                    entry, agent, NPC_ID, state, gateway, nowMs,
                    () -> gateway.completeQuest(agent, QUEST_ID, NPC_ID, selection));
        }

        int huntMap = selectedMap(entry, agent, targetMob, state, gateway, nowMs);
        if (!AgentMushroomKingdomPostStorySupport.travel(
                entry, agent, huntMap, state, gateway, nowMs)) return true;
        AgentMushroomKingdomMapReservationRuntime.maintain(
                AgentRuntimeIdentityRuntime.botId(entry), huntMap, gateway.mapId(agent),
                nowMs, RESERVATION_MS);
        state.active("King Pepe's Scroll target " + targetMob + " progress "
                + gateway.questProgress(agent, QUEST_ID, targetMob) + '/' + REQUIRED.get(targetMob));
        AgentQuestHuntingBridge.engage(entry, agent, gateway,
                "mushroom-kingdom:pepe-scroll:" + targetMob,
                AgentHuntingVisitRequest.Purpose.QUEST_OBJECTIVE,
                Set.of(targetMob), Set.of(), nowMs);
        return true;
    }

    public static void cancel(AgentRuntimeEntry entry) {
        AgentPrimitiveCapabilityGatewayRuntime.gateway().stop(entry);
        release(entry);
    }

    private static int selectedMap(AgentRuntimeEntry entry, Character agent, int mobId,
                                   AgentMushroomKingdomPostStoryState state,
                                   PrimitiveCapabilityGateway gateway, long nowMs) {
        int selected = state.scrollMap(mobId);
        if (selected > 0) {
            var lease = AgentMushroomKingdomMapReservationRuntime.maintain(
                    AgentRuntimeIdentityRuntime.botId(entry), selected, gateway.mapId(agent),
                    nowMs, RESERVATION_MS);
            if (lease == AgentMushroomKingdomMapReservationRuntime.LeaseState.TRAVELING
                    || lease == AgentMushroomKingdomMapReservationRuntime.LeaseState.OCCUPYING) {
                return selected;
            }
        }
        List<AgentMushroomKingdomCatalog.HuntMap> ranked = HUNT_MAPS.get(mobId).stream()
                .map(mapId -> AgentMushroomKingdomCatalog.huntMaps().stream()
                        .filter(map -> map.mapId() == mapId).findFirst().orElseThrow())
                .toList();
        Map<Integer, Integer> occupancy = new LinkedHashMap<>();
        ranked.forEach(map -> occupancy.put(map.mapId(), gateway.characterCount(agent, map.mapId())));
        var clients = AgentClientGatewayRuntime.clients();
        int world = clients.hasClient(agent) ? clients.world(agent) : 0;
        int channel = clients.hasClient(agent) ? clients.channel(agent) : 0;
        int mapId = AgentMushroomKingdomMapReservationRuntime.selectAndReserve(
                AgentRuntimeIdentityRuntime.botId(entry), world, channel, ranked, occupancy,
                nowMs, RESERVATION_MS).orElseThrow().map().mapId();
        state.selectScrollMap(mobId, mapId);
        return mapId;
    }

    private static void release(AgentRuntimeEntry entry) {
        AgentMushroomKingdomMapReservationRuntime.release(
                AgentRuntimeIdentityRuntime.botId(entry));
    }

    private static void block(AgentRuntimeEntry entry,
                              AgentMushroomKingdomPostStoryState state,
                              PrimitiveCapabilityGateway gateway,
                              String reason,
                              long nowMs) {
        gateway.stop(entry);
        release(entry);
        state.block(reason);
        Character agent = AgentRuntimeIdentityRuntime.bot(entry);
        if (agent != null) {
            AgentMushroomKingdomFarmProgressRuntime.recordStopReason(
                    agent.getId(), reason, nowMs);
        }
    }

    private static Map<Integer, List<Integer>> huntMaps() {
        Map<Integer, List<Integer>> maps = new LinkedHashMap<>();
        maps.put(3300000, List.of(106020100, 106020200, 106020300));
        maps.put(3300001, List.of(106020300, 106020200, 106020100));
        maps.put(3300002, List.of(106020401, 106020402));
        maps.put(3300003, List.of(106021100, 106021000, 106020800, 106020700));
        maps.put(3300004, List.of(106021300, 106021200, 106021100));
        return Map.copyOf(maps);
    }

    private static Map<Integer, Integer> requirements() {
        Map<Integer, Integer> required = new LinkedHashMap<>();
        required.put(3300000, 200);
        required.put(3300001, 200);
        required.put(3300002, 300);
        required.put(3300003, 400);
        required.put(3300004, 400);
        return java.util.Collections.unmodifiableMap(required);
    }
}
