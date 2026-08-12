package server.agents.capabilities.combat;

import client.Character;
import config.YamlConfig;
import server.agents.behavior.AgentBehaviorCalibrationState;
import server.agents.behavior.AgentBehaviorPolicyProfile;
import server.agents.behavior.AgentBehaviorRuntime;
import server.agents.behavior.AgentBehaviorFeatureProfile;
import server.agents.capabilities.behavior.AgentCrowdScalingPolicy;
import server.agents.integration.cosmic.CosmicAgentPerceptionSnapshotFactory;
import server.agents.model.AgentPosition;
import server.agents.perception.AgentPerceptionSnapshot;
import server.agents.runtime.AgentRuntimeEntry;
import server.life.Monster;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import server.agents.capabilities.behavior.AgentBehaviorTelemetry;

/** Combat adapter for response latency, claim tolerance, and profile target diversity. */
public final class AgentCombatBehaviorRuntime {
    private static final int AVAILABLE_MOB_RESPONSE_MAX_MS = config.AgentTuning.intValue(
            "server.agents.capabilities.combat.AgentCombatBehaviorRuntime.AVAILABLE_MOB_RESPONSE_MAX_MS");

    private AgentCombatBehaviorRuntime() {
    }

    public static boolean responseReady(
            AgentRuntimeEntry entry, Character agent, List<Monster> candidates, long nowMs) {
        if (!AgentBehaviorRuntime.enabled(entry) || !AgentBehaviorFeatureProfile.current().enabled()) return true;
        if (candidates == null || candidates.isEmpty()) {
            entry.capabilityStates().require(AgentCombatBehaviorState.STATE_KEY).clearStimulus();
            return false;
        }
        entry.capabilityStates().require(AgentCombatBehaviorState.STATE_KEY).markCandidateOpportunity();
        long stimulus = candidates.stream().mapToLong(Monster::getObjectId).sorted()
                .reduce(candidates.size(), (left, right) -> left * 31L + right);
        AgentBehaviorCalibrationState calibration = AgentBehaviorRuntime.calibration(entry);
        AgentPerceptionSnapshot perception =
                CosmicAgentPerceptionSnapshotFactory.capture(agent, nowMs);
        int jitterPercent = calibration.stablePercent("response", stimulus) / 5;
        int responseDelayMs = AgentCrowdScalingPolicy.responseDelayMs(
                calibration.responseBaselineMs(),
                jitterPercent,
                AgentCrowdScalingPolicy.totalCharacters(perception));
        responseDelayMs = Math.min(responseDelayMs, AVAILABLE_MOB_RESPONSE_MAX_MS);
        boolean ready = entry.capabilityStates().require(AgentCombatBehaviorState.STATE_KEY)
                .ready(stimulus, nowMs, responseDelayMs);
        if (!ready) AgentBehaviorTelemetry.responseDeferred();
        return ready;
    }

    public static List<Monster> respectClaims(AgentRuntimeEntry entry,
                                               List<Monster> candidates,
                                               Map<Monster, Integer> occupancy) {
        if (!AgentBehaviorRuntime.enabled(entry) || !AgentBehaviorFeatureProfile.current().enabled()
                || candidates.size() < 2) return candidates;
        AgentBehaviorPolicyProfile policy = AgentBehaviorRuntime.policy(entry);
        int driveBonus = AgentBehaviorRuntime.adaptation(entry).combatDrive() >= 75 ? 1 : 0;
        int tolerance = policy.targeting().claimTolerance() + driveBonus;
        List<Monster> available = candidates.stream()
                .filter(candidate -> occupancy.getOrDefault(candidate, 0) < tolerance)
                .toList();
        if (!available.isEmpty() && available.size() < candidates.size()
                && AgentBehaviorRuntime.calibration(entry).nextPercent("claim-avoid")
                < policy.targeting().claimAvoidPercent()) {
            AgentBehaviorTelemetry.claimAlternative();
            return new ArrayList<>(available);
        }
        return candidates;
    }

    public static int selectTargetIndex(
            AgentRuntimeEntry entry, Character agent, int candidateCount) {
        if (!AgentBehaviorRuntime.enabled(entry) || !AgentBehaviorFeatureProfile.current().enabled()
                || candidateCount < 2) return -1;
        AgentPerceptionSnapshot perception =
                CosmicAgentPerceptionSnapshotFactory.capture(agent, System.currentTimeMillis());
        AgentPosition position = agent == null || agent.getPosition() == null
                ? null : new AgentPosition(agent.getPosition().x, agent.getPosition().y);
        int variationPercent = AgentCrowdScalingPolicy.targetVariationPercent(
                AgentCrowdScalingPolicy.localCharacters(perception, position));
        if (variationPercent <= 0) {
            return 0;
        }
        AgentBehaviorPolicyProfile.Targeting policy = AgentBehaviorRuntime.policy(entry).targeting();
        int nearWeight = policy.nearWeight() * variationPercent / 100;
        int middleWeight = policy.middleWeight() * variationPercent / 100;
        int bestWeight = policy.bestWeight()
                + policy.nearWeight() - nearWeight
                + policy.middleWeight() - middleWeight;
        int total = bestWeight + nearWeight + middleWeight;
        int roll = AgentBehaviorRuntime.calibration(entry).nextPercent("target") * total / 100;
        if (roll < bestWeight) return 0;
        if (roll < bestWeight + nearWeight) {
            return Math.min(candidateCount - 1, 1 + candidateCount / 4);
        }
        return Math.min(candidateCount - 1, candidateCount / 2);
    }

    public static boolean anchorRole(AgentRuntimeEntry entry, Character agent) {
        if (!AgentBehaviorRuntime.enabled(entry)
                || !AgentBehaviorFeatureProfile.current().enabled()) {
            return false;
        }
        AgentPerceptionSnapshot perception =
                CosmicAgentPerceptionSnapshotFactory.capture(agent, System.currentTimeMillis());
        AgentPosition position = agent == null || agent.getPosition() == null
                ? null : new AgentPosition(agent.getPosition().x, agent.getPosition().y);
        int effectivePercent = AgentCrowdScalingPolicy.anchorPercent(
                AgentBehaviorRuntime.policy(entry).targeting().anchorPercent(),
                AgentCrowdScalingPolicy.localCharacters(perception, position));
        return AgentBehaviorRuntime.calibration(entry).stablePercent("anchor", 0L)
                < effectivePercent;
    }

    public static boolean waitingForResponse(AgentRuntimeEntry entry) {
        return entry != null && entry.capabilityStates().find(AgentCombatBehaviorState.STATE_KEY)
                .map(AgentCombatBehaviorState::responseDeferred).orElse(false);
    }

    public static boolean candidateOpportunity(AgentRuntimeEntry entry) {
        return entry != null && entry.capabilityStates().find(AgentCombatBehaviorState.STATE_KEY)
                .map(AgentCombatBehaviorState::candidateOpportunity).orElse(false);
    }

    public static void noCandidateOpportunity(AgentRuntimeEntry entry) {
        if (entry != null) entry.capabilityStates().find(AgentCombatBehaviorState.STATE_KEY)
                .ifPresent(AgentCombatBehaviorState::clearStimulus);
    }

    public static void targetAcquired(AgentRuntimeEntry entry) {
        if (entry != null) entry.capabilityStates().find(AgentCombatBehaviorState.STATE_KEY)
                .ifPresent(AgentCombatBehaviorState::clearStimulus);
    }
}
