package server.agents.capabilities.behavior;

import client.Character;
import config.YamlConfig;
import server.agents.behavior.AgentBehaviorRuntime;
import server.agents.capabilities.combat.AgentGrindTargetStateRuntime;
import server.agents.integration.cosmic.CosmicAgentPerceptionSnapshotFactory;
import server.agents.integration.AgentRuntimeIdentityRuntime;
import server.agents.perception.AgentPerceptionSnapshot;
import server.agents.runtime.AgentModeStateRuntime;
import server.agents.runtime.AgentRuntimeEntry;
import server.agents.runtime.AgentRuntimeRegistry;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import server.maps.MapleMap;

/** Stateless fairness scheduler: personality affects rank, a rotating epoch prevents permanent idling. */
public final class AgentMapActivityPolicy {
    private static final long ROTATION_MS = config.AgentTuning.longValue("server.agents.capabilities.behavior.AgentMapActivityPolicy.ROTATION_MS");
    private static final long DECISION_CACHE_MS = config.AgentTuning.longValue("server.agents.capabilities.behavior.AgentMapActivityPolicy.DECISION_CACHE_MS");
    private static final int MOB_SCARCITY_WEIGHT_PERCENT = config.AgentTuning.intValue(
            "server.agents.capabilities.behavior.AgentMapActivityPolicy.MOB_SCARCITY_WEIGHT_PERCENT");
    private static final Map<MapleMap, DecisionWindow> DECISIONS = new ConcurrentHashMap<>();

    private AgentMapActivityPolicy() {
    }

    public static boolean shouldRest(AgentRuntimeEntry entry, Character agent, long nowMs) {
        if (entry == null || agent == null || agent.getMap() == null || !AgentBehaviorRuntime.enabled(entry)) return false;
        if (DECISIONS.size() > 512) {
            DECISIONS.entrySet().removeIf(cached -> nowMs - cached.getValue().createdAtMs() > 5_000L);
        }
        DecisionWindow window = DECISIONS.compute(agent.getMap(), (map, current) ->
                current != null && nowMs - current.createdAtMs() <= DECISION_CACHE_MS
                        ? current : build(map, nowMs));
        return window.restingAgentIds().contains(agent.getId());
    }

    private static DecisionWindow build(MapleMap map, long nowMs) {
        List<AgentRuntimeEntry> peers = AgentRuntimeRegistry.activeEntriesSnapshot().stream()
                .filter(peer -> {
                    Character bot = AgentRuntimeIdentityRuntime.bot(peer);
                    return bot != null && bot.getMap() == map && bot.getHp() > 0
                            && AgentBehaviorRuntime.enabled(peer)
                            && AgentModeStateRuntime.grinding(peer);
                })
                .sorted(Comparator.comparingInt(peer -> -priority(peer, nowMs)))
                .toList();
        int minimum = Math.max(1, config.AgentYamlConfig.config.agent.AGENT_MAP_CROWD_MIN_AGENTS);
        if (peers.size() < minimum) return new DecisionWindow(nowMs, Set.of());
        Character sample = AgentRuntimeIdentityRuntime.bot(peers.getFirst());
        AgentPerceptionSnapshot perception =
                CosmicAgentPerceptionSnapshotFactory.capture(sample, nowMs);
        Set<Integer> aliveMobIds = perception.mobs().stream()
                .filter(mob -> mob.alive() && mob.hp() > 0)
                .map(mob -> mob.objectId())
                .collect(Collectors.toUnmodifiableSet());
        Set<Integer> claimedMobIds = perception.agentPeers().stream()
                .filter(peer -> peer.grinding() && aliveMobIds.contains(peer.targetObjectId()))
                .map(peer -> peer.targetObjectId())
                .collect(Collectors.toUnmodifiableSet());
        int untargetedAgents = (int) perception.agentPeers().stream()
                .filter(peer -> peer.grinding() && peer.targetObjectId() < 0)
                .count();
        int unclaimedMobs = Math.max(0, aliveMobIds.size() - claimedMobIds.size());
        if (!shouldAllocateRest(
                peers.size(), aliveMobIds.size(), claimedMobIds.size(),
                untargetedAgents, unclaimedMobs)) {
            return new DecisionWindow(nowMs, Set.of());
        }
        int percentageSlots = Math.max(1, (int) Math.ceil(peers.size()
                * Math.max(1, Math.min(100, config.AgentYamlConfig.config.agent.AGENT_MAP_MAX_ACTIVE_COMBAT_PERCENT)) / 100.0));
        int activeSlots = Math.min(
                percentageSlots, Math.max(1, aliveMobIds.size()));
        int targetedAgents = (int) peers.stream()
                .filter(peer -> hasClaimedLiveTarget(peer, map, claimedMobIds))
                .count();
        int additionalActiveSlots = Math.max(0, activeSlots - targetedAgents);
        int effectiveAvoidanceScale = mobScarcityPercent(
                untargetedAgents, unclaimedMobs, MOB_SCARCITY_WEIGHT_PERCENT);
        Set<Integer> resting = peers.stream()
                .filter(peer -> AgentGrindTargetStateRuntime.activeTargetInMap(peer, map) == null)
                .skip(additionalActiveSlots)
                .filter(peer -> AgentBehaviorRuntime.calibration(peer)
                        .stablePercent("crowd-eligible", nowMs / ROTATION_MS)
                        < effectiveAvoidancePercent(
                        AgentBehaviorRuntime.policy(peer).crowd().avoidPercent(),
                        effectiveAvoidanceScale))
                .map(AgentRuntimeIdentityRuntime::bot)
                .filter(java.util.Objects::nonNull)
                .map(Character::getId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new DecisionWindow(nowMs, resting);
    }

    private static boolean hasClaimedLiveTarget(
            AgentRuntimeEntry entry, MapleMap map, Set<Integer> claimedMobIds) {
        var target = AgentGrindTargetStateRuntime.activeTargetInMap(entry, map);
        return target != null && claimedMobIds.contains(target.getObjectId());
    }

    static boolean shouldAllocateRest(
            int agentCount,
            int aliveMobCount,
            int claimedMobCount,
            int untargetedAgentCount,
            int unclaimedMobCount) {
        if (agentCount <= 1 || agentCount <= aliveMobCount) {
            return false;
        }
        if (aliveMobCount > 0 && claimedMobCount <= 0) {
            return false;
        }
        return unclaimedMobCount < Math.max(0, untargetedAgentCount);
    }

    static int mobScarcityPercent(
            int untargetedAgentCount,
            int unclaimedMobCount,
            int scarcityWeightPercent) {
        if (untargetedAgentCount <= 0 || unclaimedMobCount >= untargetedAgentCount) {
            return 0;
        }
        int shortage = Math.max(0, untargetedAgentCount - Math.max(0, unclaimedMobCount));
        int scarcity = shortage * 100 / untargetedAgentCount;
        return Math.clamp(
                scarcity * Math.clamp(scarcityWeightPercent, 0, 100) / 100,
                0,
                100);
    }

    static int effectiveAvoidancePercent(int profileAvoidancePercent, int scarcityPercent) {
        return Math.clamp(profileAvoidancePercent, 0, 100)
                * Math.clamp(scarcityPercent, 0, 100) / 100;
    }

    private static int priority(AgentRuntimeEntry entry, long nowMs) {
        int competitive = 100 - AgentBehaviorRuntime.policy(entry).crowd().avoidPercent();
        int drive = AgentBehaviorRuntime.adaptation(entry).combatDrive();
        int rotation = AgentBehaviorRuntime.calibration(entry)
                .stablePercent("crowd-rank", nowMs / ROTATION_MS) / 2;
        return competitive * 2 + drive + rotation;
    }

    private record DecisionWindow(long createdAtMs, Set<Integer> restingAgentIds) { }
}
