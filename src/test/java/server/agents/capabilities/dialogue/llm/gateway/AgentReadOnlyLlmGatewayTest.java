package server.agents.capabilities.dialogue.llm.gateway;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentReadOnlyLlmGatewayTest {
    @AfterEach
    void reset() {
        AgentReadOnlyLlmGateway.resetDialogueProvider();
    }

    @Test
    void delegatesOnlyImmutableTextAndContainsProviderFailure() {
        AgentReadOnlyLlmGateway.installDialogueProvider(
                (prompt, system) -> Optional.of(prompt + "|" + system));
        assertEquals("hello|stay in character",
                AgentReadOnlyLlmGateway.generateDialogue(
                        "hello", "stay in character").orElseThrow());

        AgentReadOnlyLlmGateway.installDialogueProvider(
                (prompt, system) -> { throw new IllegalStateException("offline"); });
        assertEquals(Optional.empty(),
                AgentReadOnlyLlmGateway.generateDialogue("hello", "system"));
    }
}
