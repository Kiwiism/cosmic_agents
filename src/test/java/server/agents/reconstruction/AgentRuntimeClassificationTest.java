package server.agents.reconstruction;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentRuntimeClassificationTest {
    private static final Path RUNTIME = Path.of("src", "main", "java", "server", "agents", "runtime");
    private static final Path CLASSIFICATION = Path.of("docs", "agents", "AGENT_RUNTIME_CLASSIFICATION.md");
    private static final Pattern ROW = Pattern.compile(
            "^\\| `([^`]+)` \\| `(LEGITIMATE_RUNTIME_ORCHESTRATION|RUNTIME_SERVICE|RUNTIME_STATE|RUNTIME_ADAPTER)` \\|",
            Pattern.MULTILINE);

    @Test
    void everyRuntimeClassHasExactlyOneApprovedClassification() throws Exception {
        Set<String> runtimeClasses = new TreeSet<>();
        try (Stream<Path> paths = Files.list(RUNTIME)) {
            paths.filter(path -> path.getFileName().toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString().replaceFirst("\\.java$", ""))
                    .forEach(runtimeClasses::add);
        }

        String document = Files.readString(CLASSIFICATION);
        Set<String> documentedClasses = new TreeSet<>();
        Matcher matcher = ROW.matcher(document);
        while (matcher.find()) {
            assertTrue(documentedClasses.add(matcher.group(1)),
                    () -> "Duplicate runtime classification for " + matcher.group(1));
        }

        assertEquals(runtimeClasses, documentedClasses);
    }
}
