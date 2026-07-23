package server.agents.plans;

import server.agents.objectives.AgentObjectiveSource;

import java.util.List;
import java.util.Map;

/**
 * Universal, versioned plan contract. Plan-specific behavior is expressed through registered
 * step operations and parameters; plans may not add private top-level schema fields.
 */
public record AgentPlanDefinition(
        int schemaVersion,
        String planId,
        String planVersion,
        String title,
        String status,
        ObjectivePolicy objective,
        List<Condition> entryCriteria,
        List<Step> steps,
        List<Condition> exitCriteria,
        List<Successor> successors) implements AgentPlan {

    public AgentPlanDefinition {
        entryCriteria = entryCriteria == null ? List.of() : List.copyOf(entryCriteria);
        steps = steps == null ? List.of() : List.copyOf(steps);
        exitCriteria = exitCriteria == null ? List.of() : List.copyOf(exitCriteria);
        successors = successors == null ? List.of() : List.copyOf(successors);
    }

    public record ObjectivePolicy(
            String type,
            int priority,
            long deadlineMs,
            int retryBudget,
            AgentObjectiveSource source,
            String behaviorVersion,
            Registration registration) {
    }

    public enum Registration {
        EXECUTOR,
        STEP
    }

    public record Condition(String fact, String operator, Object value) {
    }

    public record Step(
            String stepId,
            String operation,
            List<String> capabilityIds,
            Map<String, Object> parameters,
            long timeoutMs,
            int retryBudget) {
        public Step {
            capabilityIds = capabilityIds == null ? List.of() : List.copyOf(capabilityIds);
            parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        }
    }

    public record Successor(
            String planId,
            Outcome on,
            Activation activation,
            long delayMs) {
    }

    public enum Outcome {
        SUCCEEDED,
        BLOCKED,
        FAILED,
        CANCELLED
    }

    public enum Activation {
        AUTOMATIC,
        AVAILABLE,
        NONE
    }
}
