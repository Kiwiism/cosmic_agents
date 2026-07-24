package server.agents.capabilities.behavior;

import client.Character;
import config.YamlConfig;
import server.agents.behavior.AgentBehaviorRuntime;
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
        Set<Integer> resting = peers.stream().skip(activeSlots)
                .filter(peer -> AgentBehaviorRuntime.calibration(peer)
                        .stablePercent("crowd-eligible", nowMs / ROTATION_MS)
                        < AgentBehaviorRuntime.policy(peer).crowd().avoidPercent())
                .map(AgentRuntimeIdentityRuntime::bot)
                .filter(java.util.Objects::nonNull)
                .map(Character::getId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new DecisionWindow(nowMs, resting);
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

    private static int priority(AgentRuntimeEntry entry, long nowMs) {
        int competitive = 100 - AgentBehaviorRuntime.policy(entry).crowd().avoidPercent();
        int drive = AgentBehaviorRuntime.adaptation(entry).combatDrive();
        int rotation = AgentBehaviorRuntime.calibration(entry)
                .stablePercent("crowd-rank", nowMs / ROTATION_MS) / 2;
        return competitive * 2 + drive + rotation;
    }

    private record DecisionWindow(long createdAtMs, Set<Integer> restingAgentIds) { }
}
