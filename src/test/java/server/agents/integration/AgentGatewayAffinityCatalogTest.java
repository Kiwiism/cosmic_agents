package server.agents.integration;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentGatewayAffinityCatalogTest {
    private static final Path INTEGRATION_SOURCE = Path.of("src/main/java/server/agents/integration");
    private static final Pattern GATEWAY_DECLARATION = Pattern.compile(
            "public\\s+(?:interface|final\\s+class)\\s+([A-Za-z0-9_]+Gateway)\\b");

    @Test
    void everyGatewayTypeAndOperationHasAnAffinityClassification() {
        for (Class<?> gatewayType : AgentGatewayAffinityCatalog.gatewayTypes()) {
            assertNotNull(AgentGatewayAffinityCatalog.affinity(gatewayType), gatewayType.getName());
            for (Method method : gatewayType.getDeclaredMethods()) {
                assertNotNull(AgentGatewayAffinityCatalog.affinity(method), method.toString());
            }
        }
    }

    @Test
    void externalAndReadOnlyOverridesRemainExplicit() throws Exception {
        assertEquals(
                AgentGatewayThreadAffinity.ASYNC_EXTERNAL,
                AgentGatewayAffinityCatalog.affinity(
                        CharacterGateway.class.getMethod("save", client.Character.class, boolean.class)));
        assertEquals(
                AgentGatewayThreadAffinity.READ_ONLY_SNAPSHOT,
                AgentGatewayAffinityCatalog.affinity(SkillGateway.class));
        assertTrue(AgentGatewayAffinityCatalog.multiShardReady());
    }

    @Test
    void everyDeclaredGatewayContractIsInTheClosedAuditCatalog() throws Exception {
        Set<String> catalogNames = AgentGatewayAffinityCatalog.gatewayTypes().stream()
                .map(Class::getSimpleName)
                .collect(Collectors.toSet());
        try (var files = Files.list(INTEGRATION_SOURCE)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith("Gateway.java")).toList()) {
                var matcher = GATEWAY_DECLARATION.matcher(Files.readString(file));
                if (matcher.find()) {
                    assertTrue(catalogNames.contains(matcher.group(1)),
                            matcher.group(1) + " is missing an affinity classification");
                }
            }
        }
    }
}
