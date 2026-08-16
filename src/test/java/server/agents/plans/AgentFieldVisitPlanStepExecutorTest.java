package server.agents.plans;

import org.junit.jupiter.api.Test;
import server.agents.objectives.AgentObjectiveSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentFieldVisitPlanStepExecutorTest {
    private final AgentFieldVisitPlanStepExecutor executor = new AgentFieldVisitPlanStepExecutor();

    @Test
    void acceptsBoundedFreeOrObjectiveFieldStep() {
        AgentPlanDefinition plan = plan(Map.of(
                "durationMs", 120_000L,
                "fieldMapId", 100000000,
                "mobIds", List.of(100100, 100101),
                "killsPerMob", 12,
                "narrationLevel", "verbose"));

        assertDoesNotThrow(() -> executor.validateDefinition(plan, plan.steps().getFirst()));
        assertTrue(AgentPlanStepExecutorRegistry.defaultRegistry()
                .find(AgentFieldVisitPlanStepExecutor.OPERATION).isPresent());
    }

    @Test
    void rejectsUnboundedOrInvalidTargets() {
        AgentPlanDefinition missingDuration = plan(Map.of("mobIds", List.of(100100)));
        AgentPlanDefinition invalidMob = plan(Map.of(
                "durationMs", 1_000L, "mobIds", List.of(-1)));

        assertThrows(AgentPlanValidationException.class, () -> executor.validateDefinition(
                missingDuration, missingDuration.steps().getFirst()));
        assertThrows(AgentPlanValidationException.class, () -> executor.validateDefinition(
                invalidMob, invalidMob.steps().getFirst()));
    }

    private static AgentPlanDefinition plan(Map<String, Object> parameters) {
        AgentPlanDefinition.Step step = new AgentPlanDefinition.Step(
                "field-break", AgentFieldVisitPlanStepExecutor.OPERATION,
                List.of("field.local"), parameters, 180_000L, 0);
        return new AgentPlanDefinition(
                1, "field-test-plan", "1", "Field test plan", "executable",
                new AgentPlanDefinition.ObjectivePolicy(
                        "test", 1, 0L, 0, AgentObjectiveSource.QUEST_PLAN,
                        "field-test-v1", AgentPlanDefinition.Registration.STEP),
                List.of(), List.of(step), List.of(), List.of());
    }
}
