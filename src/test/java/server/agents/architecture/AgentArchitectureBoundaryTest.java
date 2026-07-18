package server.agents.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentArchitectureBoundaryTest {
    private static final Path AGENTS = Path.of("src", "main", "java", "server", "agents");
    private static final Pattern CAPABILITY_IMPORT = Pattern.compile(
            "^import server\\.agents\\.capabilities\\.([^.]+)\\.", Pattern.MULTILINE);

    @Test
    void pureContractsDoNotDependOnCosmicRuntimeObjects() throws IOException {
        List<Path> roots = List.of(
                AGENTS.resolve("model"),
                AGENTS.resolve("capabilities").resolve("contracts"),
                AGENTS.resolve("policy").resolve("behavior"));
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
    void highestRiskConcreteCapabilityDependenciesCannotIncrease() throws IOException {
        Map<String, Integer> ceilings = Map.ofEntries(
                Map.entry("navigation->movement", 74),
                Map.entry("movement->navigation", 41),
                Map.entry("combat->movement", 61),
                Map.entry("combat->navigation", 12),
                Map.entry("trade->inventory", 56),
                Map.entry("trade->dialogue", 25),
                Map.entry("supplies->dialogue", 10),
                Map.entry("supplies->combat", 9));
        Map<String, Integer> actual = dependencyCounts();
        ceilings.forEach((edge, ceiling) -> assertTrue(actual.getOrDefault(edge, 0) <= ceiling,
                () -> edge + " concrete imports increased above migration ceiling " + ceiling));
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
}
