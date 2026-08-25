package server.agents.plans;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import server.agents.capabilities.build.profiles.AgentApBuildProfileRepository;
import server.agents.capabilities.build.profiles.AgentSpBuildProfileRepository;
import server.agents.progression.AgentCareerBuildBundle;
import server.agents.progression.AgentCareerBuildBundleRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        assertEquals(14, repository.all().size());
        assertTrue(repository.all().stream()
                .allMatch(plan -> plan.schemaVersion() == AgentPlanSchemaValidator.CURRENT_SCHEMA_VERSION));

        AgentPlanDefinition full = repository.require("maple-island-full-mvp");
        assertEquals(List.of("southperry-to-lith-harbor"),
                full.successors().stream().map(AgentPlanDefinition.Successor::planId).toList());

        AgentPlanDefinition individual = repository.require("victoria-individual-quest");
        assertTrue(individual.exitCriteria().stream().anyMatch(condition ->
                condition.fact().equals("quest.requested")
                        && condition.operator().equals("completed")));
        assertEquals("second-job-advancement",
                repository.require("victoria-second-job").steps().getFirst().operation());
        assertEquals(List.of("mushroom-kingdom-questline"),
                repository.require("victoria-second-job").successors().stream()
                        .map(AgentPlanDefinition.Successor::planId).toList());
        assertEquals(AgentPlanDefinition.Activation.AUTOMATIC,
                repository.require("victoria-second-job").successors().getFirst().activation());
        AgentPlanDefinition mushroom = repository.require("mushroom-kingdom-questline");
        assertEquals("mushroom-kingdom-questline", mushroom.steps().getFirst().operation());
        assertTrue(mushroom.exitCriteria().stream().anyMatch(condition ->
                condition.fact().equals("quest.2336")
                        && condition.operator().equals("completed")));

        Set<String> careers = repository.require("southperry-to-lith-harbor").successors().stream()
                .map(AgentPlanDefinition.Successor::planId)
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of(
                "victoria-warrior-level30",
                "victoria-bowman-level30",
                "victoria-magician-level30",
                "victoria-thief-level30",
                "victoria-pirate-level30"), careers);

        assertEquals(1L, repository.all().stream()
                .flatMap(plan -> plan.successors().stream())
                .filter(successor ->
                        successor.activation() == AgentPlanDefinition.Activation.AUTOMATIC)
                .count());
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

    @Test
    void everyExplorerCareerPlanUsesTheSameUniversalContractAndResolvableBuildProfiles() {
        AgentPlanRepository plans = AgentPlanRepository.defaultRepository();
        AgentCareerBuildBundleRepository bundles = AgentCareerBuildBundleRepository.defaultRepository();
        AgentApBuildProfileRepository apProfiles = AgentApBuildProfileRepository.defaultRepository();
        AgentSpBuildProfileRepository spProfiles = AgentSpBuildProfileRepository.defaultRepository();
        List<String> careerPlanIds = List.of(
                "victoria-warrior-level30",
                "victoria-bowman-level30",
                "victoria-magician-level30",
                "victoria-thief-level30",
                "victoria-pirate-level30");

        Set<String> commonOperations = null;
        Set<String> coveredCareers = new java.util.HashSet<>();
        for (String planId : careerPlanIds) {
            AgentPlanDefinition plan = plans.require(planId);
            assertEquals(AgentPlanDefinition.Registration.STEP, plan.objective().registration());
            assertEquals("progression.career-level30", plan.objective().type());
            assertTrue(plan.exitCriteria().stream().anyMatch(condition ->
                    condition.fact().equals("character.level")
                            && condition.operator().equals("gte")
                            && Integer.valueOf(30).equals(condition.value())));

            Set<String> operations = plan.steps().stream()
                    .map(AgentPlanDefinition.Step::operation)
                    .collect(Collectors.toSet());
            if (commonOperations == null) {
                commonOperations = operations;
            } else {
                assertEquals(commonOperations, operations,
                        "all Explorer career plans must pass through the same executor operations");
            }

            AgentPlanDefinition.Condition bundleCriterion = plan.entryCriteria().stream()
                    .filter(condition -> condition.fact().equals("career.bundleId"))
                    .findFirst()
                    .orElseThrow();
            @SuppressWarnings("unchecked")
            List<String> bundleIds = (List<String>) bundleCriterion.value();
            for (String bundleId : bundleIds) {
                AgentCareerBuildBundle bundle = bundles.find(bundleId).orElseThrow();
                assertTrue(apProfiles.find(bundle.apProfileId()).isPresent(),
                        () -> "missing AP profile for " + bundleId);
                assertTrue(spProfiles.find(bundle.spProfileId()).isPresent(),
                        () -> "missing SP profile for " + bundleId);
                assertEquals(30, spProfiles.find(bundle.spProfileId()).orElseThrow().supportedThroughLevel());
                coveredCareers.add(bundle.career());
            }
        }

        assertEquals(Set.of("warrior", "bowman", "magician", "thief-claw", "thief-dagger",
                "pirate-gun", "pirate-knuckle"), coveredCareers);
        assertEquals(Set.of("staged-first-job-journey", "victoria-training"), commonOperations);
    }
}
