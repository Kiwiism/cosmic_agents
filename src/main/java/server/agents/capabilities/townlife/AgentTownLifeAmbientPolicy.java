package server.agents.capabilities.townlife;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Immutable ambient-session tuning installed by an external population owner. */
public record AgentTownLifeAmbientPolicy(
        Map<AgentTownLifeState.Activity, ActivityRule> activities,
        TransitionWeights transitions,
        List<Integer> chairItemIds) {

    public AgentTownLifeAmbientPolicy {
        EnumMap<AgentTownLifeState.Activity, ActivityRule> copy =
                new EnumMap<>(AgentTownLifeState.Activity.class);
        if (activities != null) {
            copy.putAll(activities);
        }
        activities = Map.copyOf(copy);
        transitions = transitions == null ? TransitionWeights.standard() : transitions;
        chairItemIds = List.copyOf(chairItemIds == null ? List.of() : chairItemIds);
        if (activities.isEmpty()) {
            throw new IllegalArgumentException("ambient TownLife requires activity rules");
        }
    }

    public record ActivityRule(int targetPercent, int hardMax,
                               long minimumDwellMs, long maximumDwellMs) {
        public ActivityRule {
            if (targetPercent < 0 || targetPercent > 100 || hardMax <= 0
                    || minimumDwellMs <= 0L || maximumDwellMs < minimumDwellMs) {
                throw new IllegalArgumentException("invalid ambient TownLife activity rule");
            }
        }
    }

    public record TransitionWeights(int continueInPlace, int relocateSameActivity,
                                    int switchActivity, int requestExit) {
        public TransitionWeights {
            if (continueInPlace < 0 || relocateSameActivity < 0 || switchActivity < 0
                    || requestExit < 0
                    || continueInPlace + relocateSameActivity + switchActivity + requestExit <= 0) {
                throw new IllegalArgumentException("invalid ambient TownLife transition weights");
            }
        }

        static TransitionWeights standard() {
            return new TransitionWeights(30, 20, 45, 5);
        }

        int total() {
            return continueInPlace + relocateSameActivity + switchActivity + requestExit;
        }
    }
}
