package server.agents.plans;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPlanRepositoryTest {
    @Test
    void catalogUsesOneStrictSchemaAndContainsTheIntendedProgressionChain() {
        AgentPlanRepository repository = AgentPlanRepository.defaultRepository();

        assertEquals(11, repository.all().size());
        assertTrue(repository.all().stream()
                .allMatch(plan -> plan.schemaVersion() == AgentPlanSchemaValidator.CURRENT_SCHEMA_VERSION));

        AgentPlanDefinition full = repository.require("maple-island-full-mvp");
        assertEquals(List.of("southperry-to-lith-harbor"),
                full.successors().stream().map(AgentPlanDefinition.Successor::planId).toList());

        Set<String> careers = repository.require("southperry-to-lith-harbor").successors().stream()
                .map(AgentPlanDefinition.Successor::planId)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(
                "victoria-warrior-level30",
                "victoria-bowman-level30",
                "victoria-magician-level30",
                "victoria-thief-level30",
                "victoria-pirate-level30"), careers);

        assertTrue(repository.all().stream()
                .flatMap(plan -> plan.successors().stream())
                .allMatch(successor ->
                        successor.activation() == AgentPlanDefinition.Activation.AVAILABLE));
        assertFalse(repository.all().stream()
                .flatMap(plan -> plan.successors().stream())
                .anyMatch(successor ->
                        successor.activation() == AgentPlanDefinition.Activation.AUTOMATIC));
    }

    @Test
    void everyOperationHasOneRegisteredExecutor() {
        AgentPlanStepExecutorRegistry registry = new AgentPlanStepExecutorRegistry(List.of(
                new AgentOrderedObjectivePlanStepExecutor(),
                new AgentSouthperryLithTransferStepExecutor(),
                new AgentFirstJobPlanStepExecutor(),
                new AgentVictoriaTrainingPlanStepExecutor()));

        for (AgentPlanDefinition plan : AgentPlanRepository.defaultRepository().all()) {
            for (AgentPlanDefinition.Step step : plan.steps()) {
                assertEquals(step.operation(), registry.require(step.operation()).operation());
            }
        }
    }
}
