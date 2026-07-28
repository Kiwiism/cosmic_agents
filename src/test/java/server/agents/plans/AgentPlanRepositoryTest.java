package server.agents.plans;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentPlanRepositoryTest {
    @Test
    void indexContainsEveryExecutablePlanResourceExactlyOnce() throws Exception {
        Path planDirectory = Path.of("src/main/resources/agents/plans");
        Set<String> planFiles;
        try (var files = Files.list(planDirectory)) {
            planFiles = files
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".plan.json"))
                    .collect(Collectors.toSet());
        }

        JsonNode index = new ObjectMapper().readTree(
                planDirectory.resolve("index.json").toFile());
        List<String> indexedResources = new java.util.ArrayList<>();
        index.path("resources").forEach(node -> indexedResources.add(node.asText()));

        Set<String> indexedResourceSet = new java.util.HashSet<>(indexedResources);
        assertEquals(indexedResources.size(), indexedResourceSet.size(),
                "plan index must not contain duplicate resources");
        assertEquals(planFiles, indexedResourceSet,
                "every executable *.plan.json resource must be indexed");
        for (AgentPlanDefinition plan : AgentPlanRepository.defaultRepository().all()) {
            assertTrue(planFiles.contains(plan.planId() + ".plan.json"),
                    "plan filename must match its planId");
        }
    }

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
        AgentPlanStepExecutorRegistry registry =
                AgentPlanStepExecutorRegistry.defaultRegistry();

        for (AgentPlanDefinition plan : AgentPlanRepository.defaultRepository().all()) {
            for (AgentPlanDefinition.Step step : plan.steps()) {
                assertEquals(step.operation(), registry.require(step.operation()).operation());
            }
        }
    }
}
