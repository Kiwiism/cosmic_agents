package server.agents.social;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocialDialogueArchitectureBoundaryTest {
    @Test
    void providerAndOllamaAdapterCannotReachMutableAgentOrCosmicRuntime() throws Exception {
        String provider = Files.readString(Path.of(
                "src/main/java/server/agents/social/provider/DialogueProvider.java"));
        String ollama = Files.readString(Path.of(
                "src/main/java/server/agents/integration/ollama/OllamaDialogueProvider.java"));
        String application = Files.readString(Path.of(
                "src/main/java/server/agents/social/conversation/AgentSocialDialogueApplication.java"));

        for (String source : new String[]{provider, ollama}) {
            assertFalse(source.contains("client.Character"));
            assertFalse(source.contains("server.maps.MapleMap"));
            assertFalse(source.contains("AgentRuntimeEntry"));
            assertFalse(source.contains("AgentMailboxRuntime"));
        }
        assertTrue(application.contains("AgentAsyncTaskGateway"));
        assertTrue(application.contains("AgentAsyncWorkKind.LLM_NETWORK"));
        assertFalse(Files.exists(Path.of(
                "src/main/java/server/agents/capabilities/dialogue/llm/AgentLlmReplyService.java")));
    }
}
