package server.agents.capabilities.dialogue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import server.agents.runtime.AgentMailboxRuntime;
import server.agents.runtime.AgentRuntimeEntry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

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
        assertTrue(whisper.contains("AgentChatMailboxDispatcher.handleChat"));
    }

    @Test
    void enabledMailboxReturnsImmediatelyAndCompletesWhenAgentDrainsIt() {
        System.setProperty("agents.mailbox.enabled", "true");
        AgentRuntimeEntry entry = new AgentRuntimeEntry(null, null, null);

        CompletableFuture<Boolean> result = AgentChatMailboxDispatcher.handleChat(entry, "help");

        assertFalse(result.isDone());
        assertTrue(entry.actionMailbox().size() > 0);

        AgentMailboxRuntime.drain(entry);

        assertTrue(result.isDone());
        result.join();
    }

    @Test
    void dispatcherContainsNoBlockingResultWait() throws Exception {
        String dispatcher = Files.readString(Path.of(
                "src/main/java/server/agents/capabilities/dialogue/AgentChatMailboxDispatcher.java"));

        assertFalse(dispatcher.contains(".get("));
        assertFalse(dispatcher.contains("TimeUnit"));
    }
}
