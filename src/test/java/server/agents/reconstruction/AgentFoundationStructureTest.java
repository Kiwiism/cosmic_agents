package server.agents.reconstruction;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentFoundationStructureTest {
    @Test
    void foundationClassesExist() throws Exception {
        List<String> classes = List.of(
                "server.agents.api.AgentService",
                "server.agents.runtime.AgentSession",
                "server.agents.capabilities.supplies.AgentAutopotCleanupService",
                "server.agents.capabilities.party.AgentPartyLifecycleService",
                "server.agents.runtime.AgentSpawnPositionService",
                "server.agents.runtime.AgentTickScheduler",
                "server.agents.commands.AgentCommandRouter",
                "server.agents.commands.AgentCommandParser",
                "server.agents.plans.AgentPlanRunner",
                "server.agents.capabilities.AgentCapability",
                "server.agents.events.AgentEventBus",
                "server.agents.policy.AgentCombatPolicy",
                "server.agents.profiles.AgentProfileRepository",
                "server.agents.legacy.LegacyBotParityAdapter",
                "server.agents.integration.AgentServerAdapter",
                "server.agents.integration.cosmic.CosmicAgentServerAdapter");

        for (String className : classes) {
            Class.forName(className);
        }
    }

    @Test
    void productionBotPackageRemainsAbsent() {
        assertFalse(Files.exists(Path.of("src", "main", "java", "server", "bots")));
    }
}
