package server.agents.capabilities.navigation;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentNavigationCacheIsolationTest {
    @Test
    void testRuntimeUsesMavenTargetCache() {
        Path cacheDirectory = AgentNavigationGraphService.cacheDirectory().toAbsolutePath().normalize();
        Path buildDirectory = Path.of("target").toAbsolutePath().normalize();

        assertTrue(cacheDirectory.startsWith(buildDirectory),
                () -> "tests must not write navigation graphs into the live cache: " + cacheDirectory);
    }
}
