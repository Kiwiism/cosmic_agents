package server.agents.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentArchitectureBoundaryTest {
    private static final Path AGENTS = Path.of("src", "main", "java", "server", "agents");
    private static final Pattern CAPABILITY_IMPORT = Pattern.compile(
            "^import server\\.agents\\.capabilities\\.([^.]+)\\.", Pattern.MULTILINE);
    private static final Pattern PLAN_IMPORT = Pattern.compile(
            "^import server\\.agents\\.plans\\.", Pattern.MULTILINE);

    @Test
    void pureContractsDoNotDependOnCosmicRuntimeObjects() throws IOException {
        List<Path> roots = List.of(
                AGENTS.resolve("model"),
                AGENTS.resolve("capabilities").resolve("contracts"),
                AGENTS.resolve("policy").resolve("behavior"),
                AGENTS.resolve("profiles"));
        for (Path root : roots) {
            try (var files = Files.walk(root)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(file);
                    assertFalse(source.matches("(?s).*import (client|server\\.maps|server\\.life|net\\.server|tools\\.packet)\\..*"),
                            () -> file + " leaks a mutable Cosmic runtime type into a pure contract");
                }
            }
        }
    }

    @Test
    void behaviorProfilesDoNotOwnCapabilityImplementations() throws IOException {
        assertTreeExcludes(
                AGENTS.resolve("profiles"),
                List.of(
                        "import server.agents.capabilities.",
                        "import server.agents.plans.",
                        "import server.agents.progression.",
                        "import server.agents.integration.cosmic."),
                "behavior profiles must remain declarative inputs to policy adapters");
    }

    @Test
    void genericTownLifeCoreDoesNotOwnProgressionOrPlanImplementations() throws IOException {
        List<Path> genericCore = List.of(
                AGENTS.resolve("capabilities").resolve("townlife")
                        .resolve("AgentTownLifeRuntime.java"),
                AGENTS.resolve("capabilities").resolve("townlife")
                        .resolve("AgentTownLifeArrivalExtensionRepository.java"),
                AGENTS.resolve("capabilities").resolve("townlife")
                        .resolve("AgentTownLifeController.java"));
        for (Path file : genericCore) {
            if (!Files.exists(file)) {
                continue;
            }
            String source = Files.readString(file);
            assertFalse(source.contains("import server.agents.progression."),
                    () -> file + " must request routing and lifecycle through neutral contracts");
            assertFalse(source.contains("import server.agents.plans."),
                    () -> file + " must not own a progression plan implementation");
        }
    }

    @Test
    void foregroundPauseContractIsRuntimeOwned() {
        assertTrue(Files.exists(AGENTS.resolve("runtime").resolve("AgentForegroundPauseRuntime.java")));
        assertFalse(Files.exists(AGENTS.resolve("plans").resolve("AgentPlanPauseRuntime.java")));
    }

    @Test
    void orchestrationAndLlmGatewayDoNotPerformCosmicMutations() throws IOException {
        List<Path> roots = List.of(
                AGENTS.resolve("plans"),
                AGENTS.resolve("policy"),
                AGENTS.resolve("personality"),
                AGENTS.resolve("runtime").resolve("decision"),
                AGENTS.resolve("runtime").resolve("autonomy"),
                AGENTS.resolve("memory"),
                AGENTS.resolve("coordination").resolve("session"),
                AGENTS.resolve("capabilities").resolve("dialogue").resolve("llm")
                        .resolve("gateway"));
        List<String> directMutations = List.of(
                ".setPosition(",
                ".changeMap(",
                ".gainItem(",
                ".setHp(",
                ".setMp(",
                ".setJob(",
                ".setLevel(",
                ".addItem(",
                ".removeItem(",
                ".updateSingleStat(");
        for (Path root : roots) {
            if (Files.exists(root)) {
                assertTreeExcludes(root, directMutations,
                        "orchestration must issue capability commands instead of mutating Cosmic state");
            }
        }
    }

    @Test
    void autonomyKernelCannotCaptureOrMutateCosmicStateDirectly() throws IOException {
        assertTreeExcludes(
                AGENTS.resolve("runtime").resolve("autonomy"),
                List.of(
                        "import client.",
                        "import server.maps.",
                        "import server.life.",
                        "import server.agents.integration.cosmic."),
                "the autonomy kernel consumes immutable snapshots captured by the Cosmic adapter");
    }

    @Test
    void featureSpecificTopLevelObjectiveAuthoritiesCannotIncrease() throws IOException {
        Set<String> migrationAllowlist = Set.of(
                "src/main/java/server/agents/plans/AgentPlanExecutor.java",
                "src/main/java/server/agents/plans/mapleisland/AgentMapleIslandPlanRuntime.java",
                "src/main/java/server/agents/progression/AgentCareerObjectiveRuntime.java",
                "src/main/java/server/agents/progression/AgentVictoriaTrainingObjectiveRuntime.java",
                "src/main/java/server/agents/capabilities/supplies/AgentSupplyProcurementRuntime.java",
                "src/main/java/server/agents/runtime/maintenance/AgentRemediationCoordinator.java");
        Set<String> actual = new HashSet<>();
        try (var files = Files.walk(AGENTS)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (Files.readString(file).contains("AgentObjectiveKernel.start(")) {
                    actual.add(file.toString().replace('\\', '/'));
                }
            }
        }
        assertTrue(migrationAllowlist.containsAll(actual),
                () -> "new top-level objective authority bypasses the autonomy migration: "
                        + actual.stream().filter(path -> !migrationAllowlist.contains(path)).toList());
    }

    @Test
    void readOnlyLlmGatewayCannotSeeMutableRuntimeTypes() throws IOException {
        assertTreeExcludes(
                AGENTS.resolve("capabilities").resolve("dialogue").resolve("llm")
                        .resolve("gateway"),
                List.of(
                        "import client.",
                        "import server.maps.",
                        "import server.life.",
                        "import server.agents.runtime.AgentRuntimeEntry",
                        "import server.agents.capabilities.runtime.AgentCapabilityRuntime"),
                "dialogue-only model providers receive immutable text, never mutation handles");
    }

    @Test
    void highestRiskConcreteCapabilityDependenciesCannotIncrease() throws IOException {
        Map<String, Integer> ceilings = Map.ofEntries(
                // Reviewed after teleport/Flash Jump execution landed in 3be10ae349. Navigation
                // currently composes movement primitives directly; freeze that accepted debt
                // until a traversal port can replace the concrete imports.
                Map.entry("navigation->movement", 93),
                Map.entry("movement->navigation", 41),
                Map.entry("combat->movement", 61),
                // The pre-reliability combat baseline contains fifteen navigation imports.
                // Keep route validation behind the existing path-service seam so it adds none.
                Map.entry("combat->navigation", 15),
                Map.entry("trade->inventory", 56),
                Map.entry("trade->dialogue", 25),
                Map.entry("supplies->dialogue", 10),
                Map.entry("supplies->combat", 9));
        Map<String, Integer> actual = dependencyCounts();
        ceilings.forEach((edge, ceiling) -> assertTrue(actual.getOrDefault(edge, 0) <= ceiling,
                () -> edge + " concrete imports increased above migration ceiling " + ceiling));
    }

    @Test
    void capabilityToPlanCompatibilityDependenciesCannotIncrease() throws IOException {
        int[] imports = {0};
        Path capabilities = AGENTS.resolve("capabilities");
        try (var files = Files.walk(capabilities)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                Matcher matcher = PLAN_IMPORT.matcher(Files.readString(file));
                while (matcher.find()) {
                    imports[0]++;
                }
            }
        }
        assertTrue(imports[0] <= 19,
                () -> "capabilities must not acquire new dependencies on plan implementations; "
                        + "current compatibility import count is " + imports[0]);
    }

    @Test
    void combatDecisionAndRangedTacticalStateRemainCapabilityOwned() throws IOException {
        String runtimeEntry = Files.readString(AGENTS.resolve("runtime")
                .resolve("AgentRuntimeEntry.java"));
        assertFalse(runtimeEntry.contains("AgentCombatDecisionState"),
                "combat decision frames belong in the capability-state registry");
        assertFalse(runtimeEntry.contains("AgentRangedTacticalState"),
                "ranged tactical commitments belong in the capability-state registry");

        String rangedEngagement = Files.readString(AGENTS.resolve("capabilities")
                .resolve("combat").resolve("AgentGrindRangedEngagementService.java"));
        assertFalse(rangedEngagement.contains("prevCooldown"),
                "attack success must come from AgentAttackTransactionResult, not cooldown mutation");
        assertTrue(rangedEngagement.contains("attackResult.committed()"));
    }

    @Test
    void townLifePolicyAndEncounterMutationRemainTownScoped() throws IOException {
        String controller = Files.readString(AGENTS.resolve("capabilities")
                .resolve("townlife").resolve("AgentTownLifeControllerRuntime.java"));
        String encounters = Files.readString(AGENTS.resolve("capabilities")
                .resolve("townlife").resolve("AgentTownLifeEncounterCoordinator.java"));
        assertTrue(controller.contains("AgentTownLifeControllerRegistry"));
        assertFalse(controller.contains("static volatile AgentTownLifeController"),
                "one town's optional controller must not replace every town's policy");
        assertTrue(encounters.contains("AgentTownLifeScopeLocks"));
        assertFalse(encounters.contains("private static final Object LOCK"),
                "unrelated towns must not serialize every encounter mutation");
    }

    @Test
    void navigationBuildDiagnosticsRemainOutsideGraphConstructionService() throws IOException {
        String graphService = Files.readString(AGENTS.resolve("capabilities")
                .resolve("navigation").resolve("AgentNavigationGraphService.java"));
        assertTrue(Files.exists(AGENTS.resolve("capabilities").resolve("navigation")
                .resolve("AgentNavigationGraphBuildProfile.java")));
        assertFalse(graphService.contains("class BuildProfileBuilder"));
    }

    private static Map<String, Integer> dependencyCounts() throws IOException {
        Map<String, Integer> counts = new HashMap<>();
        Path capabilities = AGENTS.resolve("capabilities");
        try (var packages = Files.list(capabilities)) {
            for (Path sourcePackage : packages.filter(Files::isDirectory).toList()) {
                String sourceName = sourcePackage.getFileName().toString();
                try (var files = Files.walk(sourcePackage)) {
                    for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                        Matcher matcher = CAPABILITY_IMPORT.matcher(Files.readString(file));
                        while (matcher.find()) {
                            String target = matcher.group(1);
                            if (!target.equals(sourceName)) {
                                counts.merge(sourceName + "->" + target, 1, Integer::sum);
                            }
                        }
                    }
                }
            }
        }
        return counts;
    }

    private static void assertTreeExcludes(Path root,
                                           List<String> forbidden,
                                           String rationale) throws IOException {
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                for (String token : forbidden) {
                    assertFalse(source.contains(token),
                            () -> file + " contains " + token + ": " + rationale);
                }
            }
        }
    }
}
