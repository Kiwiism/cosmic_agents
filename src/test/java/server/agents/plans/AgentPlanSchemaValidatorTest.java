package server.agents.plans;

import org.junit.jupiter.api.Test;
import server.agents.objectives.AgentObjectiveSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentPlanSchemaValidatorTest {
    @Test
    void rejectsStepsWithoutDeclaredCapabilities() {
        AgentPlanDefinition plan = plan(
                List.of(step(List.of())),
                List.of());

        assertThrows(AgentPlanValidationException.class,
                () -> AgentPlanSchemaValidator.validate(plan));
    }

    @Test
    void rejectsDuplicateCapabilitiesWithinAStep() {
        AgentPlanDefinition plan = plan(
                List.of(step(List.of("combat", "combat"))),
                List.of());

        assertThrows(AgentPlanValidationException.class,
                () -> AgentPlanSchemaValidator.validate(plan));
    }

    @Test
    void rejectsAmbiguousAutomaticSuccessors() {
        AgentPlanDefinition plan = plan(
                List.of(step(List.of("combat"))),
                List.of(
                        successor("next-a", AgentPlanDefinition.Activation.AUTOMATIC),
                        successor("next-b", AgentPlanDefinition.Activation.AUTOMATIC)));

        assertThrows(AgentPlanValidationException.class,
                () -> AgentPlanSchemaValidator.validate(plan));
    }

    private static AgentPlanDefinition plan(
            List<AgentPlanDefinition.Step> steps,
            List<AgentPlanDefinition.Successor> successors) {
        return new AgentPlanDefinition(
                AgentPlanSchemaValidator.CURRENT_SCHEMA_VERSION,
                "test-plan",
                "1",
                "Test plan",
                "executable",
                new AgentPlanDefinition.ObjectivePolicy(
                        "test", 1, 1_000L, 1, AgentObjectiveSource.QUEST_PLAN,
                        "test-v1", AgentPlanDefinition.Registration.EXECUTOR),
                List.of(),
                steps,
                List.of(),
                successors);
    }

    private static AgentPlanDefinition.Step step(List<String> capabilities) {
        return new AgentPlanDefinition.Step(
                "step", "test-operation", capabilities, Map.of(), 0L, 0);
    }

    private static AgentPlanDefinition.Successor successor(
            String planId, AgentPlanDefinition.Activation activation) {
        return new AgentPlanDefinition.Successor(
                planId, AgentPlanDefinition.Outcome.SUCCEEDED, activation, 0L);
    }
}
