package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentMailboxRuntime;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentChatMailboxBoundaryTest {
    @AfterEach
    void clearFlag() {
        System.clearProperty("agents.mailbox.enabled");
    }

    @Test
    void mailboxIsDisabledByDefault() {
        System.clearProperty("agents.mailbox.enabled");
        assertFalse(AgentMailboxRuntime.enabled());
        System.setProperty("agents.mailbox.enabled", "true");
        assertTrue(AgentMailboxRuntime.enabled());
    }

    @Test
    void chatIngressUsesMailboxCompatibilityDispatcher() throws Exception {
        String route = Files.readString(Path.of(
                "src/main/java/server/agents/capabilities/dialogue/AgentChatRouteCoordinator.java"));
        String whisper = Files.readString(Path.of(
                "src/main/java/server/agents/integration/cosmic/CosmicAgentWhisperCommandBridge.java"));

        assertTrue(route.contains("AgentChatMailboxDispatcher.handleChat"));
        assertTrue(whisper.contains("AgentChatMailboxDispatcher::handleChat"));
    }
}
