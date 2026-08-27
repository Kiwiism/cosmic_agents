package server.agents.progression;

import client.Character;
import client.QuestStatus;
import server.agents.integration.AgentPartyGatewayRuntime;
import server.agents.integration.AgentPrimitiveCapabilityGatewayRuntime;
import server.agents.integration.PrimitiveCapabilityGateway;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.hunting.AgentHuntingVisitRequest;

import java.util.Set;

/** Bounded post-story Yeti rematch runtime; stops after the planned weapon or ten runs. */
public final class AgentMushroomKingdomYetiFarmRuntime {
    private static final int INSTANCE_MAP_ID = 106_021_500;
    private static final Set<Integer> YETIS = Set.of(3300005, 3300006, 3300007);
    private static final long AGENT_SCAN_MS = config.AgentTuning.longValue(
            "server.agents.progression.AgentMushroomKingdomYetiFarmRuntime.AGENT_SCAN_MS");
    private static final long HUMAN_RESPONSE_MS = config.AgentTuning.longValue(
            "server.agents.progression.AgentMushroomKingdomYetiFarmRuntime.HUMAN_RESPONSE_MS");
    private static final long LOOT_GRACE_MS = config.AgentTuning.longValue(
            "server.agents.progression.AgentMushroomKingdomYetiFarmRuntime.LOOT_GRACE_MS");
    private static final long EMPTY_INSTANCE_RECOVERY_MS = config.AgentTuning.longValue(
            "server.agents.progression.AgentMushroomKingdomYetiFarmRuntime.EMPTY_INSTANCE_RECOVERY_MS");
    private static final long TIMEOUT_MS = 45L * 60L * 1_000L;

    private AgentMushroomKingdomYetiFarmRuntime() { }

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
            state.active("waiting to revive before resuming Yeti farming");
            return true;
        }
        if (agent.getLevel() > 38 || gateway.questStatus(agent, 2336)
                != QuestStatus.Status.COMPLETED.getId()) {
            block(entry, agent, state, gateway,
                    "Yeti farming requires completed quest 2336 at level 30 through 38", nowMs);
            return true;
        }
        AgentPepeEquipmentSnapshot equipment = AgentPepeEquipmentCatalog.capture(agent);
        if (equipment.owned()) {
            finishParty(agent, state);
            gateway.stop(entry);
            state.complete("desired Pepe weapon " + equipment.desiredWeaponItemId() + " acquired");
            return true;
        }
        int weaponBox = AgentPepeEquipmentCatalog.weaponBoxItemId(agent.getJob().getId());
        if (gateway.freeSlots(agent, weaponBox) < 1
                || gateway.freeSlots(agent, equipment.desiredWeaponItemId()) < 1) {
            block(entry, agent, state, gateway,
                    "Yeti farming needs one USE and one EQUIP inventory slot", nowMs);
            return true;
        }
        AgentMushroomKingdomFarmProgress progress =
                AgentMushroomKingdomFarmProgressRuntime.load(agent.getId(), nowMs);
        if (progress.yetiCooldownUntilMs() > nowMs
                || progress.yetiRuns() >= AgentMushroomKingdomFarmProgressRuntime.MAX_YETI_RUNS) {
            finishParty(agent, state);
            gateway.stop(entry);
            state.complete("bounded ten-run Yeti campaign is complete");
            return true;
        }
        state.observe(gateway.mapId(agent), progress.yetiRuns(), nowMs);
        if (nowMs - state.progressAtMs() > TIMEOUT_MS) {
            block(entry, agent, state, gateway,
                    "Yeti farm made no map or completed-run progress for 45 minutes", nowMs);
            return true;
        }
        if (gateway.mapId(agent) == INSTANCE_MAP_ID) {
            AgentMushroomKingdomYetiPartyRuntime.leaveLobby(state);
            int monsters = gateway.liveMonsterCount(agent, YETIS);
            if (monsters > 0) {
                state.sawYetiBoss();
                AgentQuestHuntingBridge.engage(entry, agent, gateway,
                        "mushroom-kingdom:yeti-farm",
                        AgentHuntingVisitRequest.Purpose.ROUTE_HARVEST, YETIS, Set.of(), nowMs);
                return true;
            }
            if (!state.yetiBossSeen()) {
                if (nowMs - state.progressAtMs() < EMPTY_INSTANCE_RECOVERY_MS) {
                    state.active("waiting briefly for the Yeti rematch spawn");
                    return true;
                }
                state.active("leaving an empty resumed Yeti instance without run credit");
                if (!AgentMushroomKingdomPostStorySupport.nearPortal(
                        entry, agent, 1, gateway)) return true;
                gateway.stop(entry);
                if (!gateway.enterPortal(agent, 1)) {
                    return AgentMushroomKingdomPostStorySupport.fail(entry, state, gateway,
                            "empty Yeti rematch exit portal rejected entry", nowMs);
                }
                state.capabilityProgress(nowMs);
                return true;
            }
            state.beginYetiLootGrace(nowMs);
            if (!state.yetiLootGraceExpired(nowMs, LOOT_GRACE_MS)) {
                state.active("waiting for class-box loot priority");
                return true;
            }
            if (!state.yetiRunCounted()) {
                weaponBox = AgentPepeEquipmentCatalog.weaponBoxItemId(agent.getJob().getId());
                int mixedBox = AgentPepeEquipmentCatalog.mixedBoxItemId(agent.getJob().getId());
                gateway.lootNearby(agent, Set.of(weaponBox, mixedBox));
                boolean relevantBox = gateway.itemCount(agent, weaponBox) > 0
                        || gateway.itemCount(agent, mixedBox) > 0;
                AgentPepeEquipmentCatalog.openRelevantBoxes(agent, gateway);
                equipment = AgentPepeEquipmentCatalog.capture(agent);
                progress = AgentMushroomKingdomFarmProgressRuntime.recordYetiRun(
                        agent.getId(), relevantBox,
                        equipment.owned() ? equipment.desiredWeaponItemId() : 0, nowMs);
                boolean stop = equipment.owned() || progress.yetiRuns()
                        >= AgentMushroomKingdomFarmProgressRuntime.MAX_YETI_RUNS;
                state.countYetiRun(stop);
                state.capabilityProgress(nowMs);
            }
            if (!AgentMushroomKingdomPostStorySupport.nearPortal(entry, agent, 1, gateway)) {
                return true;
            }
            gateway.stop(entry);
            if (!gateway.enterPortal(agent, 1)) {
                return AgentMushroomKingdomPostStorySupport.fail(entry, state, gateway,
                        "Yeti rematch exit portal rejected entry", nowMs);
            }
            return true;
        }

        if (state.yetiRunCounted()) {
            if (state.stopAfterYetiExit()) {
                finishParty(agent, state);
                gateway.stop(entry);
                state.complete(AgentPepeEquipmentCatalog.capture(agent).owned()
                        ? "desired Pepe weapon acquired"
                        : "ten Yeti runs completed; returning to the global planner");
                return true;
            }
            state.resetYetiRun();
            state.restartYetiLobbyVisit(nowMs);
        }
        if (!AgentMushroomKingdomPostStorySupport.travel(entry, agent,
                AgentMushroomKingdomYetiPartyRuntime.LOBBY_MAP_ID, state, gateway, nowMs)) {
            return true;
        }
        boolean atPortal = AgentMushroomKingdomPostStorySupport.nearPortal(
                entry, agent, 2, gateway);
        AgentMushroomKingdomYetiPartyRuntime.Decision party =
                AgentMushroomKingdomYetiPartyRuntime.prepare(
                        agent, state, gateway, AgentMushroomKingdomYetiPartyRuntime.Mode.FARM,
                        nowMs, AGENT_SCAN_MS, HUMAN_RESPONSE_MS);
        if (party == AgentMushroomKingdomYetiPartyRuntime.Decision.WAITING || !atPortal) {
            state.active("matching a post-story Yeti party and approaching the entrance");
            return true;
        }
        gateway.stop(entry);
        if (!gateway.runPortalNpcScript(agent, 2, 1300013, 1)) {
            return AgentMushroomKingdomPostStorySupport.fail(entry, state, gateway,
                    "King Pepe rematch dialogue did not start an instance", nowMs);
        }
        state.capabilityProgress(nowMs);
        return true;
    }

    public static void cancel(AgentRuntimeEntry entry, Character agent) {
        AgentPrimitiveCapabilityGatewayRuntime.gateway().stop(entry);
        entry.capabilityStates().find(AgentMushroomKingdomPostStoryState.STATE_KEY)
                .ifPresent(state -> finishParty(agent, state));
        AgentMushroomKingdomFarmProgressRuntime.recordStopReason(
                agent.getId(), "Yeti farming cancelled", System.currentTimeMillis());
    }

    private static void block(AgentRuntimeEntry entry, Character agent,
                              AgentMushroomKingdomPostStoryState state,
                              PrimitiveCapabilityGateway gateway, String reason, long nowMs) {
        finishParty(agent, state);
        gateway.stop(entry);
        state.block(reason);
        AgentMushroomKingdomFarmProgressRuntime.recordStopReason(agent.getId(), reason, nowMs);
    }

    private static void finishParty(Character agent, AgentMushroomKingdomPostStoryState state) {
        AgentMushroomKingdomYetiPartyRuntime.leaveLobby(state);
        if (AgentPartyGatewayRuntime.party().hasParty(agent)) {
            AgentPartyGatewayRuntime.party().leaveCurrentParty(agent);
        }
    }
}
