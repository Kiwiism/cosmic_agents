package server.agents.field;

import java.util.Comparator;
import java.util.List;

/** Pure selection policy for lending an occupied station to a temporary quest visitor. */
public final class AgentFieldPreemptionPolicy {
    private AgentFieldPreemptionPolicy() {
    }

    public static Selection select(
            Request request,
            List<Candidate> candidates,
            Policy policy) {
        if (request == null || candidates == null || policy == null || !policy.enabled()) {
            return Selection.rejected("preemption disabled");
        }
        return candidates.stream()
                .filter(candidate -> candidate.objectiveCoverage() > 0)
                .filter(candidate -> candidate.intentType() == AgentFieldIntent.Type.FREE_GRIND
                        || candidate.intentType() == AgentFieldIntent.Type.PARTY_COVERAGE)
                .filter(candidate -> !candidate.busy())
                .filter(candidate -> candidate.leaseAgeMs() >= policy.minimumLeaseAgeMs())
                .filter(candidate -> candidate.cooldownRemainingMs() == 0L)
                .filter(candidate -> !candidate.playerOccupied())
                .map(candidate -> new Ranked(candidate, displacementScore(candidate, policy)))
                .filter(ranked -> ranked.score() <= policy.maximumDisplacementScore())
                .min(Comparator.comparingLong(Ranked::score)
                        .thenComparingInt(ranked -> ranked.candidate().agentId()))
                .map(ranked -> new Selection(true, ranked.candidate().agentId(),
                        ranked.score(), ranked.candidate().stationId(),
                        "quest visitor borrowed the lowest-cost station; incumbent paused at safe rest"))
                .orElseGet(() -> Selection.rejected("no eligible incumbent under displacement threshold"));
    }

    private static long displacementScore(Candidate candidate, Policy policy) {
        return (long) candidate.liveRelevantPopulation() * policy.livePopulationWeight()
                + candidate.visitorDistancePx()
                + (candidate.replacementAvailable() ? 0L : policy.safeRestPenalty());
    }

    public record Request(int visitorAgentId, String objectiveId, long requestedAtMs) {
        public Request {
            if (visitorAgentId <= 0 || objectiveId == null || requestedAtMs < 0L) {
                throw new IllegalArgumentException("valid quest visitor preemption request is required");
            }
        }
    }

    public record Candidate(
            int agentId,
            AgentFieldIntent.Type intentType,
            String stationId,
            int objectiveCoverage,
            int liveRelevantPopulation,
            int visitorDistancePx,
            long leaseAgeMs,
            long cooldownRemainingMs,
            boolean busy,
            boolean playerOccupied,
            boolean replacementAvailable) {
        public Candidate {
            if (agentId <= 0 || intentType == null || stationId == null || stationId.isBlank()
                    || objectiveCoverage < 0 || liveRelevantPopulation < 0
                    || visitorDistancePx < 0 || leaseAgeMs < 0L || cooldownRemainingMs < 0L) {
                throw new IllegalArgumentException("valid incumbent evidence is required");
            }
        }
    }

    public record Policy(
            boolean enabled,
            long minimumLeaseAgeMs,
            int livePopulationWeight,
            int safeRestPenalty,
            long maximumDisplacementScore) {
        public Policy {
            if (minimumLeaseAgeMs < 0L || livePopulationWeight < 0 || safeRestPenalty < 0
                    || maximumDisplacementScore < 0L) {
                throw new IllegalArgumentException("valid preemption policy limits are required");
            }
        }
    }

    public record Selection(
            boolean approved,
            int incumbentAgentId,
            long score,
            String stationId,
            String reason) {
        public Selection {
            stationId = stationId == null ? "" : stationId;
            reason = reason == null ? "" : reason;
            if (score < 0L || approved && (incumbentAgentId <= 0 || stationId.isBlank())) {
                throw new IllegalArgumentException("valid preemption selection is required");
            }
        }

        static Selection rejected(String reason) {
            return new Selection(false, 0, 0L, "", reason);
        }
    }

    private record Ranked(Candidate candidate, long score) {
    }
}
