package server.agents.integration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentClientGatewayBoundaryTest {
    @Test
    void headlessClientImplementationLivesAtCosmicBoundary() throws IOException {
        String spawn = Files.readString(Path.of(
                "src/main/java/server/agents/commands/AgentSpawnCommandExecutor.java"));
        String offlineRuntime = Files.readString(Path.of(
                "src/main/java/server/agents/integration/cosmic/CosmicAgentOfflineLoader.java"));
        String offlineService = Files.readString(Path.of(
                "src/main/java/server/agents/integration/cosmic/CosmicAgentOfflineLoadService.java"));
        String maker = Files.readString(Path.of(
                "src/main/java/server/agents/capabilities/build/AgentMakerService.java"));
        String lifecycle = Files.readString(Path.of(
                "src/main/java/server/agents/runtime/AgentLifecycleService.java"));
        String session = Files.readString(Path.of(
                "src/main/java/server/agents/runtime/AgentSessionRuntime.java"));
        String support = Files.readString(Path.of(
                "src/main/java/server/agents/capabilities/combat/AgentSupportSpecialMoveExecutor.java"));
        String cosmic = Files.readString(Path.of(
                "src/main/java/server/agents/integration/cosmic/CosmicAgentClientGateway.java"));

        assertFalse(spawn.contains("BotClient"));
        assertFalse(spawn.contains("BotCreator"));
        assertFalse(offlineRuntime.contains("BotClient"));
        assertFalse(offlineService.contains("BotClient"));
        assertFalse(maker.contains("import client.Client"));
        assertFalse(maker.contains("bot.getClient()"));
        assertFalse(lifecycle.contains("leader.getClient()"));
        assertFalse(session.contains("bot.getClient()"));
        assertFalse(session.contains("saveCharToDB"));
        assertFalse(support.contains("agent.getClient()"));
        assertTrue(spawn.contains("AgentClientGatewayRuntime.clients()"));
        assertTrue(offlineRuntime.contains("AgentClientGatewayRuntime.clients()"));
        assertTrue(maker.contains("AgentClientGatewayRuntime.clients().tryAcquire(bot)"));
        assertTrue(lifecycle.contains("AgentClientGatewayRuntime.clients().world(leader)"));
        assertTrue(session.contains("AgentClientGatewayRuntime.clients().channel(bot)"));
        assertTrue(session.contains("AgentCharacterGatewayRuntime.characters().save"));
        assertTrue(support.contains("AgentClientGatewayRuntime.clients().hasClient(agent)"));
        assertTrue(cosmic.contains("new BotClient(world, channel)"));
        assertTrue(cosmic.contains("BotCreator.createCharacter(client, name)"));
    }
}
