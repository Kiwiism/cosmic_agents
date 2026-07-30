package server.agents.capabilities.combat;

import java.util.List;
import java.util.function.Predicate;

/**
 * Chooses a candidate tier only. Existing navigation and combat scoring choose
 * the concrete target inside the tier.
 */
public final class AgentQuestLocalClearTargetPolicy {
    private AgentQuestLocalClearTargetPolicy() {
    }

    public record Selection<T>(List<T> candidates,
                               AgentCombatCandidateClass candidateClass,
                               AgentCombatDecisionReason reason) {
        public Selection {
            candidates = List.copyOf(candidates);
        }
    }

    public static <T> Selection<T> select(List<T> candidates,
                                          Predicate<T> required,
                                          Predicate<T> local,
                                          boolean allowLocalSweep) {
        List<T> requiredLocal = candidates.stream()
                .filter(required).filter(local).toList();
        if (!requiredLocal.isEmpty()) {
            return new Selection<>(requiredLocal, AgentCombatCandidateClass.REQUIRED,
                    AgentCombatDecisionReason.REQUIRED_LOCAL);
        }

        List<T> incidentalLocal = candidates.stream()
                .filter(required.negate()).filter(local).toList();
        if (allowLocalSweep && !incidentalLocal.isEmpty()) {
            return new Selection<>(incidentalLocal, AgentCombatCandidateClass.INCIDENTAL,
                    AgentCombatDecisionReason.INCIDENTAL_PLATFORM_SWEEP);
        }

        List<T> requiredAnywhere = candidates.stream().filter(required).toList();
        if (!requiredAnywhere.isEmpty()) {
            return new Selection<>(requiredAnywhere, AgentCombatCandidateClass.REQUIRED,
                    AgentCombatDecisionReason.REQUIRED_DEBT);
        }

        if (!candidates.isEmpty()) {
            return new Selection<>(candidates, AgentCombatCandidateClass.INCIDENTAL,
                    AgentCombatDecisionReason.INCIDENTAL_NO_REQUIRED_AVAILABLE);
        }
        return new Selection<>(List.of(), AgentCombatCandidateClass.UNRELATED,
                AgentCombatDecisionReason.CLOSEST_REACHABLE_FALLBACK);
    }
}
