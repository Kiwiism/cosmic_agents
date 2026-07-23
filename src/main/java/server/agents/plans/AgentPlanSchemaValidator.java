package server.agents.plans;

import java.util.HashSet;
import java.util.Set;

public final class AgentPlanSchemaValidator {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final Set<String> CONDITION_OPERATORS = Set.of(
            "present", "eq", "gte", "gte-input", "in", "between", "active", "all-succeeded");

    private AgentPlanSchemaValidator() {
    }

    public static void validate(AgentPlanDefinition plan) {
        require(plan != null, "plan is required");
        require(plan.schemaVersion() == CURRENT_SCHEMA_VERSION,
                "unsupported schemaVersion for " + id(plan));
        require(text(plan.planId()), "planId is required");
        require(text(plan.planVersion()), "planVersion is required for " + id(plan));
        require(text(plan.title()), "title is required for " + id(plan));
        require("executable".equals(plan.status()), "plan must be executable: " + id(plan));
        validateObjective(plan);
        require(!plan.steps().isEmpty(), "at least one step is required for " + id(plan));

        Set<String> stepIds = new HashSet<>();
        for (AgentPlanDefinition.Step step : plan.steps()) {
            require(step != null && text(step.stepId()), "stepId is required for " + id(plan));
            require(stepIds.add(step.stepId()), "duplicate stepId " + step.stepId() + " in " + id(plan));
            require(text(step.operation()), "operation is required for " + step.stepId());
            require(step.timeoutMs() >= 0L, "timeoutMs must be non-negative for " + step.stepId());
            require(step.retryBudget() >= 0, "retryBudget must be non-negative for " + step.stepId());
            require(step.capabilityIds().stream().allMatch(AgentPlanSchemaValidator::text),
                    "capabilityIds must be non-blank for " + step.stepId());
        }
        plan.entryCriteria().forEach(condition -> validateCondition(plan, condition));
        plan.exitCriteria().forEach(condition -> validateCondition(plan, condition));
        for (AgentPlanDefinition.Successor successor : plan.successors()) {
            require(successor != null && text(successor.planId()),
                    "successor planId is required for " + id(plan));
            require(!successor.planId().equals(plan.planId()),
                    "plan cannot succeed itself: " + id(plan));
            require(successor.on() != null && successor.activation() != null && successor.delayMs() >= 0L,
                    "valid successor policy is required for " + id(plan));
        }
    }

    private static void validateObjective(AgentPlanDefinition plan) {
        AgentPlanDefinition.ObjectivePolicy objective = plan.objective();
        require(objective != null, "objective policy is required for " + id(plan));
        require(text(objective.type()), "objective type is required for " + id(plan));
        require(objective.priority() >= 0, "objective priority must be non-negative for " + id(plan));
        require(objective.deadlineMs() >= 0L, "objective deadline must be non-negative for " + id(plan));
        require(objective.retryBudget() >= 0, "objective retryBudget must be non-negative for " + id(plan));
        require(objective.source() != null, "objective source is required for " + id(plan));
        require(text(objective.behaviorVersion()), "behaviorVersion is required for " + id(plan));
        require(objective.registration() != null, "objective registration is required for " + id(plan));
    }

    private static void validateCondition(AgentPlanDefinition plan, AgentPlanDefinition.Condition condition) {
        require(condition != null && text(condition.fact()) && text(condition.operator()),
                "conditions require fact and operator for " + id(plan));
        require(CONDITION_OPERATORS.contains(condition.operator()),
                "unsupported condition operator " + condition.operator() + " for " + id(plan));
        require(supportedFact(condition.fact()),
                "unsupported condition fact " + condition.fact() + " for " + id(plan));
    }

    private static String id(AgentPlanDefinition plan) {
        return plan == null || plan.planId() == null ? "<unknown>" : plan.planId();
    }

    private static boolean text(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean supportedFact(String fact) {
        return Set.of(
                "map.id", "region", "character.level", "character.firstJob",
                "career.bundle", "career.bundleId", "career.stage",
                "navigation.lithTownSide", "plan.steps").contains(fact)
                || fact.startsWith("input.")
                || fact.startsWith("quest.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AgentPlanValidationException(message);
        }
    }
}
